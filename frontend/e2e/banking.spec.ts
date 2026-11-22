/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: banking.spec.ts
 * Description: Exercises customer workflows in Chromium against the live stack.
 * Project version: 0.1.0
 */

import { randomUUID } from 'node:crypto'
import { readFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'

// Exercises the full browser workflow against live services, including recovery after a dropped transfer response.
test('registers, funds, transfers, downloads both statement formats and signs out', async ({ page }) => {
  const email = 'browser-' + randomUUID() + '@example.test'
  const password = 'educational-test-password'
  await page.goto('/')
  await page.getByRole('button', { name: 'Create a profile' }).click()
  await page.getByLabel('Full name').fill('Test Customer')
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Create profile', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Profile created')
  await page.getByLabel('Password').fill(password)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible()

  const navigation = page.getByRole('navigation', { name: 'Banking' })
  await navigation.getByRole('button', { name: 'Accounts', exact: true }).click()
  await page.getByRole('button', { name: 'Open account' }).click()
  await expect(page.getByRole('heading', { name: 'Chequing' })).toBeVisible()
  await page.getByLabel('Account type').selectOption('SAVINGS')
  await page.getByRole('button', { name: 'Open account' }).click()
  await expect(page.getByRole('heading', { name: 'Savings' })).toBeVisible()
  await page.getByRole('button', { name: /Add demo funds to Chequing/ }).click()
  await expect(page.getByRole('status')).toContainText('$500.00 CAD in simulated funds added')

  await navigation.getByRole('button', { name: 'Transfer money', exact: true }).click()
  const to = page.getByLabel('To account')
  await to.selectOption({ index: 1 })
  await page.getByLabel('Amount (CAD)').fill('125.50')
  await page.getByRole('button', { name: 'Review transfer' }).click()
  await page.getByRole('button', { name: 'Confirm transfer' }).click()
  await expect(page.getByRole('heading', { name: 'Transfer complete' })).toBeVisible()
  await expect(page.getByText('Reference', { exact: true })).toBeVisible()

  await navigation.getByRole('button', { name: 'Overview', exact: true }).click()
  await expect(page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Chequing' }) }))
    .toContainText('$374.50 CAD')
  await expect(page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Savings' }) }))
    .toContainText('$125.50 CAD')
  await navigation.getByRole('button', { name: 'Transactions', exact: true }).click()
  await expect(page.getByText('Internal transfer', { exact: true })).toBeVisible()
  await expect(page.getByText('Demo funding', { exact: true })).toBeVisible()

  await navigation.getByRole('button', { name: 'Statements', exact: true }).click()
  await page.getByRole('button', { name: 'Generate statement' }).click()
  await expect(page.getByRole('heading', { name: 'Statement summary' })).toBeVisible()
  const csvEvent = page.waitForEvent('download')
  await page.getByRole('link', { name: 'Download CSV' }).click()
  const csv = await csvEvent
  expect(csv.suggestedFilename()).toMatch(/\.csv$/)
  expect(await readFile(await csv.path(), 'utf8')).toContain('"Closing balance","374.50"')
  const pdfEvent = page.waitForEvent('download')
  await page.getByRole('link', { name: 'Download PDF' }).click()
  const pdf = await pdfEvent
  expect((await readFile(await pdf.path())).subarray(0, 5).toString()).toBe('%PDF-')

  // Let PostgreSQL commit, then drop the response before React receives it.
  // Refreshing the tab must recover the key and find the existing receipt.
  await page.route('**/api/v1/transfers', async route => {
    const committed = await route.fetch()
    expect(committed.status()).toBe(201)
    await route.abort('failed')
  }, { times: 1 })
  await navigation.getByRole('button', { name: 'Transfer money', exact: true }).click()
  await page.getByLabel('To account').selectOption({ index: 1 })
  await page.getByLabel('Amount (CAD)').fill('10.00')
  await page.getByRole('button', { name: 'Review transfer' }).click()
  await page.getByRole('button', { name: 'Confirm transfer' }).click()
  await expect(page.getByRole('heading', { name: 'Transfer outcome unconfirmed' })).toBeVisible()
  await page.reload()
  await navigation.getByRole('button', { name: 'Transfer money', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Transfer outcome unconfirmed' })).toBeVisible()
  await page.getByRole('button', { name: 'Check status', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Transfer complete' })).toBeVisible()
  await navigation.getByRole('button', { name: 'Overview', exact: true }).click()
  await expect(page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Chequing' }) }))
    .toContainText('$364.50 CAD')

  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  expect((await page.request.get('/api/v1/accounts')).status()).toBe(401)
})

// Checks the narrow-screen layout and confirms that readiness includes the database.
test('sign-in fits a narrow browser and service readiness reaches PostgreSQL', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBe(360)
  const readiness = await page.request.get('/actuator/health/readiness')
  expect(readiness.status()).toBe(200)
  expect(await readiness.json()).toEqual({ status: 'UP' })
})

// @vitest-environment jsdom
/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: banking-ui.test.tsx
 * Description: Tests form behavior, API feedback, and transfer recovery.
 * Project version: 0.1.0
 */

import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App } from '../App'
import { AuthPage } from '../pages/AuthPage'
import { HistoryPage } from '../pages/HistoryPage'
import { StatementsPage } from '../pages/StatementsPage'
import { TransferPage, validAmount } from '../pages/TransferPage'
import { ApiError, api } from '../services/api'
import type { Account, TransferReceipt } from '../types'

const from: Account = {
  id: '71bbce1e-edf8-47d9-972e-2cfa2802c91d', type: 'CHEQUING', status: 'ACTIVE',
  currency: 'CAD', createdAt: '2026-11-22T12:00:00Z', balance: '500.00',
}
const to: Account = { ...from, id: '0ae384d5-15a8-4e5f-ae43-6af289568784', type: 'SAVINGS', balance: '0.00' }
const accounts = [from, to]
const receipt: TransferReceipt = {
  id: '1ae384d5-15a8-4e5f-ae43-6af289568784', kind: 'TRANSFER', status: 'COMPLETED',
  currency: 'CAD', sourceAccountId: from.id, destinationAccountId: to.id,
  amount: '12.50', completedAt: '2026-11-22T13:00:00Z',
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  sessionStorage.clear()
})

describe('Northstar browser screens', () => {
  // Verifies that local form constraints prevent a request and that server login errors remain safe.
  it('applies local login constraints and renders a safe authentication error', async () => {
    const login = vi.spyOn(api, 'login').mockRejectedValue(new ApiError(401, 'INVALID_CREDENTIALS', 'Email or password is incorrect.'))
    const user = userEvent.setup()
    render(<AuthPage onSignIn={vi.fn()} />)
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(login).not.toHaveBeenCalled()
    await user.type(screen.getByLabelText('Email address'), 'tester@example.test')
    await user.type(screen.getByLabelText('Password'), 'bad-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect((await screen.findByRole('alert')).textContent).toBe('Email or password is incorrect.')
  })

  // Checks that account balances come from the response shown after loading.
  it('loads and renders account balances supplied by the backend', async () => {
    vi.spyOn(api, 'me').mockResolvedValue({
      id: 'demo', fullName: 'Demo Customer', email: 'demo@example.test', createdAt: from.createdAt,
    })
    vi.spyOn(api, 'accounts').mockResolvedValue({ accounts, totalBalance: '500.00', demoFundingEnabled: true })
    vi.spyOn(api, 'history').mockResolvedValue({ items: [], hasNext: false, page: 0, size: 5 })
    render(<App />)
    expect(screen.getByRole('status').textContent).toMatch(/checking your session/i)
    expect(await screen.findByRole('heading', { name: 'Chequing' })).toBeTruthy()
    expect(screen.getAllByText('$500.00')).toHaveLength(2)
    expect(screen.getByText('Welcome, Demo Customer')).toBeTruthy()
  })

  // Controls the request completion to verify both the loading state and the rendered activity.
  it('displays a transaction after its loading state', async () => {
    let resolve: (value: Awaited<ReturnType<typeof api.history>>) => void = () => undefined
    vi.spyOn(api, 'history').mockReturnValue(new Promise(done => { resolve = done }))
    render(<HistoryPage accounts={accounts} />)
    expect(screen.getByRole('status').textContent).toMatch(/loading transactions/i)
    resolve({
      items: [{ id: 'entry', transferId: receipt.id, direction: 'CREDIT', amount: '12.50',
        createdAt: receipt.completedAt, type: receipt.kind, status: receipt.status }],
      hasNext: false, page: 0, size: 20,
    })
    expect(await screen.findByText('Internal transfer')).toBeTruthy()
    expect(screen.getByText('+$12.50')).toBeTruthy()
  })

  // Simulates an interrupted transfer and checks that the retry preserves its original key and payload.
  it('rejects invalid decimal input and reuses the key and body after an uncertain transfer', async () => {
    expect(validAmount('0')).toBe(false)
    expect(validAmount('1.001')).toBe(false)
    expect(validAmount('1e2')).toBe(false)
    const transfer = vi.spyOn(api, 'transfer').mockRejectedValueOnce(new TypeError('Connection interrupted'))
      .mockResolvedValueOnce(receipt)
    vi.spyOn(api, 'transferByKey').mockRejectedValue(new ApiError(404, 'TRANSFER_NOT_FOUND', 'No committed transfer was found.'))
    const user = userEvent.setup()
    render(<TransferPage accounts={accounts} customerId="demo" onChange={vi.fn()} />)
    await user.selectOptions(screen.getByLabelText('To account'), to.id)
    await user.type(screen.getByLabelText('Amount (CAD)'), '12.50')
    await user.click(screen.getByRole('button', { name: 'Review transfer' }))
    await user.click(screen.getByRole('button', { name: 'Confirm transfer' }))
    expect(await screen.findByRole('heading', { name: 'Transfer outcome unconfirmed' })).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Check status' }))
    expect(await screen.findByText(/no committed transfer is visible/i)).toBeTruthy()
    await user.click(screen.getByRole('button', { name: 'Retry same request' }))
    expect(await screen.findByRole('heading', { name: 'Transfer complete' })).toBeTruthy()
    expect(transfer).toHaveBeenCalledTimes(2)
    expect(transfer.mock.calls[0]).toEqual(transfer.mock.calls[1])
  })

  // Checks that download links are built from the same account and dates as the preview.
  it('uses the previewed account and dates in the statement download links', async () => {
    const summary = vi.spyOn(api, 'statement').mockResolvedValue({
      accountId: from.id, openingBalance: '0.00', closingBalance: '500.00', credits: '500.00', debits: '0.00',
      entries: 1, pdfAvailable: true,
    })
    const user = userEvent.setup()
    render(<StatementsPage accounts={accounts} />)
    expect(screen.queryByRole('link', { name: 'Download CSV' })).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Generate statement' }))
    const csv = await screen.findByRole('link', { name: 'Download CSV' })
    const pdf = screen.getByRole('link', { name: 'Download PDF' })
    expect(csv.getAttribute('href')).toBe(api.statementUrl(summary.mock.calls[0][0], 'CSV'))
    expect(pdf.getAttribute('href')).toBe(api.statementUrl(summary.mock.calls[0][0], 'PDF'))
  })
})

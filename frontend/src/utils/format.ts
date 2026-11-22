/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: format.ts
 * Description: Formats exact decimal amounts and UTC timestamps for display.
 * Project version: 0.1.0
 */

import type { Account } from '../types'

/** Adds presentation separators without converting exact decimal strings to binary floats. */
export function money(amount: string) {
  const [whole, cents = '00'] = amount.replace(/^-/, '').split('.')
  const grouped = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return (amount.startsWith('-') ? '−' : '') + '$' + grouped + '.' + cents.padEnd(2, '0')
}

export function accountName(account: Account) {
  return account.type === 'CHEQUING' ? 'Chequing' : 'Savings'
}

export function accountLabel(account: Account) {
  return accountName(account) + ' · ' + account.id.slice(-6).toUpperCase()
}

const dateTime = new Intl.DateTimeFormat('en-CA', {
  dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC',
})

export function dateLabel(value: string) {
  return dateTime.format(new Date(value)) + ' UTC'
}

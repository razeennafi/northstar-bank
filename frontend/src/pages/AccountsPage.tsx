/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AccountsPage.tsx
 * Description: Opens accounts, requests demo funding, and renders authoritative balances.
 * Project version: 0.1.0
 */

import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { api, friendlyError } from '../services/api'
import type { Account, AccountOverview, AccountType } from '../types'
import { accountLabel, accountName, dateLabel, money } from '../utils/format'

// Displays an account’s server-provided balance and allows the caller to supply an optional action.
export function AccountCard({ account, details = false, action }: {
  account: Account
  details?: boolean
  action?: ReactNode
}) {
  return (
    <article className="account-card">
      <div className="account-card-heading">
        <span className="account-symbol" aria-hidden="true">{account.type === 'CHEQUING' ? 'C' : 'S'}</span>
        <div><h3>{accountName(account)}</h3><small>•• {account.id.slice(-6).toUpperCase()}</small></div>
      </div>
      <p className="account-amount">{money(account.balance)} <small>CAD</small></p>
      {details && (
        <details>
          <summary>Account details</summary>
          <dl className="account-details">
            <dt>Account ID</dt><dd className="reference">{account.id}</dd>
            <dt>Status</dt><dd>{account.status === 'ACTIVE' ? 'Active' : 'Closed'}</dd>
            <dt>Opened</dt><dd>{dateLabel(account.createdAt)}</dd>
          </dl>
        </details>
      )}
      {action}
    </article>
  )
}

export function AccountsPage({ overview, onChange }: { overview: AccountOverview; onChange: () => void }) {
  const [type, setType] = useState<AccountType>('CHEQUING')
  const [busy, setBusy] = useState<string>()
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [funded, setFunded] = useState<string[]>([])

  // Opens the selected account type and refreshes the overview after the API confirms creation.
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy('create')
    setError('')
    setNotice('')
    try {
      const account = await api.createAccount(type)
      setNotice(accountName(account) + ' account opened.')
      onChange()
    } catch (failure) {
      setError(friendlyError(failure))
    } finally {
      setBusy(undefined)
    }
  }

  // Requests a one-time simulated grant and refreshes the balance only after confirmation.
  async function fund(account: Account) {
    setBusy(account.id)
    setError('')
    setNotice('')
    try {
      const receipt = await api.fund(account.id)
      setFunded(previous => [...previous, account.id])
      setNotice(money(receipt.amount) + ' CAD in simulated funds added to ' + accountLabel(account) + '.')
      onChange()
    } catch (failure) {
      setError(friendlyError(failure))
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <div className="stack">
      <section className="panel">
        <h2>Open an account</h2>
        <form className="inline-form" onSubmit={create}>
          <label><span>Account type</span><select value={type} onChange={event => setType(event.target.value as AccountType)}>
            <option value="CHEQUING">Chequing</option><option value="SAVINGS">Savings</option>
          </select></label>
          <button disabled={!!busy}>{busy === 'create' ? 'Opening…' : 'Open account'}</button>
        </form>
        {notice && <p className="feedback success" role="status">{notice}</p>}
        {error && <p className="feedback error" role="alert">{error}</p>}
      </section>
      <section aria-labelledby="owned-accounts">
        <h2 id="owned-accounts">Your accounts</h2>
        {!overview.accounts.length && <p className="empty">Your new accounts will appear here.</p>}
        <div className="account-grid">
          {overview.accounts.map(account => (
            <AccountCard key={account.id} account={account} details action={
              overview.demoFundingEnabled && account.status === 'ACTIVE' && (
                <button type="button" className="secondary fund-button" disabled={!!busy || funded.includes(account.id)}
                  aria-label={'Add demo funds to ' + accountLabel(account)} onClick={() => void fund(account)}>
                  {busy === account.id ? 'Adding funds…' : funded.includes(account.id) ? 'Demo funds added' : 'Add $500 demo funds'}
                </button>
              )
            } />
          ))}
        </div>
      </section>
    </div>
  )
}

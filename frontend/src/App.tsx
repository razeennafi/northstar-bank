/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: App.tsx
 * Description: Restores the customer session and routes the authenticated workspace.
 * Project version: 0.1.0
 */

import { useEffect, useState } from 'react'
import { useResource } from './hooks/useResource'
import { AccountCard, AccountsPage } from './pages/AccountsPage'
import { AuthPage } from './pages/AuthPage'
import { HistoryPage } from './pages/HistoryPage'
import { StatementsPage } from './pages/StatementsPage'
import { TransferPage } from './pages/TransferPage'
import { api, friendlyError, sessionExpired } from './services/api'
import type { Customer } from './types'
import { dateLabel, money } from './utils/format'

const pages = ['Overview', 'Accounts', 'Transfer money', 'Transactions', 'Statements', 'Profile'] as const
type Page = typeof pages[number]

export function App() {
  const session = useResource(api.me)
  const [customer, setCustomer] = useState<Customer | null>()
  // Undefined defers to the initial session check. Null is an explicit sign-out.
  const current = customer === undefined ? session.value : customer

  useEffect(() => {
    const expire = () => setCustomer(null)
    window.addEventListener(sessionExpired, expire)
    return () => window.removeEventListener(sessionExpired, expire)
  }, [])

  if (current) return <Workspace key={current.id} customer={current} onSignOut={() => setCustomer(null)} />
  if (current === null) return <AuthPage onSignIn={setCustomer} />
  return (
    <main className="boot">
      <h1>Northstar Bank</h1>
      {session.error ? (
        <><p className="feedback error" role="alert">The banking service is unavailable.</p>
          <button type="button" onClick={session.reload}>Try again</button></>
      ) : <p role="status">Checking your session…</p>}
    </main>
  )
}

// Owns the signed-in navigation and refreshes the account overview after successful changes.
function Workspace({ customer, onSignOut }: { customer: Customer; onSignOut: () => void }) {
  const [page, setPage] = useState<Page>('Overview')
  const [signingOut, setSigningOut] = useState(false)
  const [notice, setNotice] = useState('')
  const overview = useResource(api.accounts)

  // Ends the server session before clearing the signed-in workspace.
  async function signOut() {
    setSigningOut(true)
    setNotice('')
    try {
      await api.logout()
      onSignOut()
    } catch (failure) {
      setNotice(friendlyError(failure))
    } finally {
      setSigningOut(false)
    }
  }

  const accounts = overview.value?.accounts ?? []
  return (
    <div className="workspace">
      <a className="skip-link" href="#main">Skip to main content</a>
      <aside className="sidebar">
        <div className="wordmark"><span className="brand-mark" aria-hidden="true">✦</span> Northstar Bank</div>
        <nav aria-label="Banking">
          {pages.map(item => (
            <button key={item} type="button" aria-current={page === item ? 'page' : undefined}
              onClick={() => setPage(item)}>{item}</button>
          ))}
        </nav>
        <span className="sidebar-footer">Educational simulation</span>
      </aside>
      <main id="main" className="workspace-main">
        <header className="page-header">
          <div><h1>{page}</h1>{page === 'Overview' && <p>Welcome, {customer.fullName}</p>}</div>
          <button type="button" className="secondary" disabled={signingOut} onClick={() => void signOut()}>
            {signingOut ? 'Signing out…' : 'Sign out'}
          </button>
        </header>
        {notice && <p className="feedback error" role="alert">{notice}</p>}
        {page !== 'Profile' && overview.error && <div className="feedback error" role="alert">
          {overview.error} <button type="button" className="secondary" onClick={overview.reload}>Try again</button>
        </div>}
        {page !== 'Profile' && overview.loading && !overview.value && <p className="panel empty" role="status">Loading accounts…</p>}
        {page === 'Overview' && overview.value && (
          <div className="stack dashboard">
            <section className="balance-card">
              <div><h2>Total balance</h2><p>{money(overview.value.totalBalance)} <small>CAD</small></p></div>
              <button type="button" onClick={() => setPage(accounts.length ? 'Transfer money' : 'Accounts')}>
                {accounts.length ? 'Make a transfer' : 'Open an account'}
              </button>
            </section>
            <section className="stack" aria-label="Account balances">
              <div className="section-heading"><h2>Your accounts</h2>
                {!!accounts.length && <button className="secondary" type="button" onClick={() => setPage('Accounts')}>Manage accounts</button>}
              </div>
              {accounts.length ? <div className="account-grid">
                {accounts.map(account => <AccountCard key={account.id} account={account} />)}
              </div> : <p className="empty">You don’t have any accounts yet.</p>}
            </section>
            <HistoryPage accounts={accounts} compact />
          </div>
        )}
        {page === 'Accounts' && overview.value && <AccountsPage overview={overview.value} onChange={overview.reload} />}
        {page === 'Transfer money' && overview.value && <TransferPage customerId={customer.id} accounts={accounts} onChange={overview.reload} />}
        {page === 'Transactions' && overview.value && <HistoryPage accounts={accounts} />}
        {page === 'Statements' && overview.value && <StatementsPage accounts={accounts} />}
        {page === 'Profile' && <section className="panel narrow">
          <h2>Your information</h2>
          <dl className="review"><dt>Name</dt><dd>{customer.fullName}</dd><dt>Email address</dt><dd>{customer.email}</dd>
            <dt>Member since</dt><dd>{dateLabel(customer.createdAt)}</dd>
          </dl>
        </section>}
      </main>
    </div>
  )
}

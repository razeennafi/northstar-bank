/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: HistoryPage.tsx
 * Description: Filters and paginates ledger entries for a selected account.
 * Project version: 0.1.0
 */

import { useCallback, useState } from 'react'
import { AccountSelect } from '../components/AccountSelect'
import { EntryTable } from '../components/EntryTable'
import { useResource } from '../hooks/useResource'
import { api } from '../services/api'
import type { Account } from '../types'

export function HistoryPage({ accounts, compact = false }: { accounts: Account[]; compact?: boolean }) {
  const [selected, setSelected] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [sort, setSort] = useState('DESC')
  const [page, setPage] = useState(0)
  const id = selected || accounts[0]?.id || ''
  const invalidRange = !!from && !!to && from > to
  // Each change starts a new read. useResource aborts the previous read on cleanup.
  // Builds the history request from the current account, date window, sort order, and page.
  const load = useCallback((signal: AbortSignal) => {
    if (!id || invalidRange) return Promise.resolve({ items: [], hasNext: false, page: 0, size: 0 })
    const params = new URLSearchParams({ page: String(page), size: compact ? '5' : '20', sort })
    if (from) params.set('from', from)
    if (to) params.set('to', to)
    return api.history(id, params, signal)
  }, [id, from, to, page, sort, compact, invalidRange])
  const activity = useResource(load)

  return (
    <section className="panel stack" aria-label={compact ? 'Recent activity' : 'Transaction history'}>
      {compact && <h2>Recent activity</h2>}
      {accounts.length === 0 ? <p className="empty">Open an account to see its activity.</p> : (
        <>
          <div className="filters">
            <AccountSelect label="Account" accounts={accounts} selected={id} setSelected={value => { setSelected(value); setPage(0) }} />
            {!compact && <>
              <label><span>From (UTC)</span><input type="date" value={from} onChange={event => { setFrom(event.target.value); setPage(0) }} /></label>
              <label><span>To (UTC)</span><input type="date" value={to} onChange={event => { setTo(event.target.value); setPage(0) }} /></label>
              <label><span>Sort by date</span><select value={sort} onChange={event => { setSort(event.target.value); setPage(0) }}>
                <option value="DESC">Newest first</option><option value="ASC">Oldest first</option>
              </select></label>
            </>}
          </div>
          {invalidRange && <p className="feedback error" role="alert">Choose an end date on or after the start date.</p>}
          {!invalidRange && activity.loading && <p role="status" className="empty">Loading transactions…</p>}
          {!invalidRange && activity.error && (
            <div className="feedback error" role="alert">{activity.error} <button type="button" className="secondary" onClick={activity.reload}>Try again</button></div>
          )}
          {!invalidRange && !activity.loading && !activity.error && activity.value && (
            <>
              <EntryTable entries={activity.value.items} showDetails={!compact} />
              {!compact && (page > 0 || activity.value.hasNext) && (
                <div className="pagination">
                  <button type="button" className="secondary" disabled={page === 0} onClick={() => setPage(previous => previous - 1)}>Previous</button>
                  <span>Page {page + 1}</span>
                  <button type="button" className="secondary" disabled={!activity.value.hasNext} onClick={() => setPage(previous => previous + 1)}>Next</button>
                </div>
              )}
            </>
          )}
        </>
      )}
    </section>
  )
}

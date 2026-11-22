/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: StatementsPage.tsx
 * Description: Previews statement totals and exposes authorized download links.
 * Project version: 0.1.0
 */

import { useState } from 'react'
import type { FormEvent } from 'react'
import { AccountSelect } from '../components/AccountSelect'
import { api, friendlyError } from '../services/api'
import type { Account, StatementSelection, StatementSummary } from '../types'
import { money } from '../utils/format'

export function StatementsPage({ accounts }: { accounts: Account[] }) {
  const today = new Date().toISOString().slice(0, 10)
  const [selected, setSelected] = useState('')
  const [from, setFrom] = useState(today.slice(0, 8) + '01')
  const [to, setTo] = useState(today)
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')
  const [preview, setPreview] = useState<{ request: StatementSelection; summary: StatementSummary }>()
  const accountId = selected || accounts[0]?.id || ''

  // Validates the period and requests totals for the selected account before offering downloads.
  async function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setPreview(undefined)
    if (!from || !to || from > to) {
      setError('Choose an end date on or after the start date.')
      return
    }
    const request = { accountId, from, to }
    // Keep the successful query with its totals so download links use the reviewed period.
    setWorking(true)
    try {
      setPreview({ request, summary: await api.statement(request) })
    } catch (failure) {
      setError(friendlyError(failure))
    } finally {
      setWorking(false)
    }
  }

  if (!accounts.length) return <p className="panel empty">Open an account to generate statements.</p>
  return (
    <div className="stack">
      <section className="panel">
        <h2>Statement period</h2>
        <form onSubmit={generate}>
          <fieldset className="filters" disabled={working}>
            <AccountSelect label="Account" accounts={accounts} selected={accountId}
              setSelected={value => { setSelected(value); setPreview(undefined) }} />
            <label><span>From (UTC)</span><input required type="date" value={from} onChange={event => { setFrom(event.target.value); setPreview(undefined) }} /></label>
            <label><span>To (UTC)</span><input required type="date" value={to} onChange={event => { setTo(event.target.value); setPreview(undefined) }} /></label>
            <button>{working ? 'Generating…' : 'Generate statement'}</button>
          </fieldset>
        </form>
        {error && <p role="alert" className="feedback error">{error}</p>}
      </section>
      {preview && (
        <section className="panel stack" aria-label="Statement preview">
          <h2>Statement summary</h2>
          <dl className="summary-grid">
            <div><dt>Opening balance</dt><dd>{money(preview.summary.openingBalance)}</dd></div>
            <div><dt>Closing balance</dt><dd>{money(preview.summary.closingBalance)}</dd></div>
            <div><dt>Credits</dt><dd>{money(preview.summary.credits)}</dd></div>
            <div><dt>Debits</dt><dd>{money(preview.summary.debits)}</dd></div>
          </dl>
          <div className="actions">
            <a className="button" download href={api.statementUrl(preview.request, 'CSV')}>Download CSV</a>
            {preview.summary.pdfAvailable &&
              <a className="button secondary" download href={api.statementUrl(preview.request, 'PDF')}>Download PDF</a>}
          </div>
          {!preview.summary.pdfAvailable && <p>Select a shorter period to download a PDF.</p>}
        </section>
      )}
    </div>
  )
}

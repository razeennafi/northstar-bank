/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: TransferPage.tsx
 * Description: Reviews transfers and recovers uncertain outcomes with the original key.
 * Project version: 0.1.0
 */

import { useState } from 'react'
import type { FormEvent } from 'react'
import { AccountSelect } from '../components/AccountSelect'
import { ApiError, api, friendlyError } from '../services/api'
import type { Account, TransferDetails, TransferReceipt } from '../types'
import { accountLabel, money } from '../utils/format'

type PendingTransfer = { key: string; details: TransferDetails }
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

// Checks CAD input as text so validation does not lose precision through floating-point conversion.
export function validAmount(input: string) {
  return /^\d{1,17}(?:\.\d{1,2})?$/.test(input) && /[1-9]/.test(input)
}

function recover(key: string): PendingTransfer | undefined {
  // Browser storage can be stale or edited. Check the shape before offering a retry.
  try {
    const value: PendingTransfer | null = JSON.parse(sessionStorage.getItem(key) ?? 'null')
    if (value && uuid.test(value.key) && uuid.test(value.details.sourceAccountId)
      && uuid.test(value.details.destinationAccountId) && validAmount(value.details.amount) && value.details.currency === 'CAD') {
      return value
    }
  } catch {
    // A blocked or cleared browser storage area leaves the current form usable.
  }
}

export function TransferPage({ customerId, accounts, onChange }: {
  customerId: string
  accounts: Account[]
  onChange: () => void
}) {
  const storageKey = 'northstar-transfer-' + customerId
  const [pending, setPending] = useState(() => recover(storageKey))
  const [review, setReview] = useState<TransferDetails>()
  const [receipt, setReceipt] = useState<TransferReceipt>()
  const [source, setSource] = useState('')
  const [destination, setDestination] = useState('')
  const [recipient, setRecipient] = useState('')
  const [external, setExternal] = useState(false)
  const [amount, setAmount] = useState('')
  const [working, setWorking] = useState(false)
  const [error, setError] = useState('')
  const [lookupNote, setLookupNote] = useState('')
  const eligible = accounts.filter(account => account.status === 'ACTIVE')

  // Keeps the pending request available across reloads when browser storage is usable.
  function store(value?: PendingTransfer) {
    setPending(value)
    try {
      if (value) sessionStorage.setItem(storageKey, JSON.stringify(value))
      else sessionStorage.removeItem(storageKey)
    } catch {
      // The in-memory copy still protects retries while this page stays open.
    }
  }

  // Builds the review state from validated account choices and amount before any request is sent.
  function prepare(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const from = source || eligible[0]?.id
    const to = external ? recipient.trim() : destination
    if (!from || !uuid.test(to) || from.toLowerCase() === to.toLowerCase()) {
      setError('Choose two different accounts and enter a valid recipient account ID.')
      return
    }
    if (!validAmount(amount.trim())) {
      setError('Enter a positive CAD amount with up to two decimal places.')
      return
    }
    setReview({ sourceAccountId: from, destinationAccountId: to, amount: amount.trim(), currency: 'CAD' })
    setReceipt(undefined)
  }

  // Clears the pending request after a receipt is known and refreshes the displayed balances.
  function complete(result: TransferReceipt) {
    setReceipt(result)
    setReview(undefined)
    store()
    setAmount('')
    onChange()
  }

  // Submits or retries the same request. A transport error leaves the outcome unresolved.
  async function send(attempt: PendingTransfer, firstAttempt: boolean) {
    setWorking(true)
    setError('')
    setLookupNote('')
    store(attempt)
    try {
      complete(await api.transfer(attempt.details, attempt.key))
    } catch (failure) {
      // A missing response or a server error may follow a successful commit.
      // Further attempts always reuse this key and the reviewed request body.
      if (firstAttempt && failure instanceof ApiError && failure.status >= 400 && failure.status < 500) {
        setError(friendlyError(failure))
        store()
      } else {
        setLookupNote('The transfer is still unconfirmed. Check its status or retry with the same request.')
      }
    } finally {
      setWorking(false)
    }
  }

  // Checks for a committed receipt without creating a new posting.
  async function lookup(attempt: PendingTransfer) {
    setWorking(true)
    setLookupNote('')
    try {
      complete(await api.transferByKey(attempt.key))
    } catch (failure) {
      // A lookup sees committed receipts only. A 404 leaves the original request pending.
      setLookupNote(failure instanceof ApiError && failure.status === 404
        ? 'No committed transfer is visible yet. You can retry with this same request.'
        : 'The status check could not finish. This request is still unconfirmed.')
    } finally {
      setWorking(false)
    }
  }

  function name(id: string) {
    const account = accounts.find(value => value.id === id)
    return account ? accountLabel(account) : id
  }

  if (pending) return (
    <section className="panel stack narrow">
      {working ? <h2 role="status">Checking transfer…</h2> : <h2>Transfer outcome unconfirmed</h2>}
      <p>{money(pending.details.amount)} CAD to <span className="reference">{name(pending.details.destinationAccountId)}</span></p>
      <p><small>Request key</small><br /><code className="reference">{pending.key}</code></p>
      {lookupNote && <p className="feedback neutral" role="status">{lookupNote}</p>}
      <div className="actions">
        <button type="button" disabled={working} onClick={() => void lookup(pending)}>Check status</button>
        <button type="button" disabled={working} className="secondary" onClick={() => void send(pending, false)}>Retry same request</button>
      </div>
    </section>
  )

  if (review) return (
    <section className="panel stack narrow" aria-labelledby="review-title">
      <h2 id="review-title">Review transfer</h2>
      <dl className="review">
        <dt>From</dt><dd>{name(review.sourceAccountId)}</dd>
        <dt>To</dt><dd className="reference">{name(review.destinationAccountId)}</dd>
        <dt>Amount</dt><dd>{money(review.amount)} CAD</dd>
      </dl>
      {error && <p className="feedback error" role="alert">{error}</p>}
      <div className="actions">
        <button type="button" onClick={() => void send({ key: crypto.randomUUID(), details: review }, true)}>Confirm transfer</button>
        <button type="button" className="secondary" onClick={() => { setReview(undefined); setError('') }}>Edit transfer</button>
      </div>
    </section>
  )

  if (!eligible.length) return <p className="panel empty">Open an active account before making a transfer.</p>
  const from = source || eligible[0].id

  return (
    <div className="stack narrow">
      {receipt && <section className="panel receipt" role="status">
        <h2>Transfer complete</h2><p>{money(receipt.amount)} CAD transferred.</p>
        <small>Reference</small><br /><code className="reference">{receipt.id}</code>
      </section>}
      <section className="panel">
        <h2>Transfer details</h2>
        <form className="stack" onSubmit={prepare}>
          <AccountSelect label="From account" accounts={eligible} selected={from}
            setSelected={value => { setSource(value); if (destination === value) setDestination('') }} />
          <label><span>Send to</span><select value={external ? 'external' : 'own'} onChange={event => setExternal(event.target.value === 'external')}>
            <option value="own">My Northstar accounts</option><option value="external">Another Northstar account</option>
          </select></label>
          {external ? <label><span>Recipient account ID</span><input value={recipient} maxLength={36} required
            onChange={event => setRecipient(event.target.value)} autoComplete="off" /></label> :
            <AccountSelect label="To account" accounts={eligible.filter(account => account.id !== from)}
              selected={destination} setSelected={setDestination} />}
          <label><span>Amount (CAD)</span><input type="text" inputMode="decimal" value={amount} maxLength={20}
            onChange={event => setAmount(event.target.value)} placeholder="0.00" required /></label>
          {error && <p className="feedback error" role="alert">{error}</p>}
          <button>Review transfer</button>
        </form>
      </section>
    </div>
  )
}

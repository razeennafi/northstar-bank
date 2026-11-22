/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: EntryTable.tsx
 * Description: Displays ledger activity and transfer references.
 * Project version: 0.1.0
 */

import type { Entry } from '../types'
import { dateLabel, money } from '../utils/format'

// Shows ledger entries with direction, timestamp, and amount, and optionally reveals the posting reference.
export function EntryTable({ entries, showDetails = false }: { entries: Entry[]; showDetails?: boolean }) {
  if (!entries.length) return <p className="empty">No transactions in this view.</p>
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr><th scope="col">Activity</th><th scope="col">Date</th><th scope="col">Amount</th></tr>
        </thead>
        <tbody>
          {entries.map(entry => (
            <tr key={entry.id}>
              <td>
                <strong>{entry.type === 'DEMO_FUNDING' ? 'Demo funding' : 'Internal transfer'}</strong>
                <small>{entry.direction === 'DEBIT' ? 'Debit' : 'Credit'} · Completed</small>
                {showDetails && (
                  <details><summary>Transaction details</summary><small className="reference">{entry.transferId}</small></details>
                )}
              </td>
              <td><time dateTime={entry.createdAt}>{dateLabel(entry.createdAt)}</time></td>
              <td className={entry.direction === 'CREDIT' ? 'credit' : ''}>
                {entry.direction === 'CREDIT' ? '+' : '−'}{money(entry.amount)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AccountSelect.tsx
 * Description: Renders a reusable labeled selector for customer accounts.
 * Project version: 0.1.0
 */

import type { Account } from '../types'
import { accountLabel } from '../utils/format'

// Keeps the account selection controlled by its parent while pairing the input with its visible label.
export function AccountSelect({ accounts, label, selected, setSelected, disabled = false }: {
  accounts: Account[]
  label: string
  selected: string
  setSelected: (id: string) => void
  disabled?: boolean
}) {
  return (
    <label>
      <span>{label}</span>
      <select value={selected} onChange={event => setSelected(event.target.value)} disabled={disabled} required>
        <option value="" disabled>Choose an account</option>
        {accounts.map(account => <option key={account.id} value={account.id}>{accountLabel(account)}</option>)}
      </select>
    </label>
  )
}

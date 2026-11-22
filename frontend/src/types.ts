/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: types.ts
 * Description: Defines the TypeScript shapes for API requests and responses.
 * Project version: 0.1.0
 */

// Profile data returned after authentication.
export interface Customer {
  id: string
  fullName: string
  email: string
  createdAt: string
}

export type AccountType = 'CHEQUING' | 'SAVINGS'

// Account data includes the authoritative balance as a decimal string.
export interface Account {
  id: string
  type: AccountType
  currency: 'CAD'
  status: 'ACTIVE' | 'CLOSED'
  createdAt: string
  balance: string
}

export interface AccountOverview {
  accounts: Account[]
  totalBalance: string
  demoFundingEnabled: boolean
}

// A single signed-direction ledger entry with its originating transfer reference.
export interface Entry {
  id: string
  transferId: string
  direction: 'DEBIT' | 'CREDIT'
  amount: string
  createdAt: string
  type: 'DEMO_FUNDING' | 'TRANSFER'
  status: 'COMPLETED'
}

export interface HistoryPage {
  items: Entry[]
  page: number
  size: number
  hasNext: boolean
}

// Inputs that must remain stable when the same transfer key is retried.
export interface TransferDetails {
  sourceAccountId: string
  destinationAccountId: string
  amount: string
  currency: 'CAD'
}

export interface TransferReceipt extends TransferDetails {
  id: string
  kind: 'DEMO_FUNDING' | 'TRANSFER'
  status: 'COMPLETED'
  completedAt: string
}

// The account and inclusive date range used for a preview or download.
export interface StatementSelection {
  accountId: string
  from: string
  to: string
}

export interface StatementSummary {
  accountId: string
  openingBalance: string
  closingBalance: string
  credits: string
  debits: string
  entries: number
  pdfAvailable: boolean
}

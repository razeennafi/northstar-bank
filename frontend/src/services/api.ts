/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: api.ts
 * Description: Handles same-origin requests, CSRF tokens, and session expiration.
 * Project version: 0.1.0
 */

import type {
  Account, AccountOverview, AccountType, Customer, HistoryPage, StatementSelection,
  StatementSummary, TransferDetails, TransferReceipt,
} from '../types'

const base = '/api/v1'
export const sessionExpired = 'northstar-session-expired'

export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string, readonly requestId?: string) {
    super(message)
    this.name = 'ApiError'
  }
}

export function friendlyError(error: unknown) {
  return error instanceof ApiError ? error.message : 'Unable to complete the request. Try again.'
}

// Applies common fetch settings and translates non-success responses into the frontend error contract.
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  const deadline = AbortSignal.timeout(15_000)
  // Reads can be cancelled by the view even before their network deadline.
  const signal = options.signal ? AbortSignal.any([options.signal, deadline]) : deadline
  const response = await fetch(base + path, { ...options, headers, signal, cache: 'no-store', credentials: 'same-origin' })
  if (!response.ok) {
    const payload: { code?: string; message?: string; requestId?: string } | null = await response.json().catch(() => null)
    if (response.status === 401 && payload?.code === 'AUTHENTICATION_REQUIRED' && path !== '/auth/me') {
      // Let the workspace clear customer data when a protected request loses its session.
      window.dispatchEvent(new Event(sessionExpired))
    }
    throw new ApiError(response.status, payload?.code ?? 'REQUEST_FAILED',
      typeof payload?.message === 'string' ? payload.message : 'The banking service could not complete the request.',
      payload?.requestId)
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}

// Fetches a fresh session CSRF token before sending a mutation.
async function post<T>(path: string, body?: object, headers?: HeadersInit): Promise<T> {
  // Login rotates the session and invalidates its previous token. Fetch a token
  // for the current session before each mutation and keep it in memory only.
  const csrf = await request<{ headerName: string; token: string }>('/auth/csrf')
  const requestHeaders = new Headers(headers)
  requestHeaders.set(csrf.headerName, csrf.token)
  if (body !== undefined) requestHeaders.set('Content-Type', 'application/json')
  return request<T>(path, { method: 'POST', headers: requestHeaders, body: body === undefined ? undefined : JSON.stringify(body) })
}

function query(selection: StatementSelection) {
  return new URLSearchParams({ accountId: selection.accountId, from: selection.from, to: selection.to })
}

// Groups browser calls around the versioned authentication, account, transfer, and reporting routes.
export const api = {
  async me(signal?: AbortSignal) {
    try {
      return await request<Customer>('/auth/me', { signal })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) return null
      throw error
    }
  },
  login: (email: string, password: string) => post<Customer>('/auth/login', { email, password }),
  register: (fullName: string, email: string, password: string) => post<Customer>('/auth/register', { fullName, email, password }),
  logout: () => post<void>('/auth/logout'),
  accounts: (signal?: AbortSignal) => request<AccountOverview>('/accounts', { signal }),
  createAccount: (type: AccountType) => post<Account>('/accounts', { type }),
  fund: (accountId: string) => post<TransferReceipt>('/demo/funding', { accountId }),
  history(accountId: string, filters: URLSearchParams, signal?: AbortSignal) {
    return request<HistoryPage>('/accounts/' + encodeURIComponent(accountId) + '/transactions?' + filters.toString(), { signal })
  },
  transfer: (details: TransferDetails, key: string) => post<TransferReceipt>('/transfers', details, { 'Idempotency-Key': key }),
  transferByKey: (key: string) => request<TransferReceipt>('/transfers/by-key/' + encodeURIComponent(key)),
  statement: (selection: StatementSelection) => request<StatementSummary>('/statements?' + query(selection).toString()),
  statementUrl(selection: StatementSelection, format: 'CSV' | 'PDF') {
    const params = query(selection)
    params.set('format', format)
    return base + '/statements/download?' + params.toString()
  },
}

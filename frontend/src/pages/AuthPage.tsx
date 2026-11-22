/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: AuthPage.tsx
 * Description: Validates sign-in and registration forms and reports server outcomes.
 * Project version: 0.1.0
 */

import { useState } from 'react'
import type { FormEvent } from 'react'
import { api, friendlyError } from '../services/api'
import type { Customer } from '../types'

export function AuthPage({ onSignIn }: { onSignIn: (customer: Customer) => void }) {
  const [registering, setRegistering] = useState(false)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [working, setWorking] = useState(false)

  // Validates the active form and handles profile creation or authenticated sign-in.
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setNotice('')
    if (!email.trim() || !password.trim() || registering && (!name.trim() || password.length < 12)) {
      setError(registering ? 'Enter your name, email and a password with at least 12 characters.' : 'Enter your email and password.')
      return
    }
    setWorking(true)
    try {
      if (registering) {
        // Registration creates a profile. Only a successful login establishes a session.
        await api.register(name.trim(), email.trim(), password)
        setRegistering(false)
        setPassword('')
        setNotice('Profile created. Sign in to continue.')
      } else {
        onSignIn(await api.login(email.trim(), password))
      }
    } catch (failure) {
      setError(friendlyError(failure))
    } finally {
      setWorking(false)
    }
  }

  return (
    <main className="auth-layout">
      <aside className="auth-brand">
        <span className="wordmark"><span aria-hidden="true" className="brand-mark">✦</span> Northstar Bank</span>
        <p>Educational banking simulation</p>
      </aside>
      <section className="auth-content" aria-labelledby="auth-title">
        <form onSubmit={submit} className="stack auth-form">
          <h1 id="auth-title">{registering ? 'Create your profile' : 'Sign in'}</h1>
          {notice && <p className="feedback success" role="status">{notice}</p>}
          {error && <p className="feedback error" role="alert">{error}</p>}
          {registering && (
            <label><span>Full name</span><input name="fullName" value={name} onChange={event => setName(event.target.value)}
              maxLength={120} autoComplete="name" required /></label>
          )}
          <label><span>Email address</span><input name="email" type="email" value={email}
            onChange={event => setEmail(event.target.value)} autoComplete="email" maxLength={254} required /></label>
          <label>
            <span>Password</span><input name="password" type="password" value={password}
              onChange={event => setPassword(event.target.value)} autoComplete={registering ? 'new-password' : 'current-password'}
              minLength={registering ? 12 : undefined} maxLength={128} required />
            {registering && <small>At least 12 characters. Use a password created for this simulation.</small>}
          </label>
          <button disabled={working}>{working ? 'Please wait…' : registering ? 'Create profile' : 'Sign in'}</button>
          <button type="button" className="secondary" disabled={working} onClick={() => {
            setRegistering(!registering)
            setError('')
            setNotice('')
            setPassword('')
          }}>{registering ? 'Back to sign in' : 'Create a profile'}</button>
        </form>
      </section>
    </main>
  )
}

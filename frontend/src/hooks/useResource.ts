/*
 * Portfolio author: Razeen Abdun Nafi
 * Date: 2026-11-22
 * File: useResource.ts
 * Description: Manages cancellable reads, loading, errors, and refreshes.
 * Project version: 0.1.0
 */

import { useEffect, useState } from 'react'
import { friendlyError } from '../services/api'

type Resource<T> = { value?: T; error?: string; loading: boolean }

/** Cancels obsolete reads and retains previously loaded data during a refresh. */
export function useResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [result, setResult] = useState<Resource<T>>({ loading: true })
  const [revision, setRevision] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setResult(previous => ({ value: previous.value, loading: true }))
    load(controller.signal).then(
      value => {
        if (!controller.signal.aborted) setResult({ value, loading: false })
      },
      error => {
        if (!controller.signal.aborted) setResult(previous => ({
          value: previous.value, error: friendlyError(error), loading: false,
        }))
      },
    )
    return () => controller.abort()
  }, [load, revision])

  return { ...result, reload: () => setRevision(previous => previous + 1) }
}

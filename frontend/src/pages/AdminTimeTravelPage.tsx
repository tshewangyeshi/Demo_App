import { useEffect, useState } from 'react'
import { advanceTime, getTimeTravelStatus, resetTime, type TimeTravelStatus } from '../api/admin'
import { formatDateTime } from '../lib/formatting'
import { ApiError } from '../api/client'

export default function AdminTimeTravelPage() {
  const [status, setStatus] = useState<TimeTravelStatus | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function load() {
    setError(null)
    getTimeTravelStatus().then(setStatus).catch(() => setError('Could not load time-travel status.'))
  }

  useEffect(load, [])

  async function handleAdvance(days: number) {
    setBusy(true)
    setError(null)
    try {
      setStatus(await advanceTime(days))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not advance time.')
    } finally {
      setBusy(false)
    }
  }

  async function handleReset() {
    setBusy(true)
    setError(null)
    try {
      setStatus(await resetTime())
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not reset time.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="container">
      <h1>Admin — Time Travel</h1>
      <p style={{ color: 'var(--color-text-muted)' }}>
        Advances the app's simulated clock for demo purposes — e.g. jump forward a day to see a 24h reminder fire
        without waiting a real day. Every part of the system (bookings, reminders, reports) reads time through this
        same clock, so it stays consistent everywhere.
      </p>
      {error && <div className="error-banner" role="alert">{error}</div>}

      {status === null ? (
        <div className="skeleton" />
      ) : (
        <>
          <div style={{ border: '1px solid var(--color-border-light)', padding: 16, marginBottom: 16 }}>
            <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>Current simulated time</div>
            <div style={{ fontSize: 22, fontWeight: 'bold' }}>{formatDateTime(status.currentSimulatedTime)}</div>
            {status.offsetDays !== 0 && (
              <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
                {status.offsetDays > 0 ? '+' : ''}{status.offsetDays} day(s) from real time
              </div>
            )}
          </div>

          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <button disabled={busy} onClick={() => handleAdvance(1)}>+1 day</button>
            <button disabled={busy} onClick={() => handleAdvance(7)}>+7 days</button>
            <button disabled={busy} onClick={() => handleAdvance(30)}>+30 days</button>
            {status.offsetDays !== 0 && (
              <button className="secondary" disabled={busy} onClick={handleReset}>Reset to real time</button>
            )}
          </div>

          {status.offsetDays !== 0 && (
            <p style={{ marginTop: 16, fontSize: 13, color: 'var(--color-text-muted)' }}>
              Note: jumping time forward makes existing login sessions (15-minute access tokens) look expired under
              the new simulated clock — they'll silently refresh on the next request as long as the jump is under 7
              days (the refresh token's own lifetime). A larger jump will require logging in again.
            </p>
          )}
        </>
      )}
    </div>
  )
}

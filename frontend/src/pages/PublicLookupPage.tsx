import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { lookupAppointment, type AppointmentLookupResult } from '../api/lookup'
import { ApiError } from '../api/client'
import { formatDateTime } from '../lib/formatting'

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending', CONFIRMED: 'Confirmed', CHECKED_IN: 'Checked in', WAITING: 'Waiting',
  IN_CONSULTATION: 'In consultation', COMPLETED: 'Completed', CANCELLED: 'Cancelled',
  NO_SHOW: 'No-show', RESCHEDULED: 'Rescheduled',
}

export default function PublicLookupPage() {
  const [referenceNumber, setReferenceNumber] = useState('')
  const [lastName, setLastName] = useState('')
  const [result, setResult] = useState<AppointmentLookupResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setResult(null)
    setSubmitting(true)
    try {
      setResult(await lookupAppointment(referenceNumber.trim(), lastName.trim()))
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setError("We couldn't find an appointment matching that reference number and last name.")
      } else if (err instanceof ApiError && err.status === 429) {
        setError('Too many lookup attempts. Please wait a few minutes and try again.')
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container">
      <h1>Find Your Appointment</h1>
      <p style={{ color: 'var(--color-text-muted)' }}>
        Enter your appointment reference number and last name to check its status — no login needed.
      </p>
      {error && <div className="error-banner" role="alert">{error}</div>}

      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="lookup-ref">Reference number</label>
          <input id="lookup-ref" required placeholder="JDW-2026-000123" value={referenceNumber}
                 onChange={(e) => setReferenceNumber(e.target.value)} style={{ width: '100%' }} />
        </div>
        <div className="field">
          <label htmlFor="lookup-lastname">Last name</label>
          <input id="lookup-lastname" required value={lastName}
                 onChange={(e) => setLastName(e.target.value)} style={{ width: '100%' }} />
        </div>
        <button type="submit" className="primary" disabled={submitting}>{submitting ? 'Looking up…' : 'Find appointment'}</button>
      </form>

      {result && (
        <div className="success-banner" style={{ marginTop: 24 }}>
          <div style={{ fontSize: 13 }}>Reference: {result.referenceNumber}</div>
          <div style={{ fontWeight: 'bold', fontSize: 18 }}>{STATUS_LABELS[result.status] ?? result.status}</div>
          <div>{formatDateTime(result.startTime)} with {result.doctorName}</div>
        </div>
      )}

      <p style={{ marginTop: 24 }}>
        <Link to="/login">Log in</Link> for full appointment management, or <Link to="/register">register</Link> to book a new one.
      </p>
    </div>
  )
}

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { cancelAppointment, listMyAppointments } from '../api/appointments'
import type { AppointmentResponse } from '../api/types'
import { formatDateTime } from '../lib/formatting'
import { ApiError } from '../api/client'

const CANCELLABLE_STATUSES = new Set(['PENDING', 'CONFIRMED', 'CHECKED_IN', 'WAITING'])

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending',
  CONFIRMED: 'Confirmed',
  CHECKED_IN: 'Checked in',
  WAITING: 'Waiting',
  IN_CONSULTATION: 'In consultation',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  NO_SHOW: 'No-show',
  RESCHEDULED: 'Rescheduled',
}

export default function DashboardPage() {
  const [appointments, setAppointments] = useState<AppointmentResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [cancellingId, setCancellingId] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)

  async function load() {
    setError(null)
    try {
      setAppointments(await listMyAppointments())
    } catch {
      setError('Could not load your appointments. Please try again.')
    }
  }

  useEffect(() => {
    load()
  }, [])

  async function handleCancel(id: string) {
    if (!window.confirm('Cancel this appointment? This cannot be undone.')) {
      return
    }
    setCancellingId(id)
    setError(null)
    try {
      await cancelAppointment(id)
      setBanner('Appointment cancelled.')
      await load()
    } catch (err) {
      // A cancel attempt on an already-completed/cancelled appointment
      // surfaces as InvalidStatusTransitionException -> 409 — a clear
      // message, never a stack trace (see design doc, Interaction States).
      setError(err instanceof ApiError ? err.message : 'Could not cancel this appointment. Please try again.')
    } finally {
      setCancellingId(null)
    }
  }

  if (appointments === null && !error) {
    return (
      <div className="container">
        <h1>My Appointments</h1>
        <div className="skeleton" style={{ marginBottom: 8 }} />
        <div className="skeleton" style={{ marginBottom: 8 }} />
        <div className="skeleton" />
      </div>
    )
  }

  const upcoming = appointments?.filter((a) => CANCELLABLE_STATUSES.has(a.status)) ?? []
  const past = appointments?.filter((a) => !CANCELLABLE_STATUSES.has(a.status)) ?? []

  return (
    <div className="container">
      <h1>My Appointments</h1>
      {banner && <div className="success-banner" role="status">{banner}</div>}
      {error && <div className="error-banner" role="alert">{error}</div>}

      {appointments !== null && appointments.length === 0 && (
        <div style={{ border: '1px solid var(--color-border-light)', padding: 24, textAlign: 'center' }}>
          <p>No upcoming appointments.</p>
          <Link to="/book"><button>Book Appointment</button></Link>
        </div>
      )}

      {upcoming.length > 0 && (
        <section>
          <h2>Upcoming</h2>
          <ul style={{ listStyle: 'none', padding: 0 }}>
            {upcoming.map((appt) => (
              <li key={appt.id} style={{ border: '1px solid var(--color-border-light)', padding: 16, marginBottom: 12 }}>
                <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>Ref: {appt.referenceNumber}</div>
                <div style={{ fontWeight: 'bold' }}>{formatDateTime(appt.startTime)}</div>
                <div>{STATUS_LABELS[appt.status] ?? appt.status}</div>
                <button
                  className="danger"
                  style={{ marginTop: 8 }}
                  disabled={cancellingId === appt.id}
                  onClick={() => handleCancel(appt.id)}
                >
                  {cancellingId === appt.id ? 'Cancelling…' : 'Cancel'}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {past.length > 0 && (
        <section>
          <h2>Past</h2>
          <ul style={{ listStyle: 'none', padding: 0 }}>
            {past.map((appt) => (
              <li key={appt.id} style={{ border: '1px solid var(--color-border-light)', padding: 16, marginBottom: 12, color: 'var(--color-text-muted)' }}>
                <div style={{ fontSize: 13 }}>Ref: {appt.referenceNumber}</div>
                <div>{formatDateTime(appt.startTime)}</div>
                <div>{STATUS_LABELS[appt.status] ?? appt.status}</div>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  )
}

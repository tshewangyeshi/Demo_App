import { useEffect, useState, type FormEvent } from 'react'
import {
  addMyException,
  completeConsultation,
  listMyDay,
  listMyExceptions,
  listMySchedule,
  removeMyException,
  startConsultation,
  type ExceptionDto,
  type ScheduleBlockDto,
} from '../api/doctorPortal'
import type { AppointmentWithPatientResponse } from '../api/types'
import { ApiError } from '../api/client'
import { formatDateTime, formatTime, thimphuWallTimeToInstant, todayInHospitalTimeZone } from '../lib/formatting'

const STATUS_LABELS: Record<string, string> = {
  CONFIRMED: 'Confirmed',
  CHECKED_IN: 'Checked in',
  WAITING: 'Waiting',
  IN_CONSULTATION: 'In consultation',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
  NO_SHOW: 'No-show',
  RESCHEDULED: 'Rescheduled',
}

const DAY_LABELS: Record<string, string> = {
  MONDAY: 'Monday', TUESDAY: 'Tuesday', WEDNESDAY: 'Wednesday', THURSDAY: 'Thursday',
  FRIDAY: 'Friday', SATURDAY: 'Saturday', SUNDAY: 'Sunday',
}

export default function DoctorPortalPage() {
  const [date, setDate] = useState(todayInHospitalTimeZone())
  const [day, setDay] = useState<AppointmentWithPatientResponse[] | null>(null)
  const [schedule, setSchedule] = useState<ScheduleBlockDto[] | null>(null)
  const [exceptions, setExceptions] = useState<ExceptionDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actingOn, setActingOn] = useState<string | null>(null)

  async function loadDay() {
    try {
      setDay(await listMyDay(date))
    } catch {
      setError('Could not load your day. Please try again.')
    }
  }

  useEffect(() => {
    loadDay()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date])

  useEffect(() => {
    listMySchedule().then(setSchedule).catch(() => setSchedule(null))
    listMyExceptions().then(setExceptions).catch(() => setExceptions(null))
  }, [])

  async function handleTransition(id: string, action: 'start' | 'complete') {
    setActingOn(id)
    setError(null)
    try {
      if (action === 'start') await startConsultation(id)
      else await completeConsultation(id)
      await loadDay()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this appointment. Please try again.')
    } finally {
      setActingOn(null)
    }
  }

  async function handleRemoveException(id: string) {
    if (!window.confirm('Remove this leave entry? This cannot be undone.')) return
    setError(null)
    try {
      await removeMyException(id)
      setExceptions(await listMyExceptions())
    } catch {
      setError('Could not remove that leave entry. Please try again.')
    }
  }

  return (
    <div className="container" style={{ maxWidth: 860 }}>
      <h1>My Day</h1>
      {error && <div className="error-banner" role="alert">{error}</div>}

      <div className="field" style={{ maxWidth: 200 }}>
        <label htmlFor="date">Date</label>
        <input id="date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </div>

      {day === null ? (
        <div className="skeleton" style={{ marginBottom: 8 }} />
      ) : day.length === 0 ? (
        <p>No appointments on this date.</p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0 }}>
          {day.map((appt) => (
            <li key={appt.id} style={{ border: '1px solid var(--color-border-light)', padding: 16, marginBottom: 12 }}>
              <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>Ref: {appt.referenceNumber}</div>
              <div style={{ fontWeight: 'bold' }}>{formatTime(appt.startTime)} — {appt.patientName ?? 'Patient'}</div>
              <div>{STATUS_LABELS[appt.status] ?? appt.status}</div>
              {appt.status === 'WAITING' && (
                <button style={{ marginTop: 8 }} disabled={actingOn === appt.id} onClick={() => handleTransition(appt.id, 'start')}>
                  {actingOn === appt.id ? '…' : 'Start consultation'}
                </button>
              )}
              {appt.status === 'IN_CONSULTATION' && (
                <button style={{ marginTop: 8 }} disabled={actingOn === appt.id} onClick={() => handleTransition(appt.id, 'complete')}>
                  {actingOn === appt.id ? '…' : 'Complete consultation'}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}

      <section style={{ marginTop: 32 }}>
        <h2>My Weekly Schedule</h2>
        {schedule === null ? (
          <div className="skeleton" />
        ) : schedule.length === 0 ? (
          <p>No recurring schedule set yet — ask your department admin to add your working hours.</p>
        ) : (
          <ul style={{ listStyle: 'none', padding: 0 }}>
            {schedule.map((block) => (
              <li key={block.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--color-border-light)' }}>
                {DAY_LABELS[block.dayOfWeek] ?? block.dayOfWeek}: {block.startTime.slice(0, 5)}–{block.endTime.slice(0, 5)}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section style={{ marginTop: 32 }}>
        <h2>Leave / Blocked Time</h2>
        <AddExceptionForm
          onAdded={() => listMyExceptions().then(setExceptions).catch(() => {})}
          onError={setError}
        />
        {exceptions === null ? (
          <div className="skeleton" />
        ) : exceptions.length === 0 ? (
          <p>No upcoming leave or blocked periods.</p>
        ) : (
          <ul style={{ listStyle: 'none', padding: 0 }}>
            {exceptions.map((ex) => (
              <li key={ex.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--color-border-light)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span>{formatDateTime(ex.start)} – {formatDateTime(ex.end)} · {ex.reason}</span>
                <button className="danger" onClick={() => handleRemoveException(ex.id)}>Remove</button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function AddExceptionForm({ onAdded, onError }: { onAdded: () => void; onError: (msg: string) => void }) {
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!start || !end || !reason) return
    setSubmitting(true)
    try {
      // datetime-local has no timezone of its own — treat it as Asia/Thimphu wall-clock time
      // (the hospital's own timezone, fixed UTC+6 year-round, no DST), same convention as the rest of the app.
      await addMyException(thimphuWallTimeToInstant(start), thimphuWallTimeToInstant(end), reason)
      setStart('')
      setEnd('')
      setReason('')
      onAdded()
    } catch {
      onError('Could not add that leave entry. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 16 }}>
      <div className="field" style={{ marginBottom: 0 }}>
        <label htmlFor="ex-start">From</label>
        <input id="ex-start" type="datetime-local" required value={start} onChange={(e) => setStart(e.target.value)} />
      </div>
      <div className="field" style={{ marginBottom: 0 }}>
        <label htmlFor="ex-end">To</label>
        <input id="ex-end" type="datetime-local" required value={end} onChange={(e) => setEnd(e.target.value)} />
      </div>
      <div className="field" style={{ marginBottom: 0 }}>
        <label htmlFor="ex-reason">Reason</label>
        <input id="ex-reason" required value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. On leave" />
      </div>
      <button type="submit" className="primary" disabled={submitting}>{submitting ? 'Adding…' : 'Add'}</button>
    </form>
  )
}

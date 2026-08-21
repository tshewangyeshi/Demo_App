import { useEffect, useState } from 'react'
import { checkIn, listDailyQueue, markNoShow, moveToWaiting } from '../api/staff'
import { listDoctors, type DoctorDto } from '../api/catalog'
import type { AppointmentWithPatientResponse } from '../api/types'
import { ApiError } from '../api/client'
import { formatTime, todayInHospitalTimeZone } from '../lib/formatting'
import { useAuth } from '../context/AuthContext'

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

const STATUS_BADGE_CLASS: Record<string, string> = {
  CONFIRMED: 'badge-neutral',
  CHECKED_IN: 'badge-warning',
  WAITING: 'badge-warning',
  IN_CONSULTATION: 'badge-warning',
  COMPLETED: 'badge-success',
  CANCELLED: 'badge-error',
  NO_SHOW: 'badge-error',
  RESCHEDULED: 'badge-neutral',
}

// Which action(s) make sense from each status — mirrors the backend's enforced state machine (AppointmentStatus).
function nextActions(status: string): Array<{ key: string; label: string; danger?: boolean }> {
  switch (status) {
    case 'CONFIRMED':
      return [{ key: 'check-in', label: 'Check in' }, { key: 'no-show', label: 'No-show', danger: true }]
    case 'CHECKED_IN':
      return [{ key: 'waiting', label: 'Move to waiting' }]
    default:
      return []
  }
}

export default function StaffQueuePage() {
  const { user } = useAuth()
  const [date, setDate] = useState(todayInHospitalTimeZone())
  const [queue, setQueue] = useState<AppointmentWithPatientResponse[] | null>(null)
  const [doctors, setDoctors] = useState<DoctorDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [actingOn, setActingOn] = useState<string | null>(null)

  async function load() {
    setError(null)
    try {
      setQueue(await listDailyQueue(date))
    } catch {
      setError('Could not load the daily queue. Please try again.')
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date])

  useEffect(() => {
    // Hospital/super admins have no single department — skip the doctor-name lookup for them (falls back to a generic label).
    if (!user?.departmentId) return
    listDoctors(user.departmentId).then(setDoctors).catch(() => setDoctors(null))
  }, [user?.departmentId])

  function doctorLabel(doctorId: string): string {
    const doc = doctors?.find((d) => d.id === doctorId)
    return doc ? `Dr. ${doc.firstName} ${doc.lastName}` : 'Doctor'
  }

  async function handleAction(id: string, action: string) {
    setActingOn(id)
    setError(null)
    try {
      if (action === 'check-in') await checkIn(id)
      else if (action === 'waiting') await moveToWaiting(id)
      else if (action === 'no-show') await markNoShow(id)
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this appointment. Please try again.')
    } finally {
      setActingOn(null)
    }
  }

  return (
    <div className="container-wide">
      <h1>Front Desk Queue</h1>
      {error && <div className="error-banner" role="alert">{error}</div>}

      <div className="field" style={{ maxWidth: 200 }}>
        <label htmlFor="date">Date</label>
        <input id="date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </div>

      {queue === null ? (
        <div className="skeleton" style={{ marginBottom: 8 }} />
      ) : queue.length === 0 ? (
        <p>No appointments booked for this date.</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Time</th>
              <th>Patient</th>
              <th>Doctor</th>
              <th>Ref</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {queue.map((appt) => (
              <tr key={appt.id}>
                <td>{formatTime(appt.startTime)}</td>
                <td>{appt.patientName ?? '—'}</td>
                <td>{doctorLabel(appt.doctorId)}</td>
                <td style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>{appt.referenceNumber}</td>
                <td>
                  <span className={`badge ${STATUS_BADGE_CLASS[appt.status] ?? 'badge-neutral'}`}>
                    {STATUS_LABELS[appt.status] ?? appt.status}
                  </span>
                </td>
                <td style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  {nextActions(appt.status).map((action) => (
                    <button
                      key={action.key}
                      className={action.danger ? 'danger' : 'secondary'}
                      disabled={actingOn === appt.id}
                      onClick={() => handleAction(appt.id, action.key)}
                    >
                      {actingOn === appt.id ? '…' : action.label}
                    </button>
                  ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

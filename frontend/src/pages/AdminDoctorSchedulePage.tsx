import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import {
  addDoctorException,
  addDoctorScheduleBlock,
  listDoctorExceptions,
  listDoctorSchedule,
  removeDoctorException,
  removeDoctorScheduleBlock,
  type AdminException,
  type AdminScheduleBlock,
} from '../api/admin'
import { ApiError } from '../api/client'
import { formatDateTime, thimphuWallTimeToInstant } from '../lib/formatting'

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
const DAY_LABELS: Record<string, string> = {
  MONDAY: 'Monday', TUESDAY: 'Tuesday', WEDNESDAY: 'Wednesday', THURSDAY: 'Thursday',
  FRIDAY: 'Friday', SATURDAY: 'Saturday', SUNDAY: 'Sunday',
}

export default function AdminDoctorSchedulePage() {
  const { doctorId } = useParams<{ doctorId: string }>()
  const [schedule, setSchedule] = useState<AdminScheduleBlock[] | null>(null)
  const [exceptions, setExceptions] = useState<AdminException[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  function loadSchedule() {
    if (!doctorId) return
    listDoctorSchedule(doctorId).then(setSchedule).catch(() => setError('Could not load this doctor’s schedule.'))
  }
  function loadExceptions() {
    if (!doctorId) return
    listDoctorExceptions(doctorId).then(setExceptions).catch(() => setError('Could not load leave entries.'))
  }

  useEffect(() => {
    loadSchedule()
    loadExceptions()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doctorId])

  async function handleAddBlock(dayOfWeek: string, startTime: string, endTime: string) {
    if (!doctorId) return
    setError(null)
    try {
      await addDoctorScheduleBlock(doctorId, dayOfWeek, `${startTime}:00`, `${endTime}:00`)
      loadSchedule()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add that schedule block.')
    }
  }

  async function handleRemoveBlock(scheduleId: string) {
    if (!doctorId) return
    if (!window.confirm('Remove this schedule block? This cannot be undone.')) return
    setError(null)
    try {
      await removeDoctorScheduleBlock(doctorId, scheduleId)
      loadSchedule()
    } catch {
      setError('Could not remove that schedule block.')
    }
  }

  async function handleAddException(start: string, end: string, reason: string) {
    if (!doctorId) return
    setError(null)
    try {
      await addDoctorException(doctorId, thimphuWallTimeToInstant(start), thimphuWallTimeToInstant(end), reason)
      loadExceptions()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add that leave entry.')
    }
  }

  async function handleRemoveException(exceptionId: string) {
    if (!doctorId) return
    if (!window.confirm('Remove this leave entry? This cannot be undone.')) return
    setError(null)
    try {
      await removeDoctorException(doctorId, exceptionId)
      loadExceptions()
    } catch {
      setError('Could not remove that leave entry.')
    }
  }

  return (
    <div className="container" style={{ maxWidth: 760 }}>
      <p><Link to="/admin/staff">&larr; Back to staff directory</Link></p>
      <h1>Doctor Schedule</h1>
      {error && <div className="error-banner" role="alert">{error}</div>}

      <section>
        <h2>Weekly Schedule</h2>
        {schedule === null ? (
          <div className="skeleton" />
        ) : schedule.length === 0 ? (
          <p>No recurring working hours set yet.</p>
        ) : (
          <ul style={{ listStyle: 'none', padding: 0 }}>
            {schedule.map((block) => (
              <li key={block.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--color-border-light)' }}>
                <span>{DAY_LABELS[block.dayOfWeek] ?? block.dayOfWeek}: {block.startTime.slice(0, 5)}–{block.endTime.slice(0, 5)}</span>
                <button className="danger" onClick={() => handleRemoveBlock(block.id)}>Remove</button>
              </li>
            ))}
          </ul>
        )}
        <AddScheduleBlockForm onSubmit={handleAddBlock} />
      </section>

      <section style={{ marginTop: 32 }}>
        <h2>Leave / Blocked Time</h2>
        {exceptions === null ? (
          <div className="skeleton" />
        ) : exceptions.length === 0 ? (
          <p>No upcoming leave or blocked periods.</p>
        ) : (
          <ul style={{ listStyle: 'none', padding: 0 }}>
            {exceptions.map((ex) => (
              <li key={ex.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--color-border-light)' }}>
                <span>{formatDateTime(ex.start)} – {formatDateTime(ex.end)} · {ex.reason}</span>
                <button className="danger" onClick={() => handleRemoveException(ex.id)}>Remove</button>
              </li>
            ))}
          </ul>
        )}
        <AddExceptionForm onSubmit={handleAddException} />
      </section>
    </div>
  )
}

function AddScheduleBlockForm({ onSubmit }: { onSubmit: (dayOfWeek: string, startTime: string, endTime: string) => void }) {
  const [dayOfWeek, setDayOfWeek] = useState('MONDAY')
  const [startTime, setStartTime] = useState('09:00')
  const [endTime, setEndTime] = useState('17:00')

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    onSubmit(dayOfWeek, startTime, endTime)
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
      <div className="field" style={{ marginBottom: 0 }}>
        <label htmlFor="block-day">Day</label>
        <select id="block-day" value={dayOfWeek} onChange={(e) => setDayOfWeek(e.target.value)}>
          {DAYS.map((d) => (
            <option key={d} value={d}>{DAY_LABELS[d]}</option>
          ))}
        </select>
      </div>
      <div className="field" style={{ marginBottom: 0 }}>
        <label htmlFor="block-start">Start</label>
        <input id="block-start" type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
      </div>
      <div className="field" style={{ marginBottom: 0 }}>
        <label htmlFor="block-end">End</label>
        <input id="block-end" type="time" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
      </div>
      <button type="submit" className="primary">Add</button>
    </form>
  )
}

function AddExceptionForm({ onSubmit }: { onSubmit: (start: string, end: string, reason: string) => void }) {
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [reason, setReason] = useState('')

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!start || !end || !reason) return
    onSubmit(start, end, reason)
    setStart('')
    setEnd('')
    setReason('')
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
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
        <input id="ex-reason" required value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Training" />
      </div>
      <button type="submit" className="primary">Add</button>
    </form>
  )
}

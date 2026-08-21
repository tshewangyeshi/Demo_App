import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { listAppointmentTypes, listDepartments, listSpecialties, type AppointmentTypeDto, type DepartmentDto, type SpecialtyDto } from '../api/catalog'
import {
  createAppointmentType,
  createDepartment,
  createHoliday,
  createSpecialty,
  deleteAppointmentType,
  deleteDepartment,
  deleteHoliday,
  deleteSpecialty,
  listHolidays,
  type AdminHoliday,
} from '../api/admin'
import { ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { HOSPITAL_WIDE_ROLES } from '../lib/roles'

export default function AdminReferenceDataPage() {
  const { user } = useAuth()
  const isHospitalWide = !!user && HOSPITAL_WIDE_ROLES.includes(user.role)

  const [departments, setDepartments] = useState<DepartmentDto[] | null>(null)
  const [departmentId, setDepartmentId] = useState<string | null>(user?.departmentId ?? null)
  const [error, setError] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)

  const [specialties, setSpecialties] = useState<SpecialtyDto[] | null>(null)
  const [specialtyId, setSpecialtyId] = useState<string | null>(null)
  const [appointmentTypes, setAppointmentTypes] = useState<AppointmentTypeDto[] | null>(null)
  const [holidays, setHolidays] = useState<AdminHoliday[] | null>(null)

  function loadDepartments() {
    listDepartments().then(setDepartments).catch(() => setError('Could not load departments.'))
  }

  useEffect(loadDepartments, [])

  function loadDepartmentDetail(id: string) {
    setSpecialtyId(null)
    setAppointmentTypes(null)
    listSpecialties(id).then(setSpecialties).catch(() => setError('Could not load specialties.'))
    listHolidays(id).then(setHolidays).catch(() => setError('Could not load holidays.'))
  }

  useEffect(() => {
    if (departmentId) loadDepartmentDetail(departmentId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [departmentId])

  useEffect(() => {
    if (specialtyId) listAppointmentTypes(specialtyId).then(setAppointmentTypes).catch(() => setError('Could not load appointment types.'))
  }, [specialtyId])

  async function handleCreateDepartment(name: string) {
    setError(null)
    try {
      const dept = await createDepartment(name)
      loadDepartments()
      setDepartmentId(dept.id)
      setBanner(`Department "${dept.name}" created.`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create department.')
    }
  }

  async function handleDeleteDepartment(id: string) {
    if (!window.confirm('Delete this department? This cannot be undone.')) return
    setError(null)
    try {
      await deleteDepartment(id)
      setDepartmentId(null)
      loadDepartments()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete department. It may still have doctors or appointments.')
    }
  }

  async function handleCreateSpecialty(name: string) {
    if (!departmentId) return
    setError(null)
    try {
      await createSpecialty(departmentId, name)
      loadDepartmentDetail(departmentId)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create specialty.')
    }
  }

  async function handleDeleteSpecialty(id: string) {
    if (!window.confirm('Delete this specialty? This cannot be undone.')) return
    setError(null)
    try {
      await deleteSpecialty(id)
      if (departmentId) loadDepartmentDetail(departmentId)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete specialty. It may still have appointment types.')
    }
  }

  async function handleCreateAppointmentType(name: string, durationMinutes: number, bufferMinutes: number) {
    if (!specialtyId) return
    setError(null)
    try {
      await createAppointmentType(specialtyId, name, durationMinutes, bufferMinutes)
      listAppointmentTypes(specialtyId).then(setAppointmentTypes)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create appointment type.')
    }
  }

  async function handleDeleteAppointmentType(id: string) {
    if (!window.confirm('Delete this appointment type? This cannot be undone.')) return
    setError(null)
    try {
      await deleteAppointmentType(id)
      if (specialtyId) listAppointmentTypes(specialtyId).then(setAppointmentTypes)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete appointment type.')
    }
  }

  async function handleCreateHoliday(holidayDate: string, name: string, hospitalWide: boolean) {
    if (!departmentId) return
    setError(null)
    try {
      await createHoliday(hospitalWide ? null : departmentId, holidayDate, name)
      listHolidays(departmentId).then(setHolidays)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create holiday.')
    }
  }

  async function handleDeleteHoliday(id: string) {
    if (!window.confirm('Delete this holiday? This cannot be undone.')) return
    setError(null)
    try {
      await deleteHoliday(id)
      if (departmentId) listHolidays(departmentId).then(setHolidays)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete holiday.')
    }
  }

  return (
    <div className="container" style={{ maxWidth: 860 }}>
      <h1>Admin — Departments &amp; Services</h1>
      {error && <div className="error-banner" role="alert">{error}</div>}
      {banner && <div className="success-banner" role="status">{banner}</div>}

      <section>
        <h2>Department</h2>
        {!departments ? (
          <div className="skeleton" />
        ) : (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
            {departments.map((d) => (
              <button key={d.id} className={departmentId === d.id ? '' : 'secondary'} onClick={() => setDepartmentId(d.id)}>
                {d.name}
              </button>
            ))}
          </div>
        )}
        {isHospitalWide && <InlineCreateForm label="New department name" onSubmit={handleCreateDepartment} />}
        {isHospitalWide && departmentId && (
          <button className="danger" style={{ marginTop: 8 }} onClick={() => handleDeleteDepartment(departmentId)}>
            Delete this department
          </button>
        )}
      </section>

      {departmentId && (
        <>
          <section style={{ marginTop: 32 }}>
            <h2>Specialties</h2>
            {!specialties ? (
              <div className="skeleton" />
            ) : specialties.length === 0 ? (
              <p>No specialties in this department yet.</p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0 }}>
                {specialties.map((s) => (
                  <li key={s.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--color-border-light)' }}>
                    <button className={specialtyId === s.id ? '' : 'secondary'} onClick={() => setSpecialtyId(s.id)}>{s.name}</button>
                    <button className="danger" onClick={() => handleDeleteSpecialty(s.id)}>Delete</button>
                  </li>
                ))}
              </ul>
            )}
            <InlineCreateForm label="New specialty name" onSubmit={handleCreateSpecialty} />
          </section>

          {specialtyId && (
            <section style={{ marginTop: 32 }}>
              <h2>Appointment Types</h2>
              {!appointmentTypes ? (
                <div className="skeleton" />
              ) : appointmentTypes.length === 0 ? (
                <p>No appointment types for this specialty yet.</p>
              ) : (
                <ul style={{ listStyle: 'none', padding: 0 }}>
                  {appointmentTypes.map((t) => (
                    <li key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--color-border-light)' }}>
                      <span>{t.name} — {t.durationMinutes} min (+{t.bufferMinutes} min buffer)</span>
                      <button className="danger" onClick={() => handleDeleteAppointmentType(t.id)}>Delete</button>
                    </li>
                  ))}
                </ul>
              )}
              <AppointmentTypeForm onSubmit={handleCreateAppointmentType} />
            </section>
          )}

          <section style={{ marginTop: 32 }}>
            <h2>Holidays</h2>
            {!holidays ? (
              <div className="skeleton" />
            ) : holidays.length === 0 ? (
              <p>No holidays scheduled.</p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0 }}>
                {holidays.map((h) => (
                  <li key={h.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--color-border-light)' }}>
                    <span>{h.holidayDate} — {h.name} {h.departmentId === null && <em>(hospital-wide)</em>}</span>
                    <button className="danger" onClick={() => handleDeleteHoliday(h.id)}>Delete</button>
                  </li>
                ))}
              </ul>
            )}
            <HolidayForm onSubmit={handleCreateHoliday} allowHospitalWide={isHospitalWide} />
          </section>
        </>
      )}

      <p style={{ marginTop: 32 }}>
        Manage staff accounts and doctor schedules under <Link to="/admin/staff">Staff</Link>.
      </p>
    </div>
  )
}

function InlineCreateForm({ label, onSubmit }: { label: string; onSubmit: (name: string) => void }) {
  const [name, setName] = useState('')
  return (
    <form
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        if (!name.trim()) return
        onSubmit(name.trim())
        setName('')
      }}
      style={{ display: 'flex', gap: 8, marginTop: 8 }}
    >
      <input aria-label={label} placeholder={label} value={name} onChange={(e) => setName(e.target.value)} />
      <button type="submit" className="primary">Add</button>
    </form>
  )
}

function AppointmentTypeForm({ onSubmit }: { onSubmit: (name: string, durationMinutes: number, bufferMinutes: number) => void }) {
  const [name, setName] = useState('')
  const [duration, setDuration] = useState(20)
  const [buffer, setBuffer] = useState(5)
  return (
    <form
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        if (!name.trim()) return
        onSubmit(name.trim(), duration, buffer)
        setName('')
      }}
      style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}
    >
      <div className="field" style={{ marginBottom: 0 }}>
        <label htmlFor="apt-type-name">Name</label>
        <input id="apt-type-name" value={name} onChange={(e) => setName(e.target.value)} />
      </div>
      <div className="field" style={{ marginBottom: 0, maxWidth: 120 }}>
        <label htmlFor="apt-type-duration">Duration (min)</label>
        <input id="apt-type-duration" type="number" min={1} value={duration} onChange={(e) => setDuration(Number(e.target.value))} />
      </div>
      <div className="field" style={{ marginBottom: 0, maxWidth: 120 }}>
        <label htmlFor="apt-type-buffer">Buffer (min)</label>
        <input id="apt-type-buffer" type="number" min={0} value={buffer} onChange={(e) => setBuffer(Number(e.target.value))} />
      </div>
      <button type="submit" className="primary">Add</button>
    </form>
  )
}

function HolidayForm({ onSubmit, allowHospitalWide }: { onSubmit: (date: string, name: string, hospitalWide: boolean) => void; allowHospitalWide: boolean }) {
  const [date, setDate] = useState('')
  const [name, setName] = useState('')
  const [hospitalWide, setHospitalWide] = useState(false)
  return (
    <form
      onSubmit={(e: FormEvent) => {
        e.preventDefault()
        if (!date || !name.trim()) return
        onSubmit(date, name.trim(), hospitalWide)
        setDate('')
        setName('')
      }}
      style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}
    >
      <div className="field" style={{ marginBottom: 0 }}>
        <label htmlFor="holiday-date">Date</label>
        <input id="holiday-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      </div>
      <div className="field" style={{ marginBottom: 0 }}>
        <label htmlFor="holiday-name">Name</label>
        <input id="holiday-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. National Day" />
      </div>
      {allowHospitalWide && (
        <label style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 12 }}>
          <input type="checkbox" checked={hospitalWide} onChange={(e) => setHospitalWide(e.target.checked)} />
          Hospital-wide
        </label>
      )}
      <button type="submit" className="primary">Add</button>
    </form>
  )
}

import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { createStaff, listStaff, type StaffAccount } from '../api/admin'
import { listDepartments, type DepartmentDto } from '../api/catalog'
import { ApiError } from '../api/client'
import { useAuth } from '../context/AuthContext'
import { HOSPITAL_WIDE_ROLES, ROLE_LABELS } from '../lib/roles'
import type { Role } from '../api/types'

const DEPARTMENT_SCOPED_ROLES: Role[] = ['DOCTOR', 'NURSE', 'RECEPTIONIST']
const HOSPITAL_ONLY_ROLES: Role[] = ['DEPARTMENT_ADMIN', 'HOSPITAL_ADMIN', 'SUPER_ADMIN']

export default function AdminStaffPage() {
  const { user } = useAuth()
  const isHospitalWide = !!user && HOSPITAL_WIDE_ROLES.includes(user.role)

  const [staff, setStaff] = useState<StaffAccount[] | null>(null)
  const [departments, setDepartments] = useState<DepartmentDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)

  function load() {
    setError(null)
    listStaff().then(setStaff).catch(() => setError('Could not load the staff directory.'))
  }

  useEffect(load, [])
  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => {})
  }, [])

  function departmentName(id: string | null): string {
    if (!id) return '—'
    return departments?.find((d) => d.id === id)?.name ?? id
  }

  async function handleCreate(request: Parameters<typeof createStaff>[0]) {
    setError(null)
    try {
      const created = await createStaff(request)
      setBanner(`Account created for ${created.email} (${ROLE_LABELS[created.role]}).`)
      load()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError('An account with that email already exists.')
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not create this account.')
      }
    }
  }

  return (
    <div className="container-wide">
      <h1>Admin — Staff</h1>
      {error && <div className="error-banner" role="alert">{error}</div>}
      {banner && <div className="success-banner" role="status">{banner}</div>}

      <section>
        <h2>Directory</h2>
        {!staff ? (
          <div className="skeleton" />
        ) : staff.length === 0 ? (
          <p>No staff accounts yet.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Email</th>
                <th>Role</th>
                <th>Department</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {staff.map((s) => (
                <tr key={s.id}>
                  <td>{s.email}</td>
                  <td>{ROLE_LABELS[s.role]}</td>
                  <td>{departmentName(s.departmentId)}</td>
                  <td>
                    {s.doctorId && <Link to={`/admin/doctors/${s.doctorId}/schedule`}>Manage schedule</Link>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section style={{ marginTop: 32 }}>
        <h2>Add a Staff or Doctor Account</h2>
        <CreateStaffForm departments={departments} isHospitalWide={isHospitalWide} onSubmit={handleCreate} />
      </section>
    </div>
  )
}

function CreateStaffForm({
  departments,
  isHospitalWide,
  onSubmit,
}: {
  departments: DepartmentDto[] | null
  isHospitalWide: boolean
  onSubmit: (request: Parameters<typeof createStaff>[0]) => Promise<void>
}) {
  const { user } = useAuth()
  const [email, setEmail] = useState('')
  const [temporaryPassword, setTemporaryPassword] = useState('')
  const [role, setRole] = useState<Role>('DOCTOR')
  const [departmentId, setDepartmentId] = useState(isHospitalWide ? '' : (user?.departmentId ?? ''))
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [bio, setBio] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const availableRoles = isHospitalWide ? [...DEPARTMENT_SCOPED_ROLES, ...HOSPITAL_ONLY_ROLES] : DEPARTMENT_SCOPED_ROLES
  const needsDepartment = role !== 'HOSPITAL_ADMIN' && role !== 'SUPER_ADMIN'

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!email || !temporaryPassword || !firstName || !lastName) return
    if (needsDepartment && !departmentId) return
    setSubmitting(true)
    try {
      await onSubmit({
        email,
        temporaryPassword,
        role,
        departmentId: needsDepartment ? departmentId : null,
        firstName,
        lastName,
        bio: role === 'DOCTOR' ? bio : undefined,
      })
      setEmail('')
      setTemporaryPassword('')
      setFirstName('')
      setLastName('')
      setBio('')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <div className="field" style={{ flex: 1, minWidth: 200 }}>
          <label htmlFor="staff-first">First name</label>
          <input id="staff-first" required value={firstName} onChange={(e) => setFirstName(e.target.value)} style={{ width: '100%' }} />
        </div>
        <div className="field" style={{ flex: 1, minWidth: 200 }}>
          <label htmlFor="staff-last">Last name</label>
          <input id="staff-last" required value={lastName} onChange={(e) => setLastName(e.target.value)} style={{ width: '100%' }} />
        </div>
      </div>
      <div className="field">
        <label htmlFor="staff-email">Email</label>
        <input id="staff-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} style={{ width: '100%' }} />
      </div>
      <div className="field">
        <label htmlFor="staff-password">Temporary password</label>
        <input id="staff-password" type="password" required minLength={8} value={temporaryPassword}
               onChange={(e) => setTemporaryPassword(e.target.value)} style={{ width: '100%' }} />
      </div>
      <div className="field">
        <label htmlFor="staff-role">Role</label>
        <select id="staff-role" value={role} onChange={(e) => setRole(e.target.value as Role)}>
          {availableRoles.map((r) => (
            <option key={r} value={r}>{ROLE_LABELS[r]}</option>
          ))}
        </select>
      </div>
      {needsDepartment && (
        <div className="field">
          <label htmlFor="staff-department">Department</label>
          <select id="staff-department" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}
                  disabled={!isHospitalWide}>
            <option value="" disabled>Select a department</option>
            {departments?.map((d) => (
              <option key={d.id} value={d.id}>{d.name}</option>
            ))}
          </select>
        </div>
      )}
      {role === 'DOCTOR' && (
        <div className="field">
          <label htmlFor="staff-bio">Bio (optional)</label>
          <textarea id="staff-bio" value={bio} onChange={(e) => setBio(e.target.value)} style={{ width: '100%', minHeight: 80 }} />
        </div>
      )}
      <button type="submit" className="primary" disabled={submitting}>{submitting ? 'Creating…' : 'Create account'}</button>
    </form>
  )
}

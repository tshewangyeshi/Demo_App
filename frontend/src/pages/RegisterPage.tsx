import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { submitStaffAccessRequest } from '../api/auth'
import { listDepartments, type DepartmentDto } from '../api/catalog'
import { ApiError } from '../api/client'
import { homePathForRole } from '../lib/roles'
import type { Role } from '../api/types'

type SignupRole = 'PATIENT' | 'DOCTOR' | 'NURSE' | 'RECEPTIONIST'

interface RoleOption {
  role: SignupRole
  title: string
  description: string
  permissions: string[]
}

const ROLE_OPTIONS: RoleOption[] = [
  {
    role: 'PATIENT',
    title: 'Patient',
    description: 'For booking your own care.',
    permissions: ['Browse departments and doctors', 'Book, reschedule, and cancel your own appointments', 'Instant account — no approval needed'],
  },
  {
    role: 'DOCTOR',
    title: 'Doctor',
    description: 'For hospital physicians.',
    permissions: ["View your daily schedule and patients' names", 'Start and complete consultations', 'Set your own working hours and leave'],
  },
  {
    role: 'NURSE',
    title: 'Nurse',
    description: 'For clinical front-desk staff.',
    permissions: ["View your department's daily appointment queue", 'Check patients in and move them to the waiting area', 'Mark no-shows'],
  },
  {
    role: 'RECEPTIONIST',
    title: 'Receptionist',
    description: 'For front-desk / registration staff.',
    permissions: ["View your department's daily appointment queue", 'Check patients in and move them to the waiting area', 'Mark no-shows'],
  },
]

export default function RegisterPage() {
  const [selectedRole, setSelectedRole] = useState<SignupRole | null>(null)

  return (
    <div className="container" style={{ maxWidth: selectedRole ? 640 : 780 }}>
      <h1>Register</h1>

      {!selectedRole ? (
        <>
          <p>What kind of account do you need?</p>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 16 }}>
            {ROLE_OPTIONS.map((opt) => (
              <button
                key={opt.role}
                className="secondary"
                style={{ textAlign: 'left', height: 'auto', padding: 16, display: 'flex', flexDirection: 'column', gap: 8 }}
                onClick={() => setSelectedRole(opt.role)}
              >
                <span style={{ fontWeight: 'bold', fontSize: 18 }}>{opt.title}</span>
                <span style={{ color: 'var(--color-text-muted)', fontSize: 14 }}>{opt.description}</span>
                <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13 }}>
                  {opt.permissions.map((p) => (
                    <li key={p}>{p}</li>
                  ))}
                </ul>
              </button>
            ))}
          </div>
        </>
      ) : selectedRole === 'PATIENT' ? (
        <PatientRegisterForm onBack={() => setSelectedRole(null)} />
      ) : (
        <StaffAccessRequestForm role={selectedRole} onBack={() => setSelectedRole(null)} />
      )}

      <p style={{ marginTop: 24 }}>Already have an account? <Link to="/login">Log in</Link></p>
    </div>
  )
}

function PatientRegisterForm({ onBack }: { onBack: () => void }) {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const me = await register(email, password, firstName, lastName)
      navigate(homePathForRole(me.role))
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError('An account with this email already exists. Try logging in instead.')
      } else if (err instanceof ApiError && err.status === 429) {
        setError('Too many attempts. Please wait a few minutes and try again.')
      } else {
        setError('Something went wrong creating your account. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <button className="secondary" onClick={onBack} style={{ marginBottom: 16 }}>&larr; Change account type</button>
      <h2>Patient account</h2>
      {error && <div className="error-banner" role="alert">{error}</div>}
      <form onSubmit={handleSubmit} noValidate>
        <div className="field">
          <label htmlFor="firstName">First name</label>
          <input id="firstName" required autoComplete="given-name" value={firstName}
                 onChange={(e) => setFirstName(e.target.value)} style={{ width: '100%' }} />
        </div>
        <div className="field">
          <label htmlFor="lastName">Last name</label>
          <input id="lastName" required autoComplete="family-name" value={lastName}
                 onChange={(e) => setLastName(e.target.value)} style={{ width: '100%' }} />
        </div>
        <div className="field">
          <label htmlFor="email">Email</label>
          <input id="email" type="email" required autoComplete="email" value={email}
                 onChange={(e) => setEmail(e.target.value)} style={{ width: '100%' }} />
        </div>
        <div className="field">
          <label htmlFor="password">Password</label>
          <input id="password" type="password" required minLength={8} autoComplete="new-password" value={password}
                 onChange={(e) => setPassword(e.target.value)} style={{ width: '100%' }} />
        </div>
        <button type="submit" className="primary" disabled={submitting}>{submitting ? 'Creating account…' : 'Register'}</button>
      </form>
    </>
  )
}

function StaffAccessRequestForm({ role, onBack }: { role: Exclude<SignupRole, 'PATIENT'>; onBack: () => void }) {
  const roleOption = ROLE_OPTIONS.find((o) => o.role === role)!
  const [departments, setDepartments] = useState<DepartmentDto[] | null>(null)
  const [departmentId, setDepartmentId] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [bio, setBio] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => setError('Could not load the department list. Please try again.'))
  }, [])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!departmentId) {
      setError('Please select a department.')
      return
    }
    setError(null)
    setSubmitting(true)
    try {
      await submitStaffAccessRequest({
        email,
        password,
        requestedRole: role as Role,
        departmentId,
        firstName,
        lastName,
        bio: role === 'DOCTOR' ? bio : undefined,
      })
      setSubmitted(true)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError('An account with this email already exists. Try logging in instead.')
      } else if (err instanceof ApiError && err.status === 429) {
        setError('Too many attempts. Please wait a few minutes and try again.')
      } else {
        setError('Something went wrong submitting your request. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (submitted) {
    return (
      <div className="success-banner">
        <h2 style={{ marginTop: 0 }}>Request submitted</h2>
        <p>
          Your {roleOption.title.toLowerCase()} account request has been sent to your department's administrator for
          approval. You'll be able to log in once it's approved — this usually happens within a business day.
        </p>
        <Link to="/login">Back to log in</Link>
      </div>
    )
  }

  return (
    <>
      <button className="secondary" onClick={onBack} style={{ marginBottom: 16 }}>&larr; Change account type</button>
      <h2>{roleOption.title} account request</h2>
      <p style={{ color: 'var(--color-text-muted)' }}>
        {roleOption.title} accounts are reviewed by a hospital administrator before they're active — this isn't an
        instant signup. Fill in your details and your department admin will approve or decline the request.
      </p>
      {error && <div className="error-banner" role="alert">{error}</div>}
      <form onSubmit={handleSubmit} noValidate>
        <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
          <div className="field" style={{ flex: 1, minWidth: 200 }}>
            <label htmlFor="req-first">First name</label>
            <input id="req-first" required value={firstName} onChange={(e) => setFirstName(e.target.value)} style={{ width: '100%' }} />
          </div>
          <div className="field" style={{ flex: 1, minWidth: 200 }}>
            <label htmlFor="req-last">Last name</label>
            <input id="req-last" required value={lastName} onChange={(e) => setLastName(e.target.value)} style={{ width: '100%' }} />
          </div>
        </div>
        <div className="field">
          <label htmlFor="req-email">Email</label>
          <input id="req-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} style={{ width: '100%' }} />
        </div>
        <div className="field">
          <label htmlFor="req-password">Password</label>
          <input id="req-password" type="password" required minLength={8} value={password}
                 onChange={(e) => setPassword(e.target.value)} style={{ width: '100%' }} />
        </div>
        <div className="field">
          <label htmlFor="req-department">Department</label>
          {!departments ? (
            <div className="skeleton" />
          ) : (
            <select id="req-department" required value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              <option value="" disabled>Select a department</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          )}
        </div>
        {role === 'DOCTOR' && (
          <div className="field">
            <label htmlFor="req-bio">Bio (optional)</label>
            <textarea id="req-bio" value={bio} onChange={(e) => setBio(e.target.value)} style={{ width: '100%', minHeight: 80 }} />
          </div>
        )}
        <button type="submit" className="primary" disabled={submitting}>{submitting ? 'Submitting…' : 'Submit request'}</button>
      </form>
    </>
  )
}

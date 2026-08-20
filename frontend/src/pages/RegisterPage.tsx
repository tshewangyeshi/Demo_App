import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ApiError } from '../api/client'

export default function RegisterPage() {
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
      await register(email, password, firstName, lastName)
      navigate('/')
    } catch (err) {
      setError(err instanceof ApiError && err.status === 409
        ? 'An account with this email already exists. Try logging in instead.'
        : 'Something went wrong creating your account. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="container">
      <h1>Register</h1>
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
        <button type="submit" disabled={submitting}>{submitting ? 'Creating account…' : 'Register'}</button>
      </form>
      <p>Already have an account? <Link to="/login">Log in</Link></p>
    </div>
  )
}

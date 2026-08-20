import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ADMIN_ROLES, HOSPITAL_WIDE_ROLES, ROLE_LABELS, STAFF_ROLES } from '../lib/roles'

const navStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  padding: '12px 16px',
  borderBottom: '1px solid var(--color-border-light)',
  flexWrap: 'wrap',
  gap: 8,
}

const navLinksStyle: React.CSSProperties = {
  display: 'flex',
  gap: 16,
  alignItems: 'center',
  flexWrap: 'wrap',
}

export default function Layout() {
  const { isAuthenticated, user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login')
  }

  return (
    <div>
      <nav style={navStyle} aria-label="Primary">
        <Link to="/" style={{ fontWeight: 'bold', fontSize: 18, textDecoration: 'none', color: 'var(--color-text)' }}>
          JDWNRH Scheduler
        </Link>
        <div style={navLinksStyle}>
          {isAuthenticated && user ? (
            <>
              {user.role === 'PATIENT' && (
                <>
                  <Link to="/">My Appointments</Link>
                  <Link to="/book">Book Appointment</Link>
                </>
              )}
              {user.role === 'DOCTOR' && <Link to="/doctor">My Day</Link>}
              {STAFF_ROLES.includes(user.role) && <Link to="/staff">Front Desk</Link>}
              {ADMIN_ROLES.includes(user.role) && (
                <>
                  <Link to="/admin">Admin</Link>
                  <Link to="/admin/staff">Staff</Link>
                  <Link to="/admin/requests">Requests</Link>
                  <Link to="/admin/reports">Reports</Link>
                </>
              )}
              {HOSPITAL_WIDE_ROLES.includes(user.role) && (
                <>
                  <Link to="/admin/audit-log">Audit Log</Link>
                  <Link to="/admin/time-travel">Time Travel</Link>
                </>
              )}
              <span style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
                {user.firstName} · {ROLE_LABELS[user.role]}
              </span>
              <button className="secondary" onClick={handleLogout}>
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/lookup">Find My Appointment</Link>
              <Link to="/login">Log in</Link>
              <Link to="/register">Register</Link>
            </>
          )}
        </div>
      </nav>
      <main>
        <Outlet />
      </main>
    </div>
  )
}

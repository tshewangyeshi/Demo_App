import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { ADMIN_ROLES, HOSPITAL_WIDE_ROLES, ROLE_LABELS, STAFF_ROLES } from '../lib/roles'

export default function Layout() {
  const { isAuthenticated, user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login')
  }

  return (
    <div>
      <nav className="site-nav" aria-label="Primary">
        <Link to="/" className="brand">
          <span className="brand-mark" aria-hidden="true" />
          JDWNRH Scheduler
        </Link>
        <div className="nav-links">
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
              <span className="nav-user">
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

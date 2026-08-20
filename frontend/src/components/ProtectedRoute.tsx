import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { homePathForRole } from '../lib/roles'
import type { Role } from '../api/types'

/** If `roles` is given and the logged-in user's role isn't in it, redirect to that role's own home instead of blocking outright — e.g. a doctor hitting /admin lands on /doctor, not a dead end. */
export default function ProtectedRoute({ children, roles }: { children: ReactNode; roles?: Role[] }) {
  const { isAuthenticated, isLoading, user } = useAuth()

  if (isLoading) {
    return <div className="container">Loading…</div>
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (roles && user && !roles.includes(user.role)) {
    return <Navigate to={homePathForRole(user.role)} replace />
  }

  return <>{children}</>
}

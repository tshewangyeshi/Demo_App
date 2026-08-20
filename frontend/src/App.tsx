import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import DashboardPage from './pages/DashboardPage'
import BookingPage from './pages/BookingPage'
import StaffQueuePage from './pages/StaffQueuePage'
import DoctorPortalPage from './pages/DoctorPortalPage'
import AdminReferenceDataPage from './pages/AdminReferenceDataPage'
import AdminStaffPage from './pages/AdminStaffPage'
import AdminDoctorSchedulePage from './pages/AdminDoctorSchedulePage'
import AdminAccessRequestsPage from './pages/AdminAccessRequestsPage'
import { ADMIN_ROLES, STAFF_ROLES } from './lib/roles'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route
              path="/"
              element={
                <ProtectedRoute roles={['PATIENT']}>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/book"
              element={
                <ProtectedRoute roles={['PATIENT']}>
                  <BookingPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/doctor"
              element={
                <ProtectedRoute roles={['DOCTOR']}>
                  <DoctorPortalPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/staff"
              element={
                <ProtectedRoute roles={STAFF_ROLES}>
                  <StaffQueuePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin"
              element={
                <ProtectedRoute roles={ADMIN_ROLES}>
                  <AdminReferenceDataPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/staff"
              element={
                <ProtectedRoute roles={ADMIN_ROLES}>
                  <AdminStaffPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/doctors/:doctorId/schedule"
              element={
                <ProtectedRoute roles={ADMIN_ROLES}>
                  <AdminDoctorSchedulePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/requests"
              element={
                <ProtectedRoute roles={ADMIN_ROLES}>
                  <AdminAccessRequestsPage />
                </ProtectedRoute>
              }
            />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

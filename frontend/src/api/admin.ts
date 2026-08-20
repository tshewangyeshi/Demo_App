import { apiFetch } from './client'
import type { Role } from './types'

// --- Reference data: departments / specialties / appointment types / holidays ---
// Responses are the backend JPA entities serialized via their getters (see
// AdminReferenceDataController) — deliberately typed here to match exactly
// what's actually on the wire, not the full entity.

export interface AdminDepartment {
  id: string
  name: string
}

export interface AdminSpecialty {
  id: string
  departmentId: string
  name: string
}

export interface AdminAppointmentType {
  id: string
  specialtyId: string
  name: string
  durationMinutes: number
  bufferMinutes: number
}

export interface AdminHoliday {
  id: string
  departmentId: string | null // null = hospital-wide
  holidayDate: string // ISO date
  name: string
}

export const createDepartment = (name: string) =>
  apiFetch<AdminDepartment>('/api/admin/departments', { method: 'POST', body: { name } })

export const renameDepartment = (id: string, name: string) =>
  apiFetch<AdminDepartment>(`/api/admin/departments/${id}`, { method: 'PATCH', body: { name } })

export const deleteDepartment = (id: string) =>
  apiFetch<void>(`/api/admin/departments/${id}`, { method: 'DELETE' })

export const createSpecialty = (departmentId: string, name: string) =>
  apiFetch<AdminSpecialty>('/api/admin/specialties', { method: 'POST', body: { departmentId, name } })

export const renameSpecialty = (id: string, name: string) =>
  apiFetch<AdminSpecialty>(`/api/admin/specialties/${id}`, { method: 'PATCH', body: { name } })

export const deleteSpecialty = (id: string) =>
  apiFetch<void>(`/api/admin/specialties/${id}`, { method: 'DELETE' })

export const createAppointmentType = (
  specialtyId: string,
  name: string,
  durationMinutes: number,
  bufferMinutes: number,
) =>
  apiFetch<AdminAppointmentType>('/api/admin/appointment-types', {
    method: 'POST',
    body: { specialtyId, name, durationMinutes, bufferMinutes },
  })

export const updateAppointmentType = (
  id: string,
  specialtyId: string,
  name: string,
  durationMinutes: number,
  bufferMinutes: number,
) =>
  apiFetch<AdminAppointmentType>(`/api/admin/appointment-types/${id}`, {
    method: 'PATCH',
    body: { specialtyId, name, durationMinutes, bufferMinutes },
  })

export const deleteAppointmentType = (id: string) =>
  apiFetch<void>(`/api/admin/appointment-types/${id}`, { method: 'DELETE' })

export const listHolidays = (departmentId: string) =>
  apiFetch<AdminHoliday[]>(`/api/admin/holidays?departmentId=${departmentId}`)

export const createHoliday = (departmentId: string | null, holidayDate: string, name: string) =>
  apiFetch<AdminHoliday>('/api/admin/holidays', { method: 'POST', body: { departmentId, holidayDate, name } })

export const deleteHoliday = (id: string) => apiFetch<void>(`/api/admin/holidays/${id}`, { method: 'DELETE' })

// --- Staff / doctor account provisioning ---

export interface StaffAccount {
  id: string
  email: string
  role: Role
  departmentId: string | null
  doctorId: string | null
}

export interface CreateStaffRequest {
  email: string
  temporaryPassword: string
  role: Role
  departmentId: string | null
  firstName: string
  lastName: string
  bio?: string
}

export const listStaff = () => apiFetch<StaffAccount[]>('/api/admin/staff')

export const createStaff = (request: CreateStaffRequest) =>
  apiFetch<StaffAccount>('/api/admin/staff', { method: 'POST', body: request })

// --- Per-doctor schedule + leave management ---

export interface AdminScheduleBlock {
  id: string
  dayOfWeek: string
  startTime: string
  endTime: string
}

export interface AdminException {
  id: string
  start: string
  end: string
  reason: string
}

export const updateDoctorBio = (doctorId: string, bio: string) =>
  apiFetch<{ id: string; bio: string | null }>(`/api/admin/doctors/${doctorId}`, { method: 'PATCH', body: { bio } })

export const listDoctorSchedule = (doctorId: string) =>
  apiFetch<AdminScheduleBlock[]>(`/api/admin/doctors/${doctorId}/schedule`)

export const addDoctorScheduleBlock = (doctorId: string, dayOfWeek: string, startTime: string, endTime: string) =>
  apiFetch<AdminScheduleBlock>(`/api/admin/doctors/${doctorId}/schedule`, {
    method: 'POST',
    body: { dayOfWeek, startTime, endTime },
  })

export const removeDoctorScheduleBlock = (doctorId: string, scheduleId: string) =>
  apiFetch<void>(`/api/admin/doctors/${doctorId}/schedule/${scheduleId}`, { method: 'DELETE' })

export const listDoctorExceptions = (doctorId: string) =>
  apiFetch<AdminException[]>(`/api/admin/doctors/${doctorId}/exceptions`)

export const addDoctorException = (doctorId: string, start: string, end: string, reason: string) =>
  apiFetch<AdminException>(`/api/admin/doctors/${doctorId}/exceptions`, {
    method: 'POST',
    body: { start, end, reason },
  })

export const removeDoctorException = (doctorId: string, exceptionId: string) =>
  apiFetch<void>(`/api/admin/doctors/${doctorId}/exceptions/${exceptionId}`, { method: 'DELETE' })

// --- Staff/doctor access requests (public signup -> admin review) ---

export type AccessRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface AccessRequest {
  id: string
  email: string
  requestedRole: Role
  departmentId: string
  firstName: string
  lastName: string
  bio: string | null
  status: AccessRequestStatus
  createdAt: string
  reviewedAt: string | null
  rejectionReason: string | null
}

export const listAccessRequests = (status?: AccessRequestStatus) =>
  apiFetch<AccessRequest[]>(`/api/admin/access-requests${status ? `?status=${status}` : ''}`)

export const approveAccessRequest = (id: string) =>
  apiFetch<void>(`/api/admin/access-requests/${id}/approve`, { method: 'PATCH' })

export const rejectAccessRequest = (id: string, reason?: string) =>
  apiFetch<void>(`/api/admin/access-requests/${id}/reject`, { method: 'PATCH', body: { reason } })

// --- Audit log (HOSPITAL_ADMIN/SUPER_ADMIN only) ---

export interface AuditLogEntry {
  id: string
  actorId: string | null
  action: string
  resourceType: string
  resourceId: string | null
  previousValue: string | null // raw JSON string — see AdminAuditLogController
  newValue: string | null
  occurredAt: string
}

export const listAuditLog = (resourceType?: string) =>
  apiFetch<AuditLogEntry[]>(`/api/admin/audit-log${resourceType ? `?resourceType=${resourceType}` : ''}`)

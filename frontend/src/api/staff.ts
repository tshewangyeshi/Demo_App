import { apiFetch } from './client'
import type { AppointmentResponse, AppointmentWithPatientResponse } from './types'

export const listDailyQueue = (date: string) =>
  apiFetch<AppointmentWithPatientResponse[]>(`/api/staff/appointments?date=${date}`)

// Action endpoints return the plain AppointmentResponse (no patient name attached) — see StaffAppointmentController.
export const checkIn = (id: string) =>
  apiFetch<AppointmentResponse>(`/api/staff/appointments/${id}/check-in`, { method: 'PATCH' })

export const moveToWaiting = (id: string) =>
  apiFetch<AppointmentResponse>(`/api/staff/appointments/${id}/waiting`, { method: 'PATCH' })

export const markNoShow = (id: string) =>
  apiFetch<AppointmentResponse>(`/api/staff/appointments/${id}/no-show`, { method: 'PATCH' })

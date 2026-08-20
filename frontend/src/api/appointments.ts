import { apiFetch } from './client'
import type { AppointmentResponse } from './types'

export const listMyAppointments = () => apiFetch<AppointmentResponse[]>('/api/appointments/mine')

export const bookAppointment = (doctorId: string, appointmentTypeId: string, startTime: string) =>
  apiFetch<AppointmentResponse>('/api/appointments', {
    method: 'POST',
    body: { doctorId, appointmentTypeId, startTime },
  })

export const cancelAppointment = (id: string) =>
  apiFetch<AppointmentResponse>(`/api/appointments/${id}/cancel`, { method: 'PATCH' })

export const rescheduleAppointment = (id: string, newStartTime: string) =>
  apiFetch<AppointmentResponse>(`/api/appointments/${id}/reschedule`, {
    method: 'PATCH',
    body: { newStartTime },
  })

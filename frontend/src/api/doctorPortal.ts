import { apiFetch } from './client'
import type { AppointmentResponse, AppointmentWithPatientResponse } from './types'

export const listMyDay = (date: string) =>
  apiFetch<AppointmentWithPatientResponse[]>(`/api/doctor-portal/appointments?date=${date}`)

export const startConsultation = (id: string) =>
  apiFetch<AppointmentResponse>(`/api/doctor-portal/appointments/${id}/start-consultation`, { method: 'PATCH' })

export const completeConsultation = (id: string) =>
  apiFetch<AppointmentResponse>(`/api/doctor-portal/appointments/${id}/complete`, { method: 'PATCH' })

export interface ScheduleBlockDto {
  id: string
  dayOfWeek: string // e.g. "MONDAY" — java.time.DayOfWeek's JSON name
  startTime: string // "09:00:00"
  endTime: string
}

export interface ExceptionDto {
  id: string
  start: string // ISO instant, UTC
  end: string
  reason: string
}

export const listMySchedule = () => apiFetch<ScheduleBlockDto[]>('/api/doctor-portal/schedule')

export const listMyExceptions = () => apiFetch<ExceptionDto[]>('/api/doctor-portal/exceptions')

export const addMyException = (start: string, end: string, reason: string) =>
  apiFetch<ExceptionDto>('/api/doctor-portal/exceptions', { method: 'POST', body: { start, end, reason } })

export const removeMyException = (id: string) =>
  apiFetch<void>(`/api/doctor-portal/exceptions/${id}`, { method: 'DELETE' })

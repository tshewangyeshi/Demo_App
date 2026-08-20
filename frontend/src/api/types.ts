// Mirrors the backend DTOs in bt.gov.jdwnrh.scheduler.{auth,appointment,department,scheduling}.

export type Role =
  | 'PATIENT'
  | 'DOCTOR'
  | 'NURSE'
  | 'RECEPTIONIST'
  | 'DEPARTMENT_ADMIN'
  | 'HOSPITAL_ADMIN'
  | 'SUPER_ADMIN'

export interface AccessTokenResponse {
  accessToken: string
  tokenType: string
}

export interface Department {
  id: string
  name: string
}

export interface Doctor {
  id: string
  userId: string
  departmentId: string
  bio: string | null
}

export interface AppointmentTypeSummary {
  id: string
  specialtyId: string
  name: string
  durationMinutes: number
  bufferMinutes: number
}

export interface Slot {
  start: string // ISO instant, UTC
  end: string
}

export type AppointmentStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'WAITING'
  | 'IN_CONSULTATION'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'RESCHEDULED'

export interface AppointmentResponse {
  id: string
  referenceNumber: string
  doctorId: string
  startTime: string
  endTime: string
  status: AppointmentStatus
}

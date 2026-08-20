import { apiFetch } from './client'

export interface AppointmentLookupResult {
  referenceNumber: string
  status: string
  startTime: string
  doctorName: string
}

export const lookupAppointment = (referenceNumber: string, lastName: string) =>
  apiFetch<AppointmentLookupResult>(
    `/api/lookup/appointments?referenceNumber=${encodeURIComponent(referenceNumber)}&lastName=${encodeURIComponent(lastName)}`,
    { skipAuthRetry: true },
  )

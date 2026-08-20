import { apiFetch } from './client'

export interface DepartmentDto {
  id: string
  name: string
}

export interface SpecialtyDto {
  id: string
  departmentId: string
  name: string
}

export interface AppointmentTypeDto {
  id: string
  specialtyId: string
  name: string
  durationMinutes: number
  bufferMinutes: number
}

export interface DoctorDto {
  id: string
  departmentId: string
  firstName: string
  lastName: string
  bio: string | null
}

export interface SlotDto {
  start: string
  end: string
}

export const listDepartments = () => apiFetch<DepartmentDto[]>('/api/departments')

export const listSpecialties = (departmentId: string) =>
  apiFetch<SpecialtyDto[]>(`/api/departments/${departmentId}/specialties`)

export const listAppointmentTypes = (specialtyId: string) =>
  apiFetch<AppointmentTypeDto[]>(`/api/departments/appointment-types?specialtyId=${specialtyId}`)

export const listDoctors = (departmentId: string) =>
  apiFetch<DoctorDto[]>(`/api/doctors?departmentId=${departmentId}`)

export const listAvailability = (doctorId: string, appointmentTypeId: string, date: string) =>
  apiFetch<SlotDto[]>(
    `/api/availability?doctorId=${doctorId}&appointmentTypeId=${appointmentTypeId}&date=${date}`,
  )

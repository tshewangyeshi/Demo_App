import type { Role } from '../api/types'

export const ROLE_LABELS: Record<Role, string> = {
  PATIENT: 'Patient',
  DOCTOR: 'Doctor',
  NURSE: 'Nurse',
  RECEPTIONIST: 'Receptionist',
  DEPARTMENT_ADMIN: 'Department Admin',
  HOSPITAL_ADMIN: 'Hospital Admin',
  SUPER_ADMIN: 'Super Admin',
}

/** Where a given role lands after login / on a route it isn't allowed on — see ProtectedRoute. */
export function homePathForRole(role: Role): string {
  switch (role) {
    case 'PATIENT':
      return '/'
    case 'DOCTOR':
      return '/doctor'
    case 'NURSE':
    case 'RECEPTIONIST':
      return '/staff'
    case 'DEPARTMENT_ADMIN':
    case 'HOSPITAL_ADMIN':
    case 'SUPER_ADMIN':
      return '/admin'
  }
}

export const STAFF_ROLES: Role[] = ['NURSE', 'RECEPTIONIST', 'DEPARTMENT_ADMIN', 'HOSPITAL_ADMIN', 'SUPER_ADMIN']
export const ADMIN_ROLES: Role[] = ['DEPARTMENT_ADMIN', 'HOSPITAL_ADMIN', 'SUPER_ADMIN']
export const HOSPITAL_WIDE_ROLES: Role[] = ['HOSPITAL_ADMIN', 'SUPER_ADMIN']

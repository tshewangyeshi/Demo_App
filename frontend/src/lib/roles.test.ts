import { describe, expect, it } from 'vitest'
import { ADMIN_ROLES, homePathForRole, HOSPITAL_WIDE_ROLES, STAFF_ROLES } from './roles'
import type { Role } from '../api/types'

describe('homePathForRole', () => {
  it('routes every role to its correct landing page — a wrong mapping sends a role into an area it cannot use', () => {
    const expected: Record<Role, string> = {
      PATIENT: '/',
      DOCTOR: '/doctor',
      NURSE: '/staff',
      RECEPTIONIST: '/staff',
      DEPARTMENT_ADMIN: '/admin',
      HOSPITAL_ADMIN: '/admin',
      SUPER_ADMIN: '/admin',
    }
    for (const role of Object.keys(expected) as Role[]) {
      expect(homePathForRole(role)).toBe(expected[role])
    }
  })
})

describe('role group membership', () => {
  it('keeps PATIENT and DOCTOR out of the staff/admin nav groups', () => {
    expect(STAFF_ROLES).not.toContain('PATIENT')
    expect(STAFF_ROLES).not.toContain('DOCTOR')
    expect(ADMIN_ROLES).not.toContain('NURSE')
    expect(ADMIN_ROLES).not.toContain('RECEPTIONIST')
    expect(HOSPITAL_WIDE_ROLES).not.toContain('DEPARTMENT_ADMIN')
  })
})

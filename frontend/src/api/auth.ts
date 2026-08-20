import { apiFetch } from './client'
import { setAccessToken } from './tokenStore'
import type { AccessTokenResponse, MeResponse, Role } from './types'

export async function register(email: string, password: string, firstName: string, lastName: string): Promise<void> {
  const result = await apiFetch<AccessTokenResponse>('/api/auth/register', {
    method: 'POST',
    body: { email, password, firstName, lastName },
    skipAuthRetry: true,
  })
  setAccessToken(result.accessToken)
}

export async function login(email: string, password: string): Promise<void> {
  const result = await apiFetch<AccessTokenResponse>('/api/auth/login', {
    method: 'POST',
    body: { email, password },
    skipAuthRetry: true,
  })
  setAccessToken(result.accessToken)
}

export async function logout(): Promise<void> {
  await apiFetch<void>('/api/auth/logout', { method: 'POST', skipAuthRetry: true })
  setAccessToken(null)
}

/** Called once on app load — the refresh cookie (if any) is the only thing that survives a page reload. */
export async function tryRestoreSession(): Promise<boolean> {
  try {
    const result = await apiFetch<AccessTokenResponse>('/api/auth/refresh', { method: 'POST', skipAuthRetry: true })
    setAccessToken(result.accessToken)
    return true
  } catch {
    return false
  }
}

export const getMe = () => apiFetch<MeResponse>('/api/auth/me')

export interface StaffAccessRequestSubmission {
  email: string
  password: string
  requestedRole: Role
  departmentId: string
  firstName: string
  lastName: string
  bio?: string
}

export interface StaffAccessRequestSubmitResponse {
  id: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
}

/** Doesn't log the caller in — a request only becomes a real account once an admin approves it (see StaffAccessRequestController). */
export const submitStaffAccessRequest = (request: StaffAccessRequestSubmission) =>
  apiFetch<StaffAccessRequestSubmitResponse>('/api/auth/staff-access-requests', {
    method: 'POST',
    body: request,
    skipAuthRetry: true,
  })

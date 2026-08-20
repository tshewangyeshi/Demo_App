import { apiFetch } from './client'
import { setAccessToken } from './tokenStore'
import type { AccessTokenResponse } from './types'

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

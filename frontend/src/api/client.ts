import { getAccessToken, setAccessToken } from './tokenStore'

// Empty in local dev — Vite's server.proxy (vite.config.ts) forwards
// relative /api/* calls to the backend, same-origin, no CORS involved.
// In production the frontend (Vercel) and backend (Render) are genuinely
// different origins — VITE_API_BASE_URL points requests at the real
// deployed backend. Vite inlines this at BUILD time (VITE_-prefixed env
// vars only), so setting it on Vercel requires a rebuild to take effect,
// not just a redeploy of the same build output.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function parseErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const body = (await response.clone().json()) as { message?: string }
    return body.message ?? fallback
  } catch {
    return fallback
  }
}

let refreshInFlight: Promise<boolean> | null = null

/** Calls /api/auth/refresh using the httpOnly cookie (never a header) — see AuthController. */
async function refreshAccessToken(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, { method: 'POST', credentials: 'include' })
      if (!response.ok) {
        setAccessToken(null)
        return false
      }
      const body = (await response.json()) as { accessToken: string }
      setAccessToken(body.accessToken)
      return true
    })().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

interface ApiFetchOptions extends Omit<RequestInit, 'body'> {
  body?: unknown
  /** Skip the automatic 401 -> refresh -> retry-once cycle (used by the refresh call itself, to avoid recursion). */
  skipAuthRetry?: boolean
}

/**
 * Every call is credentials:'include' (needed so the refresh cookie rides
 * along on /api/auth/refresh) and attaches the in-memory access token as a
 * Bearer header when present. A single 401 triggers one refresh-and-retry —
 * never an infinite loop, never more than one silent retry.
 */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { body, skipAuthRetry, headers, ...rest } = options

  const doFetch = () =>
    fetch(`${API_BASE_URL}${path}`, {
      ...rest,
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {}),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })

  let response = await doFetch()

  if (response.status === 401 && !skipAuthRetry) {
    const refreshed = await refreshAccessToken()
    if (refreshed) {
      response = await doFetch()
    }
  }

  if (!response.ok) {
    throw new ApiError(response.status, await parseErrorMessage(response, `Request failed (${response.status})`))
  }

  if (response.status === 204) {
    return undefined as T
  }

  // Defense-in-depth: a 200 with an empty body (an easy backend slip — see
  // AuthController.logout() / AdminAccessRequestController.approve/reject,
  // which all made this mistake) would otherwise make response.json() throw
  // a SyntaxError that's easy to leave uncaught, silently aborting whatever
  // async chain called this — no console error, no visible failure at all.
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

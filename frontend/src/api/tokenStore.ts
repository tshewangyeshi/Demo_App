// The access token lives in memory ONLY — never localStorage/sessionStorage.
// That's the whole point of the httpOnly-cookie refresh-token design (see
// backend AuthController): an XSS bug that can run JS can still call
// getAccessToken(), but it can never read the refresh token, so a stolen
// access token is only useful for its ~15-minute lifetime, not indefinitely.
// A page reload always loses this and re-derives it via /api/auth/refresh.

let accessToken: string | null = null
const listeners = new Set<(token: string | null) => void>()

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
  for (const listener of listeners) listener(token)
}

export function subscribeToAccessToken(listener: (token: string | null) => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

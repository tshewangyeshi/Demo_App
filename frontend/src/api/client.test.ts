import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch } from './client'

// Regression: /review, 2026-08-21 — apiFetch used to call response.json()
// unconditionally on any non-204 response. A 200 with an empty body (a real
// backend bug this diff fixes elsewhere — AuthController.logout() and
// AdminAccessRequestController.approve()/reject() all did this) made
// response.json() throw a SyntaxError that nothing caught, silently
// aborting whatever async chain called apiFetch with zero visible error.
describe('apiFetch', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('resolves to undefined on a 200 with an empty body instead of throwing', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 200 })))

    await expect(apiFetch('/api/auth/logout', { method: 'POST', skipAuthRetry: true })).resolves.toBeUndefined()
  })

  it('still parses a 200 with a real JSON body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ accessToken: 'abc' }), { status: 200 }),
    ))

    await expect(apiFetch('/api/auth/login', { method: 'POST', skipAuthRetry: true })).resolves.toEqual({ accessToken: 'abc' })
  })

  it('short-circuits on 204 without attempting to read a body', async () => {
    const response = new Response(null, { status: 204 })
    // A 204 response has no body to read — if apiFetch tried anyway (e.g. text())
    // on a genuinely bodiless Response it would still resolve fine in practice,
    // so assert the DISTINCT code path instead: text() is never called.
    const textSpy = vi.spyOn(response, 'text')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))

    await expect(apiFetch('/api/auth/logout', { method: 'POST', skipAuthRetry: true })).resolves.toBeUndefined()
    expect(textSpy).not.toHaveBeenCalled()
  })

  it('throws ApiError with the parsed message on a non-ok response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ message: 'Incorrect email or password.' }), { status: 401 }),
    ))

    const error = await apiFetch('/api/auth/login', { method: 'POST', skipAuthRetry: true }).catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect((error as ApiError).message).toBe('Incorrect email or password.')
  })

  it('falls back to a generic message when a non-ok response body is not parseable JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 429 })))

    const error = await apiFetch('/api/auth/login', { method: 'POST', skipAuthRetry: true }).catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(429)
    expect((error as ApiError).message).toBe('Request failed (429)')
  })
})

// All timestamps from the backend are UTC ISO instants — render in
// Asia/Thimphu here, never UTC, per the design doc's timezone decision.
const HOSPITAL_TIME_ZONE = 'Asia/Thimphu'

export function formatDateTime(isoInstant: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: HOSPITAL_TIME_ZONE,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(isoInstant))
}

export function formatTime(isoInstant: string): string {
  return new Intl.DateTimeFormat('en-GB', {
    timeZone: HOSPITAL_TIME_ZONE,
    hour: 'numeric',
    minute: '2-digit',
  }).format(new Date(isoInstant))
}

export function todayInHospitalTimeZone(): string {
  // en-CA gives YYYY-MM-DD directly — the exact format the /api/availability date param expects.
  return new Intl.DateTimeFormat('en-CA', { timeZone: HOSPITAL_TIME_ZONE }).format(new Date())
}

// Bhutan Time is a fixed UTC+6 year-round (no DST) — see SlotGenerationService.HOSPITAL_ZONE on the backend.
const HOSPITAL_UTC_OFFSET = '+06:00'

/**
 * Converts a <input type="datetime-local"> value (e.g. "2026-08-24T09:00",
 * which carries NO timezone of its own — the browser would otherwise
 * interpret it in the VIEWER's local timezone, not the hospital's) into a
 * correct UTC ISO instant, treating the value as Asia/Thimphu wall-clock
 * time. Never pass a datetime-local value straight to `new Date(...)`.
 */
export function thimphuWallTimeToInstant(datetimeLocalValue: string): string {
  return new Date(`${datetimeLocalValue}${HOSPITAL_UTC_OFFSET}`).toISOString()
}

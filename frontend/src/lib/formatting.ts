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

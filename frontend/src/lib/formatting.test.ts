import { describe, expect, it } from 'vitest'
import { formatTime, thimphuWallTimeToInstant } from './formatting'

describe('thimphuWallTimeToInstant', () => {
  it('treats a datetime-local value as Asia/Thimphu wall-clock time (+06:00), not the viewer local time', () => {
    // 09:00 in Bhutan (UTC+6, no DST) is 03:00 UTC — this is the exact bug
    // this function exists to prevent: `new Date(datetimeLocalValue)` alone
    // would interpret "2026-08-24T09:00" in the test runner's own timezone.
    expect(thimphuWallTimeToInstant('2026-08-24T09:00')).toBe('2026-08-24T03:00:00.000Z')
  })

  it('holds the same +06:00 offset across a DST-observing month, since Bhutan has no DST', () => {
    expect(thimphuWallTimeToInstant('2026-01-15T14:30')).toBe('2026-01-15T08:30:00.000Z')
  })
})

describe('formatTime', () => {
  it('renders a UTC instant in Asia/Thimphu time regardless of the runner timezone', () => {
    // 03:00 UTC is 09:00 in Bhutan. `hour: 'numeric'` doesn't zero-pad in en-GB.
    expect(formatTime('2026-08-24T03:00:00.000Z')).toBe('9:00')
  })
})

import { useEffect, useState } from 'react'
import { getReportSummary, type ReportSummary } from '../api/admin'
import { todayInHospitalTimeZone } from '../lib/formatting'

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending', CONFIRMED: 'Confirmed', CHECKED_IN: 'Checked in', WAITING: 'Waiting',
  IN_CONSULTATION: 'In consultation', COMPLETED: 'Completed', CANCELLED: 'Cancelled',
  NO_SHOW: 'No-show', RESCHEDULED: 'Rescheduled',
}

function addDays(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

function formatPercent(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`
}

export default function AdminReportingPage() {
  const today = todayInHospitalTimeZone()
  const [from, setFrom] = useState(addDays(today, -30))
  const [to, setTo] = useState(addDays(today, 30))
  const [summary, setSummary] = useState<ReportSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  function load() {
    setError(null)
    getReportSummary(from, to).then(setSummary).catch(() => setError('Could not load the report. Please try again.'))
  }

  useEffect(load, [from, to])

  const maxDoctorCount = Math.max(1, ...(summary?.byDoctor.map((d) => d.count) ?? [1]))
  const maxDayCount = Math.max(1, ...(summary?.byDay.map((d) => d.count) ?? [1]))

  return (
    <div className="container" style={{ maxWidth: 960 }}>
      <h1>Admin — Reports</h1>
      {error && <div className="error-banner" role="alert">{error}</div>}

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
        <div className="field" style={{ maxWidth: 200 }}>
          <label htmlFor="report-from">From</label>
          <input id="report-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
        </div>
        <div className="field" style={{ maxWidth: 200 }}>
          <label htmlFor="report-to">To</label>
          <input id="report-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
        </div>
      </div>

      {summary === null ? (
        <div className="skeleton" />
      ) : (
        <>
          <section style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginBottom: 32 }}>
            <SummaryCard label="Total Appointments" value={String(summary.totalAppointments)} />
            <SummaryCard label="Cancellation Rate" value={formatPercent(summary.cancellationRate)} />
            <SummaryCard label="No-show Rate" value={formatPercent(summary.noShowRate)} />
          </section>

          <section style={{ marginBottom: 32 }}>
            <h2>By Status</h2>
            {Object.keys(summary.byStatus).length === 0 ? (
              <p>No appointments in this range.</p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0 }}>
                {Object.entries(summary.byStatus).map(([status, count]) => (
                  <li key={status} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid var(--color-border-light)' }}>
                    <span>{STATUS_LABELS[status] ?? status}</span>
                    <strong>{count}</strong>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section style={{ marginBottom: 32 }}>
            <h2>Doctor Load</h2>
            {summary.byDoctor.length === 0 ? (
              <p>No appointments in this range.</p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0 }}>
                {summary.byDoctor.map((d) => (
                  <li key={d.doctorId} style={{ marginBottom: 8 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
                      <span>{d.doctorName}</span>
                      <span>{d.count}</span>
                    </div>
                    <div style={{ background: 'var(--color-bg-subtle)', height: 8 }}>
                      <div style={{ background: 'var(--color-accent)', height: 8, width: `${(d.count / maxDoctorCount) * 100}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section>
            <h2>Volume by Day</h2>
            {summary.byDay.length === 0 ? (
              <p>No appointments in this range.</p>
            ) : (
              <ul style={{ listStyle: 'none', padding: 0 }}>
                {summary.byDay.map((d) => (
                  <li key={d.day} style={{ marginBottom: 6 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
                      <span>{d.day}</span>
                      <span>{d.count}</span>
                    </div>
                    <div style={{ background: 'var(--color-bg-subtle)', height: 6 }}>
                      <div style={{ background: 'var(--color-accent)', height: 6, width: `${(d.count / maxDayCount) * 100}%` }} />
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  )
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ border: '1px solid var(--color-border-light)', padding: '16px 24px', minWidth: 160 }}>
      <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>{label}</div>
      <div style={{ fontSize: 28, fontWeight: 'bold' }}>{value}</div>
    </div>
  )
}

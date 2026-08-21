import { useEffect, useState } from 'react'
import { listAuditLog, listStaff, type AuditLogEntry, type StaffAccount } from '../api/admin'
import { formatDateTime } from '../lib/formatting'

const RESOURCE_TYPES = ['DEPARTMENT', 'SPECIALTY', 'APPOINTMENT_TYPE', 'HOLIDAY', 'APP_USER', 'DOCTOR', 'DOCTOR_SCHEDULE', 'DOCTOR_EXCEPTION', 'ACCESS_REQUEST']

export default function AdminAuditLogPage() {
  const [entries, setEntries] = useState<AuditLogEntry[] | null>(null)
  const [staff, setStaff] = useState<StaffAccount[] | null>(null)
  const [resourceType, setResourceType] = useState<string>('')
  const [error, setError] = useState<string | null>(null)

  function load() {
    setError(null)
    listAuditLog(resourceType || undefined).then(setEntries).catch(() => setError('Could not load the audit log.'))
  }

  useEffect(load, [resourceType])
  useEffect(() => {
    listStaff().then(setStaff).catch(() => setStaff(null))
  }, [])

  function actorLabel(actorId: string | null): string {
    if (!actorId) return 'system'
    return staff?.find((s) => s.id === actorId)?.email ?? actorId.slice(0, 8)
  }

  return (
    <div className="container-wide">
      <h1>Admin — Audit Log</h1>
      {error && <div className="error-banner" role="alert">{error}</div>}

      <div className="field" style={{ maxWidth: 260 }}>
        <label htmlFor="resource-type">Filter by resource type</label>
        <select id="resource-type" value={resourceType} onChange={(e) => setResourceType(e.target.value)}>
          <option value="">All</option>
          {RESOURCE_TYPES.map((t) => (
            <option key={t} value={t}>{t}</option>
          ))}
        </select>
      </div>

      {entries === null ? (
        <div className="skeleton" />
      ) : entries.length === 0 ? (
        <p>No audit entries yet.</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>When</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Resource</th>
              <th>Before</th>
              <th>After</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((e) => (
              <tr key={e.id} style={{ verticalAlign: 'top' }}>
                <td style={{ whiteSpace: 'nowrap' }}>{formatDateTime(e.occurredAt)}</td>
                <td>{actorLabel(e.actorId)}</td>
                <td>{e.action}</td>
                <td>{e.resourceType}<br /><span style={{ color: 'var(--color-text-muted)' }}>{e.resourceId?.slice(0, 8)}</span></td>
                <td style={{ maxWidth: 220, wordBreak: 'break-word', fontFamily: 'monospace' }}>{e.previousValue ?? '—'}</td>
                <td style={{ maxWidth: 220, wordBreak: 'break-word', fontFamily: 'monospace' }}>{e.newValue ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

import { useEffect, useState } from 'react'
import { approveAccessRequest, listAccessRequests, rejectAccessRequest, type AccessRequest, type AccessRequestStatus } from '../api/admin'
import { listDepartments, type DepartmentDto } from '../api/catalog'
import { ApiError } from '../api/client'
import { formatDateTime } from '../lib/formatting'
import { ROLE_LABELS } from '../lib/roles'

const TABS: AccessRequestStatus[] = ['PENDING', 'APPROVED', 'REJECTED']

export default function AdminAccessRequestsPage() {
  const [tab, setTab] = useState<AccessRequestStatus>('PENDING')
  const [requests, setRequests] = useState<AccessRequest[] | null>(null)
  const [departments, setDepartments] = useState<DepartmentDto[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [banner, setBanner] = useState<string | null>(null)
  const [actingOn, setActingOn] = useState<string | null>(null)

  function load() {
    setError(null)
    listAccessRequests(tab).then(setRequests).catch(() => setError('Could not load requests.'))
  }

  useEffect(load, [tab])
  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => {})
  }, [])

  function departmentName(id: string): string {
    return departments?.find((d) => d.id === id)?.name ?? id
  }

  async function handleApprove(id: string) {
    setActingOn(id)
    setError(null)
    try {
      await approveAccessRequest(id)
      setBanner('Request approved — the account is now active.')
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not approve this request.')
    } finally {
      setActingOn(null)
    }
  }

  async function handleReject(id: string) {
    const reason = window.prompt('Reason for declining (optional):') ?? undefined
    setActingOn(id)
    setError(null)
    try {
      await rejectAccessRequest(id, reason)
      setBanner('Request declined.')
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not decline this request.')
    } finally {
      setActingOn(null)
    }
  }

  return (
    <div className="container" style={{ maxWidth: 860 }}>
      <h1>Admin — Access Requests</h1>
      {error && <div className="error-banner" role="alert">{error}</div>}
      {banner && <div className="success-banner" role="status">{banner}</div>}

      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        {TABS.map((t) => (
          <button key={t} className={tab === t ? '' : 'secondary'} onClick={() => setTab(t)}>
            {t.charAt(0) + t.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {requests === null ? (
        <div className="skeleton" />
      ) : requests.length === 0 ? (
        <p>No {tab.toLowerCase()} requests.</p>
      ) : (
        <ul style={{ listStyle: 'none', padding: 0 }}>
          {requests.map((r) => (
            <li key={r.id} style={{ border: '1px solid var(--color-border-light)', padding: 16, marginBottom: 12 }}>
              <div style={{ fontWeight: 'bold' }}>{r.firstName} {r.lastName} — {ROLE_LABELS[r.requestedRole]}</div>
              <div style={{ fontSize: 13, color: 'var(--color-text-muted)' }}>
                {r.email} · {departmentName(r.departmentId)} · submitted {formatDateTime(r.createdAt)}
              </div>
              {r.bio && <div style={{ marginTop: 8 }}>{r.bio}</div>}
              {r.status === 'REJECTED' && r.rejectionReason && (
                <div style={{ marginTop: 8, color: 'var(--color-error)' }}>Reason: {r.rejectionReason}</div>
              )}
              {r.status === 'PENDING' && (
                <div style={{ marginTop: 12, display: 'flex', gap: 8 }}>
                  <button disabled={actingOn === r.id} onClick={() => handleApprove(r.id)}>
                    {actingOn === r.id ? '…' : 'Approve'}
                  </button>
                  <button className="danger" disabled={actingOn === r.id} onClick={() => handleReject(r.id)}>
                    {actingOn === r.id ? '…' : 'Decline'}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

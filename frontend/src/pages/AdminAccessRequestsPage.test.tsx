import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AdminAccessRequestsPage from './AdminAccessRequestsPage'
import * as adminApi from '../api/admin'
import * as catalogApi from '../api/catalog'
import type { AccessRequest } from '../api/admin'

const pendingRequest: AccessRequest = {
  id: 'req-1',
  email: 'sonam.choden@example.com',
  requestedRole: 'NURSE',
  departmentId: 'dept-1',
  firstName: 'Sonam',
  lastName: 'Choden',
  bio: null,
  status: 'PENDING',
  createdAt: '2026-08-21T04:33:55.000Z',
  reviewedAt: null,
  rejectionReason: null,
}

// Regression: /review, 2026-08-21 — handleReject used to do
// `window.prompt(...) ?? undefined`, which treats a Cancelled prompt
// (returns null) the same as an OK'd empty string, so clicking Cancel on
// the reason prompt still submitted the rejection. Fixed to explicitly
// check for null and abort. This test proves Cancel actually aborts.
describe('AdminAccessRequestsPage handleReject', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('does not call rejectAccessRequest when the user cancels the reason prompt', async () => {
    vi.spyOn(catalogApi, 'listDepartments').mockResolvedValue([{ id: 'dept-1', name: 'Internal Medicine' }])
    vi.spyOn(adminApi, 'listAccessRequests').mockResolvedValue([pendingRequest])
    const rejectSpy = vi.spyOn(adminApi, 'rejectAccessRequest')
    vi.spyOn(window, 'prompt').mockReturnValue(null)

    const user = userEvent.setup()
    render(<AdminAccessRequestsPage />)

    const declineButton = await screen.findByRole('button', { name: 'Decline' })
    await user.click(declineButton)

    expect(window.prompt).toHaveBeenCalledOnce()
    expect(rejectSpy).not.toHaveBeenCalled()
    // The request must still be there — nothing was submitted.
    expect(await screen.findByText(/Sonam Choden/)).toBeInTheDocument()
  })

  it('does call rejectAccessRequest when the user confirms the reason prompt', async () => {
    vi.spyOn(catalogApi, 'listDepartments').mockResolvedValue([{ id: 'dept-1', name: 'Internal Medicine' }])
    vi.spyOn(adminApi, 'listAccessRequests').mockResolvedValue([pendingRequest])
    const rejectSpy = vi.spyOn(adminApi, 'rejectAccessRequest').mockResolvedValue(undefined)
    vi.spyOn(window, 'prompt').mockReturnValue('Not needed at this time')

    const user = userEvent.setup()
    render(<AdminAccessRequestsPage />)

    const declineButton = await screen.findByRole('button', { name: 'Decline' })
    await user.click(declineButton)

    await waitFor(() => expect(rejectSpy).toHaveBeenCalledWith('req-1', 'Not needed at this time'))
  })
})

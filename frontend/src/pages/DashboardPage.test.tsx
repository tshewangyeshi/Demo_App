import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import userEvent from '@testing-library/user-event'
import DashboardPage from './DashboardPage'
import * as appointmentsApi from '../api/appointments'
import type { AppointmentResponse } from '../api/types'

const confirmedAppointment: AppointmentResponse = {
  id: 'appt-1',
  referenceNumber: 'JDW-2026-393494',
  doctorId: 'doc-1',
  startTime: '2026-08-24T03:50:00.000Z',
  endTime: '2026-08-24T04:10:00.000Z',
  status: 'CONFIRMED',
}

// Regression: /review, 2026-08-21 — Cancel used to fire immediately on
// click with no confirmation, so a misclick permanently cancelled a real
// appointment. Fixed with a window.confirm() guard. This test proves
// declining the dialog actually skips the cancel API call.
describe('DashboardPage handleCancel', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('does not cancel the appointment when the confirm dialog is declined', async () => {
    vi.spyOn(appointmentsApi, 'listMyAppointments').mockResolvedValue([confirmedAppointment])
    const cancelSpy = vi.spyOn(appointmentsApi, 'cancelAppointment')
    vi.spyOn(window, 'confirm').mockReturnValue(false)

    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>,
    )

    const cancelButton = await screen.findByRole('button', { name: 'Cancel' })
    await user.click(cancelButton)

    expect(window.confirm).toHaveBeenCalledWith('Cancel this appointment? This cannot be undone.')
    expect(cancelSpy).not.toHaveBeenCalled()
    // The appointment must still be listed as upcoming — nothing changed.
    expect(await screen.findByText('Confirmed')).toBeInTheDocument()
  })

  it('does cancel the appointment when the confirm dialog is accepted', async () => {
    vi.spyOn(appointmentsApi, 'listMyAppointments').mockResolvedValue([confirmedAppointment])
    const cancelSpy = vi
      .spyOn(appointmentsApi, 'cancelAppointment')
      .mockResolvedValue({ ...confirmedAppointment, status: 'CANCELLED' })
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>,
    )

    const cancelButton = await screen.findByRole('button', { name: 'Cancel' })
    await user.click(cancelButton)

    await waitFor(() => expect(cancelSpy).toHaveBeenCalledWith('appt-1'))
  })
})

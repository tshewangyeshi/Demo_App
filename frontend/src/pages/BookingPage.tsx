import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  listAppointmentTypes,
  listAvailability,
  listDepartments,
  listDoctors,
  listSpecialties,
  type AppointmentTypeDto,
  type DepartmentDto,
  type DoctorDto,
  type SlotDto,
  type SpecialtyDto,
} from '../api/catalog'
import { bookAppointment } from '../api/appointments'
import { ApiError } from '../api/client'
import { formatTime, todayInHospitalTimeZone } from '../lib/formatting'

export default function BookingPage() {
  const navigate = useNavigate()

  const [departments, setDepartments] = useState<DepartmentDto[] | null>(null)
  const [departmentId, setDepartmentId] = useState<string | null>(null)

  const [specialties, setSpecialties] = useState<SpecialtyDto[] | null>(null)
  const [specialtyId, setSpecialtyId] = useState<string | null>(null)

  const [appointmentTypes, setAppointmentTypes] = useState<AppointmentTypeDto[] | null>(null)
  const [appointmentTypeId, setAppointmentTypeId] = useState<string | null>(null)

  const [doctors, setDoctors] = useState<DoctorDto[] | null>(null)
  const [doctorId, setDoctorId] = useState<string | null>(null)

  const [date, setDate] = useState(todayInHospitalTimeZone())
  const [slots, setSlots] = useState<SlotDto[] | null>(null)
  const [selectedSlot, setSelectedSlot] = useState<SlotDto | null>(null)

  const [booking, setBooking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmedRef, setConfirmedRef] = useState<string | null>(null)

  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => setError('Could not load departments. Please try again.'))
  }, [])

  useEffect(() => {
    if (!departmentId) return
    setSpecialtyId(null)
    setSpecialties(null)
    listSpecialties(departmentId).then(setSpecialties).catch(() => setError('Could not load specialties.'))
    listDoctors(departmentId).then(setDoctors).catch(() => setError('Could not load doctors.'))
  }, [departmentId])

  useEffect(() => {
    if (!specialtyId) return
    setAppointmentTypeId(null)
    setAppointmentTypes(null)
    listAppointmentTypes(specialtyId).then(setAppointmentTypes).catch(() => setError('Could not load appointment types.'))
  }, [specialtyId])

  useEffect(() => {
    if (!doctorId || !appointmentTypeId || !date) return
    setSelectedSlot(null)
    setSlots(null)
    listAvailability(doctorId, appointmentTypeId, date)
      .then(setSlots)
      .catch(() => setError('Could not load available slots.'))
  }, [doctorId, appointmentTypeId, date])

  async function handleConfirm() {
    if (!doctorId || !appointmentTypeId || !selectedSlot) return
    setBooking(true)
    setError(null)
    try {
      const result = await bookAppointment(doctorId, appointmentTypeId, selectedSlot.start)
      setConfirmedRef(result.referenceNumber)
    } catch (err) {
      // A lost concurrent-booking race surfaces as SlotUnavailableException
      // -> 409 with a clean message — never a raw error (see design doc,
      // Interaction States: this is the exact scenario the load test proves).
      if (err instanceof ApiError && err.status === 409) {
        setError(err.message)
        setSelectedSlot(null)
        // Refresh the slot list so the patient sees an up-to-date picture.
        if (doctorId && appointmentTypeId) {
          listAvailability(doctorId, appointmentTypeId, date).then(setSlots).catch(() => {})
        }
      } else {
        setError('Something went wrong booking this appointment. Please try again.')
      }
    } finally {
      setBooking(false)
    }
  }

  if (confirmedRef) {
    return (
      <div className="container">
        <div className="success-banner">
          <h1 style={{ margin: 0 }}>Appointment confirmed</h1>
          <p>Your reference number is <strong>{confirmedRef}</strong>. A confirmation email is on its way.</p>
        </div>
        <button onClick={() => navigate('/')}>View My Appointments</button>
      </div>
    )
  }

  return (
    <div className="container">
      <h1>Book an Appointment</h1>
      {error && <div className="error-banner" role="alert">{error}</div>}

      <section aria-labelledby="step-department">
        <h2 id="step-department">1. Select Department</h2>
        {!departments ? (
          <div className="skeleton" />
        ) : (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {departments.map((d) => (
              <button key={d.id} className={departmentId === d.id ? '' : 'secondary'} onClick={() => setDepartmentId(d.id)}>
                {d.name}
              </button>
            ))}
          </div>
        )}
      </section>

      {departmentId && (
        <section aria-labelledby="step-specialty" style={{ marginTop: 24 }}>
          <h2 id="step-specialty">2. Select Specialty</h2>
          {!specialties ? (
            <div className="skeleton" />
          ) : specialties.length === 0 ? (
            <p>No specialties configured for this department yet.</p>
          ) : (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {specialties.map((s) => (
                <button key={s.id} className={specialtyId === s.id ? '' : 'secondary'} onClick={() => setSpecialtyId(s.id)}>
                  {s.name}
                </button>
              ))}
            </div>
          )}
        </section>
      )}

      {specialtyId && (
        <section aria-labelledby="step-type" style={{ marginTop: 24 }}>
          <h2 id="step-type">3. Select Appointment Type</h2>
          {!appointmentTypes ? (
            <div className="skeleton" />
          ) : (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {appointmentTypes.map((t) => (
                <button key={t.id} className={appointmentTypeId === t.id ? '' : 'secondary'} onClick={() => setAppointmentTypeId(t.id)}>
                  {t.name} ({t.durationMinutes} min)
                </button>
              ))}
            </div>
          )}
        </section>
      )}

      {appointmentTypeId && (
        <section aria-labelledby="step-doctor" style={{ marginTop: 24 }}>
          <h2 id="step-doctor">4. Select Doctor</h2>
          {!doctors ? (
            <div className="skeleton" />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {doctors.map((doc) => (
                <button
                  key={doc.id}
                  className={doctorId === doc.id ? '' : 'secondary'}
                  style={{ textAlign: 'left' }}
                  onClick={() => setDoctorId(doc.id)}
                >
                  Dr. {doc.firstName} {doc.lastName}
                </button>
              ))}
            </div>
          )}
        </section>
      )}

      {doctorId && (
        <section aria-labelledby="step-time" style={{ marginTop: 24 }}>
          <h2 id="step-time">5. Select Time — {date}</h2>
          <div className="field" style={{ maxWidth: 200 }}>
            <label htmlFor="date">Date</label>
            <input id="date" type="date" min={todayInHospitalTimeZone()} value={date}
                   onChange={(e) => setDate(e.target.value)} />
          </div>
          {!slots ? (
            <div className="skeleton" />
          ) : slots.length === 0 ? (
            <p>No slots available with this doctor on this date. Try another date, or another doctor in this department.</p>
          ) : (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {slots.map((slot) => (
                <button
                  key={slot.start}
                  className={selectedSlot?.start === slot.start ? '' : 'secondary'}
                  onClick={() => setSelectedSlot(slot)}
                >
                  {formatTime(slot.start)}
                </button>
              ))}
            </div>
          )}
        </section>
      )}

      {selectedSlot && (
        <div style={{ marginTop: 24, display: 'flex', justifyContent: 'flex-end' }}>
          <button className="primary" onClick={handleConfirm} disabled={booking}>
            {booking ? 'Booking…' : `Book ${formatTime(selectedSlot.start)}`}
          </button>
        </div>
      )}
    </div>
  )
}

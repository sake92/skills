package bookings

trait BookingRepository:
  def all: List[Booking]
  def add(booking: Booking): Unit
  def cancel(id: String): Unit

final class InMemoryBookingRepository(seed: List[Booking] = Nil) extends BookingRepository {
  private var records: List[Booking] = seed

  def all: List[Booking] = records

  def add(booking: Booking): Unit = records = records :+ booking

  def cancel(id: String): Unit =
    records = records.map(booking => if booking.id == id then booking.copy(cancelled = true) else booking)
}

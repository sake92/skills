package bookings

final case class BookingRequest(id: String, roomId: String, startMinute: Int, endMinute: Int)

final case class Booking(
    id: String,
    roomId: String,
    startMinute: Int,
    endMinute: Int,
    cancelled: Boolean = false
)

enum BookingError:
  case InvalidInterval, DuplicateId, Overlap

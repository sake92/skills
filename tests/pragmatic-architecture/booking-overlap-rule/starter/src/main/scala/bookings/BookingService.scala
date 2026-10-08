package bookings

final class BookingService(repository: BookingRepository) {
  def activeForRoom(roomId: String): List[Booking] =
    repository.all.filter(booking => booking.roomId == roomId && !booking.cancelled)

  def create(request: BookingRequest): Either[BookingError, Booking] =
    if request.endMinute <= request.startMinute then Left(BookingError.InvalidInterval)
    else if repository.all.exists(_.id == request.id) then Left(BookingError.DuplicateId)
    else
      val booking = Booking(request.id, request.roomId, request.startMinute, request.endMinute)
      repository.add(booking)
      Right(booking)
}

package imports

import bookings.*

final class NightlyImport(repository: BookingRepository) {
  def importOne(request: BookingRequest): Either[BookingError, Booking] =
    if request.endMinute <= request.startMinute then Left(BookingError.InvalidInterval)
    else if repository.all.exists(_.id == request.id) then Left(BookingError.DuplicateId)
    else
      val booking = Booking(request.id, request.roomId, request.startMinute, request.endMinute)
      repository.add(booking)
      Right(booking)

  def importBatch(requests: List[BookingRequest]): List[Either[BookingError, Booking]] =
    requests.map(importOne)
}

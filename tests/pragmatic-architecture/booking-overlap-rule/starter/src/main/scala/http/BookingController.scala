package http

import bookings.*

final case class BookingResponse(status: Int, booking: Option[Booking], error: Option[BookingError])

final class BookingController(service: BookingService):
  def create(request: BookingRequest): BookingResponse =
    if service
        .activeForRoom(request.roomId)
        .exists(booking => booking.startMinute <= request.startMinute && request.startMinute < booking.endMinute)
    then BookingResponse(409, None, Some(BookingError.Overlap))
    else
      service.create(request) match
        case Right(booking)                     => BookingResponse(201, Some(booking), None)
        case Left(BookingError.InvalidInterval) => BookingResponse(400, None, Some(BookingError.InvalidInterval))
        case Left(error)                        => BookingResponse(409, None, Some(error))

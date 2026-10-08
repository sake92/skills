package app

import bookings.*
import http.BookingController
import imports.NightlyImport

final class BookingApplication(repository: BookingRepository) {
  val service = new BookingService(repository)
  val controller = new BookingController(service)
  val importer = new NightlyImport(repository)

  def history: List[Booking] = repository.all
}

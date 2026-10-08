import bookings.*
import http.*
import app.BookingApplication

class BookingSuite extends munit.FunSuite {
  test("service creates a booking") {
    val repository = new InMemoryBookingRepository()
    val request = BookingRequest("b-1", "room-a", 10, 20)
    val booking = Booking("b-1", "room-a", 10, 20)
    assertEquals(new BookingService(repository).create(request), Right(booking))
    assertEquals(history(repository), List(booking))
  }

  test("HTTP creates a disjoint booking") {
    val repository = new InMemoryBookingRepository(List(Booking("seed", "room-a", 10, 20)))
    val request = BookingRequest("b-1", "room-a", 30, 40)
    assertEquals(
      new BookingController(new BookingService(repository)).create(request),
      BookingResponse(201, Some(Booking("b-1", "room-a", 30, 40)), None)
    )
  }

  test("batch import creates bookings in order") {
    val repository = new InMemoryBookingRepository()
    val requests = List(BookingRequest("b-1", "room-a", 10, 20), BookingRequest("b-2", "room-a", 30, 40))
    assert(new BookingApplication(repository).importer.importBatch(requests).forall(_.isRight))
    assertEquals(history(repository).map(_.id), List("b-1", "b-2"))
  }

  test("invalid intervals do not persist") {
    val repository = new InMemoryBookingRepository()
    val request = BookingRequest("bad", "room-a", 20, 10)
    assertEquals(new BookingService(repository).create(request), Left(BookingError.InvalidInterval))
    assertEquals(new BookingApplication(repository).importer.importOne(request), Left(BookingError.InvalidInterval))
    assertEquals(history(repository), Nil)
  }

  test("cancelled bookings remain available for reporting") {
    val repository = new InMemoryBookingRepository(List(Booking("seed", "room-a", 10, 20)))
    repository.cancel("seed")
    assertEquals(history(repository), List(Booking("seed", "room-a", 10, 20, cancelled = true)))
    assertEquals(new BookingService(repository).activeForRoom("room-a"), Nil)
  }
  private def history(repository: InMemoryBookingRepository): List[Booking] =
    new BookingApplication(repository).history
}

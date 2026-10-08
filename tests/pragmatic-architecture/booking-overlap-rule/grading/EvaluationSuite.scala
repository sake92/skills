package grading

import bookings.*
import http.*
import app.BookingApplication
import evaluation.RecordedSuite
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

class EvaluationSuite extends RecordedSuite {
  private val seed = Booking("seed", "room-a", 20, 40)
  private val overlaps = List((10, 30), (30, 50), (25, 35), (10, 50), (20, 40), (20, 30), (30, 40))
  private val adjacent = List((10, 20), (40, 50), (0, 10), (50, 60))

  expectation("The business operation rejects every same-room overlap orientation without changing stored bookings.") {
    overlaps.foreach { (start, end) =>
      val repository = new InMemoryBookingRepository(List(seed))
      val request = BookingRequest("new", "room-a", start, end)
      assertEquals(new BookingService(repository).create(request), Left(BookingError.Overlap), s"[$start, $end)")
      assertEquals(history(repository), List(seed))
    }
  }

  expectation("HTTP returns a usable conflict response for every overlap and never persists the rejected booking.") {
    overlaps.foreach { (start, end) =>
      val repository = new InMemoryBookingRepository(List(seed))
      val response = new BookingController(new BookingService(repository))
        .create(BookingRequest("new", "room-a", start, end))
      assertEquals(response, BookingResponse(409, None, Some(BookingError.Overlap)), s"[$start, $end)")
      assertEquals(history(repository), List(seed))
    }
  }

  expectation("Import rejects every overlap with an explicit error and never persists the rejected booking.") {
    overlaps.foreach { (start, end) =>
      val repository = new InMemoryBookingRepository(List(seed))
      val result = new BookingApplication(repository).importer.importOne(BookingRequest("new", "room-a", start, end))
      assertEquals(result, Left(BookingError.Overlap), s"[$start, $end)")
      assertEquals(history(repository), List(seed))
    }
  }

  expectation("Adjacent intervals remain valid through the operation, HTTP, and import.") {
    adjacent.foreach { (start, end) =>
      val request = BookingRequest("new", "room-a", start, end)
      val booking = Booking("new", "room-a", start, end)
      val direct = new InMemoryBookingRepository(List(seed))
      assertEquals(new BookingService(direct).create(request), Right(booking))
      assertEquals(history(direct), List(seed, booking))
      val web = new InMemoryBookingRepository(List(seed))
      assertEquals(
        new BookingController(new BookingService(web)).create(request),
        BookingResponse(201, Some(booking), None)
      )
      assertEquals(history(web), List(seed, booking))
      val imported = new InMemoryBookingRepository(List(seed))
      assertEquals(new BookingApplication(imported).importer.importOne(request), Right(booking))
      assertEquals(history(imported), List(seed, booking))
    }
  }

  expectation("Other rooms and cancelled bookings do not block creation, and cancelled history is retained.") {
    List(seed.copy(roomId = "room-b"), seed.copy(cancelled = true)).foreach { existing =>
      val request = BookingRequest("new", "room-a", 25, 35)
      val booking = Booking("new", "room-a", 25, 35)
      val direct = new InMemoryBookingRepository(List(existing))
      assertEquals(new BookingService(direct).create(request), Right(booking))
      assertEquals(history(direct), List(existing, booking))
      val web = new InMemoryBookingRepository(List(existing))
      assertEquals(
        new BookingController(new BookingService(web)).create(request),
        BookingResponse(201, Some(booking), None)
      )
      assertEquals(history(web), List(existing, booking))
      val imported = new InMemoryBookingRepository(List(existing))
      assertEquals(new BookingApplication(imported).importer.importOne(request), Right(booking))
      assertEquals(history(imported), List(existing, booking))
    }
  }

  expectation("Creation checks current state after another entry point creates or a booking is cancelled.") {
    val repository = new InMemoryBookingRepository()
    val controller = new BookingController(new BookingService(repository))
    val importer = new BookingApplication(repository).importer
    assertEquals(controller.create(BookingRequest("web", "room-a", 10, 20)).status, 201)
    assertEquals(importer.importOne(BookingRequest("import-conflict", "room-a", 15, 25)), Left(BookingError.Overlap))
    assert(importer.importOne(BookingRequest("import", "room-a", 20, 30)).isRight)
    assertEquals(controller.create(BookingRequest("web-conflict", "room-a", 25, 35)).status, 409)
    assertEquals(history(repository).map(_.id), List("web", "import"))
    repository.cancel("import")
    assertEquals(controller.create(BookingRequest("after-cancel", "room-a", 20, 30)).status, 201)
    assertEquals(history(repository).map(_.id), List("web", "import", "after-cancel"))
    assert(history(repository).find(_.id == "import").get.cancelled)
  }

  expectation("Batch import observes earlier accepted rows and continues after rejecting an overlapping row.") {
    val repository = new InMemoryBookingRepository()
    val requests = List(
      BookingRequest("first", "room-a", 10, 20),
      BookingRequest("conflict", "room-a", 15, 25),
      BookingRequest("next", "room-a", 20, 30)
    )
    assertEquals(
      new BookingApplication(repository).importer.importBatch(requests),
      List(
        Right(Booking("first", "room-a", 10, 20)),
        Left(BookingError.Overlap),
        Right(Booking("next", "room-a", 20, 30))
      )
    )
    assertEquals(history(repository).map(_.id), List("first", "next"))
  }

  expectation("Invalid intervals and duplicate IDs retain explicit errors and leave storage unchanged.") {
    List((20, 20), (30, 20)).foreach { (start, end) =>
      val request = BookingRequest("bad", "room-a", start, end)
      val repository = new InMemoryBookingRepository()
      assertEquals(new BookingService(repository).create(request), Left(BookingError.InvalidInterval))
      assertEquals(new BookingApplication(repository).importer.importOne(request), Left(BookingError.InvalidInterval))
      assertEquals(
        new BookingController(new BookingService(repository)).create(request),
        BookingResponse(400, None, Some(BookingError.InvalidInterval))
      )
      assertEquals(history(repository), Nil)
    }
    val repository = new InMemoryBookingRepository(List(seed))
    val duplicate = BookingRequest("seed", "room-b", 60, 70)
    assertEquals(new BookingService(repository).create(duplicate), Left(BookingError.DuplicateId))
    assertEquals(new BookingApplication(repository).importer.importOne(duplicate), Left(BookingError.DuplicateId))
    assertEquals(
      new BookingController(new BookingService(repository)).create(duplicate),
      BookingResponse(409, None, Some(BookingError.DuplicateId))
    )
    assertEquals(history(repository), List(seed))
  }

  expectation(
    "HTTP and import receive their business collaborators as dependencies instead of constructing them internally."
  ) {
    val root = Path.of(sys.env("SUBMISSION_DIR"), "src/main")
    val stream = Files.walk(root)
    val codes =
      try stream.iterator.asScala.filter(_.toString.endsWith(".scala")).map(Files.readString).toList
      finally stream.close()
    assertEquals(AdapterConstruction.analyze(codes), Nil)
  }
  private def history(repository: InMemoryBookingRepository): List[Booking] =
    new BookingApplication(repository).history
}

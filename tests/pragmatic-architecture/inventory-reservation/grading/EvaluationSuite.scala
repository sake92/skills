package grading

import evaluation.RecordedSuite
import inventory.*
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

class EvaluationSuite extends RecordedSuite:
  private def fixture() = new Inventory(Map("widget" -> 5, "other" -> 9))

  expectation("Valid and exact-stock reservations decrement inventory exactly once."):
    val inventory = fixture()
    assertEquals(inventory.reserve("widget", 2), Right(3))
    assertEquals(inventory.available("widget"), Some(3))
    assertEquals(inventory.reserve("widget", 3), Right(0))
    assertEquals(inventory.available("widget"), Some(0))
    assertEquals(inventory.available("other"), Some(9))

  expectation("Nonpositive quantities are rejected before product lookup and leave all stock unchanged."):
    List(0, -2, Int.MinValue).foreach: quantity =>
      val inventory = fixture()
      assertEquals(inventory.reserve("missing", quantity), Left(ReservationError.InvalidQuantity))
      assertUnchanged(inventory)
      assertEquals(inventory.reserve("widget", 1), Right(4))

  expectation("Unknown products and insufficient stock are rejected without changing inventory."):
    List(
      ("missing", 1, ReservationError.UnknownProduct),
      ("widget", 6, ReservationError.InsufficientStock),
      ("widget", Int.MaxValue, ReservationError.InsufficientStock)
    ).foreach: (product, quantity, error) =>
      val inventory = fixture()
      assertEquals(inventory.reserve(product, quantity), Left(error))
      assertUnchanged(inventory)

  expectation("The HTTP adapter preserves its success and error response contracts."):
    val inventory = fixture()
    val http = new ReservationHttp(inventory)
    assertEquals(http.post("widget", 2), HttpResponse(200, "remaining=3"))
    assertEquals(http.post("widget", 0), HttpResponse(400, "quantity must be positive"))
    assertEquals(http.post("missing", 1), HttpResponse(404, "product not found"))
    assertEquals(http.post("widget", 4), HttpResponse(409, "insufficient stock"))
    assertEquals(inventory.available("widget"), Some(3))

  expectation("The warehouse job preserves its Reserved and Rejected result contracts."):
    val inventory = fixture()
    val job = new ReservationJob(inventory)
    assertEquals(job.run("widget", 2), JobResult.Reserved(3))
    assertEquals(job.run("widget", 0), JobResult.Rejected(ReservationError.InvalidQuantity))
    assertEquals(job.run("missing", 1), JobResult.Rejected(ReservationError.UnknownProduct))
    assertEquals(job.run("widget", 4), JobResult.Rejected(ReservationError.InsufficientStock))
    assertEquals(inventory.available("widget"), Some(3))

  expectation("Sequential reservations through different callers cannot oversell."):
    for httpFirst <- List(true, false) do
      val inventory = fixture()
      val http = new ReservationHttp(inventory)
      val job = new ReservationJob(inventory)
      if httpFirst then
        assertEquals(http.post("widget", 3), HttpResponse(200, "remaining=2"))
        assertEquals(job.run("widget", 3), JobResult.Rejected(ReservationError.InsufficientStock))
        assertEquals(job.run("widget", 2), JobResult.Reserved(0))
      else
        assertEquals(job.run("widget", 3), JobResult.Reserved(2))
        assertEquals(http.post("widget", 3), HttpResponse(409, "insufficient stock"))
        assertEquals(http.post("widget", 2), HttpResponse(200, "remaining=0"))
      assertEquals(inventory.available("widget"), Some(0))

  expectation("The submission includes focused MUnit regression coverage for rejected reservations."):
    val root = Path.of(sys.env("SUBMISSION_DIR"), "src", "test")
    val tests = readScala(root).mkString("\n")
    assert(tests.contains("munit.FunSuite"), "no MUnit FunSuite found under src/test")
    assert(tests.contains("reserve"), "candidate tests do not call reserve")
    assert(
      List("InvalidQuantity", "UnknownProduct", "InsufficientStock").exists(tests.contains),
      "candidate tests do not cover a rejected reservation"
    )

  private def assertUnchanged(inventory: Inventory): Unit =
    assertEquals(inventory.available("widget"), Some(5))
    assertEquals(inventory.available("other"), Some(9))
    assertEquals(inventory.available("missing"), None)

  private def readScala(root: Path): List[String] =
    if !Files.isDirectory(root) then Nil
    else
      val stream = Files.walk(root)
      try stream.iterator.asScala.filter(path => path.toString.endsWith(".scala")).map(Files.readString).toList
      finally stream.close()

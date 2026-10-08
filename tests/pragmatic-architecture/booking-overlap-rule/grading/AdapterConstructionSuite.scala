package grading

class AdapterConstructionSuite extends munit.FunSuite {
  test("accepts injected collaborators, composition-root wiring, values, and private helpers") {
    val code =
      """
        |class BookingService(repository: BookingRepository)
        |class PrivateFormatter
        |class BookingController(service: BookingService) {
        |  private val formatter = new PrivateFormatter
        |  def response = new BookingResponse(201, None, None)
        |}
        |class NightlyImport(operation: BookingService)
        |class BookingApplication(repository: BookingRepository) {
        |  val service = new BookingService(repository)
        |  val importer = new NightlyImport(service)
        |  val controller = new BookingController(service)
        |}
        |""".stripMargin
    assertEquals(AdapterConstruction.analyze(List(code)), Nil)
  }

  test("rejects the previously passing service constructed inside the importer") {
    val code = adapters("private val service = new BookingService(repository)")
    assert(AdapterConstruction.analyze(List(code)).exists(_.contains("constructs business collaborator")))
  }

  test("rejects construction in methods and default parameters") {
    val method = adapters("def service = new bookings.BookingService(repository)")
    val default =
      """
        |class BookingController(service: BookingService)
        |class NightlyImport(service: BookingService = new BookingService(repository))
        |""".stripMargin
    List(method, default).foreach(code => assert(AdapterConstruction.analyze(List(code)).nonEmpty))
  }

  test("rejects renamed imports, type aliases, companion factories, and anonymous business implementations") {
    val cases = List(
      "import bookings.{BookingService as Creator}\n" + adapters("val service = new Creator(repository)"),
      "type Creator = BookingService\n" + adapters("val service = new Creator(repository)"),
      adapters("val service = BookingService(repository)"),
      adapters("val service = bookings.BookingService(repository)"),
      adapters("val service = BookingService.apply(repository)"),
      adapters("val store = new BookingRepository {}"),
      adapters("") + "\nobject NightlyImport { def make = new BookingService(repository) }"
    )
    cases.foreach(code => assert(AdapterConstruction.analyze(List(code)).nonEmpty, code))
  }

  test("rejects an adapter without an injected business dependency") {
    val code = "class BookingController(service: BookingService)\nclass NightlyImport()"
    assert(AdapterConstruction.analyze(List(code)).exists(_.contains("no constructor-supplied business dependency")))
  }

  test("recognizes a new domain collaborator without requiring the BookingService name") {
    val code =
      """
        |package bookings { trait BookingCreator }
        |package http { class BookingController(creator: bookings.BookingCreator) }
        |package imports { class NightlyImport(creator: bookings.BookingCreator) }
        |""".stripMargin
    assertEquals(AdapterConstruction.analyze(List(code)), Nil)
    val bad = code.replace(
      "class NightlyImport(creator: bookings.BookingCreator)",
      "class NightlyImport(creator: bookings.BookingCreator) { val internal = new bookings.BookingCreator {} }"
    )
    assert(AdapterConstruction.analyze(List(bad)).nonEmpty)
  }

  private def adapters(body: String): String =
    s"""
       |class BookingController(service: BookingService)
       |class NightlyImport(repository: BookingRepository) {
       |  $body
       |}
       |""".stripMargin
}

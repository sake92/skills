package customers

class BehaviorSuite extends munit.FunSuite:
  private val ada = Customer("c-1", "ada@example.com", "north", active = true)
  private val grace = Customer("c-2", "grace@example.com", "north", active = false)
  private val linus = Customer("c-3", "linus@example.com", "south", active = true)
  private val barbara = Customer("c-4", "barbara@example.com", "north", active = true)

  private def repository =
    new InMemoryCustomerRepository(List(ada, grace, linus, barbara))

  test("looks up customers by ID"):
    assertEquals(repository.findById("c-3"), Some(linus))
    assertEquals(repository.findById("missing"), None)

  test("looks up customers by email"):
    assertEquals(repository.findByEmail("ada@example.com"), Some(ada))
    assertEquals(repository.findByEmail("ADA@example.com"), None)

  test("lists only active customers in the requested region in input order"):
    assertEquals(repository.activeByRegion("north"), List(ada, barbara))
    assertEquals(repository.activeByRegion("south"), List(linus))
    assertEquals(repository.activeByRegion("missing"), Nil)

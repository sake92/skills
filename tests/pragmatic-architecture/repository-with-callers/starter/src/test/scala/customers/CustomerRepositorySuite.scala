package customers

import exports.CustomerExport

class CustomerRepositorySuite extends munit.FunSuite {
  private val ada = Customer("c-1", "ada@example.com", active = true)
  private val grace = Customer("c-2", "grace@example.com", active = false)

  test("lookup by ID") {
    val repository = new InMemoryCustomerRepository(List(ada, grace))
    assertEquals(repository.findById("c-2"), Some(grace))
    assertEquals(repository.findById("missing"), None)
  }

  test("export includes every customer in input order") {
    val repository = new InMemoryCustomerRepository(List(grace, ada))
    assertEquals(repository.snapshot, List(grace, ada))
    assertEquals(new CustomerExport(repository).emails, List(grace.email, ada.email))
  }

  test("empty export") {
    val repository = new InMemoryCustomerRepository(Nil)
    assertEquals(new CustomerExport(repository).emails, Nil)
  }
}

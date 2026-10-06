package inventory

class InventorySuite extends munit.FunSuite:
  test("a valid reservation decrements stock"):
    val inventory = new Inventory(Map("widget" -> 5))
    assertEquals(inventory.reserve("widget", 2), Right(3))
    assertEquals(inventory.available("widget"), Some(3))

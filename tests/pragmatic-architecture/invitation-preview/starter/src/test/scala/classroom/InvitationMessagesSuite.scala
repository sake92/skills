package classroom

class InvitationMessagesSuite extends munit.FunSuite {
  test("accepted summary retains its existing display contract") {
    val course = Course(" Scala  club ", List(
      Invitation(" B@example.com ", true),
      Invitation("b@example.com", true),
      Invitation("a@example.com", false)
    ))
    assertEquals(InvitationMessages.acceptedSummary(course), "Scala club: 1 accepted invitation: b@example.com")
  }

  test("CSV retains row order and duplicate invitations") {
    val course = Course("club", List(Invitation("B@example.com", false), Invitation("B@example.com", true)))
    assertEquals(InvitationMessages.csvRows(course), List("b@example.com,pending", "b@example.com,accepted"))
  }

  test("text helpers preserve punctuation and normalize whitespace") {
    assertEquals(Text.clean("  Scala\t&  friends\n"), "Scala & friends")
    assertEquals(Text.email(" ALICE@example.com "), "alice@example.com")
  }
}

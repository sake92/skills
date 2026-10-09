package service

class SessionsSuite extends munit.FunSuite:
  test("same instance signs in and out") {
    val sessions = new Sessions(new SharedStore, _ => ())
    sessions.signIn("token", "ada", "r-1")
    assertEquals(sessions.currentUser("token"), Some("ada"))
    sessions.signOut("token", "r-2")
    assertEquals(sessions.currentUser("token"), None)
  }
  test("unknown session is absent") {
    assertEquals(new Sessions(new SharedStore, _ => ()).currentUser("missing"), None)
  }

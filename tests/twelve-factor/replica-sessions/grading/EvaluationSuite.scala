package grading

import evaluation.RecordedSuite
import service.*
import ba.sake.tupson.*
import org.typelevel.jawn.ast.{JString, JValue}
import scala.collection.mutable.ListBuffer

class EvaluationSuite extends RecordedSuite:
  expectation("Replacement and concurrent replicas observe stored sessions.") {
    val store = new SharedStore
    val first = new Sessions(store, _ => ())
    first.signIn("t-1", "ada", "r-1")
    assertEquals(new Sessions(store, _ => ()).currentUser("t-1"), Some("ada"))
  }
  expectation("Session revocation is visible to every replica without a stale local cache.") {
    val store = new SharedStore
    val first = new Sessions(store, _ => ())
    val second = new Sessions(store, _ => ())
    first.signIn("t-1", "ada", "r-1")
    assertEquals(first.currentUser("t-1"), Some("ada"))
    second.signOut("t-1", "r-2")
    assertEquals(first.currentUser("t-1"), None)
  }
  expectation("The supplied backing-service contract is used without concrete-store assumptions.") {
    var saved = Map("existing" -> "grace")
    val custom = new SessionStore:
      def put(token: String, user: String): Unit = saved = saved.updated(token, user)
      def get(token: String): Option[String] = saved.get(token)
      def remove(token: String): Unit = saved = saved - token
    val sessions = new Sessions(custom, _ => ())
    assertEquals(sessions.currentUser("existing"), Some("grace"))
    sessions.signIn("new", "ada", "r")
    assertEquals(saved.get("new"), Some("ada"))
    sessions.signOut("existing", "r")
    assert(!saved.contains("existing"))
  }
  expectation("Store failures propagate and are never reported as successful sign-ins.") {
    val logs = ListBuffer.empty[String]
    val failing = new SessionStore:
      def put(token: String, user: String): Unit = throw new IllegalStateException("store unavailable")
      def get(token: String): Option[String] = None
      def remove(token: String): Unit = ()
    val sessions = new Sessions(failing, logs += _)
    intercept[Exception](sessions.signIn("credential-canary", "ada", "r-1"))
    assert(!logs.mkString.contains("credential-canary"))
  }
  expectation(
    "Successful events are line-oriented structured JSON with request correlation and no session credentials."
  ) {
    val logs = ListBuffer.empty[String]
    val sessions = new Sessions(new SharedStore, logs += _)
    sessions.signIn("credential-canary", "a\"da", "r-1")
    sessions.signOut("credential-canary", "r-2")
    assertEquals(logs.size, 2)
    logs.zip(List("r-1", "r-2")).foreach { (line, request) =>
      assert(!line.contains("credential-canary"))
      assert(!line.contains('\n'))
      val obj = line.parseJson[Map[String, JValue]]
      assert(obj.values.exists(_ == JString(request)))
      assert(obj.size >= 2, "log lacks an event field")
    }
  }

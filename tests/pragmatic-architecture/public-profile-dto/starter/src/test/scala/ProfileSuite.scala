import java.util.UUID
import accounts.*
import backups.AccountBackup
import profiles.ProfileService
import http.*
import ba.sake.tupson.*
import org.typelevel.jawn.ast.{JString, JValue}

class ProfileSuite extends munit.FunSuite {
  private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
  private val user =
    UserRow(id, "Ada", "Learning Scala", None, "ada@example.com", "hash", Some("token"), false, "2026-01-01")
  private def repository = new InMemoryUserRepository(List(user))

  test("profile lookup returns name and JSON content type") {
    val response = new ProfileController(new ProfileService(repository)).get(id.toString)
    assertEquals(response.status, 200)
    assertEquals(response.contentType, "application/json")
    assertEquals(response.body.parseJson[Map[String, JValue]]("displayName"), JString("Ada"))
  }

  test("missing profile and invalid URL identifier") {
    val controller = new ProfileController(new ProfileService(repository))
    assertEquals(controller.get(new UUID(0, 2).toString), ProfileResponse(404, """{"error":"profile not found"}"""))
    assertEquals(controller.get("bad"), ProfileResponse(400, """{"error":"invalid identifier"}"""))
  }

  test("internal account backup round trips all stored fields") {
    val backup = new AccountBackup(repository)
    assertEquals(backup.restore(backup.exportAccount(id).get), user)
    assertEquals(backup.exportAccount(new UUID(0, 2)), None)
    assertEquals(repository.findById(id), Some(user))
  }
}

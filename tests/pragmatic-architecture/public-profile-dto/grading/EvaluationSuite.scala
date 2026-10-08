package grading

import java.util.UUID
import accounts.*
import backups.AccountBackup
import profiles.ProfileService
import http.*
import ba.sake.tupson.*
import org.typelevel.jawn.ast.{JNull, JString, JValue}
import evaluation.RecordedSuite

class EvaluationSuite extends RecordedSuite {
  private val id = new UUID(0, 1)
  private val user = UserRow(
    id,
    "Ada",
    "Learning Scala",
    Some("https://example.com/avatar.png"),
    "private@example.com",
    "private-password-hash",
    Some("private-refresh-token"),
    true,
    "2026-01-01"
  )
  private val publicFields = Set("id", "displayName", "bio", "avatarUrl", "memberSinceYear")

  expectation(
    "Successful public profiles preserve displayed fields and include the correct integer memberSinceYear."
  ) {
    List("1998-12-31", "2026-01-01", "2031-07-09").foreach { date =>
      val row = user.copy(createdAt = date)
      val response = controller(row).get(id.toString)
      assertEquals(response.status, 200)
      assertEquals(response.contentType, "application/json")
      val json = fields(response)
      val expected = Map[String, JValue](
        "id" -> JString(id.toString),
        "displayName" -> JString(user.displayName),
        "bio" -> JString(user.bio),
        "avatarUrl" -> JString(user.avatarUrl.get),
        "memberSinceYear" -> date.take(4).parseJson[JValue]
      )
      expected.foreach { case (key, value) => assertEquals(json.get(key), Some(value)) }
    }
  }

  expectation("Profiles with no avatar retain the avatarUrl key with a JSON null value.") {
    val response = controller(user.copy(avatarUrl = None, refreshToken = None)).get(id.toString)
    assertEquals(response.status, 200)
    assertEquals(fields(response)("avatarUrl"), JNull)
  }

  expectation("Public responses expose no email, password hash, refresh token, admin flag, or account timestamp.") {
    List(user, user.copy(refreshToken = None, avatarUrl = None, isAdmin = false)).foreach { row =>
      val response = controller(row).get(id.toString)
      val json = fields(response)
      assertEquals(json.keySet, publicFields)
      List("email", "passwordHash", "refreshToken", "isAdmin", "createdAt").foreach(key => assert(!json.contains(key)))
      List(row.email, row.passwordHash).foreach(secret => assert(!response.body.contains(secret)))
      row.refreshToken.foreach(secret => assert(!response.body.contains(secret)))
    }
  }

  expectation("Profile text is serialized as JSON safely, including quotes, newlines, Unicode, and empty values.") {
    List(
      user.copy(displayName = "Ada \"A\"\\", bio = "line one\nŽivjeli! <script>\"passwordHash\":\"fake\"</script>"),
      user.copy(displayName = "", bio = "", avatarUrl = Some(""))
    ).foreach { row =>
      val response = controller(row).get(id.toString)
      assertEquals(response.status, 200)
      val json = fields(response)
      assertEquals(json("displayName"), JString(row.displayName))
      assertEquals(json("bio"), JString(row.bio))
      assertEquals(json("avatarUrl"), JString(row.avatarUrl.get))
    }
  }

  expectation("Invalid and missing profile identifiers retain JSON errors, status codes, and content type.") {
    val web = controller(user)
    List("bad", "").foreach { invalid =>
      val response = web.get(invalid)
      assertEquals(response.status, 400)
      assertEquals(response.contentType, "application/json")
      assertEquals(fields(response), Map[String, JValue]("error" -> JString("invalid identifier")))
    }
    val missing = web.get(new UUID(0, 2).toString)
    assertEquals(missing.status, 404)
    assertEquals(missing.contentType, "application/json")
    assertEquals(fields(missing), Map[String, JValue]("error" -> JString("profile not found")))
  }

  expectation(
    "Persistence and the internal backup round trip retain every account field after a public profile read."
  ) {
    List(user, user.copy(avatarUrl = None, refreshToken = None)).foreach { row =>
      val repository = new InMemoryUserRepository(List(row))
      val backup = new AccountBackup(repository)
      val before = backup.exportAccount(id).get
      val response = new ProfileController(new ProfileService(repository)).get(id.toString)
      assertEquals(response.status, 200)
      assertEquals(repository.findById(id), Some(row))
      assertEquals(backup.restore(before), row)
      assertEquals(backup.restore(backup.exportAccount(id).get), row)
      val privateJson = before.parseJson[Map[String, JValue]]
      assertEquals(privateJson("passwordHash"), JString(row.passwordHash))
      assertEquals(privateJson("refreshToken"), row.refreshToken.map(JString.apply).getOrElse(JNull))
      assertEquals(backup.exportAccount(new UUID(0, 2)), None)
    }
  }

  private def controller(row: UserRow): ProfileController =
    new ProfileController(new ProfileService(new InMemoryUserRepository(List(row))))

  private def fields(response: ProfileResponse): Map[String, JValue] = response.body.parseJson[Map[String, JValue]]
}

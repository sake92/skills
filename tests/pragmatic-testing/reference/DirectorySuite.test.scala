//> using test.dep org.scalameta::munit:1.3.6

package directory

import ba.sake.tupson.*
import org.typelevel.jawn.ast.*
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import java.time.Duration
import scala.util.Using

class DirectorySuite extends munit.FunSuite {
  private def fixture(run: (Path, DirectoryApp, HttpClient) => Unit): Unit =
    val database = Files.createTempFile("directory-test-", ".db")
    try
      Using.resource(new DirectoryApp(database)) { app =>
        Using.resource(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) { client =>
          run(database, app, client)
        }
      }
    finally Files.deleteIfExists(database)

  private def request(
      client: HttpClient,
      app: DirectoryApp,
      method: String,
      path: String,
      body: String = ""
  ): HttpResponse[String] =
    client.send(
      HttpRequest
        .newBuilder(URI.create(app.baseUrl + path))
        .timeout(Duration.ofSeconds(3))
        .header("Content-Type", "application/json")
        .method(
          method,
          if body.isEmpty then HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body)
        )
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )

  private def create(client: HttpClient, app: DirectoryApp, name: String, email: String): User =
    val response = request(client, app, "POST", "/users", NewUser(name, email).toJson)
    assertEquals(response.statusCode(), 201)
    response.body().parseJson[User]

  test("updates cross HTTP binding, persist to SQLite, and return public projections") {
    fixture { (database, app, client) =>
      val user = create(client, app, "Ana", "ana@example.test")
      val updated =
        request(client, app, "PUT", s"/users/${user.id}", UserUpdate("Ana Marić", "new@example.test", 0).toJson)
      assertEquals(updated.statusCode(), 200)
      val expected = user.copy(name = "Ana Marić", email = "new@example.test", revision = 1)
      assertEquals(updated.body().parseJson[User], expected)
      val found = request(client, app, "GET", s"/users/${user.id}")
      assertEquals(found.statusCode(), 200)
      assertEquals(found.body().parseJson[User], expected)
      val fields = found.body().parseJson[Map[String, JValue]]
      assertEquals(fields.keySet, Set("id", "name", "email", "revision"))
      val history = request(client, app, "GET", s"/users/${user.id}/email-changes")
      assertEquals(
        history.body().parseJson[List[EmailChange]],
        List(EmailChange("ana@example.test", "new@example.test"))
      )
      Using.resource(new DirectoryApp(database)) { reopened =>
        assertEquals(request(client, reopened, "GET", s"/users/${user.id}").body().parseJson[User], expected)
      }
    }
  }

  test("invalid updates, stale revisions, and missing IDs have no side effects") {
    fixture { (_, app, client) =>
      val user = create(client, app, "Ana", "ana@example.test")
      val path = s"/users/${user.id}"
      assertEquals(
        request(client, app, "PUT", path, UserUpdate("", "changed@example.test", 0).toJson).statusCode(),
        400
      )
      assertEquals(request(client, app, "GET", path).body().parseJson[User], user)
      assertEquals(
        request(client, app, "PUT", path, UserUpdate("Other", "changed@example.test", 99).toJson).statusCode(),
        409
      )
      assertEquals(request(client, app, "GET", path).body().parseJson[User], user)
      assertEquals(request(client, app, "GET", path + "/email-changes").body().parseJson[List[EmailChange]], Nil)
      assertEquals(request(client, app, "GET", "/users/absent").statusCode(), 404)
    }
  }

  test("a unique-email failure rolls back the history entry and user update") {
    fixture { (_, app, client) =>
      val first = create(client, app, "Ana", "ana@example.test")
      create(client, app, "Ben", "ben@example.test")
      val path = s"/users/${first.id}"
      val response = request(client, app, "PUT", path, UserUpdate("Changed", "ben@example.test", 0).toJson)
      assertEquals(response.statusCode(), 409)
      assertEquals(request(client, app, "GET", path).body().parseJson[User], first)
      assertEquals(request(client, app, "GET", path + "/email-changes").body().parseJson[List[EmailChange]], Nil)
    }
  }
}

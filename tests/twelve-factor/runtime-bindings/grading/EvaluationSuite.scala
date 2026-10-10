package grading

import evaluation.RecordedSuite
import gateway.*
import com.sun.net.httpserver.HttpServer
import java.net.{InetSocketAddress, URI, ServerSocket}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import java.time.Duration
import java.util.concurrent.{TimeUnit, ConcurrentLinkedQueue}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

class EvaluationSuite extends RecordedSuite {
  private val valid = Map(
    "PORT" -> "8129",
    "DIRECTORY_URL" -> "http://127.0.0.1:9011/tenant/slots?site=west",
    "DIRECTORY_TOKEN" -> "fixture-credential",
    "DIRECTORY_TIMEOUT_MS" -> "1900",
    "MAX_SLOTS" -> "2"
  )
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()

  expectation("III: Independent runtime settings have the same meaning for every deployment label.") {
    val expected = Config(8129, valid("DIRECTORY_URL"), valid("DIRECTORY_TOKEN"), 1900, 2)
    List(None, Some("development"), Some("staging"), Some("production"), Some("review-317"))
      .foreach { label =>
        val env = label.fold(valid)(value => valid.updated("APP_ENV", value))
        assertEquals(Config.load(env.updated("UNRELATED_SETTING", "ignored")), expected)
      }
    assertEquals(Config.load(valid - "DIRECTORY_TIMEOUT_MS" - "MAX_SLOTS").timeoutMs, 2000)
    assertEquals(Config.load(valid - "DIRECTORY_TIMEOUT_MS" - "MAX_SLOTS").maxSlots, 25)
  }

  expectation("III: Missing and unusable required settings fail with the setting name.") {
    List("PORT", "DIRECTORY_URL", "DIRECTORY_TOKEN").foreach { key =>
      assertSettingFailure(valid - key, key)
      assertSettingFailure(valid.updated(key, ""), key)
      assertSettingFailure(valid.updated(key, "   "), key)
    }
    List("0", "65536", "abc").foreach(value => assertSettingFailure(valid.updated("PORT", value), "PORT"))
    List("not-a-url", "http:///slots", "file:///tmp/slots", "http://host/slots#fragment")
      .foreach(value => assertSettingFailure(valid.updated("DIRECTORY_URL", value), "DIRECTORY_URL"))
  }

  expectation("III: Supplied optional settings are validated rather than silently defaulted.") {
    List(
      "DIRECTORY_TIMEOUT_MS" -> List("0", "-1", "30001", "abc", ""),
      "MAX_SLOTS" -> List("0", "-1", "1001", "abc", "")
    ).foreach { (key, values) =>
      values.foreach(value => assertSettingFailure(valid.updated(key, value), key))
    }
    assertEquals(Config.load(valid.updated("DIRECTORY_TIMEOUT_MS", "1").updated("MAX_SLOTS", "1000")).maxSlots, 1000)
    assertEquals(Config.load(valid.updated("DIRECTORY_TIMEOUT_MS", "30000")).timeoutMs, 30000)
  }

  expectation("III: Invalid deployment settings terminate the real entrypoint before it serves work.") {
    val port = freePort()
    val child = launch(valid.updated("PORT", port.toString).updated("MAX_SLOTS", "-1"))
    try
      assert(child.process.waitFor(3, TimeUnit.SECONDS), "invalid configuration left the service running")
      assert(child.process.exitValue() != 0, "invalid configuration exited successfully")
      val output = child.output
      assert(output.contains("MAX_SLOTS"), output)
      assert(!output.contains("listening="), "invalid configuration accepted work")
      assert(!output.contains(valid("DIRECTORY_TOKEN")), "credential leaked to startup output")
    finally child.close()
  }

  expectation("III: Started components consume their validated settings without rereading the environment.") {
    withDirectory { directory =>
      val original = valid.updated("DIRECTORY_URL", directory.url).updated("APP_ENV", "production")
      val environment = mutable.Map.from(original)
      val config = Config.load(environment)
      environment.update("MAX_SLOTS", "1")
      environment.update("DIRECTORY_TOKEN", "changed-token")
      environment.update("DIRECTORY_URL", "http://127.0.0.1:1/other")
      assertEquals(DirectoryClient(config, environment).slots(), "morning\nafternoon")
      val app = Gateway.start(config.copy(port = 0), environment)
      try
        val response = get(s"http://127.0.0.1:${app.port}/slots")
        assertEquals(response.statusCode(), 200)
        assertEquals(response.body(), "morning\nafternoon")
      finally app.close()
    }
  }

  expectation(
    "IV: The same compiled service uses replacement endpoint paths, queries, and credentials in fresh processes."
  ) {
    withDirectory { first =>
      withDirectory { second =>
        List(first -> "review-317", second -> "production").foreach { (directory, label) =>
          val port = freePort()
          val child = launch(
            valid
              .updated("PORT", port.toString)
              .updated("DIRECTORY_URL", directory.url)
              .updated("APP_ENV", label)
          )
          try
            child.awaitListening()
            val response = get(s"http://127.0.0.1:$port/slots")
            assertEquals(response.statusCode(), 200, child.output)
            assertEquals(response.body(), "morning\nafternoon")
            assertEquals(directory.requests.size(), 1)
            assertEquals(directory.requests.peek(), "/tenant/slots?site=west|Bearer fixture-credential")
          finally child.close()
        }
      }
    }
  }

  expectation("IV: Directory failures remain failures without exposing credentials or upstream payloads.") {
    withDirectory { directory =>
      val config = Config(0, directory.url, "wrong-credential", 1000, 2)
      val app = Gateway.start(config, Map.empty)
      try
        val response = get(s"http://127.0.0.1:${app.port}/slots")
        assertEquals(response.statusCode(), 502)
        assert(!response.body().contains("wrong-credential"))
        assert(!response.body().contains("upstream-private-payload"))
        assertEquals(directory.requests.size(), 1)
      finally app.close()
    }
  }

  expectation("III: Invalid bindings and credentials produce useful credential-safe errors.") {
    val canary = "secret-canary-29"
    List(
      valid.updated("DIRECTORY_URL", s"http://user:$canary@host/slots"),
      valid.updated("DIRECTORY_URL", s"http://user:$canary@/slots"),
      valid.updated("DIRECTORY_TOKEN", s"$canary\r\nInjected: yes")
    )
      .foreach { env =>
        val key = if env("DIRECTORY_TOKEN").contains(canary) then "DIRECTORY_TOKEN" else "DIRECTORY_URL"
        val error = intercept[Exception](Config.load(env))
        val message = Option(error.getMessage).getOrElse("")
        assert(message.contains(key), "diagnostic did not name the setting")
        assert(!message.contains(canary), "credential leaked to configuration error")
      }
  }

  private def assertSettingFailure(env: Map[String, String], key: String): Unit =
    val error = intercept[Exception](Config.load(env))
    assert(Option(error.getMessage).exists(_.contains(key)), s"diagnostic did not name $key")

  private def get(url: String): HttpResponse[String] =
    val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).build()
    client.send(request, HttpResponse.BodyHandlers.ofString())

  private def freePort(): Int =
    val socket = ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))
    try socket.getLocalPort
    finally socket.close()

  private final class Directory(val server: HttpServer, val requests: ConcurrentLinkedQueue[String]):
    val url = s"http://127.0.0.1:${server.getAddress.getPort}/tenant/slots?site=west"

  private def withDirectory[A](body: Directory => A): A =
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val requests = ConcurrentLinkedQueue[String]()
    server.createContext(
      "/tenant/slots",
      exchange =>
        val uri = exchange.getRequestURI.toString
        val authorization = exchange.getRequestHeaders.getFirst("Authorization")
        requests.add(s"$uri|$authorization")
        val authorized = authorization == "Bearer fixture-credential" && uri == "/tenant/slots?site=west"
        val response =
          (if authorized then "morning\nafternoon\nevening" else "upstream-private-payload").getBytes(UTF_8)
        exchange.sendResponseHeaders(if authorized then 200 else 401, response.length)
        try exchange.getResponseBody.write(response)
        finally exchange.close()
    )
    server.start()
    try body(Directory(server, requests))
    finally server.stop(0)

  private final class Child(val process: Process, val log: Path) extends AutoCloseable:
    def output: String = Files.readString(log)
    def awaitListening(): Unit =
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      while process.isAlive && !output.contains("listening=") && System.nanoTime() < deadline do Thread.sleep(20)
      assert(output.contains("listening="), s"entrypoint did not start: $output")
    def close(): Unit =
      process.destroy()
      if !process.waitFor(2, TimeUnit.SECONDS) then
        process.destroyForcibly()
        assert(process.waitFor(2, TimeUnit.SECONDS), "fixture JVM did not terminate")
      Files.deleteIfExists(log)

  private def launch(environment: Map[String, String]): Child =
    val log = Files.createTempFile("gateway-grading-", ".log")
    val java = Path.of(System.getProperty("java.home"), "bin", "java").toString
    val command = ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), "gateway.Main")
    command.environment().clear()
    command.environment().putAll(environment.asJava)
    command.redirectErrorStream(true)
    command.redirectOutput(log.toFile)
    try Child(command.start(), log)
    catch
      case error: Throwable =>
        Files.deleteIfExists(log)
        throw error
}

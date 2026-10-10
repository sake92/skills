package gateway

import com.sun.net.httpserver.HttpServer
import java.net.{InetSocketAddress, URI}
import java.nio.charset.StandardCharsets.UTF_8

class GatewaySuite extends munit.FunSuite {
  test("production uses the configured directory") {
    val env = Map(
      "APP_ENV" -> "production",
      "PORT" -> "8089",
      "DIRECTORY_URL" -> "http://localhost:9010/slots",
      "DIRECTORY_TOKEN" -> "test-token"
    )
    assertEquals(Config.load(env).directoryUrl, env("DIRECTORY_URL"))
  }

  test("the gateway serves slots from an authenticated directory") {
    val directory = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    directory.createContext(
      "/slots",
      exchange =>
        val authorized = exchange.getRequestHeaders.getFirst("Authorization") == "Bearer test-token"
        val body = (if authorized then "morning\nafternoon\nevening" else "denied").getBytes(UTF_8)
        exchange.sendResponseHeaders(if authorized then 200 else 401, body.length)
        try exchange.getResponseBody.write(body)
        finally exchange.close()
    )
    directory.start()
    try
      val config = Config(0, s"http://127.0.0.1:${directory.getAddress.getPort}/slots", "test-token", 2000, 2)
      val gateway = Gateway.start(config, Map("MAX_SLOTS" -> "2"))
      try
        val client = java.net.http.HttpClient.newHttpClient()
        val request = java.net.http.HttpRequest
          .newBuilder(URI.create(s"http://127.0.0.1:${gateway.port}/slots"))
          .timeout(java.time.Duration.ofSeconds(3))
          .build()
        val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
        assertEquals(response.statusCode(), 200)
        assertEquals(response.body(), "morning\nafternoon")
      finally gateway.close()
    finally directory.stop(0)
  }
}

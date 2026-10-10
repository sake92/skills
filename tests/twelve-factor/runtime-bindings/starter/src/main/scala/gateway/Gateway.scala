package gateway

import com.sun.net.httpserver.HttpServer
import java.net.{HttpURLConnection, InetSocketAddress, URI}
import java.nio.charset.StandardCharsets.UTF_8

final class DirectoryClient(config: Config, environment: scala.collection.Map[String, String]):
  def slots(): String =
    val connection = URI.create(config.directoryUrl).toURL.openConnection().asInstanceOf[HttpURLConnection]
    connection.setConnectTimeout(config.timeoutMs)
    connection.setReadTimeout(config.timeoutMs)
    connection.setRequestProperty("Authorization", s"Bearer ${config.directoryToken}")
    try
      val input = connection.getInputStream
      val body = try new String(input.readAllBytes(), UTF_8)
      finally input.close()
      val limit = environment.get("MAX_SLOTS").flatMap(_.toIntOption).getOrElse(25)
      body.linesIterator.take(limit).mkString("\n")
    finally connection.disconnect()

final class RunningGateway(private val server: HttpServer) extends AutoCloseable:
  val port: Int = server.getAddress.getPort
  def close(): Unit = server.stop(0)

object Gateway:
  def start(config: Config, environment: scala.collection.Map[String, String]): RunningGateway =
    val directory = DirectoryClient(config, environment)
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", config.port), 0)
    server.createContext(
      "/slots",
      exchange =>
        try
          val body = directory.slots().getBytes(UTF_8)
          exchange.sendResponseHeaders(200, body.length)
          val output = exchange.getResponseBody
          try output.write(body)
          finally output.close()
        catch
          case _: Exception =>
            val body = "directory unavailable".getBytes(UTF_8)
            exchange.sendResponseHeaders(502, body.length)
            val output = exchange.getResponseBody
            try output.write(body)
            finally output.close()
        finally exchange.close()
    )
    try
      server.start()
      RunningGateway(server)
    catch
      case error: Throwable =>
        server.stop(0)
        throw error

object Main:
  def main(args: Array[String]): Unit =
    val config = try Config.load(sys.env)
    catch
      case error: IllegalArgumentException =>
        System.err.println(s"configuration error: ${error.getMessage}")
        sys.exit(2)
    val gateway = Gateway.start(config, sys.env)
    Runtime.getRuntime.addShutdownHook(Thread(() => gateway.close(), "gateway-shutdown"))
    println(s"listening=${gateway.port}")

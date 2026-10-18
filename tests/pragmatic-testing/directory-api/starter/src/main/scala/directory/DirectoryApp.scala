package directory

import java.net.InetSocketAddress
import java.nio.file.Path as FilePath
import ba.sake.sharaf.*
import ba.sake.tupson.*
import ba.sake.sharaf.undertow.SharafUndertowHandler
import sttp.model.StatusCode
import io.undertow.Undertow
import io.undertow.server.handlers.BlockingHandler

class DirectoryApp(database: FilePath, port: Int = 0) extends AutoCloseable {
  private val repository = new UserRepository(database)
  private val routes = Routes {
    case POST -> Path("users") =>
      val input = Request.current.bodyJson[NewUser]
      if !valid(input.name, input.email) then Response.withStatus(StatusCode.BadRequest)
      else
        repository.create(input) match
          case Right(user)  => Response.withBody(user.publicView).withStatus(StatusCode.Created)
          case Left(status) => Response.withStatus(StatusCode(status))

    case GET -> Path("users", id) =>
      repository.find(id) match
        case Some(user) => Response.withBody(user.publicView)
        case None       => Response.withStatus(StatusCode.NotFound)

    case PUT -> Path("users", id) =>
      val input = Request.current.bodyJson[UserUpdate]
      if !valid(input.name, input.email) then Response.withStatus(StatusCode.BadRequest)
      else
        repository.update(id, input) match
          case Right(user)  => Response.withBody(user.publicView)
          case Left(status) => Response.withStatus(StatusCode(status))

    case GET -> Path("users", id, "email-changes") =>
      repository.find(id) match
        case Some(_) => Response.withBody(repository.history(id))
        case None    => Response.withStatus(StatusCode.NotFound)
  }
  private val handler = SharafHandler.exceptions(SharafHandler.routes(routes), ExceptionMapper.json)
  private val server = Undertow
    .builder()
    .addHttpListener(port, "127.0.0.1")
    .setIoThreads(1)
    .setWorkerThreads(4)
    .setHandler(BlockingHandler(SharafUndertowHandler(handler)))
    .build()
  server.start()

  def baseUrl: String =
    val address = server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress]
    s"http://127.0.0.1:${address.getPort}"
  def close(): Unit = server.stop()

  private def valid(name: String, email: String): Boolean = name.trim.nonEmpty && email.contains("@")
}

object Main:
  def main(args: Array[String]): Unit =
    val database = FilePath.of(args.headOption.getOrElse("directory.db"))
    val port = args.lift(1).map(_.toInt).getOrElse(8080)
    val app = new DirectoryApp(database, port)
    Runtime.getRuntime.addShutdownHook(new Thread(() => app.close()))
    println(s"Directory listening on ${app.baseUrl}")

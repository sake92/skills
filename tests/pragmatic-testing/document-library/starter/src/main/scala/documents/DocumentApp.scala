package documents

import java.net.InetSocketAddress
import java.io.{ByteArrayInputStream, InputStream}
import software.amazon.awssdk.services.s3.S3Client
import ba.sake.sharaf.*
import ba.sake.tupson.*
import ba.sake.sharaf.undertow.SharafUndertowHandler
import sttp.model.StatusCode
import io.undertow.Undertow
import io.undertow.server.handlers.BlockingHandler

class DocumentApp(s3: S3Client, bucket: String, port: Int = 0) extends AutoCloseable {
  private val documents = new Documents(s3, bucket)
  private val routes = Routes {
    case POST -> Path("documents") =>
      documents.upload(Request.current.bodyJson[Upload]) match
        case Right(document) => Response.withBody(document).withStatus(StatusCode.Created)
        case Left(status)    => Response.withStatus(StatusCode(status))
    case PUT -> Path("documents", id) =>
      val input = Request.current.bodyJson[Upload]
      documents.replace(id, input) match
        case Right(document) => Response.withBody(document)
        case Left(status)    => Response.withStatus(StatusCode(status))
    case GET -> Path("documents")     => Response.withBody(documents.list())
    case GET -> Path("documents", id) =>
      documents.find(id) match
        case Some(document) => Response.withBody(document)
        case None           => Response.withStatus(StatusCode.NotFound)
    case GET -> Path("documents", id, "content") =>
      documents.content(id) match
        case Right((bytes, contentType)) =>
          Response.withBody[InputStream](new ByteArrayInputStream(bytes)).settingHeader("Content-Type", contentType)
        case Left(status) => Response.withStatus(StatusCode(status))
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

}

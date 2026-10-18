//> using test.dep org.scalameta::munit:1.3.6
//> using test.dep io.floci:testcontainers-floci:2.16.1
package documents

import ba.sake.tupson.*
import io.floci.testcontainers.FlociContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.auth.credentials.*
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.{Base64, UUID}
import scala.util.Using

class DocumentSuite extends munit.FunSuite {
  private val floci = new FlociContainer(DockerImageName.parse("floci/floci:2.3.0")).withDockerSocket(false)
  private var s3: S3Client = scala.compiletime.uninitialized
  override def beforeAll(): Unit =
    floci.start()
    try
      s3 = S3Client
        .builder()
        .endpointOverride(URI.create(floci.getEndpoint()))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .forcePathStyle(true)
        .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(5)).build())
        .build()
    catch
      case e: Exception =>
        floci.close()
        throw e
  override def afterAll(): Unit =
    try if s3 != null then s3.close()
    finally floci.close()

  private def fixture(run: (String, DocumentApp, HttpClient) => Unit): Unit =
    val bucket = "documents-" + UUID.randomUUID().toString
    s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
    Using.resource(new DocumentApp(s3, bucket)) { app =>
      Using.resource(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) { client =>
        run(bucket, app, client)
      }
    }

  private def request(
      client: HttpClient,
      app: DocumentApp,
      method: String,
      path: String,
      body: String = ""
  ): HttpResponse[Array[Byte]] =
    client.send(
      HttpRequest
        .newBuilder(URI.create(app.baseUrl + path))
        .timeout(Duration.ofSeconds(8))
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body))
        .build(),
      HttpResponse.BodyHandlers.ofByteArray()
    )
  private def json[T: JsonRW](response: HttpResponse[Array[Byte]]): T =
    new String(response.body(), java.nio.charset.StandardCharsets.UTF_8).parseJson[T]
  private val bytes = Array[Byte](0, -1, -128, 65, 10, 13)
  private val upload = Upload("report.pdf", "application/pdf", Base64.getEncoder.encodeToString(bytes))
  private def create(client: HttpClient, app: DocumentApp): Document =
    val response = request(client, app, "POST", "/documents", upload.toJson)
    assertEquals(response.statusCode(), 201)
    json[Document](response)

  test("upload stores independently readable bytes and MIME at the documented S3 key") {
    fixture { (bucket, app, client) =>
      val document = create(client, app)
      assertEquals(document.copy(id = ""), Document("", "report.pdf", "application/pdf", bytes.length))
      val keys = s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build()).contents()
      import scala.jdk.CollectionConverters.*
      assertEquals(keys.asScala.map(_.key()).toList, List(s"documents/${document.id}"))
      val stored =
        s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(s"documents/${document.id}").build())
      assertEquals(stored.asByteArray().toSeq, bytes.toSeq)
      assertEquals(stored.response().contentType(), "application/pdf")
      val found = request(client, app, "GET", s"/documents/${document.id}/content")
      assertEquals(found.statusCode(), 200)
      assertEquals(found.body().toSeq, bytes.toSeq)
      assertEquals(found.headers().firstValue("Content-Type").orElse(""), "application/pdf")
      assertEquals(json[Document](request(client, app, "GET", s"/documents/${document.id}")), document)
    }
  }
  test("replacement binds HTTP input, updates the existing object and keeps the same id") {
    fixture { (bucket, app, client) =>
      val original = create(client, app)
      val replacementBytes = Array[Byte](3, 0, -127, 70, 11, 18)
      val replacement = Upload("diagram.png", "image/png", Base64.getEncoder.encodeToString(replacementBytes))
      val response = request(client, app, "PUT", s"/documents/${original.id}", replacement.toJson)
      assertEquals(response.statusCode(), 200)
      val expected = Document(original.id, "diagram.png", "image/png", replacementBytes.length)
      assertEquals(json[Document](response), expected)
      assertEquals(json[List[Document]](request(client, app, "GET", "/documents")), List(expected))
      val stored =
        s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(s"documents/${original.id}").build())
      assertEquals(stored.asByteArray().toSeq, replacementBytes.toSeq)
      assertEquals(stored.response().contentType(), "image/png")
      assertEquals(s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build()).keyCount().intValue(), 1)
      val downloaded = request(client, app, "GET", s"/documents/${original.id}/content")
      assertEquals(downloaded.statusCode(), 200)
      assertEquals(downloaded.body().toSeq, replacementBytes.toSeq)
      assertEquals(downloaded.headers().firstValue("Content-Type").orElse(""), "image/png")
      assertEquals(request(client, app, "PUT", "/documents/absent", replacement.toJson).statusCode(), 404)
      assertEquals(
        request(client, app, "PUT", s"/documents/${original.id}", replacement.copy(name = " ").toJson).statusCode(),
        400
      )
      assertEquals(json[Document](request(client, app, "GET", s"/documents/${original.id}")), expected)
    }
  }
  test("invalid input leaves metadata and S3 unchanged") {
    fixture { (bucket, app, client) =>
      for invalid <- List(upload.copy(name = " "), upload.copy(contentType = " "), upload.copy(contentBase64 = "%%%"))
      do assertEquals(request(client, app, "POST", "/documents", invalid.toJson).statusCode(), 400)
      assertEquals(json[List[Document]](request(client, app, "GET", "/documents")), Nil)
      assertEquals(s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build()).keyCount().intValue(), 0)
    }
  }
  test("failed upload does not publish metadata or disturb previous documents") {
    fixture { (bucket, app, client) =>
      val existing = create(client, app)
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(s"documents/${existing.id}").build())
      s3.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build())
      assertEquals(request(client, app, "POST", "/documents", upload.toJson).statusCode(), 503)
      assertEquals(json[List[Document]](request(client, app, "GET", "/documents")), List(existing))
    }
  }
  test("missing metadata and deleted backing objects produce 404") {
    fixture { (bucket, app, client) =>
      assertEquals(request(client, app, "GET", "/documents/absent").statusCode(), 404)
      assertEquals(request(client, app, "GET", "/documents/absent/content").statusCode(), 404)
      val existing = create(client, app)
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(s"documents/${existing.id}").build())
      assertEquals(request(client, app, "GET", s"/documents/${existing.id}/content").statusCode(), 404)
    }
  }
}

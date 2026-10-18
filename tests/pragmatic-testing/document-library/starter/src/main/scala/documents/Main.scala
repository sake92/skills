package documents

import java.net.URI
import java.time.Duration
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration

object Main:
  def main(args: Array[String]): Unit =
    val bucket = sys.env("DOCUMENT_BUCKET")
    val builder = S3Client
      .builder()
      .region(Region.of(sys.env.getOrElse("AWS_REGION", "us-east-1")))
      .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallTimeout(Duration.ofSeconds(10)).build())
    sys.env.get("S3_ENDPOINT").foreach(endpoint => builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true))
    val s3 = builder.build()
    try
      val app = new DocumentApp(s3, bucket, sys.env.getOrElse("PORT", "8080").toInt)
      Runtime.getRuntime.addShutdownHook(new Thread(() =>
        app.close(); s3.close()
      ))
      println(s"Documents listening on ${app.baseUrl}")
    catch
      case e: Exception =>
        s3.close()
        throw e

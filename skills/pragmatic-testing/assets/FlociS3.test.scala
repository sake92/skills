//> using scala 3.9.0
//> using test.dep org.scalameta::munit:1.3.6
//> using test.dep io.floci:testcontainers-floci:2.16.1
//> using test.dep software.amazon.awssdk:s3:2.42.29

import io.floci.testcontainers.FlociContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.{CreateBucketRequest, GetObjectRequest, PutObjectRequest}

import java.net.URI
import java.time.Duration
import java.util.UUID
import scala.concurrent.duration.*
import scala.util.Using

// Copy the fixture into your test sources; retain your project's Scala/SDK versions.
object FlociS3Fixture:
  def withS3[A](run: (S3Client, String) => A): A =
    Using.resource(
      new FlociContainer(DockerImageName.parse("floci/floci:2.3.0"))
        .withDockerSocket(false)
        .withStartupTimeout(Duration.ofSeconds(60))
    ) { floci =>
      floci.start()
      Using.resource(
        S3Client
          .builder()
          .endpointOverride(URI.create(floci.getEndpoint()))
          .region(Region.of(floci.getRegion()))
          .credentialsProvider(
            StaticCredentialsProvider.create(
              AwsBasicCredentials.create(floci.getAccessKey(), floci.getSecretKey())
            )
          )
          .forcePathStyle(true)
          .overrideConfiguration(
            ClientOverrideConfiguration
              .builder()
              .apiCallTimeout(Duration.ofSeconds(5))
              .build()
          )
          .build()
      ) { s3 =>
        val bucket = s"test-${UUID.randomUUID()}"
        s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        run(s3, bucket)
      }
    }

// This proves setup only. Replace/extend it with behavior through your application.
class FlociS3SetupTest extends munit.FunSuite {
  override val munitTimeout = 2.minutes

  test("real SDK stores bytes and content type in a disposable service") {
    FlociS3Fixture.withS3 { (s3, bucket) =>
      val bytes = Array[Byte](0, 1, -1, 10, 42)
      s3.putObject(
        PutObjectRequest
          .builder()
          .bucket(bucket)
          .key("sample.bin")
          .contentType("application/octet-stream")
          .build(),
        RequestBody.fromBytes(bytes)
      )
      val stored = s3.getObjectAsBytes(
        GetObjectRequest.builder().bucket(bucket).key("sample.bin").build()
      )
      assertEquals(stored.asByteArray().toSeq, bytes.toSeq)
      assertEquals(stored.response().contentType(), "application/octet-stream")
    }
  }
}

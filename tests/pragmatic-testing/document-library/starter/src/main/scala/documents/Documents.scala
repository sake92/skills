package documents

import ba.sake.tupson.*
import java.util.{Base64, UUID}
import scala.collection.concurrent.TrieMap
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.core.sync.RequestBody

case class Upload(name: String, contentType: String, contentBase64: String) derives JsonRW
case class Document(id: String, name: String, contentType: String, size: Int) derives JsonRW

class Documents(s3: S3Client, bucket: String) {
  private val metadata = TrieMap.empty[String, Document]

  def list(): List[Document] = metadata.values.toList.sortBy(_.id)
  def find(id: String): Option[Document] = metadata.get(id)

  def upload(input: Upload): Either[Int, Document] = store(UUID.randomUUID().toString, input)

  def replace(id: String, input: Upload): Either[Int, Document] =
    if metadata.contains(id) then store(id, input) else Left(404)

  private def store(id: String, input: Upload): Either[Int, Document] =
    val decoded = scala.util.Try(Base64.getDecoder.decode(input.contentBase64)).toOption
    if input.name.trim.isEmpty || input.contentType.trim.isEmpty || decoded.isEmpty then Left(400)
    else
      val bytes = decoded.get
      val document = Document(id, input.name, input.contentType, bytes.length)
      try
        s3.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key(document.id)).contentType(input.contentType).build(),
          RequestBody.fromBytes(bytes)
        )
        metadata.put(document.id, document)
        Right(document)
      catch case _: S3Exception => Left(503)

  def content(id: String): Either[Int, (Array[Byte], String)] =
    metadata.get(id) match
      case None           => Left(404)
      case Some(document) =>
        try
          val result = s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key(id)).build())
          Right((result.asByteArray(), result.response().contentType()))
        catch
          case _: NoSuchKeyException => Left(404)
          case _: S3Exception        => Left(503)

  private def key(id: String): String = s"documents/$id"
}

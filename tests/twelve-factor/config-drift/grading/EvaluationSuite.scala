package grading

import evaluation.RecordedSuite
import service.*
import java.nio.file.{Files, Path}

class EvaluationSuite extends RecordedSuite {
  private val valid =
    Map("PORT" -> "9123", "DATABASE_URL" -> "postgres://remote/sessions", "SESSION_TTL_MINUTES" -> "37")

  expectation("Explicit settings behave identically across deployment names.") {
    List("development", "staging", "production", "preview").foreach { name =>
      assertEquals(Config.load(valid.updated("APP_ENV", name)), Config(9123, valid("DATABASE_URL"), 37))
    }
  }
  expectation("Missing required settings fail with the setting name.") {
    valid.keys.foreach { key =>
      val error = intercept[Exception](Config.load(valid - key))
      assert(Option(error.getMessage).exists(_.contains(key)))
    }
  }
  expectation("Malformed and out-of-range numeric settings fail with the setting name.") {
    List(
      "PORT" -> "zero",
      "PORT" -> "0",
      "PORT" -> "65536",
      "SESSION_TTL_MINUTES" -> "-1",
      "SESSION_TTL_MINUTES" -> "x",
      "SESSION_TTL_MINUTES" -> "0"
    ).foreach { (key, value) =>
      val error = intercept[Exception](Config.load(valid.updated(key, value)))
      assert(Option(error.getMessage).exists(_.contains(key)))
    }
  }
  expectation("Blank or malformed backing-service bindings fail without disclosing credentials.") {
    List("", "   ", "not-a-url", "postgres://user:secret-canary@/sessions").foreach { value =>
      val error = intercept[Exception](Config.load(valid.updated("DATABASE_URL", value)))
      val message = Option(error.getMessage).getOrElse("")
      assert(message.contains("DATABASE_URL"))
      assert(!message.contains("secret-canary"))
    }
  }
  expectation("Policy consumes validated configuration rather than re-reading raw settings.") {
    val config = Config.load(valid)
    assertEquals(new SessionPolicy(config, Map.empty).expiresAfterMinutes, 37)
    assertEquals(new SessionPolicy(config, Map("SESSION_TTL_MINUTES" -> "999")).expiresAfterMinutes, 37)
  }
  expectation("Required deployment settings are documented with safe examples.") {
    val root = Path.of(sys.env("SUBMISSION_DIR"))
    val stream = Files.walk(root)
    val documents = try
      import scala.jdk.CollectionConverters.*
      stream.iterator.asScala
        .filter(p => Files.isRegularFile(p) && (p.toString.endsWith(".md") || p.getFileName.toString == ".env.example"))
        .map(Files.readString)
        .mkString("\n")
    finally stream.close()
    List("PORT", "DATABASE_URL", "SESSION_TTL_MINUTES").foreach(key => assert(documents.contains(key)))
    assert("(?i)PORT[^\\r\\n]{0,60}\\b[1-9][0-9]{1,4}\\b".r.findFirstIn(documents).nonEmpty, "no example port")
    assert(documents.contains("postgres://") || documents.contains("postgresql://"), "no example binding")
  }
}

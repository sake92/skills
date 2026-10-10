package gateway

case class Config(port: Int, directoryUrl: String, directoryToken: String, timeoutMs: Int, maxSlots: Int)

object Config:
  def load(env: scala.collection.Map[String, String]): Config =
    val profile = env.getOrElse("APP_ENV", "development")
    val url = if profile == "production" then env.getOrElse("DIRECTORY_URL", "http://localhost:9090/slots")
    else "http://localhost:9090/slots"
    Config(
      env.get("PORT").flatMap(_.toIntOption).getOrElse(8080),
      url,
      env.getOrElse("DIRECTORY_TOKEN", "local-token"),
      env.get("DIRECTORY_TIMEOUT_MS").flatMap(_.toIntOption).getOrElse(2000),
      env.get("MAX_SLOTS").flatMap(_.toIntOption).getOrElse(25)
    )

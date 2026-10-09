package service

case class Config(port: Int, databaseUrl: String, sessionTtlMinutes: Int)

object Config:
  def load(env: Map[String, String]): Config =
    val database = if env.getOrElse("APP_ENV", "development") == "production" then
      env.getOrElse("DATABASE_URL", "postgres://localhost/prod")
    else "postgres://localhost/dev"
    Config(
      env.get("PORT").flatMap(_.toIntOption).getOrElse(8080),
      database,
      env.get("SESSION_TTL_MINUTES").flatMap(_.toIntOption).getOrElse(60)
    )

final class SessionPolicy(config: Config, environment: Map[String, String]):
  def expiresAfterMinutes: Int = environment.get("SESSION_TTL_MINUTES").flatMap(_.toIntOption).getOrElse(60)
  def endpoint: String = config.databaseUrl

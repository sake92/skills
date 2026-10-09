package service

class ConfigSuite extends munit.FunSuite:
  test("production reads configured endpoint") {
    val env = Map(
      "APP_ENV" -> "production",
      "PORT" -> "9000",
      "DATABASE_URL" -> "postgres://db/service",
      "SESSION_TTL_MINUTES" -> "45"
    )
    assertEquals(Config.load(env), Config(9000, "postgres://db/service", 45))
  }
  test("policy exposes endpoint") {
    val config = Config(9000, "postgres://db/service", 45)
    assertEquals(new SessionPolicy(config, Map.empty).endpoint, config.databaseUrl)
  }

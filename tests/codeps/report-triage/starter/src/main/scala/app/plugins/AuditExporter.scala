package app.plugins

final class AuditExporter extends java.util.function.Function[String, String]:
  def apply(value: String): String = s"AUDIT:$value"

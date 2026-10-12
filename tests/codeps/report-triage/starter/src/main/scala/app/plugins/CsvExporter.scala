package app.plugins

final class CsvExporter extends java.util.function.Function[String, String]:
  def apply(value: String): String = value.replace('|', ',')

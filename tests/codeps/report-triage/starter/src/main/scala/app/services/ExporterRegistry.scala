package app.services

import java.nio.file.{Files, Path}
import java.util.Properties
import java.util.function.Function

final class ExporterRegistry(config: Path) {
  private val formats = new Properties()
  private val input = Files.newInputStream(config)
  try formats.load(input)
  finally input.close()

  def render(format: String, value: String): String =
    val className =
      Option(formats.getProperty(format)).getOrElse(throw IllegalArgumentException(s"Unknown format: $format"))
    val exporter =
      Class.forName(className).getDeclaredConstructor().newInstance().asInstanceOf[Function[String, String]]
    exporter.apply(value)
}

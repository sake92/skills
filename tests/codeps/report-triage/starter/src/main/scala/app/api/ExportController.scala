package app.api

import app.services.ExportService

final class ExportController(service: ExportService):
  def download(region: String, format: String): String = service.render(region, format).mkString("\n")

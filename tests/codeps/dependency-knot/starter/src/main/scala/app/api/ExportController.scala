package app.api

import app.services.ExportService

final class ExportController(service: ExportService):
  def download(region: String): String = service.csv(region)

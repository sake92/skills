package app.jobs

import app.services.ExportService

final class NightlyExport(service: ExportService):
  def rows(region: String): List[String] = service.render(region, "audit")

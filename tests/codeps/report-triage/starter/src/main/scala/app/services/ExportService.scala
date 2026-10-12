package app.services

import app.repositories.CustomerRepository

final class ExportService(repository: CustomerRepository, registry: ExporterRegistry):
  def render(region: String, format: String): List[String] =
    repository
      .inRegion(region)
      .map(customer => registry.render(format, s"${customer.id}|${customer.name}|${customer.region}"))

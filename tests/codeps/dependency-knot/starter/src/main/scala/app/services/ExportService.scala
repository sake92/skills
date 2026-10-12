package app.services

import app.repositories.CustomerRepository
import app.api.ResponsePolicy

final class ExportService(repository: CustomerRepository):
  def csv(region: String): String =
    val rows = repository.active(region).map { customer =>
      s"${customer.id},${customer.name},${customer.region},${ResponsePolicy.activeLabel(customer.active)}"
    }
    ("id,name,region,status" :: rows).mkString("\n")

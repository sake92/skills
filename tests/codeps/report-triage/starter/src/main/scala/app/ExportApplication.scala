package app

import java.nio.file.Path
import app.api.ExportController
import app.dao.CustomerDao
import app.domain.Customer
import app.jobs.NightlyExport
import app.repositories.CustomerRepository
import app.services.{ExporterRegistry, ExportService}

final class ExportApplication(initial: List[Customer], config: Path):
  private val repository = new CustomerRepository(new CustomerDao(initial))
  private val service = new ExportService(repository, new ExporterRegistry(config))
  val controller = new ExportController(service)
  val nightly = new NightlyExport(service)

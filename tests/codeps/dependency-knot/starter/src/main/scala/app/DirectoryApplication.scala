package app

import app.api.{CustomerController, ExportController}
import app.dao.{CustomerDao, CustomerRow, CustomerTable}
import app.jobs.{CrmSync, Reconciliation}
import app.repositories.CustomerRepository
import app.services.{CustomerService, ExportService}

final class DirectoryApplication(initial: List[CustomerRow]):
  private val dao = new CustomerDao(new CustomerTable(initial))
  private val repository = new CustomerRepository(dao)
  private val service = new CustomerService(repository)
  val customers = new CustomerController(service)
  val exports = new ExportController(new ExportService(repository))
  val sync = new CrmSync(service)
  val reconciliation = new Reconciliation(dao)

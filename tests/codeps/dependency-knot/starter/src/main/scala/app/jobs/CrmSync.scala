package app.jobs

import app.api.CustomerInput
import app.services.CustomerService

final class CrmSync(service: CustomerService):
  def importRows(rows: List[(String, String, String)]): List[Either[String, String]] =
    rows.map { (id, name, region) => service.create(CustomerInput(id, name, region)).map(_.id) }

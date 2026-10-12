package app.jobs

import app.dao.CustomerDao

final class Reconciliation(dao: CustomerDao):
  def status(id: String): Option[String] = dao.activity(id)

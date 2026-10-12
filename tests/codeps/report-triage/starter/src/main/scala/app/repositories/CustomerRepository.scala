package app.repositories

import app.dao.CustomerDao
import app.domain.Customer

final class CustomerRepository(dao: CustomerDao):
  def inRegion(region: String): List[Customer] = dao.rows.filter(_.region == region)

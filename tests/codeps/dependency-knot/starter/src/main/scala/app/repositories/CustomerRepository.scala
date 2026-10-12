package app.repositories

import app.api.CustomerView
import app.dao.CustomerDao

final class CustomerRepository(dao: CustomerDao):
  def find(id: String): Option[CustomerView] = dao.find(id)
  def active(region: String): List[CustomerView] = dao.active(region)
  def insert(customer: CustomerView): CustomerView = dao.insert(customer)
  def activity(id: String): Option[String] = dao.activity(id)

package app.services

import app.api.{CustomerInput, CustomerView}
import app.repositories.CustomerRepository

final class CustomerService(repository: CustomerRepository):
  def active(region: String): List[CustomerView] = repository.active(region)
  def find(id: String): Option[CustomerView] = repository.find(id)
  def create(input: CustomerInput): Either[String, CustomerView] =
    if input.id.trim.isEmpty || input.name.trim.isEmpty then Left("ID and name are required")
    else if repository.find(input.id).nonEmpty then Left("Customer already exists")
    else Right(repository.insert(CustomerView(input.id, input.name.trim, input.region, true)))

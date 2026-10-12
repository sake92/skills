package app.api

import app.services.CustomerService

final class CustomerController(service: CustomerService):
  def list(region: String): HttpResult = HttpResult(200, service.active(region))
  def get(id: String): HttpResult = service.find(id) match
    case Some(customer) => HttpResult(200, List(customer))
    case None           => HttpResult(404, Nil, Some("Customer not found"))
  def create(input: CustomerInput): HttpResult = service.create(input) match
    case Right(customer) => HttpResult(201, List(customer))
    case Left(message)   => HttpResult(400, Nil, Some(message))

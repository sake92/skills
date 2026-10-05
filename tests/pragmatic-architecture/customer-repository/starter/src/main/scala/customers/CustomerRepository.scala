package customers

import scala.collection.mutable

trait CustomerRepository:
  def findById(id: String): Option[Customer]
  def findByEmail(email: String): Option[Customer]
  def activeByRegion(region: String): List[Customer]

final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository:
  val records: mutable.ArrayBuffer[Customer] = mutable.ArrayBuffer.from(seed)

  def all: mutable.Buffer[Customer] = records

  def query(predicate: Customer => Boolean): mutable.Buffer[Customer] =
    records.filter(predicate)

  def findById(id: String): Option[Customer] = ???

  def findByEmail(email: String): Option[Customer] = ???

  def activeByRegion(region: String): List[Customer] = ???

package exports

import customers.CustomerRepository

final class CustomerExport(repository: CustomerRepository):
  def emails: List[String] = repository.snapshot.map(_.email)

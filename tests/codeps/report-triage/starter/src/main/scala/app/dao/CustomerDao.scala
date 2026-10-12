package app.dao

import app.domain.Customer

final class CustomerDao(initial: List[Customer]):
  def rows: List[Customer] = initial

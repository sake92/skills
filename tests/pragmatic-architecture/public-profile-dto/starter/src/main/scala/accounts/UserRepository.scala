package accounts

import java.util.UUID

trait UserRepository:
  def findById(id: UUID): Option[UserRow]

final class InMemoryUserRepository(seed: List[UserRow]) extends UserRepository:
  def findById(id: UUID): Option[UserRow] = seed.find(_.id == id)

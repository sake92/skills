package profiles

import java.util.UUID
import accounts.*

final class ProfileService(repository: UserRepository):
  def find(id: UUID): Option[UserRow] = repository.findById(id)

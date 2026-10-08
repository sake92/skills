package backups

import java.util.UUID
import ba.sake.tupson.*
import accounts.*

final class AccountBackup(repository: UserRepository):
  def exportAccount(id: UUID): Option[String] = repository.findById(id).map(_.toJson)
  def restore(json: String): UserRow = json.parseJson[UserRow]

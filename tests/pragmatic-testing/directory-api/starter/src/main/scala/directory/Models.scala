package directory

import ba.sake.tupson.JsonRW

case class NewUser(name: String, email: String) derives JsonRW
case class UserUpdate(name: String, email: String, revision: Int) derives JsonRW
case class User(id: String, name: String, email: String, revision: Int) derives JsonRW
case class StoredUser(id: String, name: String, email: String, revision: Int, internalNote: String) derives JsonRW:
  def publicView: User = User(id, name, email, revision)
case class EmailChange(previous: String, current: String) derives JsonRW

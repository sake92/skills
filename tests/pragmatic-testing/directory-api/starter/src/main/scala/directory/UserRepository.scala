package directory

import java.nio.file.Path
import java.sql.{Connection, DriverManager, SQLException}
import java.util.UUID
import scala.util.Using

class UserRepository(database: Path) {
  Class.forName("org.sqlite.JDBC")
  private val url = s"jdbc:sqlite:${database.toAbsolutePath}"
  withConnection { connection =>
    Using.resource(connection.createStatement()) { statement =>
      statement.executeUpdate(
        "CREATE TABLE IF NOT EXISTS users (id TEXT PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, revision INTEGER NOT NULL, internal_note TEXT NOT NULL)"
      )
      statement.executeUpdate(
        "CREATE TABLE IF NOT EXISTS email_changes (user_id TEXT NOT NULL, previous TEXT NOT NULL, current TEXT NOT NULL)"
      )
    }
  }

  def create(input: NewUser): Either[Int, StoredUser] =
    val user = StoredUser(UUID.randomUUID().toString, input.name, input.email, 0, "staff-only")
    try
      withConnection { connection =>
        execute(
          connection,
          "INSERT INTO users VALUES (?, ?, ?, ?, ?)",
          user.id,
          user.name,
          user.email,
          user.revision,
          user.internalNote
        )
        Right(user)
      }
    catch case _: SQLException => Left(409)

  def find(id: String): Option[StoredUser] = withConnection(connection => select(connection, id))

  def update(id: String, input: UserUpdate): Either[Int, StoredUser] = withConnection { connection =>
    connection.setAutoCommit(false)
    try
      val result = select(connection, id) match
        case None                                        => Left(404)
        case Some(old) if old.revision != input.revision => Left(409)
        case Some(old)                                   =>
          if old.email != input.email then
            execute(connection, "INSERT INTO email_changes VALUES (?, ?, ?)", id, old.email, input.email)
          execute(
            connection,
            "UPDATE users SET name = ?, email = ?, revision = revision + 1 WHERE id = ?",
            input.name,
            input.email,
            id
          )
          Right(StoredUser(id, input.name, input.email, old.revision + 1, old.internalNote))
      connection.commit()
      result
    catch
      case _: SQLException =>
        connection.rollback()
        Left(409)
  }

  def history(id: String): List[EmailChange] = withConnection { connection =>
    Using.resource(
      connection.prepareStatement("SELECT previous, current FROM email_changes WHERE user_id = ? ORDER BY rowid")
    ) { statement =>
      statement.setString(1, id)
      Using.resource(statement.executeQuery()) { rows =>
        val result = List.newBuilder[EmailChange]
        while rows.next() do result += EmailChange(rows.getString(1), rows.getString(2))
        result.result()
      }
    }
  }

  private def select(connection: Connection, id: String): Option[StoredUser] =
    Using.resource(connection.prepareStatement("SELECT * FROM users WHERE id = ?")) { statement =>
      statement.setString(1, id)
      Using.resource(statement.executeQuery()) { rows =>
        if rows.next() then
          Some(
            StoredUser(
              rows.getString("id"),
              rows.getString("name"),
              rows.getString("email"),
              rows.getInt("revision"),
              rows.getString("internal_note")
            )
          )
        else None
      }
    }

  private def execute(connection: Connection, sql: String, values: Any*): Unit =
    Using.resource(connection.prepareStatement(sql)) { statement =>
      values.zipWithIndex.foreach { (value, index) => statement.setObject(index + 1, value) }
      statement.executeUpdate()
    }

  private def withConnection[A](run: Connection => A): A = Using.resource(DriverManager.getConnection(url))(run)
}

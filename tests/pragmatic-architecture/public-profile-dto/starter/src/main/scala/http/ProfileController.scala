package http

import java.util.UUID
import ba.sake.tupson.*
import profiles.ProfileService

case class ProfileResponse(status: Int, body: String, contentType: String = "application/json")

final class ProfileController(service: ProfileService):
  def get(id: String): ProfileResponse =
    val parsed = try Some(UUID.fromString(id))
    catch case _: IllegalArgumentException => None
    parsed match
      case None       => ProfileResponse(400, """{"error":"invalid identifier"}""")
      case Some(uuid) =>
        service.find(uuid) match
          case Some(user) => ProfileResponse(200, user.toJson)
          case None       => ProfileResponse(404, """{"error":"profile not found"}""")

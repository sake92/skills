package accounts

import java.util.UUID
import ba.sake.tupson.*

// Shared with the internal backup format; these fields are needed for restoration.
case class UserRow(
    id: UUID,
    displayName: String,
    bio: String,
    avatarUrl: Option[String],
    email: String,
    passwordHash: String,
    refreshToken: Option[String],
    isAdmin: Boolean,
    createdAt: String
) derives JsonRW

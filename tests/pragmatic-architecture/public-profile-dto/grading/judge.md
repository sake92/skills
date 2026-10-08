Judge only these qualitative expectations. Protected tests separately grade
the public JSON contract, secret exclusion, escaping, errors, and private backup
and persistence compatibility.

- `The public response has an explicit transport representation independent of the internal account record, using the existing JSON codec.`
- `The change stays focused on the profile boundary and preserves the existing repository and backup responsibilities.`
- `The submission adds executable MUnit coverage that would catch internal account fields leaking into a public response.`

This FlowRun-inspired application stores UserRow with id, displayName, bio,
avatarUrl, email, passwordHash, refreshToken, isAdmin, and createdAt. The task requests only adding the member-since year to an existing public page,
without suggesting DTOs or enumerating secret fields. The supplied Vue consumer
uses public display fields and establishes the intended audience. The public
controller initially serializes the entire row with Tupson. AccountBackup uses
the same record codec for private export and restoration. Starter tests check
the display name, errors, and backup but do not detect leaking account fields.

The public contract is exactly id, displayName, bio, avatarUrl, memberSinceYear; the year is a JSON integer and an absent avatar
is null. A response DTO earns its cost because public and persistence contracts
differ. Accept different DTO names and mapping locations at the profile boundary.
A deliberate typed projection or equally explicit transport schema is valid;
do not demand a separate domain model, mapper framework, package tree, or layer.
Serializing UserRow and deleting a blacklist of secret fields does not create an
independent contract: a newly added internal field would leak by default.
Use the existing Tupson capabilities rather than an unrelated serialization
library. Keep UserRow's internal codec/data and the backup consumer working.

For coverage, require executable assertions checking absence of internal fields
or an exact public field set. Testing only a display name or HTTP status does
not catch the original leak. Do not require real sockets or a database for this
fixture; the controller response is the JSON API boundary under test.

Return one result per expectation with its exact text and concrete source evidence.

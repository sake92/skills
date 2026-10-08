# Public profile DTO

Both candidates independently introduced essentially the same public DTO using Tupson and displayed the year in Vue. Only the with-skill candidate added executable private-field exclusion assertions. The one-point difference is regression coverage; this pair does not demonstrate that the skill alone caused the DTO decision.

[All benchmark cases](README.md). These are compact application fixtures, not full production repositories.

## Task and result

> Add the member-since year to the public profile page. The existing UI is in frontend/PublicProfile.vue; have the profile endpoint return memberSinceYear as a JSON integer derived from the account creation date, and show it below the bio. Preserve the existing profile display, missing-avatar behavior, and invalid/missing-profile responses. The internal account backup must still round trip. Add MUnit regression coverage and run `scala-cli test . --server=false`.

| Metric | With skill | Without skill |
| --- | ---: | ---: |
| Grade | 9/9 | 8/9 |
| Candidate seconds | 72.752 | 91.162 |
| Candidate tokens | 21,802 | 17,216 |

## Code from the submissions

The following excerpts are copied from the recorded candidates. Imports and surrounding declarations are retained where helpful; these are not rewritten ideal solutions.

### Without skill

`src/main/scala/http/ProfileController.scala`:

```scala
package http

import java.util.UUID
import ba.sake.tupson.*
import profiles.ProfileService

case class ProfileResponse(status: Int, body: String, contentType: String = "application/json")

private case class PublicProfile(id: UUID, displayName: String, bio: String, avatarUrl: Option[String], memberSinceYear: Int) derives JsonRW

final class ProfileController(service: ProfileService):
  def get(id: String): ProfileResponse =
    val parsed = try Some(UUID.fromString(id))
    catch case _: IllegalArgumentException => None
    parsed match
      case None       => ProfileResponse(400, """{"error":"invalid identifier"}""")
      case Some(uuid) =>
        service.find(uuid) match
          case Some(user) =>
            val memberSinceYear = java.time.LocalDate.parse(user.createdAt).getYear
            val profile = PublicProfile(user.id, user.displayName, user.bio, user.avatarUrl, memberSinceYear)
            ProfileResponse(200, profile.toJson)
          case None       => ProfileResponse(404, """{"error":"profile not found"}""")
```

### With skill

`src/main/scala/http/ProfileController.scala`:

```scala
package http

import java.util.UUID
import ba.sake.tupson.*
import profiles.ProfileService
import accounts.UserRow

case class ProfileResponse(status: Int, body: String, contentType: String = "application/json")

private case class PublicProfile(
    id: UUID,
    displayName: String,
    bio: String,
    avatarUrl: Option[String],
    memberSinceYear: Int
) derives JsonRW

final class ProfileController(service: ProfileService):
  def get(id: String): ProfileResponse =
    val parsed = try Some(UUID.fromString(id))
    catch case _: IllegalArgumentException => None
    parsed match
      case None       => ProfileResponse(400, """{"error":"invalid identifier"}""")
      case Some(uuid) =>
        service.find(uuid) match
          case Some(user) =>
            val publicProfile = PublicProfile(
              user.id,
              user.displayName,
              user.bio,
              user.avatarUrl,
              java.time.LocalDate.parse(user.createdAt).getYear
            )
            ProfileResponse(200, publicProfile.toJson)
          case None       => ProfileResponse(404, """{"error":"profile not found"}""")
```

### The separating regression assertions

The with-skill profile test includes these exact checks:

```scala
    assert(!response.body.contains("email"))
    assert(!response.body.contains("passwordHash"))
```

The baseline checks display fields, the year, errors, and backup restoration, but has no assertion rejecting private account fields. Both Vue submissions include:

```vue
    <p>Member since {{ profile.memberSinceYear }}</p>
```

## Diff evidence

This focused test diff shows the private-field assertions added by the with-skill candidate. The production DTOs have the same fields; their full formatting and mapping differences are in the comparison patch.

```diff
--- without-skill/src/test/scala/ProfileSuite.scala
+++ with-skill/src/test/scala/ProfileSuite.scala
@@ -17,8 +17,14 @@
     assertEquals(response.status, 200)
     assertEquals(response.contentType, "application/json")
     assertEquals(response.body.parseJson[Map[String, JValue]]("displayName"), JString("Ada"))
-    assertEquals(response.body.parseJson[Map[String, JValue]]("memberSinceYear").toString, "2026")
-    assertEquals(response.body.parseJson[Map[String, JValue]]("avatarUrl").toString, "null")
+    assertEquals(response.body.parseJson[Map[String, JValue]]("memberSinceYear"), org.typelevel.jawn.ast.JNum(2026))
+    assert(!response.body.contains("email"))
+    assert(!response.body.contains("passwordHash"))
+  }
+
+  test("public profile preserves missing avatar as null") {
+    val response = new ProfileController(new ProfileService(repository)).get(id.toString)
+    assertEquals(response.body.parseJson[Map[String, JValue]]("avatarUrl"), org.typelevel.jawn.ast.JNull)
   }

   test("missing profile and invalid URL identifier") {
```

Full evidence: [with-skill patch](patches/public-profile-dto/with-skill.patch), [baseline patch](patches/public-profile-dto/without-skill.patch), [candidate comparison](patches/public-profile-dto/comparison.patch), and [grade reports](patches/public-profile-dto/grading.json). The first two patches are relative to each run’s starter.

## Grading

Protected checks execute outside the candidate workspace. Judge checks assess the submitted source and tests. Every recorded assertion is shown below; a total alone would hide the separating criteria.

| Criterion | Grader | With skill | Without skill |
| --- | --- | --- | --- |
| Successful public profiles preserve displayed fields and include the correct integer memberSinceYear. | Protected | Pass | Pass |
| Profiles with no avatar retain the avatarUrl key with a JSON null value. | Protected | Pass | Pass |
| Public responses expose no email, password hash, refresh token, admin flag, or account timestamp. | Protected | Pass | Pass |
| Profile text is serialized as JSON safely, including quotes, newlines, Unicode, and empty values. | Protected | Pass | Pass |
| Invalid and missing profile identifiers retain JSON errors, status codes, and content type. | Protected | Pass | Pass |
| Persistence and the internal backup round trip retain every account field after a public profile read. | Protected | Pass | Pass |
| The public response has an explicit transport representation independent of the internal account record, using the existing JSON codec. | Judge | Pass | Pass |
| The change stays focused on the profile boundary and preserves the existing repository and backup responsibilities. | Judge | Pass | Pass |
| The submission adds executable MUnit coverage that would catch internal account fields leaking into a public response. | Judge | Pass | Fail |

## Interpretation and provenance

The earlier task explicitly specified a safe public contract and tied at 9/9. This ordinary feature request leaves recognition of the public/private boundary to the agent. Both candidates made that decision; the separating result concerns regression tests. Protected validation accepts a public projection (6/6) and rejects a feature-complete leaking response only on private-data exclusion (5/6). The Vue changes were inspected; this compact fixture has no browser/build test.

Recorded source: `tests/tmp/pragmatic-architecture-workspace/paired-decision-guidance-20261008/eval-6/`. Executor and qualitative judge: Pi 1.0.4 / GPT-6 Luna, low reasoning, one candidate per configuration. Timing measures candidate execution, not grading. This single pair does not establish consistency or a repeatable resource-cost difference.

With-skill source: `iteration-20261008-195847`; baseline source: `iteration-20261008-200255`. The starter Git trees and task/assertion metadata were verified to match. The [exact evaluated skill](patches/public-profile-dto/evaluated-SKILL.md) is retained. The DTO candidate used the revised model-boundary guidance before the final upfront ID paragraph was added; DTO guidance was unchanged. Its completed smoke was reused for the baseline comparison.

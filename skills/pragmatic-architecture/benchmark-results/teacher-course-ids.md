# Teacher/course IDs

Both candidates fixed the immediate reversal and added regression tests. Only the with-skill candidate introduced opaque `UserId` and `CourseId` parameters and typed the cache key. The UUID boundary formats remained compatible. The two separating grades confirm compiler rejection and focused use of distinct IDs, rather than merely counting type declarations.

[All benchmark cases](README.md). These are compact application fixtures, not full production repositories.

## Task and result

> Fix the teacher lookup bug in this application. Looking up user 00000000-0000-0000-0000-000000000001 in course 00000000-0000-0000-0000-000000000002 currently misses their membership, or returns the wrong teacher when the reversed membership exists. Repeated lookups must stay isolated by both user and course.
>
> Keep the existing URL, roster, persistence, directory, and invalid/missing lookup behavior working. This is an internal application; update affected signatures and callers as needed. Add MUnit regression coverage and run `scala-cli test . --server=false`.

| Metric | With skill | Without skill |
| --- | ---: | ---: |
| Grade | 8/8 | 6/8 |
| Candidate seconds | 65.780 | 38.560 |
| Candidate tokens | 14,540 | 8,215 |

Score gain: **+25.0 percentage points** (75% → 100%).

## Code from the submissions

The following excerpts are copied from the recorded candidates. Imports and surrounding declarations are retained where helpful; these are not rewritten ideal solutions.

### Without skill

`src/main/scala/teachers/TeacherService.scala`:

```scala
package teachers

import java.util.UUID
import scala.collection.mutable

final class TeacherService(repository: TeacherRepository) {
  private val cache = mutable.Map.empty[(UUID, UUID), TeacherRecord]

  def findByUserIdAndCourseId(userId: UUID, courseId: UUID): Option[TeacherRecord] =
    val key = (userId, courseId)
    cache.get(key).orElse {
      val found = repository.findByUserIdAndCourseId(userId, courseId)
      found.foreach(record => cache.put(key, record))
      found
    }
}
```

### With skill

`src/main/scala/teachers/TeacherService.scala`:

```scala
package teachers

import java.util.UUID
import scala.collection.mutable

final class TeacherService(repository: TeacherRepository) {
  private val cache = mutable.Map.empty[(UserId, CourseId), TeacherRecord]

  def findByUserIdAndCourseId(userId: UserId, courseId: CourseId): Option[TeacherRecord] =
    val key = (userId, courseId)
    cache.get(key).orElse {
      val found = repository.findByUserIdAndCourseId(userId, courseId)
      found.foreach(record => cache.put(key, record))
      found
    }
}
```

The with-skill repository introduces the distinction that the service and cache carry:

```scala
package teachers

import java.util.UUID

// Persistence records keep their UUID representation.
case class TeacherRecord(userId: UUID, courseId: UUID, displayName: String)

opaque type UserId = UUID
object UserId:
  def apply(value: UUID): UserId = value

opaque type CourseId = UUID
object CourseId:
  def apply(value: UUID): CourseId = value

trait TeacherRepository:
  def findByUserIdAndCourseId(userId: UserId, courseId: CourseId): Option[TeacherRecord]

final class InMemoryTeacherRepository(seed: List[TeacherRecord]) extends TeacherRepository:
  def findByUserIdAndCourseId(userId: UserId, courseId: CourseId): Option[TeacherRecord] =
    seed.find(row => row.userId == userId && row.courseId == courseId)
```

The compiler probes verify that correctly typed calls compile and swapped or raw UUID arguments do not. Runtime UUID parsing and persistence stay at the boundaries.

## Diff evidence

This focused diff compares the baseline to the with-skill result, rather than comparing either candidate to the starter:

```diff
--- without-skill/src/main/scala/teachers/TeacherService.scala
+++ with-skill/src/main/scala/teachers/TeacherService.scala
@@ -4,9 +4,9 @@
 import scala.collection.mutable

 final class TeacherService(repository: TeacherRepository) {
-  private val cache = mutable.Map.empty[(UUID, UUID), TeacherRecord]
+  private val cache = mutable.Map.empty[(UserId, CourseId), TeacherRecord]

-  def findByUserIdAndCourseId(userId: UUID, courseId: UUID): Option[TeacherRecord] =
+  def findByUserIdAndCourseId(userId: UserId, courseId: CourseId): Option[TeacherRecord] =
     val key = (userId, courseId)
     cache.get(key).orElse {
       val found = repository.findByUserIdAndCourseId(userId, courseId)
```

## Grading

Protected checks execute outside the candidate workspace. Judge checks assess the submitted source and tests. Every recorded assertion is shown below; a total alone would hide the separating criteria.

| Criterion | Grader | With skill | Without skill |
| --- | --- | --- | --- |
| HTTP lookup distinguishes user and course IDs even when the reversed membership exists. | Protected | Pass | Pass |
| Cached lookups remain isolated by both user and course, including reversed pairs. | Protected | Pass | Pass |
| The roster consumer preserves course order, missing memberships, and UUID inputs. | Protected | Pass | Pass |
| HTTP UUID strings, invalid-identifier errors, and missing-teacher responses remain compatible. | Protected | Pass | Pass |
| Service and repository lookup APIs reject swapped and raw identifiers at compile time. | Protected | Pass | Fail |
| Existing directory consumers and UUID persistence records retain their behavior. | Protected | Pass | Pass |
| Distinct user and course types protect the lookup and cache without an unrelated identifier migration. | Judge | Pass | Fail |
| The submission adds focused MUnit regression coverage with distinct user and course UUIDs. | Judge | Pass | Pass |

## Interpretation and provenance

The skill was tuned on this case before the recorded comparison. This is adaptive single-run evidence and needs held-out confirmation.

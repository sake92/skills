# Booking overlap rule

Both candidates centralized overlap rejection in the business operation and injected that service into HTTP and import consumers. Both passed all twelve checks. Their implementations differ, but this grader found no quality advantage for either. The with-skill candidate consumed more time and tokens.

[All benchmark cases](README.md). These are compact application fixtures, not full production repositories.

## Task and result

> Customers are getting overlapping room bookings through the HTTP endpoint and nightly import. Fix creation so both paths reject an interval that overlaps any active booking for the same room. Intervals are half-open: a booking ending when another starts is allowed. Bookings in other rooms and cancelled bookings must not block creation, and cancellation history must remain available.
>
> Reject conflicts without storing the rejected booking. Preserve existing invalid-interval and duplicate-ID errors and keep the existing booking history report available. Batch import should process rows in order, return a result for each row, and continue after a rejected row. This is an internal application; update application wiring and callers as needed. Add MUnit regression coverage for both entry points and run `scala-cli test . --server=false`.

| Metric | With skill | Without skill |
| --- | ---: | ---: |
| Grade | 12/12 | 12/12 |
| Candidate seconds | 95.092 | 58.338 |
| Candidate tokens | 28,418 | 20,024 |

## Code from the submissions

The following excerpts are copied from the recorded candidates. Imports and surrounding declarations are retained where helpful; these are not rewritten ideal solutions.

### Without skill

`src/main/scala/bookings/BookingService.scala`:

```scala
package bookings

final class BookingService(repository: BookingRepository) {
  def activeForRoom(roomId: String): List[Booking] =
    repository.all.filter(booking => booking.roomId == roomId && !booking.cancelled)

  def create(request: BookingRequest): Either[BookingError, Booking] =
    if request.endMinute <= request.startMinute then Left(BookingError.InvalidInterval)
    else if repository.all.exists(_.id == request.id) then Left(BookingError.DuplicateId)
    else if activeForRoom(request.roomId).exists(booking =>
        request.startMinute < booking.endMinute && booking.startMinute < request.endMinute
      ) then Left(BookingError.Overlap)
    else
      val booking = Booking(request.id, request.roomId, request.startMinute, request.endMinute)
      repository.add(booking)
      Right(booking)
}
```

### With skill

`src/main/scala/bookings/BookingService.scala`:

```scala
package bookings

final class BookingService(repository: BookingRepository) {
  def create(request: BookingRequest): Either[BookingError, Booking] =
    if request.endMinute <= request.startMinute then Left(BookingError.InvalidInterval)
    else if repository.history.exists(_.id == request.id) then Left(BookingError.DuplicateId)
    else if repository.history.exists(booking =>
        !booking.cancelled &&
          booking.roomId == request.roomId &&
          booking.startMinute < request.endMinute &&
          request.startMinute < booking.endMinute
      ) then Left(BookingError.Overlap)
    else
      val booking = Booking(request.id, request.roomId, request.startMinute, request.endMinute)
      repository.add(booking)
      Right(booking)
}
```

The with-skill adapters receive the shared service; the baseline also uses constructor injection:

`src/main/scala/http/BookingController.scala`:

```scala
package http

import bookings.*

final case class BookingResponse(status: Int, booking: Option[Booking], error: Option[BookingError])

final class BookingController(service: BookingService):
  def create(request: BookingRequest): BookingResponse =
    service.create(request) match
      case Right(booking)                     => BookingResponse(201, Some(booking), None)
      case Left(BookingError.InvalidInterval) => BookingResponse(400, None, Some(BookingError.InvalidInterval))
      case Left(error)                        => BookingResponse(409, None, Some(error))
```

`src/main/scala/imports/NightlyImport.scala`:

```scala
package imports

import bookings.*

final class NightlyImport(service: BookingService) {
  def importOne(request: BookingRequest): Either[BookingError, Booking] = service.create(request)

  def importBatch(requests: List[BookingRequest]): List[Either[BookingError, Booking]] =
    requests.map(importOne)
}
```

## Diff evidence

This focused diff compares the baseline to the with-skill result, rather than comparing either candidate to the starter:

```diff
--- without-skill/src/main/scala/bookings/BookingService.scala
+++ with-skill/src/main/scala/bookings/BookingService.scala
@@ -1,14 +1,14 @@
 package bookings

 final class BookingService(repository: BookingRepository) {
-  def activeForRoom(roomId: String): List[Booking] =
-    repository.all.filter(booking => booking.roomId == roomId && !booking.cancelled)
-
   def create(request: BookingRequest): Either[BookingError, Booking] =
     if request.endMinute <= request.startMinute then Left(BookingError.InvalidInterval)
-    else if repository.all.exists(_.id == request.id) then Left(BookingError.DuplicateId)
-    else if activeForRoom(request.roomId).exists(booking =>
-        request.startMinute < booking.endMinute && booking.startMinute < request.endMinute
+    else if repository.history.exists(_.id == request.id) then Left(BookingError.DuplicateId)
+    else if repository.history.exists(booking =>
+        !booking.cancelled &&
+          booking.roomId == request.roomId &&
+          booking.startMinute < request.endMinute &&
+          request.startMinute < booking.endMinute
       ) then Left(BookingError.Overlap)
     else
       val booking = Booking(request.id, request.roomId, request.startMinute, request.endMinute)
```

Full evidence: [with-skill patch](patches/booking-overlap-rule/with-skill.patch), [baseline patch](patches/booking-overlap-rule/without-skill.patch), [candidate comparison](patches/booking-overlap-rule/comparison.patch), and [grade reports](patches/booking-overlap-rule/grading.json). The first two patches are relative to each run’s starter.

## Grading

Protected checks execute outside the candidate workspace. Judge checks assess the submitted source and tests. Every recorded assertion is shown below; a total alone would hide the separating criteria.

| Criterion | Grader | With skill | Without skill |
| --- | --- | --- | --- |
| The business operation rejects every same-room overlap orientation without changing stored bookings. | Protected | Pass | Pass |
| HTTP returns a usable conflict response for every overlap and never persists the rejected booking. | Protected | Pass | Pass |
| Import rejects every overlap with an explicit error and never persists the rejected booking. | Protected | Pass | Pass |
| Adjacent intervals remain valid through the operation, HTTP, and import. | Protected | Pass | Pass |
| Other rooms and cancelled bookings do not block creation, and cancelled history is retained. | Protected | Pass | Pass |
| Creation checks current state after another entry point creates or a booking is cancelled. | Protected | Pass | Pass |
| Batch import observes earlier accepted rows and continues after rejecting an overlapping row. | Protected | Pass | Pass |
| Invalid intervals and duplicate IDs retain explicit errors and leave storage unchanged. | Protected | Pass | Pass |
| HTTP and import receive their business collaborators as dependencies instead of constructing them internally. | Protected | Pass | Pass |
| One coherent booking creation operation owns the shared overlap rule, and HTTP and import use it without duplicating the business policy. | Judge | Pass | Pass |
| The booking fix remains proportional and does not introduce unrelated layers, wrappers, or dependencies. | Judge | Pass | Pass |
| The submission adds focused MUnit regression coverage for overlap rejection through both HTTP and import. | Judge | Pass | Pass |

## Interpretation and provenance

This case requests consistent conflict rejection through HTTP and nightly
import. Its five starter tests pass. Protected tests also call the business
operation directly and cover overlap orientations, adjacency, cancellation,
other rooms, current state, ordered batches, explicit errors, and rejection
without writes.

Scalameta checks constructor-supplied business dependencies and rejects business
collaborator construction inside adapters. Application composition-root wiring
may construct them. The judge assesses shared rule ownership, proportionality,
and adapter regression coverage.

Both candidates passed all behavior and injection checks. This pair shows no
measured quality improvement from the skill, with higher candidate time and
token cost on the with-skill side. It remains in the comparison rather than
being discarded because of the tie.

Recorded source: `tests/tmp/pragmatic-architecture-workspace/iteration-20261008-065957/eval-4/`. Executor and qualitative judge: Pi 1.0.4 / GPT-6 Luna, low reasoning, one candidate per configuration. Timing measures candidate execution, not grading. This single pair does not establish consistency or a repeatable resource-cost difference.

This pair predates the final ID/DTO skill revisions; it was not rerun with the current skill.

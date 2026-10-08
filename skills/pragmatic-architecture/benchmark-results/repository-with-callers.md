# Repository with callers

Both candidates preserved the real export caller through `snapshot`. The with-skill candidate hid the mutable buffer and removed unused `all` and `query` helpers. It retained contained mutation behind an immutable snapshot instead of deleting an operation that another feature needed.

[All benchmark cases](README.md). These are compact application fixtures, not full production repositories.

## Task and result

> Add email lookup support to the in-memory customer repository in the starter application. Match email addresses exactly, return the first customer in input order when multiple customers share an email, and return None when there is no match.
>
> Keep the application's existing lookup and export behavior working. Add MUnit regression coverage and run `scala-cli test . --server=false`.

| Metric | With skill | Without skill |
| --- | ---: | ---: |
| Grade | 8/8 | 5/8 |
| Candidate seconds | 49.690 | 38.542 |
| Candidate tokens | 13,263 | 13,803 |

## Code from the submissions

The following excerpts are copied from the recorded candidates. Imports and surrounding declarations are retained where helpful; these are not rewritten ideal solutions.

### Without skill

`src/main/scala/customers/CustomerRepository.scala`:

```scala
package customers

import scala.collection.mutable

trait CustomerRepository:
  def findById(id: String): Option[Customer]
  def findByEmail(email: String): Option[Customer]
  def snapshot: List[Customer]

final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository {
  val records: mutable.ArrayBuffer[Customer] = mutable.ArrayBuffer.from(seed)

  def all: mutable.Buffer[Customer] = records

  def query(predicate: Customer => Boolean): mutable.Buffer[Customer] =
    records.filter(predicate)

  def findById(id: String): Option[Customer] = records.find(_.id == id)

  def findByEmail(email: String): Option[Customer] = records.find(_.email == email)

  def snapshot: List[Customer] = records.toList
}
```

### With skill

`src/main/scala/customers/CustomerRepository.scala`:

```scala
package customers

import scala.collection.mutable

trait CustomerRepository:
  def findById(id: String): Option[Customer]
  def findByEmail(email: String): Option[Customer]
  def snapshot: List[Customer]

final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository {
  private val records: mutable.ArrayBuffer[Customer] = mutable.ArrayBuffer.from(seed)

  def findById(id: String): Option[Customer] = records.find(_.id == id)

  def findByEmail(email: String): Option[Customer] = records.find(_.email == email)

  def snapshot: List[Customer] = records.toList
}
```

The export consumer still calls the required snapshot:

```scala
package exports

import customers.CustomerRepository

final class CustomerExport(repository: CustomerRepository):
  def emails: List[String] = repository.snapshot.map(_.email)
```

## Diff evidence

This focused diff compares the baseline to the with-skill result, rather than comparing either candidate to the starter:

```diff
--- without-skill/src/main/scala/customers/CustomerRepository.scala
+++ with-skill/src/main/scala/customers/CustomerRepository.scala
@@ -8,12 +8,7 @@
   def snapshot: List[Customer]

 final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository {
-  val records: mutable.ArrayBuffer[Customer] = mutable.ArrayBuffer.from(seed)
-
-  def all: mutable.Buffer[Customer] = records
-
-  def query(predicate: Customer => Boolean): mutable.Buffer[Customer] =
-    records.filter(predicate)
+  private val records: mutable.ArrayBuffer[Customer] = mutable.ArrayBuffer.from(seed)

   def findById(id: String): Option[Customer] = records.find(_.id == id)
```

Full evidence: [with-skill patch](patches/repository-with-callers/with-skill.patch), [baseline patch](patches/repository-with-callers/without-skill.patch), [candidate comparison](patches/repository-with-callers/comparison.patch), and [grade reports](patches/repository-with-callers/grading.json). The first two patches are relative to each run’s starter.

## Grading

Protected checks execute outside the candidate workspace. Judge checks assess the submitted source and tests. Every recorded assertion is shown below; a total alone would hide the separating criteria.

| Criterion | Grader | With skill | Without skill |
| --- | --- | --- | --- |
| Email lookup is exact, returns the first match, and represents absence with None. | Protected | Pass | Pass |
| Existing ID lookup behavior remains compatible. | Protected | Pass | Pass |
| The immutable snapshot API and export consumer preserve all customers in input order. | Protected | Pass | Pass |
| The repository preserves findById, findByEmail, and the caller-required snapshot without extra public helpers. | Protected | Pass | Fail |
| The implementation exposes no public mutable backing state, including constructor fields. | Protected | Pass | Fail |
| Public repository members expose no mutable collections. | Protected | Pass | Fail |
| The submission adds executable MUnit email lookup assertions. | Protected | Pass | Pass |
| The lookup enhancement stays proportional and adds no unrelated architecture or unjustified wrappers. | Judge | Pass | Pass |

## Interpretation and provenance

This case requests an email lookup enhancement while an existing export consumer
needs the repository's immutable snapshot. It checks that encapsulation changes
preserve real callers rather than deleting every extra operation. The starter's
three tests pass before the enhancement.

Protected checks cover lookup behavior, export compatibility, public surface,
mutable state and returns, and added email assertions. The judge assesses
proportionality.

Both candidates implemented email lookup, added regression coverage, and
preserved ID lookup and export behavior. The baseline retained public mutable
`records`, `all`, and `query`; the with-skill candidate hid backing state and
removed unused helpers while preserving `snapshot`. The three separating checks
were structural assertion failures, not grader compilation errors.

Recorded source: `tests/tmp/pragmatic-architecture-workspace/iteration-20261008-070906/eval-2/`. Executor and qualitative judge: Pi 1.0.4 / GPT-6 Luna, low reasoning, one candidate per configuration. Timing measures candidate execution, not grading. This single pair does not establish consistency or a repeatable resource-cost difference.

This pair predates the final ID/DTO skill revisions; it was not rerun with the current skill.

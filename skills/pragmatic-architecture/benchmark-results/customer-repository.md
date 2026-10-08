# Customer repository

The baseline implemented the requested lookups but retained public mutable backing state and generic collection access. The with-skill candidate replaced that surface with private immutable storage and the three caller-facing operations. The separating checks concern encapsulation, not lookup correctness.

[All benchmark cases](README.md). These are compact application fixtures, not full production repositories.

## Task and result

> Finish the in-memory customer repository in the starter project.
>
> The application needs to:
>
> - look up a customer by ID;
> - look up a customer by email;
> - list active customers in a region, preserving their input order.
>
> Return `None` when a lookup has no match and an empty list when a region has no active customers. Add MUnit coverage for the required behavior and run `scala-cli test . --server=false`.

| Metric | With skill | Without skill |
| --- | ---: | ---: |
| Grade | 10/10 | 7/10 |
| Candidate seconds | 58.817 | 49.724 |
| Candidate tokens | 12,892 | 11,138 |

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
  def activeByRegion(region: String): List[Customer]

final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository:
  val records: mutable.ArrayBuffer[Customer] = mutable.ArrayBuffer.from(seed)

  def all: mutable.Buffer[Customer] = records

  def query(predicate: Customer => Boolean): mutable.Buffer[Customer] =
    records.filter(predicate)

  def findById(id: String): Option[Customer] =
    records.find(_.id == id)

  def findByEmail(email: String): Option[Customer] =
    records.find(_.email == email)

  def activeByRegion(region: String): List[Customer] =
    records.iterator.filter(customer => customer.region == region && customer.active).toList
```

### With skill

`src/main/scala/customers/CustomerRepository.scala`:

```scala
package customers

import scala.collection.mutable

trait CustomerRepository:
  def findById(id: String): Option[Customer]
  def findByEmail(email: String): Option[Customer]
  def activeByRegion(region: String): List[Customer]

final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository:
  private val records: Vector[Customer] = seed.toVector

  def findById(id: String): Option[Customer] =
    records.find(_.id == id)

  def findByEmail(email: String): Option[Customer] =
    records.find(_.email == email)

  def activeByRegion(region: String): List[Customer] =
    records.filter(customer => customer.region == region && customer.active).toList
```

## Diff evidence

This focused diff compares the baseline to the with-skill result, rather than comparing either candidate to the starter:

```diff
--- without-skill/src/main/scala/customers/CustomerRepository.scala
+++ with-skill/src/main/scala/customers/CustomerRepository.scala
@@ -8,12 +8,7 @@
   def activeByRegion(region: String): List[Customer]

 final class InMemoryCustomerRepository(seed: Seq[Customer]) extends CustomerRepository:
-  val records: mutable.ArrayBuffer[Customer] = mutable.ArrayBuffer.from(seed)
-
-  def all: mutable.Buffer[Customer] = records
-
-  def query(predicate: Customer => Boolean): mutable.Buffer[Customer] =
-    records.filter(predicate)
+  private val records: Vector[Customer] = seed.toVector

   def findById(id: String): Option[Customer] =
     records.find(_.id == id)
@@ -22,4 +17,4 @@
     records.find(_.email == email)

   def activeByRegion(region: String): List[Customer] =
-    records.iterator.filter(customer => customer.region == region && customer.active).toList
+    records.filter(customer => customer.region == region && customer.active).toList
```

Full evidence: [with-skill patch](patches/customer-repository/with-skill.patch), [baseline patch](patches/customer-repository/without-skill.patch), [candidate comparison](patches/customer-repository/comparison.patch), and [grade reports](patches/customer-repository/grading.json). The first two patches are relative to each run’s starter.

## Grading

Protected checks execute outside the candidate workspace. Judge checks assess the submitted source and tests. Every recorded assertion is shown below; a total alone would hide the separating criteria.

| Criterion | Grader | With skill | Without skill |
| --- | --- | --- | --- |
| Lookup by ID returns the matching customer and None for a missing ID. | Protected | Pass | Pass |
| Lookup by email is exact and returns None for missing or differently-cased email addresses. | Protected | Pass | Pass |
| Active customers are filtered by region and status, preserve input order, and a missing region returns an empty list. | Protected | Pass | Pass |
| CustomerRepository exposes exactly findById, findByEmail, and activeByRegion. | Protected | Pass | Pass |
| InMemoryCustomerRepository exposes no public mutable state, including constructor fields. | Protected | Pass | Fail |
| Helpers are private or protected, with no public all, records, or generic query escape hatch. | Protected | Pass | Fail |
| No public method or value exposes a mutable collection type. | Protected | Pass | Fail |
| The submission includes focused MUnit coverage for all three required operations. | Protected | Pass | Pass |
| The solution is proportional to three in-memory lookups and adds no unrelated architecture. | Judge | Pass | Pass |
| The solution adds no DTO, identifier, result, or mapping wrapper without a concrete benefit. | Judge | Pass | Pass |

## Interpretation and provenance

This case asks for three ordinary in-memory lookups without naming privacy,
encapsulation, or interface minimality. Protected tests check behavior, the
repository surface, hidden mutable state and helpers, immutable returns,
forbidden escape hatches, and candidate regression coverage. The judge assesses
proportionality and unjustified wrapper types.

Both candidates implemented the behavior, added focused MUnit coverage, and
passed the qualitative checks. The baseline retained public mutable `records`,
`all`, and `query`; the with-skill candidate removed or hid them. Structural
checks separated the candidates in this pair.

Recorded source: `tests/tmp/pragmatic-architecture-workspace/iteration-20261007-140831/eval-1/`. Executor and qualitative judge: Pi 1.0.4 / GPT-6 Luna, low reasoning, one candidate per configuration. Timing measures candidate execution, not grading. This single pair does not establish consistency or a repeatable resource-cost difference.

This pair predates the final ID/DTO skill revisions; it was not rerun with the current skill.

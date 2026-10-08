# Legacy discount fix

Both patches are byte-for-byte identical, including the regression test. Both corrected decimal rounding while preserving constructor injection and existing consumers. The skill supplied no measured improvement in this pair; restraint and compatibility passed on both sides.

[All benchmark cases](README.md). These are compact application fixtures, not full production repositories.

## Task and result

> Fix the invoice discount rounding bug in this existing application. An invoice with subtotal 10.05 and a 10 percent discount currently returns 9.04; the correct payable total is 9.05. Calculate using decimal precision and round only the final payable total to two decimal places with HALF_UP.
>
> The invoice response and CSV export must agree. Keep existing missing-invoice and invalid-input behavior working. Add MUnit regression coverage and run `scala-cli test . --server=false`.

| Metric | With skill | Without skill |
| --- | ---: | ---: |
| Grade | 8/8 | 8/8 |
| Candidate seconds | 48.498 | 49.254 |
| Candidate tokens | 14,749 | 11,652 |

## Code from the submissions

The following excerpts are copied from the recorded candidates. Imports and surrounding declarations are retained where helpful; these are not rewritten ideal solutions.

### Without skill

`src/main/scala/services/DiscountService.scala`:

```scala
package services

import javax.inject.Inject
import repositories.InvoiceRepository
import scala.math.BigDecimal.RoundingMode

final class DiscountService @Inject() (repository: InvoiceRepository):
  def totalFor(id: String): Option[BigDecimal] =
    repository.find(id).map { invoice =>
      require(invoice.subtotal >= 0, "subtotal must be non-negative")
      require(invoice.discountPercent >= 0 && invoice.discountPercent <= 100, "discount must be between 0 and 100")
      val payable = invoice.subtotal * (BigDecimal(100) - invoice.discountPercent) / 100
      payable.setScale(2, RoundingMode.HALF_UP)
    }
```

### With skill

`src/main/scala/services/DiscountService.scala`:

```scala
package services

import javax.inject.Inject
import repositories.InvoiceRepository
import scala.math.BigDecimal.RoundingMode

final class DiscountService @Inject() (repository: InvoiceRepository):
  def totalFor(id: String): Option[BigDecimal] =
    repository.find(id).map { invoice =>
      require(invoice.subtotal >= 0, "subtotal must be non-negative")
      require(invoice.discountPercent >= 0 && invoice.discountPercent <= 100, "discount must be between 0 and 100")
      val payable = invoice.subtotal * (BigDecimal(100) - invoice.discountPercent) / 100
      payable.setScale(2, RoundingMode.HALF_UP)
    }
```

## Diff evidence

The candidate-to-candidate diff is empty. The two submitted patches are identical.

Full evidence: [with-skill patch](patches/legacy-discount-fix/with-skill.patch), [baseline patch](patches/legacy-discount-fix/without-skill.patch), [candidate comparison](patches/legacy-discount-fix/comparison.patch), and [grade reports](patches/legacy-discount-fix/grading.json). The first two patches are relative to each run’s starter.

## Grading

Protected checks execute outside the candidate workspace. Judge checks assess the submitted source and tests. Every recorded assertion is shown below; a total alone would hide the separating criteria.

| Criterion | Grader | With skill | Without skill |
| --- | --- | --- | --- |
| Fractional-cent discounts round the final total once with HALF_UP. | Protected | Pass | Pass |
| Zero, full, fractional, and large discounts retain decimal precision and two-place totals. | Protected | Pass | Pass |
| The existing controller and CSV export both return the corrected total in their established formats. | Protected | Pass | Pass |
| Missing invoice handling and invalid monetary input rejection remain compatible. | Protected | Pass | Pass |
| The independent legacy percentage preview remains compatible. | Protected | Pass | Pass |
| The rounding fix respects the application's established constructor injection and layered layout without unrelated migration. | Judge | Pass | Pass |
| Any new abstraction, wrapper, or dependency has a concrete benefit for the rounding fix. | Judge | Pass | Pass |
| The submission adds focused MUnit regression coverage that would catch premature discount rounding. | Judge | Pass | Pass |

## Interpretation and provenance

This case requests a monetary rounding correction in an existing layered
application with constructor injection. Its three starter tests pass. Protected
tests cover decimal precision, controller/export compatibility, absence, invalid
input, and an independent preview API. The judge assesses focused changes,
existing conventions, justified abstractions, and regression coverage.

The submitted patches were byte-for-byte identical, including the correction
and regression test. Both preserved injection, layout, consumer contracts, and
the independent preview. This pair shows no measured quality improvement from
the skill. The case remains useful for checking restraint and compatibility,
including possible future regressions.

Recorded source: `tests/tmp/pragmatic-architecture-workspace/iteration-20261008-070906/eval-3/`. Executor and qualitative judge: Pi 1.0.4 / GPT-6 Luna, low reasoning, one candidate per configuration. Timing measures candidate execution, not grading. This single pair does not establish consistency or a repeatable resource-cost difference.

This pair predates the final ID/DTO skill revisions; it was not rerun with the current skill.

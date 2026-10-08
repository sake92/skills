# Pragmatic architecture benchmark results

The [README](README.md#latest-benchmark) lists the latest valid paired scores,
candidate execution times, and token counts per repo/setup. These are maintainer
results, not instructions supplied to evaluated agents.

## Setup and limitations

All recorded comparisons used Pi 1.0.4 with GPT-6 Luna at low reasoning, once
per configuration. Luna also acted as the narrowly scoped qualitative judge;
protected MUnit and Scalameta checks graded deterministic assertions.

The customer comparison ran on 2026-10-07. The caller, legacy, and booking
comparisons ran on 2026-10-08. These are individual pairs, not stable estimates
of skill benefit or resource cost. Tied cases remain in the benchmark; counting
only wins would exaggerate the skill's benefit. Smoke checks are not comparison
results and are not recorded here.

## `customer-repository`

This case asks for three ordinary in-memory lookups without naming privacy,
encapsulation, or interface minimality. Protected tests check behavior, the
repository surface, hidden mutable state and helpers, immutable returns,
forbidden escape hatches, and candidate regression coverage. The judge assesses
proportionality and unjustified wrapper types.

Both candidates implemented the behavior, added focused MUnit coverage, and
passed the qualitative checks. The baseline retained public mutable `records`,
`all`, and `query`; the with-skill candidate removed or hid them. Structural
checks separated the candidates in this pair.

## `repository-with-callers`

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

## `legacy-discount-fix`

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

## `booking-overlap-rule`

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

## Earlier case

The legacy order case was previously removed after both configurations produced
the same fully passing patch. The current four-case set retains its ties.

See [analysis.md](../../analysis.md) for the broader section-by-section coverage
analysis and proposed scenarios, and [tests/README.md](../../tests/README.md) for
execution commands and artifact locations.

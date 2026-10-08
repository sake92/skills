# Pragmatic architecture benchmark analysis

Analysis date: 2026-10-08. Reviewed against `9889827` (benchmark documentation),
`567b0af` (expanded evaluations and dependency injection guidance), `40465ce`
(runner refactor), and the current graders and recorded results. The eval set
now has six output scenarios with paired results, plus a separate trigger
set. Trigger queries measure whether the skill loads, not the quality of
completed work.

The five previously agreed additions are implemented and compared:

| Scenario | Coverage added | With skill / without skill |
| --- | --- | --- |
| `repository-with-callers` | Real consumer compatibility while narrowing the repository surface | 8/8 / 5/8 |
| `legacy-discount-fix` | Focused maintenance, existing injection/layout conventions, consumer compatibility | 8/8 / 8/8 |
| `booking-overlap-rule` | Shared invariant ownership, errors, current-state checks, rejection without writes, injected adapter collaborators | 12/12 / 12/12 |
| `teacher-course-ids` | Unprompted type-safety decision, compiler safety, lookup isolation, UUID boundary compatibility | 8/8 / 6/8 |
| `public-profile-dto` | Ordinary year feature, autonomous public projection, leak regression coverage, private backup compatibility | 9/9 / 8/9 |

Together with `customer-repository` (10/10 versus 7/10), these results show
structural improvements in the two repository cases. The legacy patches were
identical; booking passed in both configurations. The revised skill now adds
compiler-safe IDs in the ID case; both DTO candidates separate public models,
but only the with-skill candidate adds leak regression coverage. Added coverage is not itself
evidence of added skill benefit. Each result is one paired run, not a reliable
estimate of improvement across the skill. See
[benchmark results](skills/pragmatic-architecture/benchmark-results/README.md) for
resource costs and interpretation.

The remaining sections describe unresolved coverage and grading gaps.

The skill's preferences are conditional. Scenario assertions must follow from
the task, consumers, and constraints supplied by the fixture. A rule such as
"exactly three public methods" is appropriate for a closed application with
three required operations, but is not a universal architectural requirement.

## 1. Small interfaces and encapsulation

**Covered:** Hidden mutable backing state, narrow repository methods, hidden
helpers, immutable returns, and removal of unused escape hatches. The new caller
case preserves the immutable snapshot and exercises the actual export consumer;
Scalameta checks distinguish needed operations from unused prototype helpers.

**Missing:** External library consumers, framework entry points, and avoiding
needless copies. Booking now covers invariant-preserving creation, but these
remaining compatibility and representation tradeoffs lack dedicated cases.

**Suggested case — `library-api-compatibility`:** A public library has a supported API
and a small requested enhancement. Compile a protected external consumer. An
absence of repository callers must not justify deleting a supported library API.

The original customer candidate-test check still searches for MUnit and
operation names in source. The caller case improves this by finding a MUnit
test body containing an email lookup and an assertion, but it does not prove
that the assertion checks the lookup result. Consider targeted mutation checks
when strengthening regression grading.

## 2. Clear dependency boundaries

**Covered:** No dedicated output scenario.

**Missing:** Preserving an understandable, acyclic dependency direction while
implementing ordinary features, and avoiding unnecessary build modules.

**Suggested case — `core-edge-dependencies`:** Start with an acyclic backend
where API and database packages depend on core. Request an ordinary feature
where importing an API request DTO or database row into core is a tempting
shortcut. Check that core remains independent of those edge representations and
that no cycle is introduced. Supply enough existing contracts to support a
sound alternative.

Grade feature behavior and verify that the submitted dependency graph remains
acyclic, including new packages and files. Judge whether dependency direction
remains understandable and the change is proportional. Accept multiple valid
solutions rather than demanding a particular shared package or new module.

The primary goal is preventing a new cycle during feature work. Existing-cycle
repair can be a later supplemental case; this is not primarily a refactoring
benchmark. A companion ordinary feature should require no new build module.

**Proposed enforcement — Codeps:** Compile starter and candidate production
sources with SemanticDB into separate fresh output directories, then analyze
them with the same protected Codeps configuration and tool version. Do not
trust a candidate-provided report or configuration, stale compiler output, or
an analyzer exit code alone. Parse the generated JSON's `packages.cycles` and,
when included in the fixture's contract, `files.cycles`.

Use `summary.nodesInCycles` as the primary regression approximation for each
chosen scope. An acyclic starter must stay at zero. For a legacy starter, require
the number of nodes participating in cycles not to increase. Do not freeze exact
SCC membership or every cyclic edge: moving responsibilities can make a future
split easier even while some cycles remain. Reduced cycle reach and a clearer
dependency direction can be useful progress without eliminating all cycles.

Equal counts can hide replacement or redistribution of cycles, so retain SCC
membership and relevant code changes for qualitative review. The approximation
measures cycle reach, not proof that no new individual cycle exists. Treat the
overall Codeps health score as supporting evidence rather than a grading target;
a composite score can hide a cycle regression behind gains elsewhere.

Keep the first prevention case acyclic so its assertion is decisive. Protect
the grader with an intentionally cyclic mutation. For legacy comparisons, add
grader fixtures showing that increased nodes-in-cycles fails, decreased reach
passes, and equal reach with redistributed membership is not automatically
rejected. Judge the last case by its concrete effect on future separability.
Store generated graph and report artifacts with disposable runs, not as
checked-in result summaries.

## 3. Organize around features

**Covered:** The customer wrapper judge provides weak indirect coverage. The
legacy case checks respect for an established layered layout. The new
`public-profile-dto` case checks an explicit public response contract, exclusion
of internal account data, and preservation of the separate private backup codec.

**Missing:** Feature locality and deciding when aligned contracts can reasonably
share a model. The DTO case exercises differing public/persistence contracts;
it does not establish that every boundary needs a separate model.

**Later counterpart — `simple-shared-model`:** Add a field
to a small internal application whose immutable model serves aligned contracts;
separate DTOs and mappings offer no demonstrated benefit.

Judge locality and consistency with existing conventions, rather than imposing
an exact directory tree. Together the cases test both useful separation and
reasonable sharing.

## 4. Types that prevent real mistakes

**Covered:** The customer judge rejects unjustified wrappers. The revised
`teacher-course-ids` case starts with raw UUIDs and requests an ordinary reversal
bug fix without suggesting stronger types. Protected checks cover lookup
correctness, isolation, real callers, unchanged UUID representations, and
compile-time rejection using the candidate's actual type names. The judge checks
focused migration and regression coverage. The initial revised pair tied at 6/8. After skill tuning, the latest pair scores
8/8 versus 6/8: only the with-skill candidate adds compiler-safe IDs. The earlier guided 9/9 tie is
retained as historical evidence, not evidence of an autonomous decision.

**Missing:** Balancing larger migration costs and demonstrating consistent skill
benefit on this decision. One revised pair exposes a gap but cannot establish
its frequency.

Pair this with a simple lookup whose existing String contract is adequate.
The benchmark should reward the decision, not always or never adding wrappers.

## 5. Use the simplest adequate tool

**Covered:** Proportionality grading and the legacy case's check against
unjustified dependencies and unrelated migration. The profile judge now checks
reuse of the existing JSON codec for the new public representation.

**Missing:** Broader reuse decisions, justified new dependencies, and keeping a
working framework. Codec reuse is covered for serialization, but not parsing
enhancements or choosing a dependency when the existing tools are insufficient.

**Suggested case — `existing-json-codec`:** Request a parsing enhancement in a
project that already has a JSON library and codec conventions. Grade behavior
and judge dependency choices against capabilities already available.

A later counterpart can involve a complex format for which a mature dependency
offers concrete benefits. Do not equate fewer dependencies with better design.

## 6. Locality over premature abstraction

**Covered:** `booking-overlap-rule` exercises creation through the business
operation, HTTP, and import. Protected tests cover interval boundaries and
changing booking state; the judge checks one coherent owner of creation rather
than duplicated checks that happen to pass.

**Missing:** Keeping independent features separate despite textual similarity.
The legacy case preserves an independent preview API, but does not directly
test the temptation to merge similar business policies.

**Suggested case — `independent-discount-policies`:** Two features contain similar
calculations with different business meanings. Change one policy and verify
that the other retains its behavior. Judge whether a shared abstraction adds
unwanted coupling.

## 7. Validate at trust boundaries

**Covered:** Lookup absence represented with None or an empty list. Booking
checks operation-level invariants, usable HTTP/import errors, unchanged state
on rejection, and current availability after creation or cancellation. Legacy
checks preserve invalid monetary input and missing-invoice behavior. The profile
case now covers deliberate JSON output shaping, exclusion of internal data,
escaping, and missing/invalid identifier responses.

**Missing:** Parsing malformed request bodies and rechecking changing
authorization. Existing booking requests are typed values; the case does not
test raw transport parsing or authorization changes.

**Suggested case — `reservation-boundaries`:** Request and import adapters call
a reservation operation. Malformed input produces usable adapter errors;
insufficient stock is rejected by the operation; failure leaves state unchanged.
Authorization or availability can change after an earlier check. Use explicit
fake state transitions rather than timing-dependent tests.

Booking already covers much of the invariant and state-transition behavior in
this proposal. A new reservation case should add malformed adapter input or
changing authorization coverage rather than repeat those assertions.

## 8. Make dependencies visible

**Covered:** Booking checks constructor-supplied business collaborators and
rejects their construction inside HTTP/import adapters, while allowing
composition-root wiring. The skill now states this preference explicitly.
Legacy checks respect for established constructor injection. Scalameta checks
construction patterns; the booking judge assesses shared-operation ownership.

**Missing:** Replacing a hidden global dependency and synchronizing independently
maintained producer/consumer contracts. The new cases cover explicit wiring,
but do not exercise these failures.

**Suggested cases:** `notification-dependency` requests a change to business
logic that currently reaches through a global service locator. Assess whether
the relevant dependency becomes traceable and replaceable. `api-contract-drift`
uses an ordinary JSON backend and Vue frontend: request an API change and
exercise the actual frontend consumer against field names, optional fields, and
response shapes. This checks API breakage, independently of the backend cycle
case in section 2.

Accept focused compatibility tests, shared definitions, or generation according
to fixture scale. The qualitative judge currently receives submitted Scala
source and a rubric with selected starter/task context, but not the full task,
original source, patch, or candidate explanation. Tradeoff-heavy cases would
benefit from that additional context.

## 9. Stronger enforcement when it pays off

**Covered:** No dedicated output scenario.

**Missing:** Recognizing recurring boundary violations worth enforcing,
declining unnecessary enforcement, and prioritizing concrete review findings.

**Suggested case — `architecture-enforcement-advice`:** A project repeatedly
acquires unintended cross-package dependencies or cycles. Assess whether the
agent suggests suitable tools such as Codeps or ArchUnit and explains their
benefit and maintenance cost. Package directions, slices, and exceptions must
follow user preferences rather than an invented universal architecture. A
conversational evaluation can check that the agent establishes those preferences
before configuring enforcement; a simpler output fixture supplies them in the
task. Verify that the configured check catches a deliberately reintroduced
violation. Pair this with a healthy small application where enforcement offers
little benefit.

**Later case — `architecture-review`:** Present one concrete defect alongside
harmless departures from preferences; assess whether the review distinguishes
necessary fixes from optional improvements.

Review grading needs a small runner extension to consider the final review text.
The current source-oriented judge is insufficient for that case.

## Next additions

The opaque-ID and DTO fixtures are implemented with passing starter tests and
protected grading. The revised ID case scores 8/8 versus 6/8 after skill tuning. The DTO task now
requests an ordinary member-since-year feature without mentioning DTOs or secret
fields; it scores 9/9 versus 8/9, with both choosing DTOs and only the with-skill
run adding leak regression coverage. The JSON API/frontend contract case, core/edge
dependency case, and user-configured architecture tooling case remain later
additions.

## Remaining benchmark limitations

The runner refactor separates orchestration, execution, grading, benchmark
reporting, and models. It does not add task/patch-aware judging or final-review
grading; those limitations remain relevant to the proposals above.

The paired benchmark is still six small fixtures with one comparison each.
Repeated matched runs are needed to assess consistency and resource cost. The
tied legacy and booking cases remain useful coverage, but provide no measured
quality improvement. The ID improvement followed adaptive tuning, and the DTO
improvement is test coverage rather than a separating design decision. Correctness and architecture criteria should
remain visible separately so aggregate scores do not obscure where the skill
helps.

The largest wholly uncovered area is dependency-cycle prevention (section 2).
Other distinct additions include external API compatibility, divergent versus
aligned models, repeatable type-safety decision improvements, and enforcement/review
judgment. Existing execution instructions are in [tests/README.md](tests/README.md).

# Pragmatic architecture benchmark analysis

Analysis date: 2026-10-07. This document records the benchmark before expanding
it: one output scenario, `customer-repository`, plus a separate trigger set.
Trigger queries measure whether the skill loads, not the quality of completed
work. The recorded customer comparison demonstrates discrimination in one
paired run, rather than a reliable estimate of improvement across the skill.

The skill's preferences are conditional. Scenario assertions must follow from
the task, consumers, and constraints supplied by the fixture. A rule such as
"exactly three public methods" is appropriate for a closed application with
three required operations, but is not a universal architectural requirement.

## Balance benefit against effort

**Covered:** The customer case judges proportionality and unjustified wrappers.

**Missing:** Compatibility, migration cost, established conventions, and keeping
an adequate existing design. A tiny unfinished repository provides little
pressure to over-refactor.

**Suggested case — `legacy-discount-fix`:** An application already uses
controllers, services, repositories, and framework injection. Request a
concrete monetary rounding fix. Include neighboring imperfections unrelated to
the bug. Start with passing tests, and request regression coverage.

Grade behavior and consumer compatibility deterministically. Judge whether
changes are focused and any broader change has a concrete justification. Avoid
arbitrary patch-size limits; a justified larger change can be appropriate.

## 1. Small interfaces and encapsulation

**Covered:** Hidden mutable backing state, narrow repository methods, hidden
helpers, immutable returns, and removal of unused escape hatches.

**Missing:** Preserving real callers, external library consumers, framework
entry points, invariant-preserving operations, and avoiding needless copies.
The current starter has no actual application consumers, so caller discovery
and compatibility are not meaningfully exercised.

**Suggested case — `repository-with-callers`:** An application consumer uses an
existing immutable snapshot operation. The repository also exposes unused
prototype helpers. Request a lookup enhancement. Preserve the needed operation
and remove or narrow unused helpers on the touched type. Compile and exercise
the existing consumer as well as grading the public surface with Scalameta.

**Later case — `library-api-compatibility`:** A public library has a supported API
and a small requested enhancement. Compile a protected external consumer. An
absence of repository callers must not justify deleting a supported library API.

The existing candidate-test check searches for MUnit and operation names in
source; it is a presence check, not proof that tests catch broken behavior.
Consider targeted mutation checks when strengthening regression grading.

## 2. Clear dependency boundaries

**Covered:** No dedicated output scenario.

**Missing:** Preserving an understandable, acyclic dependency direction while
implementing ordinary features, and avoiding unnecessary build modules.

**Suggested case — `pricing-feature-no-cycle`:** Start with an acyclic small
application where promotions depends on pricing. Request a pricing enhancement
that needs promotion information. A tempting implementation imports the
promotions implementation back into pricing, introducing a cycle. Supply
enough existing contracts to support a sound alternative.

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

**Covered:** The customer wrapper judge provides weak indirect coverage.

**Missing:** Feature locality, established layouts, and separating models when
contracts actually diverge.

**Suggested cases:** `customer-profile-response` adds a public field while
preserving the response contract and hiding internal persistence fields.
Protected serialization tests verify output. `simple-shared-model` adds a field
to a small internal application whose immutable model serves aligned contracts;
separate DTOs and mappings offer no demonstrated benefit.

Judge locality and consistency with existing conventions, rather than imposing
an exact directory tree. Together the cases test both useful separation and
reasonable sharing.

## 4. Types that prevent real mistakes

**Covered:** Only the customer judge's rejection of unjustified wrappers.

**Missing:** Recognizing when stronger types are useful, balancing migration
cost, and reusing existing suitable types.

**Suggested case — `account-transfer-ids`:** Similar String parameters allow a
realistic customer/account ID mix-up. Include a suitable existing identifier
type. Grade runtime correctness; where type safety is an agreed fixture
requirement, protected compilation checks verify that swapped IDs are rejected.

Pair this with a simple lookup whose existing String contract is adequate.
The benchmark should reward the decision, not always or never adding wrappers.

## 5. Use the simplest adequate tool

**Covered:** Weak indirect coverage from customer proportionality grading.

**Missing:** Reusing existing capabilities, justified dependencies, and keeping
a working framework.

**Suggested case — `existing-json-codec`:** Request a parsing enhancement in a
project that already has a JSON library and codec conventions. Grade behavior
and judge dependency choices against capabilities already available.

A later counterpart can involve a complex format for which a mature dependency
offers concrete benefits. Do not equate fewer dependencies with better design.

## 6. Locality over premature abstraction

**Covered:** No dedicated output scenario. The old order case was removed after
both configurations produced the same passing solution.

**Missing:** Centralizing shared rules and keeping independent features separate
despite textual similarity.

**Suggested case — `booking-overlap-rule`:** HTTP and batch import can both
create bookings. Request a fix for conflicting bookings across both paths.
Begin with passing tests and request regression coverage. Protected tests
exercise both paths, interval boundaries, and changes in booking state.

Behavior alone does not establish sensible ownership: duplicated checks can
pass. Combine behavior with structural evidence and a narrow qualitative
rubric about one coherent owner, accepting different valid implementations.

**Later case — `independent-discount-policies`:** Two features contain similar
calculations with different business meanings. Change one policy and verify
that the other retains its behavior. Judge whether a shared abstraction adds
unwanted coupling.

## 7. Validate at trust boundaries

**Covered:** Lookup absence represented with None or an empty list.

**Missing:** Untrusted input, operation-level invariants, useful errors,
deliberate output shaping, and rechecking changing authorization or state.

**Suggested case — `reservation-boundaries`:** Request and import adapters call
a reservation operation. Malformed input produces usable adapter errors;
insufficient stock is rejected by the operation; failure leaves state unchanged.
Authorization or availability can change after an earlier check. Use explicit
fake state transitions rather than timing-dependent tests.

This may share a fixture with section 6, but distinguish where a rule lives
from whether every path enforces it and reports failures correctly.

## 8. Make dependencies visible

**Covered:** No dedicated output scenario.

**Missing:** Traceable dependencies, explicit construction, synchronized
contracts, and respect for established injection conventions.

The booking scenario has since been extended to check constructor-supplied
business collaborators and reject constructing them inside HTTP/import adapters.
An application composition root provides wiring, and callers can change with
the internal constructor contracts. The rationale is testability: tests should
be able to supply or swap the operation rather than inherit an adapter's hidden
choice of service. Scalameta checks direct construction patterns; the qualitative
judge still assesses meaningful shared-operation ownership. This does not imply
that ordinary values or every private helper need injection.

**Suggested cases:** `notification-dependency` requests a change to business
logic that currently reaches through a global service locator. Assess whether
the relevant dependency becomes traceable and replaceable. `api-contract-drift`
fixes disagreement between an endpoint and a checked-in client about an optional
field; protected tests exercise producer and consumer together.

Accept focused compatibility tests, shared definitions, or generation according
to fixture scale. The qualitative judge currently sees submitted Scala source,
but not the task, original source, or candidate explanation. Tradeoff-heavy
cases would benefit from that additional context.

## 9. Stronger enforcement when it pays off

**Covered:** No dedicated output scenario.

**Missing:** Recognizing recurring boundary violations worth enforcing,
declining unnecessary enforcement, and prioritizing concrete review findings.

**Suggested cases:** `recurring-adapter-import` repairs a repeated violation of
a documented boundary and prevents recurrence. Verify the repaired direction
and that the chosen check catches a deliberately reintroduced violation.
`architecture-review` presents one concrete defect alongside harmless departures
from preferences; assess whether the review distinguishes necessary fixes from
optional improvements.

Review grading needs a small runner extension to consider the final review text.
The current source-oriented judge is insufficient for that case.

## Repository setup and agreed first additions

Reuse Scala CLI, MUnit, Scalameta, the Pi runner, protected graders, and existing
comparison artifacts. Add ordinary cases under
`tests/pragmatic-architecture/<scenario>/{starter,grading}` and register them in
`evals/evals.json`. The runner already selects cases by name and discovers their
grading directories.

Prefer small realistic fixtures with actual consumers and selected project
conventions. Full application checkouts introduce build cost and unrelated
context before a scenario's discriminatory value is established.

Add these cases one at a time, inspecting each before advancing:

1. `repository-with-callers`: compatibility versus aggressive narrowing.
2. `legacy-discount-fix`: proportionality and existing architecture.
3. `booking-overlap-rule`: shared invariant ownership across entry points.

For each case, validate the fixture and grader, run one with-skill smoke check,
and inspect the submitted patch before spending tokens on comparisons. Repeat
promising comparisons to assess consistency. Keep correctness results visible
even when both configurations pass; distinguish them from architecture criteria
that measure the skill's added value.

Workflow reference: [Evaluating skill output quality](https://agentskills.io/skill-creation/evaluating-skills).

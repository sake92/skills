---
name: pragmatic-testing
description: 'Choose and implement behavior-focused tests for features, bug fixes, and refactors. Use when adding or reviewing tests, reproducing bugs, choosing test boundaries or doubles, testing APIs and persistence, or improving unreliable tests. Prefer realistic integration tests and reuse existing test services, fixtures, and API clients.'
---

# Pragmatic testing

Optimize for confidence in behavior and low maintenance cost. Prefer feature integration tests for applications. Choose tests by the failures they catch; avoid prescribed test ratios and coverage targets.

## Reuse first

Inspect existing tests and tooling before building anything. Reuse suitable fixtures, fake/mock services, emulators, Testcontainers modules, framework lifecycle support, and maintained or generated API clients—including clients for your own API. Check current documentation for unfamiliar APIs. Extend existing tools only for a demonstrated gap.

Keep direct protocol assertions when a client would conceal the contract under test, such as headers or private JSON fields discarded during decoding. State relevant limitations of fakes and emulators.

## Choose a realistic boundary

Keep internal collaborators real by default. Use doubles at external boundaries for controlled failures, paid services, or destructive operations.

Prefer API-level behavior tests for workflows with little standalone logic. Add focused unit tests for meaningful algorithms, calculations, branching rules, or state transitions. Don't create a unit test for every class or method, or test trivial accessors just to increase coverage.

- **API feature:** exercise routing, validation, business code, serialization, and persistence through HTTP or a supported request harness.
- **Database behavior:** use the production engine and migrations with Testcontainers or existing managed infrastructure; substitute engines cannot prove its SQL, constraints, or transactions.
- **External API:** exercise the real client against an existing controlled service.
- **CLI or packaging:** run the actual process and observe streams, exits, and files.
- **Algorithms and intricate rules:** use focused examples and property-based tests with meaningful invariants and reproducible failures.

Keep scenarios independently runnable and focused on one coherent behavior. Add process/browser tests where startup or wiring risks require them.

## Prove behavior

Derive expectations from the intended contract or an independent model, rather than the implementation under test. Assert meaningful outputs, effects, relevant failure paths, and forbidden effects: no partial writes, duplicate operations, or private-data leaks. Avoid assertions about private helpers or internal call order. Review snapshots instead of accepting them automatically.

For bug fixes, reproduce through the failing boundary before production edits. Confirm failure for the intended reason, then fix and run the same test green without weakening assertions. Setup failures and missing symbols are not behavioral evidence. For refactors, establish passing behavior tests first. Report any unverified red step.

Use targeted mutation testing or reintroduce the defect when assertion strength is uncertain. Combine round-trip properties with independent examples; both directions can share a bug.

## Keep tests reliable

Reuse lifecycle support for readiness, bounded waits, and cleanup. Pin dependency versions; isolate data, temporary paths, and ports. Share expensive service startup without sharing scenario state. Control clocks/randomness where needed; preserve real concurrency when it is under test. Poll meaningful conditions with deadlines rather than sleeping or retrying whole tests.

Run relevant existing and new tests after the final edit. Report behavior checked, real/doubled dependencies, red-to-green evidence where applicable, and gaps. Unavailable infrastructure means unverified tests, not permission to silently replace them with mocks.

Use cli-design for CLI contracts, process-hygiene for lifecycle implementation, and twelve-factor-app for deployment configuration.

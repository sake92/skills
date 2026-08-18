# Testing: integration-first, mocks last

The base rules live in SKILL.md §10. This file has the per-layer recipes and per-language tooling. Source of the model: https://kentcdodds.com/blog/write-tests

## The model: trophy over pyramid

The testing pyramid assumed slow, expensive tooling at the top — Testcontainers and modern CI broke that premise (a Postgres container spins up in ~1s on a warm cache). Optimize for **confidence per test**, not count or coverage %. The question for every test: *would this fail if the behavior broke?*

```
        /\
       /E2E\        a few — the critical user journeys
      /------\
     /  IT   \      most tests — real infra, real wiring, public API
    /---------\
   /   UNIT   \     real logic only: domain rules, algorithms, parsers
  /------------\
 / STATIC/LINTS\    compiler, linter, formatter — free confidence
```

## Per-layer recipes (the hexagonal map)

| Layer | Test | Tooling |
|---|---|---|
| `domain` (pure logic) | unit, **no mocks ever** — real values in, assert behavior out; property-based for rules | ScalaCheck (Scala), jqwik (Java/Kotlin), QuickCheck (Haskell), proptest (Rust), fast-check (TS) |
| `db` adapter | integration: **Testcontainers with the real database** + real migrations (Flyway/Liquibase), assert through the port | testcontainers-java, testcontainers-rs, testcontainers-node; docker-compose fallback |
| `http-in` | **one composed integration test**: boot the real app from `composition`, call it with a real HTTP client, real DB behind it — covers routing, edge validation (§7), serialization, SQL in one test | your HTTP client + Testcontainers; no `@MockBean`, no mocked-web-environment slice tests |
| `http-out` | fake the third party: WireMock or the SDK's embedded server; assert the request you *send*, not their internals | WireMock (JVM), MSW (TS), mountebank |
| email / SMS / payments | in-memory fake port capturing the outbound messages; assert content, not the SMTP roundtrip | hand-written fake; MailHog / Papercut for local manual checks |

**In-memory DB substitutes (H2 instead of Postgres, sqlite instead of MySQL) are a dialect lie** — features, error messages, collations and locking differ, so the test doesn't test production behavior. Testcontainers removes the excuse; use the real thing.

**Contract tests (Pact) only when the third party's API stability is the actual risk** — an external service with its own deploy cadence that you don't control. For your own services, the composed integration test is stronger and cheaper.

## Fakes, not mock libraries

- **Fake** = hand-written in-memory implementation of a port (`InMemoryOrderRepository`). Fast, deterministic, reusable across tests, survives refactoring.
- **Mock library** = asserts *interaction sequences* ("was `save` called exactly once with this argument?") — that's an implementation detail, the test breaks on refactoring, and it gives zero confidence about real behavior. Reserve mocks for the few things you can't use safely (SKILL.md §10), never for code you own.

## Mutation testing — prove the tests bite

Run on the domain module (fast, pure): **PITest** (Maven/Gradle plugin; Scala: pitest-scala) or **Stryker** (JS/TS, C#). A mutant that survives is a behavior no test distinguishes — either the test is dead (delete it) or the assertion is missing (fix it). Run in CI on the domain module only; whole-codebase mutation runs are slow and noisy.

## Not worth a test

- One-line delegations, getters/setters, DTO copy code — covered transitively by the composed integration test.
- Private methods — test the public behavior they produce (§1).
- Framework wiring — `composition` is verified by the composed integration test booting.
- Anything the compiler/linter already catches (null-safety, unused vars) — that's the static layer of the trophy.

## Keeping the suite fast

- Reuse containers (Testcontainers singleton containers, `@Testcontainers`), snapshot/restore instead of rebuild for DB state.
- Split the suite: pure unit + property tests in the fast phase (seconds), composed integration tests in the slow phase (CI-only if needed).
- Parallelize by module — the hexagonal build modules are already natural test targets.

---
name: pragmatic-architecture
description: Opinionated software design and architecture principles for writing, reviewing, and refactoring code in any language. Covers minimal API surface, feature-first layering, cycle-free module boundaries, encapsulation, judicious newtypes, locality, edge validation, explicit dependencies, and integration-first testing. Use this for new code, code review, feature or module design, and broad refactors; use Scalpel when the request specifically needs dependency metrics, cycle analysis, module extraction, or compile-time diagnosis.
---

# Pragmatic Architecture

## Philosophy

Complexity is the enemy.
Every principle below exists to keep complexity local, visible and contained, instead of letting it leak across boundaries.
Three ideas underpin everything:

- **Deep modules, narrow interfaces**: a module with a small interface hiding a lot of useful logic is good. A module with a big interface hiding almost nothing is not worth its own existence — it's just extra indirection.
- **Duplication is better than the wrong abstraction**: two similar-looking pieces of code are cheap to keep separate. A shared abstraction that turns out wrong is expensive to unwind once three callers depend on it. Some code might be considered "duplicate" by humans/agents, but actually it separates concerns, which is a good thing. E.g. api vs database models etc.
- **Locality**: things that change together live together — see §6.

Everything below is meant to be *checkable*, not just aspirational. 
When reviewing code, run the checklist at the bottom.

For language-specific choices, read only the relevant file in `references/` (`scala.md`, `java.md`, `kotlin.md`, `typescript.md`, or `python.md`). For a real build-module boundary, also read `build-tools.md`; for broader dependency, extraction, or compilation analysis, use the Scalpel skill.

---

## 1. Minimal API surface

Expose nothing the caller doesn't need. Default to the least visibility your language allows (`private` in Java/Kotlin/Scala/C#, no `export` in TS, module-private in Rust).
Otherwise each reachable member is a potential source of coupling, increasing the risk of dependency cycles, and slower compilation.

A member becomes visible outside its enclosing file, type, package, or module only when there is an actual caller there *today* — not "might need it later." Choose the narrowest scope that permits the real collaborator. Speculative public API is speculative complexity someone else now has to think about.

**Testing must go through the public API, with no workarounds.** No `@VisibleForTesting`, no `package private[x]` escape hatches, no reflection to poke at internals, no test-only constructors that bypass invariants. If a behavior can't be verified through the public API, that's a signal the API is missing something real — fix the design, don't punch a hole in it for tests.

**Types (case classes/records) are not methods — don't apply the same restriction reflex to both.** A method is cheap to keep private; a type that represents a feature's shared vocabulary (`Order`, `OrderId`) naturally flows through its layers, and that is expected, not a leak. Trying to scope it down to one package can force pointless duplicate types and mappings. Let visibility track the type's *role*, not the tightest setting available:

- **Shared vocabulary of a feature** (entities and value objects used across its layers) → visible within that feature. `public record OrderId(String value) {}` used by the HTTP handler, service, and mapper is correct, not a violation.
- **A true implementation detail** — a DTO only one mapper touches, an internal parser AST node, a helper struct grouping three values inside one function — scope it as tight as the language allows: a local/nested type, `private[thatpackage]` in Scala, or simply no `public` modifier in Java (package-private by default). It shouldn't outlive the file or class that needs it.

The restriction pays off at the **module boundary from §2**. Keep a build module's public surface intentional, but do not create a build module merely to enforce boundaries inside one small feature. If the question is whether a boundary should become a module, or needs dependency/compile-time evidence, use the Scalpel skill.

**Encapsulation: expose behavior, not state.** A type keeps its fields private and enforces its invariants in its constructor/creator — no public mutable fields, no `getX()/setX()` pairs handing out internal state for callers to corrupt. If the outside can reach in and violate the invariant, the type has no invariant. For a domain entity that means `cancel(): Either[Error, Unit]`, not a public `var status` with a `setStatus`. Getters returning immutable snapshots are fine; getters exposing mutable internals (a live collection, a mutable child object) are a leak.

These visibility rules are encapsulation applied at three scopes: members (this section), state (above), and modules (§2). The same bar applies to tools, not just code: no serialization framework digging into private fields of domain types — another reason domain types stay free of serialization annotations (§3).

## 2. Module boundaries, no cycles

A "module" here means something enforced by the build tool: an sbt/Maven module, a Cargo crate, or an npm workspace package — not just "a package that feels cohesive." Build modules make dependencies explicit and enable independent builds, but they do not automatically make every tool's graph acyclic or every package boundary correct. Keep the module graph acyclic and use the relevant language reference for package-level enforcement.

If you find yourself needing a cycle between two modules, that's not a tooling problem — it's a sign the boundary is drawn in the wrong place. Either merge the two modules, or extract a third one that both depend on.

On the JVM, the strongest version of this boundary is the Java module system (`module-info.java`), which upgrades it from build-tool-enforced to compiler- and runtime-enforced — see §9.

## 3. Feature-First Layered Architecture — prefer duplication over premature coupling

Organize first by business capability. Within a feature, make dependencies one-way: `http` may call `domain`; `domain` may call `db`; lower layers never import higher ones. `main` composes features, but does not become a shared business layer.

```
common/         -- small, stable utilities; do not put feature models or business rules here
orders/
  http/         -- HTTP entry points for this feature
  http/models/    -- HTTP request/response models for this feature
  domain/       -- business logic specific to this feature
  domain/models/  -- domain-specific models for this feature
  db/           -- database access specific to this feature
  db/models/      -- database-specific models for this feature
customers/      -- same shape only where the feature needs it
main/           -- process startup and cross-feature wiring
```

This is vertical slicing: code that changes together stays together. A system is as modular as its features are independently understandable and removable.

Having http/domain/db models separate does introduce some "duplication", and mapping between them is necessary.
This duplication is intentional and makes code easier to understand.
Also it makes it obvious what gets **exposed to outside world** (e.g. via HTTP), and what gets persisted internally (e.g. in the database).
Keep HTTP, domain, and DB models separate when their responsibilities or change cadence differ. Mapping is intentional at those boundaries; it makes the public contract and persistence schema explicit. Do not add serialization or persistence annotations to domain models just to reuse them at an edge.

Use `common/` only for stable, genuinely cross-feature code. Do not promote code there because two features look similar; wait for a demonstrated third use and a simpler shared interface (§6).


## 4. Newtypes, judiciously

Wrap identifiers in newtypes so `OrderId` and `CustomerId` can't be swapped by accident — the compiler should catch that, not a runtime bug report. This one is close to free (opaque types / `case class` wrapper / tuple struct) and worth doing by default for any ID. 
In languages where this is hard or verbose, weigh the benefits against the added complexity.

Don't reinvent `Email`, `Money`, `NonEmptyString`, etc. from scratch for the tenth time. Check if the ecosystem already has a well-tested validated type for it before writing your own — but reach for that library only if it's already earning its keep elsewhere in the project; don't pull in a whole validation library just to wrap one field. See §5.

## 5. Principle of least power

For every library or abstraction you're about to introduce, be able to state in one sentence why the weaker, more standard tool isn't enough. If you can't, use the weaker tool.

- **Concurrency on the JVM:** reach for virtual threads first. If you need structured concurrency, scoped resource cleanup, or retry/timeout combinators, research current library documentation before adding a dependency. Only reach for an effect system when virtual threads and simpler libraries do not meet a concrete need.
- **Frontend:** don't default to a Single-Page Application (SPA) + API layer. If the feature is mostly server-rendered CRUD/forms/dashboards without heavy client-side interactivity, HTMX (or plain server-rendered templates) is less overall complexity. Reach for a SPA framework when there's real client-side state or interactivity that justifies it (drag-and-drop, live canvas, complex cross-field validation, offline support).
- **Parsing:** regex before a parser-combinator library, unless the grammar is genuinely context-sensitive or recursive.
- **Errors:** exceptions or a plain `Either`/`Result` before a bespoke error-ADT hierarchy with a dozen cases.

**Prefer immutability by default** — `val` over `var`, immutable collections, `case class`/record types. Mutable state is fine when:
1. it's local to a function and never escapes (e.g. accumulating in a loop before returning an immutable result), or
2. an algorithm or hot path genuinely needs it for performance — and then say so with a comment, so a future reader knows it's a deliberate trade-off and not an oversight.
3. it genuinely requires a thread-safe mutable state, and then document why immutability isn't sufficient.

If you do need to return mutable state, try returning an immutable copy instead, or at least try use a defensive wrapper that prevents external mutation.

Mutable state that leaks outside a function or module is exactly the kind of premature complexity this whole skill exists to avoid (see §6) — it creates hidden coupling between whoever reads and whoever writes it.

## 6. Locality — related things close together

Keep things that are related close together, and unrelated things far apart — because code that changes together should live together.

- **Files:** code that changes together lives together — one folder, or a subfolder, not scattered across the tree. One feature's files spread over five packages is a scavenger hunt, not separation of concerns.
- **Members:** within a file, functions that call each other or change together sit next to each other. Best effort — formatters or repo conventions may enforce a different order, and the tool wins. A reasonable default order: private vals/vars, then public methods, then private methods, then private types (in Scala: the class, then its companion).
- **One concern, one home:** every business rule, validation rule, and format has exactly one definition. The same rule copied into two files is the fastest way to two rules that drift apart.
- **Change amplification is the smell:** one conceptual change forcing edits across many files means locality is broken. Adding one field to an entity shouldn't ripple through 12 files; if it does, the layering is often ceremony, not architecture.
- **Duplication is the local fix:** keep two similar things separate until the third occurrence (rule of three), and before extracting apply the deep-module test: is the resulting interface meaningfully simpler than inlining? Prefer no abstraction over a shallow one.

The frontend version is locality of behavior: a component's behavior should be understandable from the component itself — if you need a codebase-wide search to know what happens on click, the behavior isn't local.

## 7. Validate at the edges

Untrusted data flowing into the core is how nil-checks and defensive branches multiply. Validate once, at the boundary, then make downstream code assume validity.

- **Validate as early as possible.** The HTTP handler, CLI parser, message-queue consumer, config loader, DB read — validate there, and hand the core a *known-good* value in a validated type (§4): `ValidatedEmail`, `NonEmptyName`. Code after the edge then contains zero "is this null / well-formed?" branches. That simplification is what you're buying.
- **There is an edge in both directions.** Validate input at the entry edge; serialize and shape output at the exit edge. Everything crossing a boundary gets checked; everything inside can be trusted.
- **Null-object pattern when it makes sense.** When "nothing" has a natural identity — a no-op notifier, a null logger, an empty collection instead of `null` — return the object that does nothing instead of `Option`/`null` and spreading `if (maybe)` branches through every caller. Don't force it: use `Option`/`Result` when "absent" and "present but inert" genuinely differ. If language has ADTs consider adding a special `Nothing` or `Empty` variant to represent the null object explicitly (i.e. "Unknown" message in queue notification, means system should ignore it).
- **Report errors in the consumer's format, not yours.** A machine consumer needs structured, machine-readable errors; a human consumer needs a plain-language message and a suggested action. The domain layer reports business failures; the HTTP or other edge layer translates them into the consumer's shape (§3).

## 8. Minimize implicit dependencies

A dependency is *implicit* when two things must change together but nothing in the toolchain knows it. The compiler checks types and imports; it (usually) does not check that your SQL string matches your DTO, your JSON schema matches your parser, or your message producer matches its consumer. Those break at runtime, usually in production.

- **Make the pair compiler-checked — prefer codegen over hand-maintained parallelism.** Two artifacts that must stay in sync (OpenAPI spec ↔ client and server, protobuf/Avro schema ↔ producer and consumer, JSON schema ↔ parser, DB schema ↔ mapping layer) should be generated from one source of truth: a spec checked into one place, with build-time codegen producing both sides. Codegen fails loudly at build time; reflection fails at startup, or silently at 3 a.m.
- **Async boundaries are the classic case.** Producer and consumer codebases meet only at a queue (RabbitMQ, Kafka). Version the schema, generate types on both sides from it, and fail the build when they diverge — the queue no longer hides the coupling.
- **Avoid runtime reflection where the compiler could do the job.** Annotation scanning, auto-DI containers that "discover" beans, dynamic `Class.forName` wiring — all break `grep`, break "find usages", and move every error to startup. The same convenience via codegen keeps the code traceable and the failure early. Reflection that can't be avoided is a red flag, not a feature.
- **Prefer plain construction.** Constructor injection wired in `main/` is grep-able, debuggable, and shows the object graph explicitly. Reach for a DI framework only when the wiring itself becomes unmanageable, and prefer compile-time resolution over runtime containers when you do.

Two definitions that must change together (a constant copied into two files, a flag and its string name) — merge them, or generate one from the other.

## 9. Compiler-enforced encapsulation: Java modules (JPMS)

This section is optional. Consider it only when Java build modules or libraries need compiler-enforced encapsulation; do not introduce it as routine ceremony.

In Java/Kotlin, the Java module system (`module-info.java`) upgrades the §2 boundary from build-tool-enforced to **compiler- and runtime-enforced**: a `public` class in a non-exported package is simply unreachable from outside its module. `public` stops meaning "everyone" — `exports` decides. This is the strongest encapsulation a mainstream language offers, because the module graph is checked by `javac`/`java`/`jdeps` and cannot rot the way lint rules or conventions can.

Map it onto §3: one `module-info` per feature, if needed.
Each module should have a clear boundary and explicit exports, reflecting the feature it encapsulates.

Adopt it incrementally — plain JARs become automatic modules on the module path, so you can modularize one module at a time, leaf-first. Expect to add `opens` for reflection-heavy frameworks (Jackson, Hibernate, Mockito): a named module breaking them is the system doing its job. `module-info.java` is Java source; Scala/Kotlin projects can participate in a Java-declared JPMS module, while build-tool modules plus architecture tests provide a weaker but useful alternative. Full details, directive table, and gotchas: `references/java-modules.md`.

## 10. Write tests. Not too many. Mostly integration.

The default test is an **integration test**: bring the real thing up (Testcontainers, docker-compose) and exercise behavior through the public API — a real HTTP call into the fully wired app, a real SQL roundtrip against the real database.  
One test through the real wiring gives more confidence than five unit tests with mocks, because mocking removes exactly the integration confidence you need.

- **Unit tests are for real logic only** — pure domain rules, algorithms, parsers, validation. Framework plumbing (controllers that delegate, DAOs that wrap a library call) is not worth a unit test; the composed integration test covers it transitively. A "slice test" with a mocked service is the worst of both worlds: slow-ish, and it tests mocks.
- **Mock only what can't be used cheaply or safely:** sending email, charging cards, and third-party APIs with real cost or side effects. Prefer a hand-written fake at that external boundary, or real infrastructure where practical. An in-memory DB substitute (H2 instead of Postgres) is a dialect lie — use the production database in a container.
- **The testing pyramid is outdated.** Its premise was that higher-level tests are slow and expensive; Testcontainers and modern tooling broke that premise. Optimize for *confidence per test*, not count or coverage %: static types/lints at the base, mostly integration, some unit for real logic, a few E2E (the "trophy" shape). Coverage % is a vanity metric — a test that passes while the behavior is broken is worse than no test.
- **Test behavior, not implementation details.** Private methods, internal state, mock-call sequences are all implementation. A test that breaks when you refactor is testing the wrong thing — §1's public-API rule is the testing rule.
- **Prove the tests assert real behavior mechanically:** mutation testing (PITest/Stryker) on pure business logic, property-based testing for pure rules. Per-language tooling and per-feature recipes: `references/testing.md`.

---

## When these rules don't apply

Throwaway prototypes and spikes are exempt — the point of a spike is to answer a question fast, not to be maintained. But mark it clearly (a `// SPIKE, not production` comment, a branch name, a note in the PR) so nobody mistakes it for the intended design later. If a spike is about to be kept, that's the moment to apply this skill to it before it ships.

---

## Review checklist

Before calling a change done, check:

- [ ] Any public *method* without an actual external caller? → make it private.
- [ ] Any implementation-detail *type* (single-mapper DTO, internal parser node) leaking wider than the file/class that needs it? → scope it down. (Shared feature vocabulary types are fine within that feature; see §1.)
- [ ] Any import cycle between modules or packages? → merge, move code to its feature, or extract a third module only when the evidence supports it.
- [ ] Is this a broader refactor (module extraction, dependency-cycle work, compile-time improvement, or dependency/encapsulation measurement)? → use Scalpel before proposing structural moves.
- [ ] Does a feature have a one-way dependency flow (`http` → `domain` → `db`), with no lower layer importing a higher one or another feature’s internals? → move the dependency downward or make the cross-feature contract explicit (§3).
- [ ] Any new abstraction/interface introduced before a 3rd real occurrence, or whose interface is nearly as complex as its implementation? → inline it instead (§6).
- [ ] Are HTTP, domain, and DB models reused across boundaries merely to save a mapping? → give each layer the model it owns when their responsibilities differ (§3).
- [ ] Any raw `String`/`Int`/`UUID` used as an identifier that should be a newtype?
- [ ] Any new library added without a one-sentence justification over the stdlib or a simpler alternative?
- [ ] Any mutable state that escapes its function/module without a comment explaining why?
- [ ] Any test that bypasses the public API (reflection, visibility hacks, test-only constructors)?
- [ ] Any one conceptual change that would force edits across many files (change amplification)? → §6.
- [ ] Related files scattered across distant packages, or mutually-calling members scattered within a file (best effort — formatters win)? → §6.
- [ ] Any business rule or validation logic duplicated in two places? → §6.
- [ ] Any untrusted data reaching core logic unvalidated (nil/well-formedness checks repeated after the edge)? → §7.
- [ ] Any public mutable field, or getter exposing mutable internal state? → §1 encapsulation.
- [ ] Errors returned in a format wrong for the consumer (raw exception to a machine, error code to a human)? → §7.
- [ ] Any pair of artifacts hand-maintained in parallel (spec ↔ code, producer ↔ consumer) that could be codegen'd from one source of truth? → §8.
- [ ] Any runtime reflection / annotation scanning / auto-wiring where compile-time codegen or plain DI would do? → §8.
- [ ] Any unit test mocking code you own instead of exercising behavior with real values, a hand-written external-boundary fake, or real infra? → §10.
- [ ] Any test asserting implementation details (private methods, internal state, mock-call sequences)? → rewrite through the public API (§1, §10).
- [ ] Framework plumbing (controllers, DAO wrappers) covered by mocked slice tests instead of one composed integration test through the real wiring? → §10.
- [ ] (JVM) `module-info.java` exporting more than the module's intended API, or reflection frameworks breaking for lack of `opens`? → §9.

## Reference files

- `references/java-modules.md` — JPMS directive reference, feature-module mapping, incremental adoption, and gotchas (SKILL.md §9).
- `references/testing.md` — integration-first tests for feature layers, fakes, mutation testing, and property-based testing (SKILL.md §10).
- `references/scala.md`, `references/java.md`, `references/kotlin.md`, `references/typescript.md`, `references/python.md` — language-specific visibility, layout, value-type, and testing decisions; read only the language in scope.
- `references/build-tools.md` — Maven, Gradle, sbt, Mill, Deder, and workspace-package boundary guidance; read only when a build-module boundary is under consideration.

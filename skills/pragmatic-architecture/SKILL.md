---
name: pragmatic-architecture
description: Opinionated software design and architecture principles for writing, reviewing, and refactoring code in any language, inspired by grugbrain.dev and John Ousterhout's "A Philosophy of Software Design". Covers minimal API surface and encapsulation, cycle-free module boundaries, hexagonal/ports-and-adapters architecture, judicious newtypes, principle of least power, locality (things that change together live together), validating at the edges, and eliminating implicit dependencies (spec-driven codegen over runtime reflection). Use this whenever writing new code, designing a module/package/directory structure, starting a new feature or service, doing a code review, refactoring code where one change touches many files, or when the user asks "how should I structure this" — even if they don't explicitly mention architecture or design. Applies to backend and frontend code alike.
---

# Pragmatic Architecture

## Philosophy

Complexity is the enemy, not "bad code". Every principle below exists to keep complexity local and visible instead of letting it leak across boundaries. Three ideas underpin everything:

- **Deep modules, narrow interfaces** (Ousterhout): a module's value is (functionality it provides) / (interface it exposes). A module with a small interface hiding a lot of useful logic is good. A module with a big interface hiding almost nothing is not worth its own existence — it's just extra indirection.
- **Duplication is better than the wrong abstraction** (grug): two similar-looking pieces of code are cheap to keep separate. A shared abstraction that turns out wrong is expensive to unwind once three callers depend on it.
- **Locality** (grug): things that change together live together — see §6.

Everything below is meant to be *checkable*, not just aspirational. When reviewing code (your own or someone else's), run the checklist at the bottom.

---

## 1. Minimal API surface

Expose nothing the caller doesn't need. Default to the least visibility your language allows (`private` in Java/Kotlin/C#, no `export` in TS, module-private in Rust, nested `def` inside `def` in Scala when only used locally).

A member becomes public only when there is an actual caller outside the module *today* — not "might need it later." Speculative public API is speculative complexity someone else now has to think about.

**Testing must go through the public API, with no workarounds.** No `@VisibleForTesting`, no `package private[x]` escape hatches, no reflection to poke at internals, no test-only constructors that bypass invariants. If a behavior can't be verified through the public API, that's a signal the API is missing something real — fix the design, don't punch a hole in it for tests.

**Types (case classes/records) are not methods — don't apply the same restriction reflex to both.** A method is cheap to keep private; a type that represents a module's core vocabulary (domain entities, IDs, port signatures — `Order`, `OrderId`, a repository trait's parameter types) naturally flows through many files in that module, and *that's expected*, not a leak. Trying to scope it down to a single package inside the module just forces a duplicate/mapping type at every internal boundary it crosses — which is the premature-abstraction mistake (§6), not discipline. Let visibility track the type's *role*, not the tightest setting available:

- **Shared vocabulary of the module** (entities, value objects, ports) → public within the module. `public record OrderId(String value) {}` used by the repository, the service, and a mapper is correct, not a violation.
- **A true implementation detail** — a DTO only one mapper touches, an internal parser AST node, a helper struct grouping three values inside one function — scope it as tight as the language allows: a local/nested type, `private[thatpackage]` in Scala, or simply no `public` modifier in Java (package-private by default). It shouldn't outlive the file or class that needs it.

The place this restriction actually pays off is the **module boundary from §2** — the one enforced by the build tool. A `domain` module with a clean public surface of entities and ports, and every internal helper type scoped to its own file, is exactly what makes it trivial to extract as a library later: a consumer sees only the types meant for them, with no noise. So spend the visibility discipline at the module boundary, not on paperwork between packages that live inside the same module anyway.

**Encapsulation: expose behavior, not state.** A type keeps its fields private and enforces its invariants in its constructor/creator — no public mutable fields, no `getX()/setX()` pairs handing out internal state for callers to corrupt. If the outside can reach in and violate the invariant, the type has no invariant. For a domain entity that means `cancel(): Either[Error, Unit]`, not a public `var status` with a `setStatus`. Getters returning immutable snapshots are fine; getters exposing mutable internals (a live collection, a mutable child object) are a leak.

These visibility rules are encapsulation applied at three scopes: members (this section), state (above), and modules (§2). The same bar applies to tools, not just code: no serialization framework digging into private fields of domain types — another reason domain types stay free of serialization annotations (§3).

## 2. Module boundaries, no cycles

A "module" here means something enforced by the build tool: an sbt/Maven module, a Cargo crate, an npm workspace package — not just "a package that feels cohesive." Real build-tool modules make cycles impossible by construction, which is why they're worth the ceremony. Package-level organization *within* a module can still grow cycles, so it needs its own enforcement (see `references/enforcement.md` for per-language tools: ArchUnit, Scalafix rules, `cargo-modules`/`cargo-deny`, ESLint boundary plugins).

If you find yourself needing a cycle between two modules, that's not a tooling problem — it's a sign the boundary is drawn in the wrong place. Either merge the two modules, or extract a third one that both depend on.

## 3. Hexagonal architecture — prefer duplication over premature coupling

Structure code so the core logic doesn't know how it's invoked or what it talks to:

```
domain/       -- entities, value objects, ports (interfaces), pure business logic
adapters/
  db/          -- implements domain ports against a specific database
  http-in/     -- entry point: REST/GraphQL/CLI — calls domain through ports
  http-out/    -- outbound calls to third-party services (payment gateway, etc.)
composition/  -- wiring / dependency injection / main — the only place that knows about everything
```

The dependency arrow only ever points *toward* `domain/`, never away from it.

**What `domain/` may depend on:** the standard library, and pure utility/newtype libraries that do no I/O (e.g. a `NonEmptyList`, a validated-`Email` type, `Instant`). **What it must not depend on:** anything that does I/O (DB driver, HTTP client, message queue client), a serialization framework (no Circe/Jackson annotations on domain classes), or a concrete infra type in a signature (`java.sql.Connection`, a specific effect type). If the domain needs something from the outside world, it defines a trait/interface (a port) — an adapter implements it, and `composition/` wires the two together.

This costs you some duplication and boilerplate up front (a port trait plus its implementation, instead of calling the DB driver directly). That's the trade you're making on purpose: it stays trivial to swap the database, mock the port in a domain-level test, or later pull `domain/` out into its own library or module. See `references/examples.md` for concrete code across Scala/Java/Rust/TypeScript, and how this maps onto frontend code (React or HTMX).

## 4. Newtypes, judiciously

Wrap identifiers in newtypes so `OrderId` and `CustomerId` can't be swapped by accident — the compiler should catch that, not a runtime bug report. This one is close to free (opaque types / `case class` wrapper / tuple struct) and worth doing by default for any ID.

Don't reinvent `Email`, `Money`, `NonEmptyString`, etc. from scratch for the tenth time. Check if the ecosystem already has a well-tested validated type for it (e.g. via `refined`, a vetted utility library) before writing your own — but reach for that library only if it's already earning its keep elsewhere in the project; don't pull in a whole validation library just to wrap one field. See §5.

## 5. Principle of least power

For every library or abstraction you're about to introduce, be able to state in one sentence why the weaker, more standard tool isn't enough. If you can't, use the weaker tool.

- **Concurrency on the JVM:** reach for virtual threads (Project Loom) first. If you need structured concurrency, scoped resource cleanup, or retry/timeout combinators, *research the [ox](https://github.com/softwaremill/ox) library first* — it gives you that on top of virtual threads without pulling in a full effect system. Only reach for cats-effect/ZIO when you have a concrete need ox doesn't cover (e.g. deep monadic composition across a large codebase that's already committed to that style).
- **Frontend:** don't default to a React SPA + API layer. If the feature is mostly server-rendered CRUD/forms/dashboards without heavy client-side interactivity, HTMX (or plain server-rendered templates) is less overall complexity. Reach for React/a SPA framework when there's real client-side state or interactivity that justifies it (drag-and-drop, live canvas, complex cross-field validation, offline support).
- **Parsing:** regex before a parser-combinator library, unless the grammar is genuinely context-sensitive or recursive.
- **Errors:** exceptions or a plain `Either`/`Result` before a bespoke error-ADT hierarchy with a dozen cases.

**Prefer immutability by default** — `val` over `var`, immutable collections, `case class`/record types. Mutable state is fine only when:
1. it's local to a function and never escapes (e.g. accumulating in a loop before returning an immutable result), or
2. an algorithm or hot path genuinely needs it for performance — and then say so with a comment, so a future reader knows it's a deliberate trade-off and not an oversight.

Mutable state that leaks outside a function or module is exactly the kind of premature complexity this whole skill exists to avoid (see §8) — it creates hidden coupling between whoever reads and whoever writes it.

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
- **Null-object pattern when it makes sense.** When "nothing" has a natural identity — a no-op notifier, a null logger, an empty collection instead of `null` — return the object that does nothing instead of `Option`/`null` and spreading `if (maybe)` branches through every caller. Don't force it: use `Option`/`Result` when "absent" and "present but inert" genuinely differ.
- **Report errors in the consumer's format, not yours.** A machine consumer (API client, log parser, monitoring) needs structured, machine-readable errors: JSON problem details, an error code, an XML fault. A human consumer needs a plain-language message and a suggestion of what to do. One error type forced into one format is usually wrong for one of them. The core raises domain errors; translating them into the consumer's shape is an adapter concern (§3).

## 8. No implicit dependencies

A dependency is *implicit* when two things must change together but nothing in the toolchain knows it. The compiler checks types and imports; it does not check that your SQL string matches your DTO, your JSON schema matches your parser, or your message producer matches its consumer. Those break at runtime, usually in production.

- **Make the pair compiler-checked — prefer codegen over hand-maintained parallelism.** Two artifacts that must stay in sync (OpenAPI spec ↔ client and server, protobuf/Avro schema ↔ producer and consumer, JSON schema ↔ parser, DB schema ↔ mapping layer) should be generated from one source of truth: a spec checked into one place, with build-time codegen producing both sides. Codegen fails loudly at build time; reflection fails at startup, or silently at 3 a.m.
- **Async boundaries are the classic case.** Producer and consumer codebases meet only at a queue (RabbitMQ, Kafka). Version the schema, generate types on both sides from it, and fail the build when they diverge — the queue no longer hides the coupling.
- **Avoid runtime reflection where the compiler could do the job.** Annotation scanning, auto-DI containers that "discover" beans, dynamic `Class.forName` wiring — all break `grep`, break "find usages", and move every error to startup. The same convenience via codegen keeps the code traceable and the failure early. Reflection that can't be avoided is a red flag, not a feature.
- **Prefer plain DI construction.** Constructor injection wired in one `composition/` file (§3) is grep-able, debuggable, and shows the object graph explicitly. Reach for a DI framework only when the wiring itself becomes unmanageable, and prefer compile-time resolution (e.g. Scala 3 givens) over runtime containers when you do.

Two definitions that must change together (a constant copied into two files, a flag and its string name) — merge them, or generate one from the other.

---

## When these rules don't apply

Throwaway prototypes and spikes are exempt — the point of a spike is to answer a question fast, not to be maintained. But mark it clearly (a `// SPIKE, not production` comment, a branch name, a note in the PR) so nobody mistakes it for the intended design later. If a spike is about to be kept, that's the moment to apply this skill to it before it ships.

---

## Review checklist

Before calling a change done, check:

- [ ] Any public *method* without an actual external caller? → make it private.
- [ ] Any implementation-detail *type* (single-mapper DTO, internal parser node) leaking wider than the file/class that needs it? → scope it down. (Shared module vocabulary types — entities, IDs, ports — are fine public within the module; see §1.)
- [ ] Any import cycle between modules or packages? → merge or extract a third module.
- [ ] Any new abstraction/interface introduced before a 3rd real occurrence, or whose interface is nearly as complex as its implementation? → inline it instead (§6).
- [ ] Does `domain/` import anything that does I/O, a serialization framework, or a concrete infra type? → move it behind a port.
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

## Reference files

- `references/examples.md` — full ports-and-adapters example with real code across Scala, Java, Rust, and TypeScript, plus how the same shape maps onto frontend code (React vs. HTMX).
- `references/enforcement.md` — per-language/build-tool commands and libraries for enforcing no-cycles and visibility rules (ArchUnit, Scalafix, `cargo-modules`/`cargo-deny`, ESLint boundary plugins, jdeps/jpackage).

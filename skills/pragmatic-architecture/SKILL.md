---
name: pragmatic-architecture
description: Opinionated software design and architecture principles for writing, reviewing, and refactoring code in any language, inspired by grugbrain.dev and John Ousterhout's "A Philosophy of Software Design". Covers minimal API surface, cycle-free module boundaries, hexagonal/ports-and-adapters architecture, avoiding premature abstraction, judicious newtypes, and the principle of least power. Use this whenever writing new code, designing a module/package/directory structure, starting a new feature or service, doing a code review, or when the user asks "how should I structure this" — even if they don't explicitly mention architecture or design. Applies to backend and frontend code alike.
---

# Pragmatic Architecture

## Philosophy

Complexity is the enemy, not "bad code". Every principle below exists to keep complexity local and visible instead of letting it leak across boundaries. Two ideas underpin everything:

- **Deep modules, narrow interfaces** (Ousterhout): a module's value is (functionality it provides) / (interface it exposes). A module with a small interface hiding a lot of useful logic is good. A module with a big interface hiding almost nothing is not worth its own existence — it's just extra indirection.
- **Duplication is better than the wrong abstraction** (grug): two similar-looking pieces of code are cheap to keep separate. A shared abstraction that turns out wrong is expensive to unwind once three callers depend on it.

Everything below is meant to be *checkable*, not just aspirational. When reviewing code (your own or someone else's), run the checklist at the bottom.

---

## 1. Minimal API surface

Expose nothing the caller doesn't need. Default to the least visibility your language allows (`private` in Java/Kotlin/C#, no `export` in TS, module-private in Rust, nested `def` inside `def` in Scala when only used locally).

A member becomes public only when there is an actual caller outside the module *today* — not "might need it later." Speculative public API is speculative complexity someone else now has to think about.

**Testing must go through the public API, with no workarounds.** No `@VisibleForTesting`, no `package private[x]` escape hatches, no reflection to poke at internals, no test-only constructors that bypass invariants. If a behavior can't be verified through the public API, that's a signal the API is missing something real — fix the design, don't punch a hole in it for tests.

**Types (case classes/records) are not methods — don't apply the same restriction reflex to both.** A method is cheap to keep private; a type that represents a module's core vocabulary (domain entities, IDs, port signatures — `Order`, `OrderId`, a repository trait's parameter types) naturally flows through many files in that module, and *that's expected*, not a leak. Trying to scope it down to a single package inside the module just forces a duplicate/mapping type at every internal boundary it crosses — which is the premature-abstraction mistake from §3, not discipline. Let visibility track the type's *role*, not the tightest setting available:

- **Shared vocabulary of the module** (entities, value objects, ports) → public within the module. `public record OrderId(String value) {}` used by the repository, the service, and a mapper is correct, not a violation.
- **A true implementation detail** — a DTO only one mapper touches, an internal parser AST node, a helper struct grouping three values inside one function — scope it as tight as the language allows: a local/nested type, `private[thatpackage]` in Scala, or simply no `public` modifier in Java (package-private by default). It shouldn't outlive the file or class that needs it.

The place this restriction actually pays off is the **module boundary from §2** — the one enforced by the build tool. A `domain` module with a clean public surface of entities and ports, and every internal helper type scoped to its own file, is exactly what makes it trivial to extract as a library later: a consumer sees only the types meant for them, with no noise. So spend the visibility discipline at the module boundary, not on paperwork between packages that live inside the same module anyway.

## 2. Module boundaries, no cycles

A "module" here means something enforced by the build tool: an sbt/Maven module, a Cargo crate, an npm workspace package — not just "a package that feels cohesive." Real build-tool modules make cycles impossible by construction, which is why they're worth the ceremony. Package-level organization *within* a module can still grow cycles, so it needs its own enforcement (see `references/enforcement.md` for per-language tools: ArchUnit, Scalafix rules, `cargo-modules`/`cargo-deny`, ESLint boundary plugins).

If you find yourself needing a cycle between two modules, that's not a tooling problem — it's a sign the boundary is drawn in the wrong place. Either merge the two modules, or extract a third one that both depend on.

## 3. Don't abstract prematurely

Wait for the third occurrence before extracting a shared abstraction (rule of three). Two similar things are a coincidence you can live with; three is a pattern worth naming.

Before extracting an interface or abstraction, apply the deep-module test: is the resulting interface meaningfully simpler than just inlining the logic? If the interface is nearly as complicated as the implementation it hides, it's a shallow module — it adds a name to learn without hiding real complexity. Prefer no abstraction over a shallow one.

## 4. Hexagonal architecture — prefer duplication over premature coupling

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

## 5. Newtypes, judiciously

Wrap identifiers in newtypes so `OrderId` and `CustomerId` can't be swapped by accident — the compiler should catch that, not a runtime bug report. This one is close to free (opaque types / `case class` wrapper / tuple struct) and worth doing by default for any ID.

Don't reinvent `Email`, `Money`, `NonEmptyString`, etc. from scratch for the tenth time. Check if the ecosystem already has a well-tested validated type for it (e.g. via `refined`, a vetted utility library) before writing your own — but reach for that library only if it's already earning its keep elsewhere in the project; don't pull in a whole validation library just to wrap one field. See §6.

## 6. Principle of least power

For every library or abstraction you're about to introduce, be able to state in one sentence why the weaker, more standard tool isn't enough. If you can't, use the weaker tool.

- **Concurrency on the JVM:** reach for virtual threads (Project Loom) first. If you need structured concurrency, scoped resource cleanup, or retry/timeout combinators, *research the [ox](https://github.com/softwaremill/ox) library first* — it gives you that on top of virtual threads without pulling in a full effect system. Only reach for cats-effect/ZIO when you have a concrete need ox doesn't cover (e.g. deep monadic composition across a large codebase that's already committed to that style).
- **Frontend:** don't default to a React SPA + API layer. If the feature is mostly server-rendered CRUD/forms/dashboards without heavy client-side interactivity, HTMX (or plain server-rendered templates) is less overall complexity. Reach for React/a SPA framework when there's real client-side state or interactivity that justifies it (drag-and-drop, live canvas, complex cross-field validation, offline support).
- **Parsing:** regex before a parser-combinator library, unless the grammar is genuinely context-sensitive or recursive.
- **Errors:** exceptions or a plain `Either`/`Result` before a bespoke error-ADT hierarchy with a dozen cases.

**Prefer immutability by default** — `val` over `var`, immutable collections, `case class`/record types. Mutable state is fine only when:
1. it's local to a function and never escapes (e.g. accumulating in a loop before returning an immutable result), or
2. an algorithm or hot path genuinely needs it for performance — and then say so with a comment, so a future reader knows it's a deliberate trade-off and not an oversight.

Mutable state that leaks outside a function or module is exactly the kind of premature complexity this whole skill exists to avoid (see §3) — it creates hidden coupling between whoever reads and whoever writes it.

---

## When these rules don't apply

Throwaway prototypes and spikes are exempt — the point of a spike is to answer a question fast, not to be maintained. But mark it clearly (a `// SPIKE, not production` comment, a branch name, a note in the PR) so nobody mistakes it for the intended design later. If a spike is about to be kept, that's the moment to apply this skill to it before it ships.

---

## Review checklist

Before calling a change done, check:

- [ ] Any public *method* without an actual external caller? → make it private.
- [ ] Any implementation-detail *type* (single-mapper DTO, internal parser node) leaking wider than the file/class that needs it? → scope it down. (Shared module vocabulary types — entities, IDs, ports — are fine public within the module; see §1.)
- [ ] Any import cycle between modules or packages? → merge or extract a third module.
- [ ] Any new abstraction/interface introduced before a 3rd real occurrence, or whose interface is nearly as complex as its implementation? → inline it instead.
- [ ] Does `domain/` import anything that does I/O, a serialization framework, or a concrete infra type? → move it behind a port.
- [ ] Any raw `String`/`Int`/`UUID` used as an identifier that should be a newtype?
- [ ] Any new library added without a one-sentence justification over the stdlib or a simpler alternative?
- [ ] Any mutable state that escapes its function/module without a comment explaining why?
- [ ] Any test that bypasses the public API (reflection, visibility hacks, test-only constructors)?

## Reference files

- `references/examples.md` — full ports-and-adapters example with real code across Scala, Java, Rust, and TypeScript, plus how the same shape maps onto frontend code (React vs. HTMX).
- `references/enforcement.md` — per-language/build-tool commands and libraries for enforcing no-cycles and visibility rules (ArchUnit, Scalafix, `cargo-modules`/`cargo-deny`, ESLint boundary plugins, jdeps/jpackage).

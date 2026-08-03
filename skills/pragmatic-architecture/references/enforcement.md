# Enforcement: making the rules mechanical, not aspirational

Build-tool module boundaries (sbt/Maven/Gradle modules, Cargo crates, npm/pnpm workspace packages) already prevent cycles *between* modules by construction — that's the strongest lever, use it first (SKILL.md §2). Everything below is for enforcing boundaries *within* a module (package-level) or double-checking the module graph itself.

## Scala / sbt

- **Cycle detection between packages:** [ArchUnit](https://www.archunit.org/) has a JVM-language-agnostic API and works fine from Scala; write a rule like `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAPackage("..db..")`.
- **Scalafix** custom rules can flag forbidden imports (e.g. ban `java.sql.*` imports inside `domain/`) and run in CI as a lint step.
- **wartremover** can enforce some visibility/style rules (e.g. no `var`, no `Any`) alongside the above.
- sbt module dependency cycles are impossible by default; if you want to *visualize* the graph, `sbt-dependency-graph` or a custom Tarjan-SCC tool over `libraryDependencies` output helps spot near-misses (e.g. two modules that keep almost needing each other).

## Java / Kotlin (Maven, Gradle)

- **ArchUnit** is the standard tool here too — same package-dependency rules, plus rules like "classes in `..domain..` must not be annotated with `@Entity`/`@JsonProperty`" to catch serialization/persistence leakage into domain types.
- **jdeps** (bundled with the JDK) can print the module dependency graph for compiled `.class`/`.jar` output — useful as a sanity check that a `domain.jar` really has zero references to a DB driver package.
- **jpackage** / the Java Platform Module System (`module-info.java`) can enforce visibility at the JVM level (`exports` only what's meant to be public) if you want compiler-enforced rather than lint-enforced boundaries — heavier to set up but the strongest guarantee.

## Rust

- **cargo-modules**: visualizes and can assert on the module/crate dependency graph (`cargo modules dependencies`).
- **cargo-deny**: can ban specific crates from being a dependency of a specific crate (e.g. deny `rusqlite` as a dependency of the `domain` crate) via `deny.toml` — this runs in CI and fails the build on violation, which is stronger than a lint warning.
- Rust's `pub`/`pub(crate)`/`pub(super)` visibility levels map directly onto §1 (minimal API surface) — default to the narrowest one that compiles, widen only when there's a real external caller.

## TypeScript / JavaScript (npm, pnpm, Nx, Turborepo)

- **eslint-plugin-boundaries** or **eslint-plugin-import**'s `no-cycle` rule: enforce that `domain/` files never import from `adapters/` or `ui/`, and catch import cycles generally.
- **Nx** (if the repo uses it) has built-in module boundary rules (`@nx/enforce-module-boundaries`) driven by tags — tag `domain` packages and forbid them from depending on tags like `infra`.
- For npm/pnpm workspaces without Nx, a plain `madge --circular` run in CI catches cycles across the whole dependency graph, framework-agnostic.

## General / cross-language

- Whatever the tool, the useful CI check is the same shape: **"fail the build if package X imports anything from package/crate/module Y."** Pick the lightest tool in the language's own ecosystem that can express that rule — don't reach for a heavyweight architecture-governance product if a five-line ESLint or ArchUnit rule does the job (this is §6, principle of least power, applied to the tooling itself).

# Build-tool boundaries

Use build-tool modules only for a boundary that needs independent ownership, release, dependency control, or build isolation. A feature directory is sufficient for ordinary local organization. Do not make `http`, `domain`, and `db` separate modules merely to enforce a small feature's layers.

| Tool | Express an acyclic boundary | Practical rule |
|---|---|---|
| Maven | Reactor modules with explicit `<dependency>` entries | A parent aggregates; feature modules depend only on declared feature APIs or shared stable modules. |
| Gradle | Included projects with `implementation(project(\":orders\"))` | Keep `api` narrow; avoid `api` dependencies unless consumers need their types. |
| sbt | `lazy val orders = project.dependsOn(...)` | Use `dependsOn` only in one direction; keep shared code as a small leaf project. |
| Mill | `object orders extends ScalaModule` and `moduleDeps` | Make module dependencies point one way; compile/test modules independently when the boundary pays off. |
| Deder | Declare project modules in the Deder project configuration | Keep module definitions explicit and verify their targets independently; follow the repository's existing Deder configuration for inter-module dependency syntax. |
| npm/pnpm | Workspace packages with explicit `package.json` dependencies | Export a feature API package only when another workspace needs it; do not bypass it with source-path imports. |

The exact configuration syntax is version-sensitive. When editing a build definition, consult the tool's current documentation and the repository's existing build conventions.

Use Scalpel when the request is broader refactoring: deciding extraction boundaries, finding cycles, assessing compile-time impact, or measuring dependency and encapsulation metrics.

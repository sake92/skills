---
name: pragmatic-architecture
description: Always load and use this skill when implementing or reviewing production application or library code to inspect callers and narrow a public API, hide mutable state or broad collection and query escape hatches, centralize a business invariant shared across entry points, untangle package or module dependencies, or decide whether model boundaries, wrapper types, and new abstractions pay for themselves. Use it even for small requests that do not mention architecture, and keep changes proportional. Do not use for test-only, documentation-only or copy-only, formatting or renaming, dependency update or report, or process-lifecycle tasks.
---

# Pragmatic Architecture

Complete the requested work with small interfaces, local complexity, and the least machinery that preserves its contracts. Keep architectural changes proportional to their concrete benefit, compatibility cost, migration risk, and maintenance effort.

## Work in this order

1. **Honor the user's stack.** When asked to use a framework, use its native APIs, utilities, test harnesses, clients, and startup helpers. Preserve the application's default handlers and middleware. An installed lower-level library is not a reason to bypass the requested framework. Adapt through its supported extension points first.
2. **Inspect the affected boundary.** Read the surrounding implementation, the complete public surface of affected types, and their callers. Include framework entry points and external consumers. Before editing, identify the applicable contract protections below and the signatures or response fields they require changing. They are part of the requested operation, not optional cleanup.
3. **Anchor behavior before implementation.** When the request supplies exact outputs, first capture those examples in regression tests with their expected values copied verbatim. Specific examples clarify general descriptions. Keep those expectations unchanged; do not replace them with the output your implementation happens to produce.
4. **Implement locally.** Include the callers, tests, fixtures, or configuration required to finish the change. Preserve working behavior and leave unrelated debt alone. A small repair should not become a broad architectural migration.
5. **Review the diff and run the relevant checks.** Verify both the requested behavior and the affected contracts. Inspect every added or replaced expression: split mixed processing stages into named local values and move every interpolated conditional into a local value, including nested branches and singular/plural wording. Passing behavior tests does not satisfy these expression requirements. Remove incidental cleanup and explain material compatibility or migration constraints.

## Protect the affected contracts

### Storage and public interfaces

When adding or changing a type's storage operations, make its mutable backing state private, including constructor fields. Search for callers of its public members. Narrow or remove unused generic collection/query accessors to that same mutable state; hiding a field while leaving another public path to it does not protect the state.

Preserve caller-required immutable views and operations. Known internal callers can establish that a prototype helper is unused; an external library API may need compatibility protection. An unrelated unused member alone is not a reason for cleanup. Internal convenience alone is not a reason to add a public API. Contained mutation is fine; prefer behavior preserving invariants over exposing mutable collections.

### Types after a demonstrated mix-up

If a defect shows that different concepts sharing a primitive representation were interchanged, prevent the same mistaken call on the affected internal operations. Use inexpensive opaque types, newtypes, or nominal wrappers through **both service and repository parameters and related cache keys**. Do not keep raw overloads or convert back to primitives between these collaborators.

Convert inside the adapters reading or writing primitive wire/storage values. Existing URLs and persisted records can retain their formats and primitive fields. Verify each affected service/repository API, including concrete implementations: correct typed calls compile; swapped and raw calls do not. Keep the migration local. A runtime test and argument reorder alone leave the demonstrated error legal. For unrelated primitives, weigh a real invariant against language friction before adding wrappers.

Converting primitive arguments at the start of a service method leaves its callers able to repeat the mix-up. Change the service's parameter types too, and move those conversions out to its HTTP, job, or other boundary callers. Preserving wire formats does not require preserving an unsafe internal signature.

### Public responses from private records

When changing a public response, inspect its consumer and source record. If the source includes credentials or private administrative/personal data, construct an explicit public projection using the existing codec, even when the request only mentions adding a field. Keep private persistence and export contracts intact.

Test the exact permitted serialized field set, or explicitly exclude every sensitive source field. Checking only one or two secrets can miss another leaking through the same boundary. Sharing a simple model is fine when the contracts agree; avoid copies and mappings without a meaningful benefit.

## Keep rules and dependencies understandable

Centralize genuinely shared business invariants where operations enforce them. Similar-looking code in independent features need not share an abstraction; extract for shared meaning or simpler callers, without a fixed occurrence threshold.

Receive business collaborators through constructors or the project's established injection mechanism. Wire them at startup, assembly factories, or framework configuration. Controllers and jobs should receive a shared operation rather than each constructing a service from lower-level dependencies. Ordinary values and private implementation helpers can be constructed locally; no new interface or injection framework is required.

Keep code that changes together close together. Feature-first organization is useful for new code; preserve an established layout when changing it adds more disruption than value. Separate transport, business, and persistence responsibilities when their contracts or invariants warrant it. A directory/package is often enough; add modules or stronger compiler/import enforcement only for demonstrated ownership, release, dependency, or build needs.

Prefer acyclic dependencies with a clear direction and the least disruptive useful repair. Use dependency analysis such as Codeps when structural uncertainty warrants it; ordinary edits do not require a reporting workflow. For contracts that must stay synchronized, choose a shared source, generation, or focused compatibility checks according to the actual failure risk.

## Choose the simplest adequate implementation

Within the requested stack, first reuse existing project code and conventions, then standard-library/platform features and installed dependencies. Add dependencies, abstractions, configuration, or extension points for a current requirement and concrete benefit. Replacing a working framework merely for a preference rarely pays off. Keeping the current design is a valid outcome.

Validate untrusted input at entry points and business invariants where operations occur. Reuse established guarantees instead of adding repeated defensive checks; authorization and changing state may need checking again. Preserve validation, error handling, security, accessibility, and data integrity. Represent meaningful absence explicitly and return errors the consumer can use.

## Apply expression style only to necessary edits

These expression preferences apply to new or necessarily changed bodies. **Do not refactor working expressions elsewhere, even in a touched method or file, to match them.** Local changes to visibility, signatures, or projections required by the contract protections above remain part of the task.

- Split long pipelines mixing selection, transformation, ordering, and rendering into meaningful named stages. Pull nested pipelines out of argument lists. Short idioms such as `value.trim.toLowerCase` are fine.
- Compute conditional values before string interpolation. Never put `if` expressions inside `${...}`.
- Keep intermediate results local and name what they represent. Do not introduce helpers or layers solely to split an expression.

```scala
val active = records.filter(_.active)
val names = active.map(_.name)
val orderedNames = names.sorted
val text = orderedNames.mkString(", ")

val mode = if (enabled) "on" else "off"
val status = s"Mode: $mode"
```

Preserve surrounding formatting. Run formatters or reformat existing code only when explicitly asked or required by CI/Git hooks; scope formatting to the relevant files where those checks allow it.

In reviews, prioritize concrete defects and costly coupling. Distinguish required fixes from optional design preferences; a preference alone is not a defect.

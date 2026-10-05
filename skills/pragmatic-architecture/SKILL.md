---
name: pragmatic-architecture
description: Practical software design guidance for writing, reviewing, and refactoring code. Balance encapsulation, locality, clear dependencies, and behavioral testing against implementation effort, migration risk, and existing project conventions.
---

# Pragmatic Architecture

Keep complexity local and interfaces small. Prefer modules that hide useful complexity over layers that mostly pass calls through.

These are design preferences, not acceptance criteria. Apply them where they make the requested work simpler, safer, or easier to change.

## Balance benefit against effort

Understand the affected code and its conventions before proposing a different design. Weigh the concrete benefit against implementation effort, compatibility, migration risk, and ongoing maintenance.

In legacy projects, work within the existing architecture unless it prevents a sound solution. A small fix should not become a 10,000-line refactor to satisfy a preference. Improve nearby design when it helps the task; leave unrelated debt alone. Broader changes make sense when they resolve demonstrated problems or the user requests them.

When a tradeoff matters, briefly explain the benefit and cost. Keeping the current design is a valid outcome. Flexibility about architecture does not excuse correctness, security, or data-integrity problems.

## 1. Small interfaces and encapsulation

Expose what callers need and keep implementation details local. Shared feature types can naturally span layers; narrowing visibility should not force needless copies or mappings. Account for framework entry points and external consumers before treating an API as unused.

Prefer behavior that preserves invariants over exposing mutable internals. Immutable values are a useful default; contained mutation is fine when it makes the implementation clearer or faster.

When changing an existing type, inspect its public members as part of the change. Keep mutable backing state private, and remove or narrow broad helpers that have no demonstrated caller; internal convenience does not justify a public API. Preserve members required by known callers or compatibility constraints.

## 2. Clear dependency boundaries

Prefer acyclic dependencies with an understandable direction. A cycle may suggest merging responsibilities or moving shared logic, but choose the least disruptive useful fix.

A directory or package often provides enough organization. Add build modules or stronger enforcement when independent ownership, releases, dependency control, or build performance justify them. Use dependency analysis, such as Codeps when available, when structural uncertainty warrants it; ordinary edits do not require a new reporting workflow.

## 3. Organize around features

Keep code that changes together close together. Feature-first organization is a useful default for new code; follow an established layout when changing it would add more disruption than value. Introduce HTTP, business, and persistence layers only where their responsibilities warrant separation.

Separate transport, domain, and database models when their contracts, invariants, or rate of change differ. Sharing a simple model can be reasonable when those concerns align. Avoid mappings and annotation bans that buy no meaningful independence.

## 4. Types that prevent real mistakes

Consider distinct ID types and validated values when they prevent likely mix-ups or encode useful invariants. Weigh that safety against language friction and migration cost. Reuse suitable existing types; not every primitive needs a wrapper or a new dependency.

## 5. Use the simplest adequate tool

Prefer existing code, standard libraries, and familiar platform features when they meet the need. Add abstractions or dependencies for concrete benefits, accounting for what the project already uses. Replacing a working framework merely to match this preference rarely pays off.

## 6. Locality over premature abstraction

Keep related behavior easy to find and change. Repeated edits across unrelated files can signal a misplaced responsibility or unnecessary indirection.

Similar-looking code need not share an abstraction. Extract when it captures a shared rule or meaningfully simplifies callers, without a fixed occurrence threshold. Keep genuinely shared business rules consistent; avoid coupling independent features just to remove textual duplication.

## 7. Validate at trust boundaries

Validate untrusted input where it enters and preserve business invariants where operations occur. Let established guarantees reduce repeated defensive checks, while recognizing that authorization and changing state may need checking again.

Shape output deliberately and report errors in a form the consumer can use. Represent absence explicitly when it matters; use empty values or no-op behavior only when they preserve the intended meaning.

## 8. Make dependencies visible

Prefer explicit construction and traceable contracts. When artifacts must stay in sync, consider a shared source, code generation, or focused compatibility tests according to the cost and failure risk. Existing dependency injection, reflection, or framework conventions can be reasonable; replace them only for a concrete improvement.

## 9. Stronger enforcement when it pays off

Compiler-enforced boundaries, import rules, and Java modules can protect important architectural constraints. Adopt them when the protection justifies setup and maintenance, especially around stable library APIs or recurring dependency problems. They are optional tools, not prerequisites for a well-designed feature.

## 10. Tests that earn their cost

Test observable behavior with the cheapest checks that provide sufficient confidence. Prefer integration tests for wiring, persistence, and boundary behavior, and focused unit tests for rules, parsers, and algorithms. Use the production database engine when dialect or transaction behavior matters; use fakes or mocks where real dependencies are costly, unsafe, or impractical.

Prefer tests through stable interfaces without forcing a production API redesign solely for testing. In legacy code, characterization tests and existing test seams can support safe incremental changes. Add property or mutation testing when the logic and risk justify it, not as a routine requirement.

In reviews, prioritize concrete defects and costly coupling. Distinguish necessary fixes from optional improvements; a departure from these preferences is not itself a defect.

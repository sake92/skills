---
name: pragmatic-testing
description: 'Choose and implement behavior-focused tests for features, bug fixes, and refactors. Use when adding or reviewing tests, reproducing bugs, choosing test boundaries or doubles, testing APIs and persistence, or improving unreliable tests. Prefer realistic integration tests and reuse existing test services, fixtures, and API clients.'
---

# Pragmatic testing

Choose tests by failures caught and maintenance cost, without prescribed ratios or coverage targets.

## 1. Plan behaviors before infrastructure

Read the public contract. Before coding, write a compact table in task notes (a file if no task-list tool exists): **behavior | expected response and state | test name/status**. Include success, validation, missing resources, and documented dependency failures. Failed writes need preserved state and no partial effects; invalid input does not exercise dependency failure.

Prefer API-level tests for workflows with little standalone logic. Use focused unit/property tests for meaningful algorithms, calculations, branching, or state transitions; skip trivial getters/setters. Use the production database engine and migrations for database behavior, and the actual process for CLI/startup contracts.

## 2. Reuse a working setup

Inspect existing fixtures, clients, and framework support. Before adding infrastructure, check the [preferred tools](references/preferred-tools.md) and briefly verify suitability, maintenance, license, and compatible versions through Context7, official docs, or GitHub. Prefer established OSS requiring no paid plan, vendor account, or license key.

For containerized dependencies, default to an existing Testcontainers module. Let it own startup, readiness, allocated endpoints, and cleanup. Keep the production SDK and internal collaborators real. Custom protocol servers or SDK implementations usually recreate work a maintained emulator already handles; require a demonstrated gap before building one.

For an external dependency, start from the project's actual client and test setup. If no suitable setup exists, research a maintained emulator or Testcontainers module for that dependency, verify the smallest required operation in isolation, then adapt that fixture into the application tests. Use the relevant notes in [external-services](references/external-services.md); do not assume a particular service, image, framework, or language.

## 3. Implement the checklist

Before the first API test, build request/response helpers with the existing serializer and typed decoder (or JSON parser). Assert decoded values; regex extraction, substring checks, and raw JSON equality couple tests to formatting rather than behavior.

Get one behavior green through the application and real collaborators, then complete the checklist. Keep scenarios independently runnable. Use focused boundary mocks for failures inaccessible through the maintained service.

For failure tests, establish valid starting state, trigger the intended fault, and observe the same system's resulting state. Verify the failing operation was reached; an earlier rejection tests a different path. Preserve the application and state while switching the dependency into failure mode.

Compare meaningful fields and independently persisted effects. Choose distinct, non-default values so swapped fields, hard-coded defaults, and shared round-trip bugs become visible. Keep protocol assertions where clients hide the contract. Ask which plausible bug each important assertion catches; if unclear, temporarily introduce that bug, verify a test-body failure, then revert it.

For bug fixes, demonstrate the intended failure before fixing it, then run the same test green. Setup/compilation errors are not regression evidence. Establish passing tests before refactoring.

## 4. Verify the deliverable

Deliver service lifecycle in the tests or a checked-in runner. Run the documented command from a fresh checkout without manually prestarted services. Use pinned dependencies, isolated data/ports, bounded waits, and owned cleanup; share expensive startup without sharing scenario state.

Reconcile the table with actual test names and assertions. A green suite does not cover a missing row: implement it or record a concrete blocker. Report the command/result, covered and blocked behaviors, real/doubled dependencies, and emulator limits. Unavailable infrastructure means unverified integration tests.

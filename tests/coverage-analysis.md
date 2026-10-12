# Evaluation coverage gaps

Inventory checked on 2026-10-12, before qualifying the first CLI case. This describes checked-in evaluation coverage, not overall skill effectiveness.

| Priority | Skill or concern | Missing coverage |
| --- | --- | --- |
| 1 | CLI design | No evaluation cases. Output contracts, exit codes, redirected output, non-interactive behavior, help, configuration precedence, and secret handling are untested. |
| 2 | Twelve-Factor app | Runtime bindings covers config and backing services. Codebase, dependencies, build/release/run, processes, packaged port binding, concurrency, disposability, dev/prod parity, logs, and admin processes lack coverage. |
| 3 | Process hygiene | Worker and subprocess examples exist. Service startup, SIGTERM, and graceful shutdown remain unevaluated. |
| 4 | All skills | Fresh held-out confirmation and repeated matched runs are missing. Published results are selected single-run examples, with adaptive development in several cases. |
| 5 | Triggering | Only pragmatic-architecture has a checked-in trigger evaluation set. A definition does not demonstrate successful triggering. |

CLI design is the next priority because its guidance has no local output evidence. Twelve-Factor has the broadest remaining scope gap: ten factors lack coverage, and backing-service checks tied in the existing case.

## First CLI task

Add JSON output to an existing inventory export command used by a nightly job. The existing command has human table output and optional file output. This is a normal feature request with compatibility constraints, rather than a prompt listing CLI best practices.

A separate configuration-precedence task would cover another part of the skill later. One JSON-export case cannot establish whole-skill coverage.

New cases require passing visible starter tests and a protected grader verified against both the starter and a corrected reference. Run the no-skill baseline first; any observed perfect baseline rejects the case for qualification. Freeze the prompt, fixture, and grader before skill changes. A with-skill smoke must score strictly higher and preserve behavior. Rejected cases and development history remain under ignored `tests/tmp/`.

## Evidence

- [CLI design status](../skills/cli-design/README.md)
- [Twelve-Factor factor coverage](../skills/twelve-factor-app/README.md)
- [Process hygiene coverage and limitations](../skills/process-hygiene/README.md)
- [Codeps examples and limitations](../skills/codeps/README.md)
- [Architecture examples and limitations](../skills/pragmatic-architecture/README.md)
- [Evaluation workflow](README.md)

The first qualified CLI development case is now [inventory JSON export](../skills/cli-design/benchmark-results/json-export.md). Its best observed 8/9 result leaves an error-context failure; the broader gaps above remain.

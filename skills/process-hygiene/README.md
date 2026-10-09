# Process hygiene maintenance

Scope: lifecycle and ownership of threads, tasks, executors, and subprocesses. Terminal UX and deployment architecture live in the sibling skills.

This revision is guidance, not an evaluated improvement. Upstream results do not prove effectiveness for this skill or Scala/JVM subprocess behavior.

Sources informing the revision:

- [pproenca's Go process skill](https://github.com/pproenca/dot-skills/tree/master/skills/.experimental/go-process-cli), MIT: process groups, graceful cancellation before force-kill, cancellable waits, reaping, and entrypoint-owned exit. Restated as language-independent principles without copying code. No corresponding agent-output benchmark was found.
- [Chris Banes's Kotlin concurrency skill](https://github.com/chrisbanes/skills/tree/main/skills/kotlin-concurrency-and-flow), Apache-2.0: preserve APIs exposing cancellation/result/failure ownership; avoid unnecessary background wrappers. Restated in original wording. Upstream [evals](https://github.com/chrisbanes/skills/blob/main/evals/README.md) cover Kotlin concerns, not this skill's subprocess mechanics.
- Nathaniel J. Smith's structured-concurrency argument and Candea/Fox's crash-only design inform the existing references.

Future protected evals should cover pipe pressure, descendant cleanup, cancellation during startup/work, executor termination, and a no-change control for a correct ownership API. Start fixtures green and grade public behavior.

# Process hygiene maintenance

Scope: lifecycle and ownership of threads, tasks, executors, and subprocesses. Terminal UX and deployment architecture live in the sibling skills.

The [evaluation fixtures](../../tests/process-hygiene/README.md) exercise build-task stdio and worker timeout cleanup. Cancellation, thread/executor ownership and service lifecycle remain unevaluated. Upstream results do not prove effectiveness for this skill.

| Case | With skill | Without skill |
| --- | ---: | ---: |
| [Build-task stdio](benchmark-results/build-task-stdio.md) | 8/8 | 7/8 |
| [Worker timeout development revision](benchmark-results/worker-timeout-development.md) | 6/7 | 4/7 |

The stdio pair favored with-skill on live prompt flushing. The baseline-first worker development revision favored with-skill on retaining helpers after their parent exited, but still exposed a shutdown race. These single pairs do not establish reliable skill effectiveness; the adaptive development result needs held-out confirmation.

Sources informing the revision:

- [pproenca's Go process skill](https://github.com/pproenca/dot-skills/tree/master/skills/.experimental/go-process-cli), MIT: process groups, graceful cancellation before force-kill, cancellable waits, reaping, and entrypoint-owned exit. Restated as language-independent principles without copying code. No corresponding agent-output benchmark was found.
- [Chris Banes's Kotlin concurrency skill](https://github.com/chrisbanes/skills/tree/main/skills/kotlin-concurrency-and-flow), Apache-2.0: preserve APIs exposing cancellation/result/failure ownership; avoid unnecessary background wrappers. Restated in original wording. Upstream [evals](https://github.com/chrisbanes/skills/blob/main/evals/README.md) cover Kotlin concerns, not this skill's subprocess mechanics.
- Nathaniel J. Smith's structured-concurrency argument and Candea/Fox's crash-only design inform the existing references.

Future cases should cover descendant cleanup, cancellation during startup/work, executor termination, and a no-change control for a correct ownership API. Start fixtures green and grade public behavior. Keep raw eval artifacts under ignored `tests/tmp/`; reports contain summaries and small excerpts only.

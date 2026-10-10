# Process hygiene maintenance

Scope: lifecycle and ownership of threads, tasks, executors, and subprocesses. Terminal UX and deployment architecture live in the sibling skills.

The [build-task stdio fixture](../../tests/process-hygiene/README.md) exercises a configured process runner, byte-stream bridge, task results/events and launcher adapter. Descendant cleanup, cancellation, thread/executor ownership and service lifecycle remain unevaluated. Upstream results do not prove effectiveness for this skill.

| Case | With skill | Without skill |
| --- | ---: | ---: |
| [Build-task stdio](benchmark-results/build-task-stdio.md) | 8/8 | 7/8 |

One matched pair favored the with-skill output on live prompt flushing. This is initial evidence, not a stable estimate of skill effectiveness.

Sources informing the revision:

- [pproenca's Go process skill](https://github.com/pproenca/dot-skills/tree/master/skills/.experimental/go-process-cli), MIT: process groups, graceful cancellation before force-kill, cancellable waits, reaping, and entrypoint-owned exit. Restated as language-independent principles without copying code. No corresponding agent-output benchmark was found.
- [Chris Banes's Kotlin concurrency skill](https://github.com/chrisbanes/skills/tree/main/skills/kotlin-concurrency-and-flow), Apache-2.0: preserve APIs exposing cancellation/result/failure ownership; avoid unnecessary background wrappers. Restated in original wording. Upstream [evals](https://github.com/chrisbanes/skills/blob/main/evals/README.md) cover Kotlin concerns, not this skill's subprocess mechanics.
- Nathaniel J. Smith's structured-concurrency argument and Candea/Fox's crash-only design inform the existing references.

Future cases should cover descendant cleanup, cancellation during startup/work, executor termination, and a no-change control for a correct ownership API. Start fixtures green and grade public behavior. Keep raw eval artifacts under ignored `tests/tmp/`; reports contain summaries and small excerpts only.

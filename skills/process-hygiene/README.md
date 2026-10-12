# Process hygiene maintenance

Scope: lifecycle and ownership of threads, tasks, executors, and subprocesses. Terminal UX and deployment architecture live in the sibling skills.

The [evaluation fixtures](../../tests/process-hygiene/README.md) exercise build-task stdio, worker timeout cleanup, cancellation during work and cleanup, and thread/executor ownership. Service lifecycle remains unevaluated. Upstream results do not prove effectiveness for this skill.

| Case | With skill | Without skill | Score gain (pp) |
| --- | ---: | ---: | ---: |
| [Build-task stdio](benchmark-results/build-task-stdio.md) | 8/8 | 7/8 | +12.5 |
| [Worker timeout development revision](benchmark-results/worker-timeout-development.md) | 6/7 | 4/7 | +28.6 |
| [Worker cancellation development](benchmark-results/worker-cancellation.md) | 6/6 | 5/6 | +16.7 |
| [Parallel build development](benchmark-results/parallel-build.md) | 8/8 | 7/8 | +12.5 |

Score gain is the difference in pass rates, in percentage points.

These are single-run examples; several followed adaptive skill revisions. Case reports describe current results and limitations. Held-out confirmation remains outstanding.

Sources informing the revision:

- [pproenca's Go process skill](https://github.com/pproenca/dot-skills/tree/master/skills/.experimental/go-process-cli), MIT: process groups, graceful cancellation before force-kill, cancellable waits, reaping, and entrypoint-owned exit. Restated as language-independent principles without copying code. No corresponding agent-output benchmark was found.
- [Chris Banes's Kotlin concurrency skill](https://github.com/chrisbanes/skills/tree/main/skills/kotlin-concurrency-and-flow), Apache-2.0: preserve APIs exposing cancellation/result/failure ownership; avoid unnecessary background wrappers. Restated in original wording. Upstream [evals](https://github.com/chrisbanes/skills/blob/main/evals/README.md) cover Kotlin concerns, not this skill's subprocess mechanics.
- Nathaniel J. Smith's structured-concurrency argument and Candea/Fox's crash-only design inform the existing references.

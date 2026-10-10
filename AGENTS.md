# Agent Instructions

## Skill maintenance

- Follow the [Agent Skills specification](https://agentskills.io/home) for skill structure and metadata.
- Before changing evaluations, read [Evaluating skills](https://agentskills.io/skill-creation/evaluating-skills).
- Keep evaluated agent instructions in `skills/<name>/SKILL.md` and maintainer-only context in `skills/<name>/README.md`.
- Keep protected graders outside candidate workspaces and do not disclose their assertions in task prompts.
- Start bug-fix fixtures with passing visible tests; validate protected checks against the defective starter and a corrected reference.
- Run and inspect the plain-model `without-skill` baseline first. An accepted evaluation case MUST NOT have a 100% no-skill grade in any observed run. If the baseline scores 100%, reject that case for qualification and develop a harder realistic task before testing skill improvements.
- Freeze the prompt, fixture, and protected grader before improving the skill. Keep model, reasoning, tools, permissions, and budget matched across configurations; never weaken the baseline to manufacture uplift.
- After baseline qualification, run a single `with-skill` smoke check before spending tokens on a full comparison. With-skill MUST score strictly higher than no-skill and preserve existing behavior; target 100% with skill. Ties and regressions fail the acceptance gate.
- Preserve and report rejected attempts, including perfect baselines, ties, and regressions, as development history; do not count them as accepted effectiveness cases or hide their grades. Disclose adaptive development and confirm improvements on fresh held-out cases before claiming reliable effectiveness.
- For twelve-factor-app, track all twelve canonical factors separately: codebase, dependencies, config, backing services, build/release/run, processes, port binding, concurrency, disposability, dev/prod parity, logs, and admin processes. Report coverage and matched results per factor; an aggregate score cannot stand in for missing factors.
- Do not commit generated workspaces under `tests/tmp/`.
- Commit example-run summaries and small code/diff excerpts only; do not commit raw candidate patches, generated grade reports, or evaluated skill snapshots under `benchmark-results/`.

## Commands

| Task | Command |
| --- | --- |
| Format eval Scala | `scala-cli fmt tests/run-eval.scala tests/eval-runner tests/pragmatic-architecture/customer-repository/grading --server=false` |
| Smoke-test with skill | `scala tests/run-eval.scala --server=false -- output --configuration with-skill --runs 1` |
| Compare with/without skill | `scala tests/run-eval.scala --server=false -- output --runs 1` |
| Check held-out triggering | `scala tests/run-eval.scala --server=false -- trigger --split validation --runs 1` |

## Repository references

| Need | File |
| --- | --- |
| Evaluation workflow | `tests/README.md` |
| Pragmatic-architecture results | `skills/pragmatic-architecture/README.md` |
| Eval definitions | `skills/pragmatic-architecture/evals/` |

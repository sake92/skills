# Agent Instructions

## Skill maintenance

- Follow the [Agent Skills specification](https://agentskills.io/home) for skill structure and metadata.
- Before changing evaluations, read [Evaluating skills](https://agentskills.io/skill-creation/evaluating-skills).
- Keep evaluated agent instructions in `skills/<name>/SKILL.md` and maintainer-only context in `skills/<name>/README.md`.
- Keep protected graders outside candidate workspaces and do not disclose their assertions in task prompts.
- Run a single `with-skill` smoke check before spending tokens on a full comparison.
- Do not commit generated workspaces under `tests/tmp/`.

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

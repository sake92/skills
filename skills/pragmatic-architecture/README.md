# Pragmatic Architecture

A compact [architecture skill](SKILL.md) for smaller interfaces, clearer boundaries, and types that prevent real mistakes. Keep improvements proportional to the task.

## Benchmark examples

One paired run per fixture. Details, code snippets, and limitations are in [benchmark-results/](benchmark-results/README.md).

These four example pairs showed higher protected/judged scores with skill. The ID result followed adaptive tuning. Skill versions differ across cases, so these rows are not one frozen-suite effectiveness estimate.

| Fixture | With skill | Without skill | Score gain (pp) |
| --- | ---: | ---: | ---: |
| [Customer repository](benchmark-results/customer-repository.md) | 10/10 | 7/10 | +30.0 |
| [Repository with callers](benchmark-results/repository-with-callers.md) | 8/8 | 5/8 | +37.5 |
| [Teacher/course IDs](benchmark-results/teacher-course-ids.md) | 8/8 | 6/8 | +25.0 |
| [Public profile DTO](benchmark-results/public-profile-dto.md) | 9/9 | 8/9 | +11.1 |

Score gain is the difference in pass rates, in percentage points.

## Current development check

On 2026-10-18, the revised skill passed all six registered cases in one full
with-skill run: customer repository 10/10, repository with callers 8/8,
teacher/course IDs 8/8, public profile 9/9, invitation preview 6/6, and workspace
tags with mandatory CI formatting 7/7. Total: **48/48**.

The expression cases had earlier no-skill baselines of 4/6 and 6/7. Their checks
cover behavior, preservation of working code, new expression structure, and
the CI formatting exception. The final run used GPT-6 Luna, low reasoning,
read/bash/edit/write tools, two concurrent candidates, and a ten-minute
subprocess timeout. The evaluated skill's SHA-256 was
`9ef8e596de44e36288d19b7e1bcbdf4586a7794e25745e707afeafb69f6ceda3`.

This was adaptively developed against these cases; it is a passing development
run, not held-out evidence of reliable effectiveness. The framework-native
tools instruction has no dedicated behavioral evaluation yet. Development
records and the review viewer remain in ignored `tests/tmp/`.

[Evaluation workflow](../../tests/README.md)

# Pragmatic Architecture

A compact [architecture skill](SKILL.md) for smaller interfaces, clearer boundaries, and types that prevent real mistakes. Keep improvements proportional to the task.

## Benchmark examples

One paired run per fixture. Details, code snippets, and limitations are in [benchmark-results/](benchmark-results/README.md).

| Fixture | With skill | Without skill |
| --- | ---: | ---: |
| [Customer repository](benchmark-results/customer-repository.md) | 10/10 | 7/10 |
| [Repository with callers](benchmark-results/repository-with-callers.md) | 8/8 | 5/8 |
| [Legacy discount fix](benchmark-results/legacy-discount-fix.md) | 8/8 | 8/8 |
| [Booking overlap rule](benchmark-results/booking-overlap-rule.md) | 12/12 | 12/12 |
| [Teacher/course IDs](benchmark-results/teacher-course-ids.md) | 8/8 | 6/8 |
| [Public profile DTO](benchmark-results/public-profile-dto.md) | 9/9 | 8/9 |

[Evaluation workflow](../../tests/README.md) · [Coverage analysis](../../analysis.md)

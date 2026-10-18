# Empty-tests S3 document API: development example

The generalized skill rerun on 2026-10-18 scored **9/9 with skill**, against the qualified **7/9 without-skill** baseline. This evaluates skill revision `01dcbbf` after merging main in `8a003de`. The starter is a working Sharaf application with the AWS SDK, documented S3 behavior, and an empty test directory. The prompt asks for tests without naming tools or test categories.

| Protected check | Without skill | With skill |
| --- | --- | --- |
| Unchanged production source/dependencies; passing tests | Pass | Pass |
| Incorrect shared object key | Detected | Detected |
| Incorrect HTTP replacement field binding | Missed | Detected |
| Corrupted uploaded bytes | Detected | Detected |
| Lost stored content type | Missed | Detected |
| Failed upload publishes metadata | Detected | Detected |
| Missing backing object returns 503 instead of 404 | Detected | Detected |
| Invalid upload creates a document | Detected | Detected |
| Harmless JSON formatting/reordering | Pass | Pass |

The baseline used direct service tests and a custom SDK double. The fresh with-skill agent wrote five HTTP tests using the production SDK and Floci through Testcontainers. It used Tupson to serialize requests and decode responses and independently read stored bytes and content type. A narrow proxy delegates to the real SDK and can reject writes after the same application has stored a document. The tests exercise rejected creation and replacement, then check existing metadata and content:

```scala
failWrites.set(true)
// POST and PUT each assert 503 here.
failWrites.set(false)
assertEquals(documents(request(base, "GET", "/documents")), List(original))
```

## Settings and limits

Pi/OpenAI gpt-6-luna, high reasoning, read/bash/edit/write tools, ten-minute candidate limit, deterministic grading without a model judge. The prompt, starter, and protected checks were frozen before skill revisions. The baseline ran first. Deterministic replay uses a twenty-minute limit; the unchanged baseline was replayed through the published grader under that same limit and remained 7/9. No evaluated candidate was repaired by the maintainer.

| Candidate cost, excluding protected grading | Without skill | With skill |
| --- | ---: | ---: |
| Execution time | 77.2 s | 360.6 s |
| Input/output tokens, excluding cached input | 15,459 | 43,965 |

Protected grading completed in 452.8 seconds. Clean replay and the JSON-formatting positive control passed. The merged runner confirmed a successful read of the skill document. No task-specific instructions were added for this rerun.

An earlier revision explicitly directed Scala/S3 work to the bundled Floci example and also scored 9/9. That instruction was removed before this fresh run. The optional tool shortlist and example remain available, so this verifies the generalized workflow with those resources, not unaided discovery of an unfamiliar service.

This is a selected, adaptively developed comparison after multiple revisions, not a held-out result or reliability estimate. Earlier development attempts did not consistently improve over baseline; fresh held-out confirmation remains required. Raw attempts, scores, transcripts, and snapshots stay in ignored local notes.

Nine passing checks do not imply exhaustive coverage. The fresh suite tests rejected replacement, but the nine-check grader does not independently measure that coverage. It starts a container per test and does not produce the requested working coverage table. The result therefore supports the selected behavior checks and emulator reuse, not perfect instruction following or attribution to the table instruction alone. It also costs more time and tokens than the baseline.

The protected handwritten reference passed 9/9 and the empty starter 0/9 before candidate comparison. Tests replay on trusted production copies; compilation/startup errors do not count as mutation detection. The fixture, protected reference, and repeatable commands are in [tests/pragmatic-testing](../../../tests/pragmatic-testing/README.md).

# Empty-tests S3 document API: development example

The selected development comparison improved from **7/9 without skill to 9/9 with skill**. The starter is a working Sharaf application with the AWS SDK, documented S3 behavior, and an empty test directory. The prompt asks for tests without naming tools or test categories.

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

The baseline used direct service tests and a custom SDK double. The with-skill agent used HTTP tests, the production SDK, and Floci through Testcontainers. It read the supplied setup example, used typed Tupson response decoding, and independently read stored bytes and content type. A missing test bucket caused an actual S3 write rejection; the test then checked metadata through the same application:

```scala
assertEquals(rejected.statusCode(), 503)
assertEquals(decode[List[Document]](send(client, app.baseUrl, "GET", "/documents", "").body()), Nil)
```

## Settings and limits

Pi/OpenAI gpt-6-luna, high reasoning, read/bash/edit/write tools, ten-minute candidate limit, deterministic grading without a model judge. The prompt, starter, and protected checks were frozen before skill revisions. The baseline ran first. Deterministic replay uses a twenty-minute limit; the unchanged baseline was replayed through the published grader under that same limit and remained 7/9. No evaluated candidate was repaired by the maintainer.

| Candidate cost, excluding protected grading | Without skill | With skill |
| --- | ---: | ---: |
| Execution time | 77.2 s | 340.6 s |
| Input/output tokens, excluding cached input | 15,459 | 35,573 |

This is a selected, adaptively developed comparison after multiple revisions, not a held-out result or reliability estimate. Earlier development attempts did not consistently improve over baseline; fresh held-out confirmation remains required. Raw attempts, scores, transcripts, and snapshots stay in ignored local notes.

Nine passing checks do not imply exhaustive coverage. The resulting suite omits rejection during replacement, hand-builds request JSON, starts a container per test, and does not produce the requested working coverage table. The result therefore supports the selected behavior checks and emulator reuse, not perfect instruction following or attribution to the table instruction alone. It also costs more time and tokens than the baseline.

The protected handwritten reference passed 9/9 and the empty starter 0/9 before candidate comparison. Tests replay on trusted production copies; compilation/startup errors do not count as mutation detection. The fixture, protected reference, and repeatable commands are in [tests/pragmatic-testing](../../../tests/pragmatic-testing/README.md).

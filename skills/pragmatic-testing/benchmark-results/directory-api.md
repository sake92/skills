# Empty-tests directory API: development example

A working Sharaf/SQLite application starts with no test code, test dependencies, fixtures or test clients. The prompt asks for automated tests without suggesting boundaries or tools. It leaves application source and existing runtime dependencies unchanged.

The selected matched pair improved from **5/8 (62.5%) without skill to 7/8 (87.5%) with skill**. Of six injected defects, the suites detected three and five respectively. Both passed on the unchanged app and with harmless JSON property/whitespace changes.

## What the agent chose

Both runs independently chose MUnit HTTP tests against the real application and temporary SQLite files. Neither added direct repository, accessor or isolated model tests. The first test writes are preserved in local transcripts. This pair demonstrates additional assertion coverage, not a change from unit tests to integration tests.

The baseline used three scenarios. The with-skill output combined its checks into one lifecycle journey, reducing failure isolation. Both used Java's HTTP client and added a JSON test dependency rather than using the application's Tupson codec. Neither added explicit request deadlines. These are observed tradeoffs, not scored preferences about test count or library names.

## Protected checks

| Check | Without skill | With skill |
| --- | --- | --- |
| Source/runtime dependencies preserved; tests pass | Pass | Pass |
| Wrong HTTP update binding | Detected | Detected |
| Private fields in lookup response | Missed | Detected |
| Invalid update changes stored data | Missed | Detected |
| Stale revision accepted | Detected | Detected |
| Failed update commits an email-history event | Missed | Missed |
| Successful update does not persist | Detected | Detected |
| Harmless JSON reordering/formatting | Pass | Pass |

The baseline checks the lookup status but does not assert its complete response:

```scala
assertEquals(request(app, "GET", s"/users/$id").statusCode(), 200)
```

The with-skill test compares the reopened lookup with a prior public update response and rejects the known private-note value:

```scala
assertEquals(json(persisted), created._2)
assert(!persisted.body().contains("staff-only"))
```

It also sends an invalid update before a valid revision-0 update; the side-effect mutant makes the subsequent valid update fail. That detects the corruption indirectly. The remaining gap is important: neither suite attempts a PUT that conflicts with another user's email and checks that the history insertion was rolled back.

## Setup and limits

Pi/OpenAI gpt-6-luna, low reasoning, read/bash/edit/write tools, one candidate per configuration, ten-minute subprocess cap. Prompt, starter, skill and protected assertions were matched; no skill edits occurred during the pair. Without-skill ran first; the with-skill trace confirms it read SKILL.md. Source/runtime inputs remained unchanged and submitted tests passed.

| Candidate cost | Without skill | With skill |
| --- | ---: | ---: |
| Execution, excluding protected grading | 92.9 s | 103.8 s |
| Input/output tokens, excluding cached input | 17,837 | 28,485 |

The protected handwritten reference passed 8/8; the empty starter received 0/8. The grader's test-execution parser initially missed MUnit 1.1's success format; that initial grade is invalid. The parser was corrected, the reference revalidated, and the unchanged baseline regraded to 5/8 before with-skill execution. Both scores above use the same corrected grader.

This is one adaptively developed example, not a reliable effectiveness estimate. Fresh held-out confirmation remains outstanding. SQLite is the actual application engine; this case does not establish Testcontainers, external fake-service discovery, or broader framework coverage. Fixtures and commands are in [tests/pragmatic-testing](../../../tests/pragmatic-testing/README.md). Raw candidate code, transcripts, grades, and skill snapshots remain ignored under tests/tmp/.

## Generalized skill rerun, 2026-10-18

After removing the service-specific instruction in revision `01dcbbf` and merging main in `8a003de`, a fresh with-skill run again scored **7/8**, against the qualified **5/8** baseline. It used the same low-reasoning model, tools, ten-minute candidate limit, prompt, starter, and protected assertions. The merged runner snapshots grader sources and their shared imports; deterministic grading had a twenty-minute cap and completed in 184.2 seconds.

The new candidate chose one HTTP/SQLite journey with MUnit and ujson. It passed clean replay and the JSON positive control and caught the same five of six injected defects. Failed-update rollback remains missing. Generation took 205.9 seconds and 36,016 input/output tokens. This is another run on the familiar development case, not held-out confirmation. No candidate code was repaired by the maintainer.

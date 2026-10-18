# Pragmatic testing evaluation

The directory-api case starts with a working Sharaf/SQLite application and an empty test directory (Git placeholder only). It supplies no test dependencies, fixtures, test clients, example tests, or suggested test categories. The agent chooses its initial suite from the application and its documented contract.

## Run a matched example

From the repository root, run the baseline first:

```bash
scala tests/run-eval.scala --server=false -- output \
  --skill-path skills/pragmatic-testing \
  --fixture-path tests/pragmatic-testing \
  --case directory-api --configuration without-skill \
  --runs 1 --timeout-minutes 10 --skip-judge
```

Only after a baseline below 100%, run the same command with `--configuration with-skill`. Keep model, reasoning, tools and budget matched. The repository defaults are Pi/OpenAI gpt-6-luna at low reasoning. A perfect observed baseline rejects a case for qualification; ties and regressions fail the comparison gate.

## Grading boundary

Candidate workspaces receive only `directory-api/starter`. The prompt is in `directory-api/prompt.md`; grader assertions and the handwritten reference remain outside candidate workspaces. The generic report-producing MUnit helper is reused from cli-design.

The grader runs submitted tests on trusted application copies. It checks clean execution and preserved source/runtime dependencies, six independent defects in HTTP binding, projection, validation, revision checks, transaction rollback and persistence, and a positive control that reorders JSON fields and changes whitespace. Empty suites receive no quality credit. Compiler/setup failures do not count as detecting a mutant. The initial grader supports MUnit and ScalaTest assertion output with the Scala CLI build; other frameworks need adapter validation before interpreting their scores.

Test boundaries are inspected separately from the score. API tests, real-database tests, focused unit tests, doubles and trivial accessor tests are described from the source and first-write transcript; test counts and library choices earn no points by themselves. A unit test is useful when it isolates meaningful logic.

The app compiles with no test framework selected:

```bash
scala-cli compile tests/pragmatic-testing/directory-api/starter --server=false
```

The protected handwritten reference can be run independently without adding it to the starter:

```bash
scala-cli test tests/pragmatic-testing/directory-api/starter \
  tests/pragmatic-testing/reference --server=false
```

The reference passed 8/8 protected checks; the empty starter received 0/8. Control reports and evaluated agent artifacts stay under ignored `tests/tmp/`. Published [example results](../../skills/pragmatic-testing/benchmark-results/directory-api.md) contain only a summary and small excerpts.

This case covers initial test selection, HTTP behavior and real SQLite semantics. It does not evaluate Postgres/Testcontainers, external-service-tool discovery, or process supervision. One adaptively developed matched pair is development evidence; fresh held-out confirmation remains outstanding.

## S3 document-library case

`document-library/starter` is a Sharaf application with AWS SDK v2 and an empty test directory. It supplies no test library, emulator setup, client wrapper, or example tests. Candidate workspaces receive only the starter. The protected grader and handwritten reference remain outside them.

Run the baseline first with explicit settings:

```bash
scala tests/run-eval.scala --server=false -- output \
  --skill-path skills/pragmatic-testing \
  --fixture-path tests/pragmatic-testing \
  --case document-library --configuration without-skill \
  --model gpt-6-luna --reasoning high --runs 1 \
  --timeout-minutes 10 --grading-timeout-minutes 20 --skip-judge
```

After a qualifying baseline below 100%, use the same command with `--configuration with-skill`. Docker must be available for emulator-based submissions. Keep candidate and grader budgets matched across configurations.

The nine checks cover clean execution, seven independent defects (storage key, HTTP field binding, binary content, content type, failed-upload metadata, missing-object status, validation), and compatible JSON formatting. The `grading/replays-submission` marker isolates candidate test dependencies from the outer grader. The adapter supports MUnit and ScalaTest; mixed frameworks require separate validation. The reference scored 9/9 and the empty starter 0/9. Failed replacement is a known coverage limit, not included in the nine checks.

Run the protected reference independently:

```bash
scala-cli test tests/pragmatic-testing/document-library/starter \
  tests/pragmatic-testing/document-library/reference --server=false
```

The [selected result](../../skills/pragmatic-testing/benchmark-results/document-library.md) is 7/9 → 9/9 after adaptive skill development. Fresh held-out confirmation is outstanding. Raw candidates and development history stay ignored under `tests/tmp/`.

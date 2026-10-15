# Pragmatic testing maintenance

Scope: selecting and implementing useful behavior tests for application features, regressions, and refactors. The skill favors realistic feature integration tests while retaining focused examples, property tests, and process/browser tests where their failure detection justifies the cost.

Reuse is part of the testing method: existing fixtures, fake services, emulators, container modules, maintained or generated API clients, and framework lifecycle support take precedence over custom test infrastructure when they fit the required behavior. Fidelity limits and protocol assertions remain explicit.

Agent instructions live in [SKILL.md](SKILL.md). CLI contracts remain in cli-design, lifecycle implementation in process-hygiene, and deployment configuration in twelve-factor-app.

## Evaluation status

The [empty-tests Sharaf directory example](benchmark-results/directory-api.md) scored **5/8 without skill → 7/8 with skill**. Both agents chose HTTP tests with real SQLite; the gain was in assertions, while transaction rollback coverage remained missing.

The [fixture and commands](../../tests/pragmatic-testing/README.md) start without test dependencies or scaffolding. This is one adaptively developed matched pair; fresh held-out confirmation remains outstanding. It does not establish reliable effectiveness or external-service/Testcontainers coverage.

## Sources

- [Kent C. Dodds: Write tests. Not too many. Mostly integration.](https://kentcdodds.com/blog/write-tests) — confidence, integration, maintenance cost, and reduced mocking. The skill applies those ideas across application types without prescribing a test-suite shape.
- [Testcontainers](https://testcontainers.com/) — disposable real dependencies for integration tests.
- [Testcontainers readiness](https://java.testcontainers.org/features/startup_and_waits/) — distinguish useful readiness from a running container.
- [Testcontainers lifecycle](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/) — supported lifecycle control and shared service startup.
- [Agent Skills specification](https://agentskills.io/specification) — skill structure and metadata.

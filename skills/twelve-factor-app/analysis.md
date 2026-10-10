# What the Twelve Factors mean for this skill

The [Twelve-Factor methodology](https://12factor.net/) describes how a service fits its development and execution environments. It concerns the codebase, dependencies, deployment configuration, resource bindings, releases, process formation, lifecycle, environment parity, event streams, and administrative work. It is broader than class design or distributed-system correctness.

Coverage and demonstrated skill benefit are different claims. A factor may already be satisfied by the repository or platform. A passing audit can establish that fact, but cannot establish that supplying the skill improves model output. A tie is evidence of adequacy on that case, not evidence of uplift. Each factor needs its own coverage status and results; a combined score can hide absent factors.

## The twelve factors

1. **Codebase.** Development, staging, and production come from one versioned application codebase. They may run different commits, tags, or branches. The methodology does not prescribe a branching strategy. For a service already in one Git repository, this is usually a setup/audit property with little useful corrective work. Shared application code is distributed as declared libraries rather than copied between independently maintained apps. [Source](https://12factor.net/codebase)

2. **Dependencies.** Everything needed to run is explicitly declared and isolated from accidental host installations. This includes libraries and tools invoked by the app, such as Graphviz. A clean machine should not succeed only because the developer previously installed an undeclared dependency. Repository manifests and clean-environment execution provide evidence. [Source](https://12factor.net/dependencies)

3. **Config.** Values that vary between deployments are outside application code; the original specifically favors environment variables. Settings are independent controls rather than hardcoded bundles selected by development/staging/production flags. Internal routes and component wiring that do not vary by deployment can remain in code. Framework profiles are not automatically defects, but environment-name switches selecting deployment credentials and endpoints conflict with this principle. [Source](https://12factor.net/config)

4. **Backing services.** Databases, caches, queues, SMTP, storage, and external APIs are attached resources. Moving from a local PostgreSQL instance to a hosted PostgreSQL instance changes connection configuration, not application code. A URL is the mechanism, not the entire principle. This does not require PostgreSQL and MySQL to be interchangeable. Configuration checks alone are insufficient if application wiring still chooses or ignores resources by deployment name. [Source](https://12factor.net/backing-services)

5. **Build, release, run.** The build produces an executable bundle. A release associates a build with deployment configuration and a unique identity. Run starts processes against that release. A configuration change creates a new release even if the build is unchanged. Existing releases are immutable. This often needs packaging and deployment artifacts rather than a class-level test. [Source](https://12factor.net/build-release-run)

6. **Processes.** Important state is outside process memory and local disk, so replacement or another instance can handle the next request. Sessions, unfinished jobs, and shared coordination cannot rely solely on a web replica. Temporary files for one operation are fine. Merely adding a database does not establish stateless process behavior. [Source](https://12factor.net/processes)

7. **Port binding.** The packaged service includes what it needs to serve requests and binds a port. It does not depend on an externally injected webserver to become a service. Routing from a public hostname belongs to the execution environment. Actual packaged-service execution provides evidence. [Source](https://12factor.net/port-binding)

8. **Concurrency.** Workloads are represented by independently scalable process types, such as web and worker. Internal threads are compatible with this model. Kubernetes can supervise the processes but is not required. Foreground execution, independent formation, and platform supervision matter more than the choice of orchestrator. [Source](https://12factor.net/concurrency)

9. **Disposability.** Processes start promptly, stop gracefully, and remain correct after sudden death. Shutdown stops intake and finishes or returns currently owned work; it need not empty the entire queue. Crash recovery cannot depend only on shutdown hooks. Container images alone do not establish this property. Service work recovery belongs here; thread and subprocess cleanup mechanics overlap with process-hygiene. [Source](https://12factor.net/disposability)

10. **Dev/prod parity.** Development exercises production-like operating conditions, especially the same backing-service types and versions. Different PostgreSQL endpoints are fine; SQLite locally and PostgreSQL in production can hide incompatibilities. The original also addresses short deployment delays and developer involvement in deployment. Unlike factor III, this concerns similarity of workflow and services rather than where configuration is stored. Architecture abstractions alone do not establish parity, and a coding benchmark cannot prove organizational practices. [Source](https://12factor.net/dev-prod-parity)

11. **Logs.** The original asks for prompt event output to stdout. The platform handles collection, routing, storage, and rotation. Application-managed logfiles conflict with this model; platform-managed files are fine. JSON, request IDs, redaction, and OpenTelemetry may be useful additional requirements, but none substitutes for the stream/platform boundary. [Source](https://12factor.net/logs)

12. **Admin processes.** Migrations, backfills, and maintenance ship with the app and run as one-off processes against the selected release, configuration, and dependency environment. A command in the same JAR using the existing configuration loader and DAOs is a natural JVM implementation. The same JAR and DAO classes are implementation choices, not independent canonical requirements. [Source](https://12factor.net/admin-processes)

## Where an evaluation can demonstrate benefit

Configuration, stateless process behavior, disposability, and administrative tasks offer strong corrective scenarios. Backing-service attachment needs real wiring behavior; build/release/run needs a deployment workflow. Dependencies, port binding, process formation, and logging also admit executable checks, although a capable baseline may already satisfy them.

Codebase provenance and the organizational parts of dev/prod parity need audit evidence and explicit limits. They should not become invented coding tasks merely to force a score difference. All twelve remain in scope, with already-satisfied, untested, tied, improved, and regressed outcomes distinguished.

The existing local evaluation scored 8/11 with the skill and 8/11 without it. Its two small modules do not demonstrate uplift or full Twelve-Factor coverage. Their validation, URL-shape, and structured-log checks include application-contract requirements beyond the canonical factors. The [earlier comparison](benchmark-results/comparison.md) remains historical evidence, rather than a certification of the skill.

## Initial repository choice

The first new fixture is a small Scala service adaptation of `sharaf-api-starter`'s configuration boundary, application wiring, and service startup responsibilities. The source starter already has environment-driven database configuration; this fixture does not claim that the source repository has the injected defects.

The adaptation uses an HTTP directory as its backing service so actual network binding can be observed without replacing PostgreSQL semantics with a mock database. It starts with passing visible tests and intentionally defective deployment configuration. Factors III and IV are the initial targets. HTTP serving is supporting infrastructure, not new evidence of factor VII or all twelve factors.

The [first frozen pair](benchmark-results/runtime-bindings.md) scored 8/8 with the unchanged skill against 7/8 without. Configuration handoff separated the candidates; attached-resource checks tied. This establishes one observed task-level improvement, with costs and limitations in the case report. It does not establish stable effectiveness or independent improvement for factor IV.

| Factor | Current new-suite evidence |
| --- | --- |
| I. Codebase | Audit outstanding. |
| II. Dependencies | Clean-environment evaluation outstanding. |
| III. Config | Six protected checks; one pair 6/6 with skill versus 5/6 without, including additional gateway contract checks. |
| IV. Backing services | Two protected HTTP checks; tied 2/2, supporting coverage without skill uplift. |
| V. Build, release, run | Deployment-workflow evaluation outstanding. |
| VI. Processes | Independent state/replacement evaluation outstanding; old session case is non-qualifying history. |
| VII. Port binding | Packaged-service evaluation outstanding. |
| VIII. Concurrency | Independent process-formation evaluation outstanding. |
| IX. Disposability | Service shutdown/crash-recovery evaluation outstanding. |
| X. Dev/prod parity | Service parity evaluation and organizational audit outstanding. |
| XI. Logs | Platform-stream evaluation outstanding; old JSON collector checks are not canonical coverage. |
| XII. Admin processes | Release/config-compatible one-off task evaluation outstanding. |

---
name: twelve-factor-app
description: 'Twelve-Factor principles for deployed services: runtime configuration, attached backing services, stateless instances, immutable releases, process types, dev/prod parity, platform logging, and one-off admin tasks. Use when building or reviewing deployment behavior or diagnosing restart, scaling, configuration, and environment differences. For task/thread/subprocess ownership use process-hygiene; for terminal interfaces use cli-design.'
---

# Twelve-Factor applications

Apply the [Twelve-Factor methodology](https://12factor.net/) where its service deployment model fits. Follow the platform contract; do not impose service conventions on desktop programs, static frontends, or local CLI state.

1. Establish the runtime, platform, process types, persistent data, and deployment contract.
2. Trace the reported failure across configuration, restart, scale-out, and dependency boundaries.
3. Make a focused change preserving application behavior and compatibility.
4. Verify affected behavior with realistic inputs and independent process instances where relevant.

## Configuration and resources

- Load deployment-varying settings outside the build using environment variables or platform configuration/secrets. Preserve platform-owned names such as PORT.
- Validate required settings at startup; name missing/invalid settings and fail before work. Build typed configuration once and inject it rather than reading the environment deep in business code.
- Document settings with safe examples. Keep real credentials out of source, arguments, logs, and config dumps. Use platform secret managers or mounted files where available.
- Bind databases, caches, queues, and other services through configuration/wiring. Avoid choosing endpoints by development/staging/production names.
- Verify the application semantics when replacing backing services.

## Processes, releases, and parity

- Keep one owned codebase, declared isolated dependencies, and repeatable builds. Monorepos may contain independently deployed services.
- Separate build, release configuration, and run. Promote the same immutable artifact between environments where supported.
- Store restart-critical state in durable backing services. Memory and ephemeral disk may cache data, but must not be the sole source of sessions, jobs, or coordination shared by replicas.
- Use distinct process types when workloads need different scaling/lifetimes. Do not schedule singleton jobs inside every web replica without coordination.
- Follow platform networking, readiness, startup, and grace-period contracts. Use process-hygiene for ownership/cleanup mechanics.
- Match development/test backing-service semantics to production where they affect behavior. Use the existing local-service mechanism; containers are one option. Deliberate substitutes need compatibility tests.

## Logs and admin tasks

- Emit logs to platform-captured process streams. The platform owns routing, storage, and rotation.
- Use structured records for filtering, configurable severity, and request/job identifiers where available. Redact credentials and sensitive payloads.
- Run migrations/backfills/maintenance as one-off tasks using the application's release, config, and wiring, with appropriate idempotence or transactional recovery.

## Checklist

- Can operators discover settings, and does invalid configuration fail at startup?
- Can another environment use the same artifact with different resource bindings?
- Do restarted/concurrent instances observe the same durable state?
- Are caches rebuildable and singleton jobs coordinated or separately owned?
- Do development/test services exercise production semantics that matter?
- Can the platform capture useful redacted logs and run admin tasks with the same wiring?

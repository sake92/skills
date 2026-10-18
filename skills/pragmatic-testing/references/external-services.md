# Reproducible external-service setup

## Dependency-specific setup

First identify the dependency boundary and the operations the contract needs. Search the repository for an existing fixture, client, generated client, container module, compose service, or test lifecycle helper. Reuse it when it provides the required behavior.

If the project has no usable setup, research a maintained OSS emulator or Testcontainers module for the actual dependency. Check official documentation and upstream release/license/archival status, then run a minimal isolated smoke test for the exact operation needed (including important error behavior). Pin the versions that passed. Keep the smoke test separate from application scenarios; it proves setup, not application behavior.

Adapt the verified fixture into the application tests using the production client. Let the fixture own readiness, dynamically allocated endpoints, credentials, and cleanup. Close the application/client before the fixture. Share expensive startup only when scenario data remains isolated. Record emulator fidelity limits and any behavior that still needs a focused boundary double.

## Other services

Use the [shortlist](preferred-tools.md) as leads, then check the maintained module's current example for the dependency you actually have. Reuse the production client, generated clients, and existing framework fixtures. Confirm the operations you need; emulators do not prove full production-service compatibility.

A brief discovery pass can use Context7, official docs, or GitHub:

```bash
npx ctx7@latest library "<dependency>" "local emulator testcontainers client setup"
npx ctx7@latest docs "<resolved-library-id>" "test setup lifecycle compatible versions"
gh api repos/<owner>/<repo> --jq '{archived, license: .license.spdx_id}'
```

Check image/module releases, maintenance, licensing, and local account/key requirements. If Docker works but a container library reports no usable environment, inspect client/daemon compatibility before replacing the setup. A generic HTTP mock fits controlled responses; it does not automatically implement a stateful domain service.

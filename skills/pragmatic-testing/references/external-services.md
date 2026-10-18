# Reproducible external-service setup

## Scala/JVM S3

Copy [FlociS3.test.scala](../assets/FlociS3.test.scala) into an empty directory and run:

```bash
scala-cli test FlociS3.test.scala --server=false
```

Prerequisites: Scala CLI, a working Docker daemon (`docker info`), and access to download pinned artifacts/images. No AWS account, credentials, manually started emulator, or reserved host port is needed. The module provides dummy credentials and the allocated endpoint. Testcontainers handles readiness and container cleanup; scoped resources close the SDK before the container, including when the test body fails.

The example uses Floci image 2.3.0, its Testcontainers 2.x module 2.16.1, AWS SDK 2.42.29, MUnit 1.3.6, and Scala 3.9.0. [Upstream setup](https://floci.io/floci/testcontainers/java/) documents the module and SDK configuration. Keep existing application Scala/SDK versions when adapting; add only test dependencies and check compatibility. `.withDockerSocket(false)` avoids mounting the Docker socket into this S3-only service.

Adapt `FlociS3Fixture.withS3` into your tests: construct the application with the supplied client and unique bucket, exercise its public API, and independently observe storage with the SDK. Close the application inside the callback, before the fixture closes the client. The included storage round trip proves setup only; it does not replace application behavior tests. For many scenarios, use the test framework's suite fixture to share container startup while retaining isolated data.

## Other services

Use the [shortlist](preferred-tools.md), then check the maintained module's current example. Reuse the production client, generated clients, and existing framework fixtures. Confirm the operations you need; emulators do not prove full production-service compatibility.

A brief discovery pass can use Context7, official docs, or GitHub:

```bash
npx ctx7@latest library "<dependency>" "local emulator testcontainers client setup"
npx ctx7@latest docs "<resolved-library-id>" "test setup lifecycle compatible versions"
gh api repos/<owner>/<repo> --jq '{archived, license: .license.spdx_id}'
```

Check image/module releases, maintenance, licensing, and local account/key requirements. If Docker works but a container library reports no usable environment, inspect client/daemon compatibility before replacing the setup. A generic HTTP mock fits controlled responses; it does not automatically implement a stateful domain service.

# Preferred test tools

Last checked: 2026-10-16. A short starting list, not an exhaustive catalogue or a required vendor. Prefer maintained OSS with no paid plan, vendor account, or license key. Verify current license, archival status, releases, image availability, and required behavior before adding a new tool. Use adoption as a tie-breaker, not proof of suitability.

| Need | Default to investigate | Image / JVM module | License | Evidence |
| --- | --- | --- | --- | --- |
| AWS APIs, including S3 | [Floci](https://github.com/floci-io/floci) | `floci/floci:2.3.0`; `io.floci:testcontainers-floci:2.16.1` | MIT | Real AWS SDK S3 binary/MIME round trip, missing objects and failed writes checked locally |
| S3-only alternative | [S3Mock](https://github.com/adobe/S3Mock) | `adobe/s3mock:5.2.3` | Apache-2.0 | Real AWS SDK S3 binary/MIME round trip checked locally |
| Generic HTTP responses and controlled failures | [WireMock](https://github.com/wiremock/wiremock) | `wiremock/wiremock:3.13.2` | Apache-2.0 | Image startup and a configured HTTP response checked locally |

These smoke checks used Docker 29.4.3 and Testcontainers 2.0.5; S3 clients used AWS SDK 2.42.29. They verify the listed operations, not complete service compatibility.

For databases, use the production engine/driver and a matching [Testcontainers module](https://java.testcontainers.org/modules/databases/); choose the image version to match production. Reuse the application's existing SDK or generated API client rather than handcrafting another client.

For a runnable Scala/JVM Floci example, use [FlociS3.test.scala](../assets/FlociS3.test.scala) and its [setup notes](external-services.md). For alternatives, start with [Floci docs](https://floci.io/floci/testcontainers/java/), [S3Mock usage](https://github.com/adobe/S3Mock#usage), or [WireMock Docker docs](https://wiremock.org/docs/standalone/docker/). Pin verified releases instead of `latest`.

The old LocalStack OSS and MinIO repositories were archived when checked; they are not defaults for new setups. Recheck upstream status before selecting a historical recommendation. Do not infer the local runner's terms from a commercial hosted offering.

When updating this list, record the date and distinguish documentation/registry checks from an actual smoke test. Keep only a few useful choices; add one for a recurring need rather than growing a tool directory.

# Booking gateway

A JVM service exposing `GET /slots`. It reads newline-separated available appointment slots from an HTTP directory and returns up to the configured number. Directory failures return HTTP 502 without exposing upstream credentials or payloads.

## Deployment contract

The platform launches `gateway.Main` with runtime environment settings. No source changes or new profile files are needed for an additional deployment. `APP_ENV` is an optional deployment label; it does not select resource bindings or change the meaning of other settings.

| Setting | Contract |
| --- | --- |
| `PORT` | Required integer from 1 through 65535. |
| `DIRECTORY_URL` | Required absolute HTTP(S) endpoint with a host. Path and query identify the resource and must be preserved. Embedded userinfo and fragments are unsupported. |
| `DIRECTORY_TOKEN` | Required nonblank single-line bearer credential. |
| `DIRECTORY_TIMEOUT_MS` | Optional integer from 1 through 30000; default 2000. |
| `MAX_SLOTS` | Optional integer from 1 through 1000; default 25. |

Supplied unusable settings fail at startup rather than falling back. Diagnostics name the setting and do not include credential values, including credentials embedded in malformed URLs. An already-started component uses its original settings. Unrelated environment settings are ignored.

The public `Config.load`, `DirectoryClient`, and `Gateway.start` APIs are also used by embedded consumers. `Gateway.start` accepts a direct `Config` with port 0 to request a free port in tests; platform `PORT=0` remains invalid.

Run tests with `scala-cli test . --server=false`.

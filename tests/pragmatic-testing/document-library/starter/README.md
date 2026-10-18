# Document library

A Scala CLI / Sharaf service storing document content in S3 with the AWS SDK for Java v2. Metadata is held in memory for the lifetime of each application instance; object content survives application restarts. This example has no authentication and is intended for an internal deployment.

Run with `scala-cli run . --server=false --main-class documents.Main`. Configure `DOCUMENT_BUCKET` (an existing bucket), `AWS_REGION` (defaults to us-east-1), and AWS credentials through the standard SDK provider chain. `S3_ENDPOINT` optionally overrides the endpoint and enables path-style access; `PORT` defaults to 8080. The caller owns the S3 client passed to `DocumentApp`; closing the app stops its HTTP server. Port 0 selects an available port, exposed by `baseUrl`.

## HTTP contract

- `POST /documents` accepts JSON with `name`, `contentType`, and `contentBase64`. Returns 201 and `{id,name,contentType,size}` after storing content. Size is the decoded byte count. Name and content type must be nonblank and contentBase64 must be valid Base64; invalid input returns 400 without creating metadata or an object. Empty decoded content is allowed.
- `PUT /documents/<id>` accepts the same JSON as POST and replaces content and metadata at the existing id. Returns 200 with updated metadata, 404 for an unknown id, or 400 for invalid input. A storage rejection returns 503 and leaves existing metadata unchanged. Replacement must overwrite the existing S3 object rather than create a second object.
- Objects use the configured bucket and the key `documents/<id>`. Original bytes and content type are preserved. Names are descriptive metadata, never object paths.
- `GET /documents` returns the metadata list, ordered by id. `GET /documents/<id>` returns metadata, or 404 for an unknown id.
- `GET /documents/<id>/content` returns original binary content with its S3 content type. Returns 404 if metadata or the backing object is absent; other S3 error responses return 503.
- An upload rejected by S3 returns 503 and must not publish a metadata record. Existing documents remain available.

Compile with `scala-cli compile . --server=false`.

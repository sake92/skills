# Generic Codeps export input

Use this reference for languages and build tools that Codeps does not parse directly. Prefer an existing, trustworthy dependency producer; do not claim support for a tool merely because it can list files or imports.

Emit Codeps's split graph shape and configure it as `source: export`:

```yaml
projects:
  app:
    root: .
    source: export
    inputs: [.codeps/deps.json]
```

```json
{
  "packages": {
    "nodes": [{"id": "com.example.orders", "ports": 0, "mutPorts": 0}],
    "edges": [{"source": "com.example.orders", "target": "com.example.billing", "weight": 1}]
  },
  "files": {
    "nodes": [{"id": "src/orders/service.ts", "packageId": "com.example.orders", "ports": 0, "mutPorts": 0}],
    "edges": [{"source": "src/orders/service.ts", "target": "src/billing/client.ts", "weight": 1}]
  }
}
```

Both graphs are independent materializations. Package ids are dotted logical names; file ids are paths relative to the configured project root. An omitted edge `weight` is `1`. A package-only producer may emit `files` with empty `nodes` and `edges`.

Populate `ports`, `mutPorts`, and `declarationSurface` only when the producer can determine them accurately. Missing values are zero, so the resulting surface and structural-use metrics are unavailable evidence—not proof that the public API is narrow or immutable. Preserve the exporter and its version/configuration alongside the project so history remains comparable.

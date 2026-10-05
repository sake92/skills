Judge only the two qualitative criteria below. Deterministic tests separately
grade behavior and repository structure; do not duplicate or override them.

1. Proportionality: Is the solution appropriately small for an in-memory
   repository with three lookup operations, without unrelated layers,
   frameworks, configuration, or abstractions?
2. Unjustified DTO/wrapper types: Did the solution add DTOs, identifier wrappers,
   result wrappers, or mapping types that provide no concrete benefit here?

Return JSON only:

```json
{
  "proportionality": {"passed": true, "evidence": "..."},
  "unjustified_types": {"passed": true, "evidence": "..."}
}
```

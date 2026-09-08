# TypeScript decisions

- Export only a feature's intended entry points. Avoid broad barrel exports that turn internal files into accidental APIs.
- Group code under `features/orders/{http,domain,db}` (or `api` on the client). Use ESLint import restrictions or `eslint-plugin-boundaries` for upward/cross-feature imports, and `import/no-cycle` or Madge when cycle detection is needed.
- Use `readonly` data and branded IDs when they prevent meaningful mistakes; do not add brands solely for ceremony.
- Keep wire and persistence schemas distinct from domain values when their concerns differ; validate untrusted data at runtime because TypeScript types disappear.
- Test pure rules without a DOM or fetch mocks; test feature edges through real framework wiring.

```ts
// features/checkout/domain/checkout.ts
export function submit(command: SubmitOrder, api: CheckoutApi): Promise<Result> {
  return api.submit(command);
}

// features/checkout/ui/CheckoutForm.tsx
// Convert view state to SubmitOrder here, then call submit().
```

For workspace-package boundaries, see `build-tools.md`.

---
name: cli-design
description: 'Design and review command-line interfaces: arguments, flags, subcommands, help, stdout/stderr, exit codes, machine-readable output, interactivity, and CLI configuration precedence. Use when building or changing a terminal tool or its scripting contract. For concurrent-work/subprocess lifecycle use process-hygiene; for service deployment configuration use twelve-factor-app.'
---

# CLI design

Preserve existing invocations and documented contracts. New machine-output modes need their own scripting contract; do not inherit human-output chatter or prompts.

## Reproduce before implementing

Before production edits, add boundary reproducers using existing invocations so missing new flags cannot mask inherited defects. For commands with file input or destructive confirmation, adapt these acceptance tests to the interface:

```text
bad = run(input_file = malformed_file)
assert bad.exit != 0
assert malformed_file.path in bad.stderr
assert bad.stdout is empty

before = contents(existing_destination)
blocked = run(output = existing_destination,
              stdin = affirmative_reply, interactive = false)
assert blocked.exit != 0
assert contents(existing_destination) == before
assert blocked.stdout is empty
assert explicit_override_option in blocked.stderr
assert no_confirmation_prompt(blocked)
```

Run them red, fix those boundary defects, and run them green with the old suite. Codec errors need source context at the reading/decoding boundary; piped approval is not interactive authorization. Preserve documented successful invocations, not those defects.

Then add failing feature tests covering direct/file machine output, empty input, invalid options, missing files, output failures, and closed stdin. Implement the feature and rerun the suite. Use bounded real-process tests when terminal behavior requires them; explain inapplicable cases.

## Implement

- Before changing argument parsing, briefly research native facilities and established libraries in current official docs. Check runtime support, maintenance, tests, and real-world use. Reuse a suitable tested parser; an existing handwritten parser is not a reason to extend it. Parse manually only if the user explicitly insists; otherwise resolve blocking constraints with them. Explain the choice briefly.
- Provide discoverable help/examples, version output, consistent long flags, and strict rejection of unknown arguments. Validate typed inputs before side effects.
- In machine mode, stdout contains only the payload. When that payload goes to a file, keep stdout empty; completion messages, progress, and diagnostics go to stderr. Preserve documented legacy human output separately.
- Check that stdin is interactive and prompting is enabled **before reading an answer**. Otherwise fail promptly and name the explicit answer/override flag. Piped input is not confirmation for a destructive operation.
- Expected errors name the operation, affected input/path, cause, and useful next step. Do not merely print an exception message. Stack traces belong in diagnostic mode.
- Exit zero on success and nonzero on failure; distinguish failures scripts must handle differently. Keep process exit at the entrypoint.
- Gate decoration on the receiving stream; honor NO_COLOR, TERM=dumb, and --no-color. Accept secrets through files, stdin, or platform mechanisms, and redact diagnostics.
- Preserve configuration precedence; parse and validate once, then pass typed values.

## Verify

Report the inherited-boundary reproducers and red-to-green evidence before claiming completion. Check help and legacy compatibility. Do not weaken assertions to accept defective behavior; identify any verification gap.

Read [interface details](references/cli.md) only for relevant conventions beyond these rules. Read [configuration](references/config.md) only when changing configuration sources, locations, or precedence. Use process-hygiene for lifecycle and twelve-factor-app for deployment architecture.

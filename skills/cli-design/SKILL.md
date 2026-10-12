---
name: cli-design
description: 'Design and review command-line interfaces: arguments, flags, subcommands, help, stdout/stderr, exit codes, machine-readable output, interactivity, and CLI configuration precedence. Use when building or changing a terminal tool or its scripting contract. For concurrent-work/subprocess lifecycle use process-hygiene; for service deployment configuration use twelve-factor-app.'
---

# CLI design

Preserve existing invocations and documented contracts. New machine-output modes need their own scripting contract; do not inherit human-output chatter or prompts.

## Reproduce before implementing

1. Map the affected invocations and their intended contracts: successful output, usage errors, input/output failures, and interactive/non-interactive behavior. Shared failure and prompt paths reused by a new feature belong to its regression scope.
2. Write regression tests **before changing production code**. Test the intended contract, not a snapshot of current mistakes. Capture stdout/stderr separately and assert exit codes and file effects. Include separate reproducers for malformed input and unattended confirmation wherever those paths exist, even if their implementation is inherited. Cover direct/file machine output and piped/closed stdin; explain any inapplicable scenario. Use bounded real-process tests when in-process calls cannot reproduce terminal behavior.
3. Run the reproducers and confirm they fail for the intended reason; isolate each failure so one missing feature does not mask another defect. Then implement the fix and rerun them alongside existing tests.

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

Confirm each reproducer now passes, help and invalid options behave correctly, and existing contracts remain compatible. Report the reproduced failure and red-to-green evidence. Do not weaken an assertion to accept the defective behavior.

Read [interface details](references/cli.md) only for relevant conventions beyond these rules. Read [configuration](references/config.md) only when changing configuration sources, locations, or precedence. Use process-hygiene for lifecycle and twelve-factor-app for deployment architecture.

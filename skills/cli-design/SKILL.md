---
name: cli-design
description: 'Design and review command-line interfaces: arguments, flags, subcommands, help, stdout/stderr, exit codes, machine-readable output, interactivity, and CLI configuration precedence. Use when building or changing a terminal tool or its scripting contract. For concurrent-work/subprocess lifecycle use process-hygiene; for service deployment configuration use twelve-factor-app.'
---

# CLI design

Preserve existing invocations and documented contracts. New machine-output modes need their own scripting contract; do not inherit human-output chatter or prompts.

## Choose the parser first

Research and choose before production edits; integrate that choice when implementing argument changes:

1. Read current official documentation for the language/platform's native argument facilities and established parsing libraries. Compare support for the requested grammar, runtime compatibility, maintenance, tests, and adoption. A local cache search is not documentation research.
2. Choose a suitable tested parser and briefly record the documentation links and reason. If the current parser is handwritten, replace its argument dispatch as part of the change. Delegate token handling, option arity, repetitions, `--`, and usage errors to the selected parser; keep domain validation separate.
3. Add the dependency when needed and verify the parser's actual API. Do not start by extending a flag loop or pattern match and defer migration until later. Manual argument parsing requires an explicit user request; if constraints prevent using a tested parser, resolve that constraint with the user. Simplicity, an existing prototype, and time savings do not grant that exception. Acknowledging skipped migration in the final answer does not complete this step.

## Reproduce before implementing

Apply the pragmatic-testing method: reuse existing process fixtures, assert observable behavior, and verify regressions fail for the intended reason before fixing them. Keep the CLI contracts below as the acceptance criteria.

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

Turn each requested behavior into a failing regression before implementing it. Include meaningful boundaries: zero versus omitted limits, repeated options, directory versus file filters, invalid typed values, and literal paths where applicable. Cover direct/file machine output, empty input, missing files, output failures, and closed stdin for relevant commands. Use bounded real-process tests when terminal behavior requires them.

For hierarchical filters, exercise both relative-path patterns and basename patterns against matching directories with deeper descendants. A subtree rule must apply to matching ancestors, not only to each leaf's path or basename.

Validate all options and domain values before output side effects. Compile patterns and validate ranges at this boundary so invalid input produces a usage error without replacing an existing destination. Implement the feature and rerun the suite. When a reproducer fails, inspect and fix the implementation, then rerun; a known failing regression means the task is still unfinished.

## Implement

- Provide discoverable help/examples, version output, consistent long flags, and strict rejection of unknown arguments. Validate typed inputs before side effects.
- In machine mode, stdout contains only the payload. When that payload goes to a file, keep stdout empty; completion messages, progress, and diagnostics go to stderr. Preserve documented legacy human output separately.
- Check that stdin is interactive and prompting is enabled **before reading an answer**. Otherwise fail promptly and name the explicit answer/override flag. Piped input is not confirmation for a destructive operation.
- Expected errors name the operation, affected input/path, cause, and useful next step. Do not merely print an exception message. Stack traces belong in diagnostic mode.
- Exit zero on success and nonzero on failure; distinguish failures scripts must handle differently. Keep process exit at the entrypoint.
- Gate decoration on the receiving stream; honor NO_COLOR, TERM=dumb, and --no-color. Accept secrets through files, stdin, or platform mechanisms, and redact diagnostics.
- Preserve configuration precedence; parse and validate once, then pass typed values.

## Verify

Before finishing, inspect production argument handling: does the chosen native/library parser actually consume the arguments, or did a handwritten dispatcher remain? Correct any skipped migration unless the user explicitly requested manual parsing. Then run the requested test command after the final edit and inspect its exit status and failures. Completion requires the old suite and new regressions to pass. Report the parser choice, inherited-boundary reproducers, and red-to-green evidence. Check help and legacy compatibility. Do not weaken assertions to accept defective behavior; identify any verification gap.

Read [interface details](references/cli.md) only for relevant conventions beyond these rules. Read [configuration](references/config.md) only when changing configuration sources, locations, or precedence. Use process-hygiene for lifecycle and twelve-factor-app for deployment architecture.

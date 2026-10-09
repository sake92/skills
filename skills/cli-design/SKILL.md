---
name: cli-design
description: 'Design and review command-line interfaces: arguments, flags, subcommands, help, stdout/stderr, exit codes, machine-readable output, interactivity, and CLI configuration precedence. Use when building or changing a terminal tool or its scripting contract. For concurrent-work/subprocess lifecycle use process-hygiene; for service deployment configuration use twelve-factor-app.'
---

# CLI design

Make common invocations discoverable and scripts predictable. Preserve existing invocation and output contracts unless the task authorizes changing them.

1. Identify users, common invocations, input sources, output consumers, and compatibility constraints.
2. Specify commands, arguments/flags, examples, output modes, and meaningful failure codes.
3. Implement with the repository's argument parser. Validate inputs before side effects.
4. Exercise help, success/failure, redirected output, and non-interactive execution.

Read [interface conventions](references/cli.md) for details and [configuration](references/config.md) when adding flags, environment variables, or files.

## Interface contract

- Provide concise help/examples, full --help, and --version. Choose no-argument behavior from the tool's purpose; filters may legitimately read stdin.
- Use consistent long flags and subcommands. Keep positionals for obvious primary inputs. Do not silently interpret typos or abbreviations as another command.
- Data goes to stdout; diagnostics/progress to stderr. Provide stable JSON or plain output where automation needs it. Human formatting may evolve separately.
- Return zero on success and nonzero on failure. Distinguish failures callers need to handle differently. Keep process exit at the entrypoint so cleanup runs.
- Gate decoration on the stream receiving it. Honor NO_COLOR, TERM=dumb, and --no-color. Never corrupt redirected data with progress.
- Prompt only with interactive input and prompting enabled. Provide flags for prompt answers and a non-interactive path that fails promptly on missing input.
- Make destructive actions deliberate, using dry-run/confirmation appropriate to the operation. Never accept secret values in command-line arguments.
- Explain expected failures with the input at fault and an actionable next step. Show stack traces only in diagnostic mode.

## Configuration

- Invocation-specific settings belong in flags; deployment/user settings may come from environment/files; shared project rules belong in versioned files.
- Preserve existing precedence. For a new tool, flags > environment > project file > user file > system file > defaults is a starting convention; document the actual sources and order.
- Parse and validate once, then pass typed values. Track sources for useful errors and redact secrets.
- Honor standard environment variables and platform-owned names. Prefix new application variables. Use platform-appropriate config/cache/state paths.
- Prefer secret files, stdin, or platform secrets. Never print secrets in errors, debug dumps, or logs.

Lifecycle mechanics belong to process-hygiene; service deployment architecture belongs to twelve-factor-app.

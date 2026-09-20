# cli/tool.py — review WITH the skill (model: haiku)

- **Hand-rolled argument parsing (lines 14-16)**: Use an argument-parsing library (argparse, click) instead of hand-indexing `sys.argv`.
- **API key on command line (line 16)**: Accept `--api-key-file=<path>` or read from stdin instead of as a positional argument, since `ps eww` reveals argv to all users on the machine.
- **Missing help text (line 13)**: Implement `-h`/`--help` flag and print concise help when run with no arguments.
- **Colors without TTY detection (lines 8-10, 20, 36)**: Check `sys.stdout.isatty()` and respect `NO_COLOR` environment variable before emitting ANSI color codes.
- **Progress and messages on stdout (lines 20, 27, 34, 36)**: Route progress, status, and error messages to stderr so stdout contains only the final JSON output.
- **KeyboardInterrupt doesn't exit with proper code (line 33-34)**: Exit with code 130 (standard for SIGINT) instead of printing "bye" and leaving exit code undefined.
- **Error exit code defaults to 0 (line 37)**: Call `sys.exit(1)` to explicitly signal failure, not `sys.exit()` which exits with 0.
- **Network call with no timeout (line 25)**: Add `timeout=30` parameter to `urllib.request.urlopen()` to prevent indefinite hangs.
- **Temporary file not cleaned up on error (lines 18-31)**: Use a try/finally block or context manager to ensure the temporary file is deleted if an exception occurs.
- **No input validation (lines 14-16)**: Validate that `project` and `output` are non-empty and `output` is a valid path before making the API request.

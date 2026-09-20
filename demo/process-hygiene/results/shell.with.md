# shell/dev.sh — review WITH the skill (model: haiku)

- **Line 1:** Missing `set -euo pipefail` — add strict mode to the shebang line to exit on failures and catch unset variables.
- **Lines 6-7:** Background processes (`npm run dev &` and `python -m http.server 9000 &`) are not recorded with `pids+=($!)` — immediately capture `$!` into a `pids` array after each backgrounded command to track them for cleanup.
- **Lines 6-7:** Background processes lack `setsid` wrapper — wrap each backgrounded process with `setsid` to isolate the process group so `kill -- -$pid` can terminate the whole subtree.
- **Missing trap:** No `trap cleanup EXIT INT TERM` — add a cleanup function that kills recorded PIDs and runs before exit to prevent orphaned processes.
- **Line 9:** Fixed `sleep 5` instead of polling with bounds — replace with a retry loop (e.g., `for _ in $(seq 1 30); do curl ... && break; sleep 1; done`) to detect readiness without arbitrary delays.
- **Line 3:** Secret passed as command-line argument — read `API_TOKEN` from a file path (e.g., `MYAPP_API_TOKEN_FILE`) or stdin instead, since arguments are visible via `ps`.
- **Line 17:** Secret embedded directly in curl command — replace with `--password-file` or read from environment variable that holds a file path (SKILL.md §2).
- **Lines 5, 12, 14, 18, 20:** Diagnostic messages on stdout — redirect all progress/diagnostic output to stderr via `>&2` to keep stdout clean.
- **Lines 11, 17:** `curl` commands lack timeouts — wrap with `timeout` or add `--max-time` flag to prevent indefinite hangs.
- **Missing:** No `wait` before script exits — add `wait 2>/dev/null || true` before the final exit to reap child processes and release ports.

# shell/dev.sh — review WITHOUT the skill (model: haiku)

- **Line 3 (`API_TOKEN=$1`)**: Missing validation of required argument; add a check that `$1` is provided and exit with an error if not.
- **Lines 6-7 (background processes)**: Backgrounded `npm run dev` and `python` processes are not tracked or cleaned up; save their PIDs and add cleanup on exit (trap/kill) or use a process manager.
- **Line 9 (`sleep 5`)**: Hard-coded sleep is unreliable for waiting on service startup; replace with a polling loop that checks the actual `/health` endpoint with retries.
- **Line 11 (curl without status code check)**: The `-s` flag hides the HTTP status, so a 500 error response is treated as success; add `-w "%{http_code}"` or check `$?` properly.
- **Line 11 (curl without timeout)**: No timeout on the health check curl; add `--max-time 5` or similar to prevent indefinite hangs.
- **Line 3 (`API_TOKEN=$1`)**: Passing the token as a command-line argument exposes it in process listings; use environment variables or secure input instead.
- **Line 17 (curl without error handling)**: No check that the API call succeeded before writing to file; add error checking and validation of the response.
- **Line 18 (assumes file exists)**: The `wc -l < orders.json` assumes the file was created successfully; check the file exists and the API call returned valid JSON first.
- **Script lacks `set -e`**: Script continues executing even if earlier commands fail; add `set -e` at the top to exit immediately on any error.
- **Lines 6-7 (no failure detection)**: If npm or Python servers fail to start, the script proceeds anyway; check their startup explicitly before continuing.

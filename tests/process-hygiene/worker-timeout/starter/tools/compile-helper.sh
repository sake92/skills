#!/bin/bash
set -eu
attempt=$1
mode=${2:-watch}
mkdir -p "$attempt"
exec 9>"$attempt/workspace.lock"
flock -x 9
trap 'printf "finished\n" >> "$attempt/progress.log"; exit 0' TERM
if [ "$mode" = once ]; then
  printf 'compiled\n'
  exit 0
fi
fifo="$attempt/idle.fifo"
[ -p "$fifo" ] || mkfifo "$fifo"
exec 8<>"$fifo"
while :; do
  printf 'working\n' >> "$attempt/progress.log"
  read -r -t 0.05 -u 8 unused || :
done

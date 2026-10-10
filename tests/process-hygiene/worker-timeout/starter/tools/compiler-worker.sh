#!/bin/bash
set -eu
attempt=$1
mode=${2:-watch}
trap 'exit 0' TERM
bash "$(dirname "$0")/compile-helper.sh" "$attempt" "$mode" &
helper=$!
wait "$helper"

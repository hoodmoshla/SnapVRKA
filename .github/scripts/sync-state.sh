#!/usr/bin/env bash
#
# Reads/writes .snapvrka/upstream-sync.json — the record of the last upstream commit that was
# merged into SnapVRKA, which is what makes the sync workflow idempotent.
#
# usage: sync-state.sh get <key>
#        sync-state.sh set <key> <value> [<key> <value> ...]
set -euo pipefail

STATE_FILE="${SNAPVRKA_SYNC_STATE:-.snapvrka/upstream-sync.json}"
ACTION="${1:-}"
shift || true

if [ ! -f "$STATE_FILE" ]; then
  echo "::error::Sync state file not found: $STATE_FILE"
  exit 1
fi

case "$ACTION" in
  get)
    key="${1:?usage: sync-state.sh get <key>}"
    python3 - "$STATE_FILE" "$key" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as fh:
    data = json.load(fh)
value = data.get(sys.argv[2])
print("" if value is None else value)
PY
    ;;
  set)
    [ "$#" -gt 0 ] || { echo "usage: sync-state.sh set <key> <value> [...]"; exit 2; }
    python3 - "$STATE_FILE" "$@" <<'PY'
import json, sys
path, pairs = sys.argv[1], sys.argv[2:]
if len(pairs) % 2 != 0:
    raise SystemExit("key/value pairs required")
with open(path, encoding="utf-8") as fh:
    data = json.load(fh)
for i in range(0, len(pairs), 2):
    key, value = pairs[i], pairs[i + 1]
    if value.isdigit():
        data[key] = int(value)
    else:
        data[key] = value
with open(path, "w", encoding="utf-8") as fh:
    json.dump(data, fh, indent=2, ensure_ascii=False)
    fh.write("\n")
PY
    ;;
  *)
    echo "usage: sync-state.sh get <key> | set <key> <value> [...]"
    exit 2
    ;;
esac

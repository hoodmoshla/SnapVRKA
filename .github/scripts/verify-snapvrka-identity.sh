#!/usr/bin/env bash
#
# Fails when an upstream merge has dropped one of SnapVRKA's own changes.
#
# Reads the marker table from .snapvrka/protected-paths.txt and verifies that every marker is
# still present. This is the guard that prevents a routine upstream sync from silently replacing
# our branding, RTL, Quick Share, theme, updater or signing setup.
#
# usage: verify-snapvrka-identity.sh [paths-file]
set -euo pipefail

PATHS_FILE="${1:-.snapvrka/protected-paths.txt}"

if [ ! -f "$PATHS_FILE" ]; then
  echo "::error::SnapVRKA protection file not found: $PATHS_FILE"
  exit 1
fi

missing=0
checked=0

while IFS= read -r line; do
  case "$line" in
    ''|'#'*) continue ;;
  esac

  path="$(printf '%s' "$line" | cut -d'|' -f1)"
  marker="$(printf '%s' "$line" | cut -d'|' -f2)"
  purpose="$(printf '%s' "$line" | cut -d'|' -f3)"

  [ -n "$path" ] || continue
  checked=$(( checked + 1 ))

  if [ ! -f "$path" ]; then
    echo "::error::SnapVRKA change lost: file '$path' is missing ($purpose)"
    missing=$(( missing + 1 ))
    continue
  fi

  if [ -n "$marker" ] && ! grep -qF -- "$marker" "$path"; then
    echo "::error::SnapVRKA change lost: '$marker' no longer present in '$path' ($purpose)"
    missing=$(( missing + 1 ))
  fi
done < "$PATHS_FILE"

if [ "$missing" -gt 0 ]; then
  echo ""
  echo "The upstream merge would remove $missing SnapVRKA-specific change(s)."
  echo "Resolve the merge manually: keep the SnapVRKA behaviour and re-apply the upstream logic on top."
  echo "No APK and no release were produced."
  exit 1
fi

echo "SnapVRKA identity verified: $checked protected path(s) intact."

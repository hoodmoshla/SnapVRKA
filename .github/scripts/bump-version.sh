#!/usr/bin/env bash
#
# Deterministic SnapVRKA version bump.
#
#   versionCode = major * 10000 + minor * 100 + patch
#   versionName = "<major>.<minor>.<patch>"
#
# The next version is derived from the *current versionCode* (current + 1) and the versionName is
# recomputed from that number, so the two can never drift apart and a release can never repeat or
# downgrade a version, no matter how often the sync runs.
#
#   10000 -> 10001  => 1.0.0 -> 1.0.1
#   10099 -> 10100  => 1.0.99 -> 1.1.0
#   19999 -> 20000  => 1.99.99 -> 2.0.0
#
# usage: bump-version.sh [--dry-run] [gradle-file]
set -euo pipefail

DRY_RUN=0
GRADLE_FILE="app/build.gradle.kts"

while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) GRADLE_FILE="$1"; shift ;;
  esac
done

if [ ! -f "$GRADLE_FILE" ]; then
  echo "::error::build file not found: $GRADLE_FILE"
  exit 1
fi

read_field() {
  sed -nE "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*\"?([^\"]*)\"?.*/\1/p" "$GRADLE_FILE" | head -1
}

CURRENT_NAME="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$GRADLE_FILE" | head -1)"
CURRENT_CODE="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$GRADLE_FILE" | head -1)"

if [ -z "$CURRENT_NAME" ] || [ -z "$CURRENT_CODE" ]; then
  echo "::error::Could not read versionName/versionCode from $GRADLE_FILE"
  exit 1
fi

NEXT_CODE=$(( CURRENT_CODE + 1 ))
MAJOR=$(( NEXT_CODE / 10000 ))
MINOR=$(( (NEXT_CODE % 10000) / 100 ))
PATCH=$(( NEXT_CODE % 100 ))
NEXT_NAME="${MAJOR}.${MINOR}.${PATCH}"

if [ "$NEXT_CODE" -le "$CURRENT_CODE" ]; then
  echo "::error::Refusing to produce a non-increasing versionCode ($NEXT_CODE <= $CURRENT_CODE)"
  exit 1
fi

if [ "$DRY_RUN" -eq 1 ]; then
  echo "current: ${CURRENT_NAME} (${CURRENT_CODE})"
  echo "next   : ${NEXT_NAME} (${NEXT_CODE})"
else
  # Only touch the two literals; everything else in the build file stays untouched.
  sed -i -E "s/^([[:space:]]*versionCode[[:space:]]*=[[:space:]]*)[0-9]+/\1${NEXT_CODE}/" "$GRADLE_FILE"
  sed -i -E "s/^([[:space:]]*versionName[[:space:]]*=[[:space:]]*\")[^\"]+(\")/\1${NEXT_NAME}\2/" "$GRADLE_FILE"

  VERIFY_CODE="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' "$GRADLE_FILE" | head -1)"
  VERIFY_NAME="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "$GRADLE_FILE" | head -1)"
  if [ "$VERIFY_CODE" != "$NEXT_CODE" ] || [ "$VERIFY_NAME" != "$NEXT_NAME" ]; then
    echo "::error::Version bump failed (file now reports ${VERIFY_NAME} / ${VERIFY_CODE})"
    exit 1
  fi
  echo "bumped ${CURRENT_NAME} (${CURRENT_CODE}) -> ${NEXT_NAME} (${NEXT_CODE})"
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "previous_version_name=${CURRENT_NAME}"
    echo "previous_version_code=${CURRENT_CODE}"
    echo "version_name=${NEXT_NAME}"
    echo "version_code=${NEXT_CODE}"
  } >> "$GITHUB_OUTPUT"
fi

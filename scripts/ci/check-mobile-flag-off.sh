#!/usr/bin/env bash
# Fail if delivery.mobile.enabled is missing from default application.properties.
# As of 2026-11-23 the default is ON; this script now enforces the flag is explicitly declared
# (not stripped). Its callers live in .github/workflows.disabled/ — kept here only so a
# manual invocation won't surprise someone who set it up before the default flipped.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PROP="$ROOT/src/main/resources/application.properties"
if [[ ! -f "$PROP" ]]; then
  echo "missing $PROP" >&2
  exit 2
fi
val=$(grep -E '^[[:space:]]*delivery\.mobile\.enabled[[:space:]]*=' "$PROP" | tail -n1 | cut -d= -f2- | tr -d '[:space:]' || true)
if [[ -z "$val" ]]; then
  echo "FAIL: delivery.mobile.enabled missing from application.properties" >&2
  exit 1
fi
if [[ "$val" != "true" && "$val" != "false" ]]; then
  echo "FAIL: delivery.mobile.enabled=$val (must be true or false)" >&2
  exit 1
fi
echo "OK: delivery.mobile.enabled=$val"

#!/usr/bin/env bash
# Fail if delivery.mobile.enabled is true in default application.properties (M0 / 0056).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
PROP="$ROOT/src/main/resources/application.properties"
if [[ ! -f "$PROP" ]]; then
  echo "missing $PROP" >&2
  exit 2
fi
# Last assignment wins
val=$(grep -E '^[[:space:]]*delivery\.mobile\.enabled[[:space:]]*=' "$PROP" | tail -n1 | cut -d= -f2- | tr -d '[:space:]' || true)
if [[ -z "$val" ]]; then
  echo "FAIL: delivery.mobile.enabled missing from application.properties (must default false)" >&2
  exit 1
fi
if [[ "$val" != "false" ]]; then
  echo "FAIL: delivery.mobile.enabled=$val (default CI must keep false; use keel.mobile.ci-opt-in on a dedicated job)" >&2
  exit 1
fi
echo "OK: delivery.mobile.enabled=false"

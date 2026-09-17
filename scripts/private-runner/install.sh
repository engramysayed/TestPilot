#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${HOME}/keel-private-runner"
mkdir -p "$WORK"
START="$WORK/start-runner.sh"
cat > "$START" <<EOF
#!/usr/bin/env bash
set -euo pipefail
export KEEL_PORTAL_URL="\${KEEL_PORTAL_URL:-http://127.0.0.1:8081}"
export KEEL_RUNNER_DRY_RUN="\${KEEL_RUNNER_DRY_RUN:-true}"
if [[ -z "\${KEEL_RUNNER_TOKEN:-}" ]]; then
  echo "Set KEEL_RUNNER_TOKEN to the tp_run_ token from project Settings."
  exit 2
fi
cd "$ROOT"
exec mvn -q -DskipTests exec:java -Dexec.mainClass=delivery.runner.PrivateRunnerAgent \\
  -Dexec.args="--portal \$KEEL_PORTAL_URL --token \$KEEL_RUNNER_TOKEN --work-dir $WORK --dry-run \$KEEL_RUNNER_DRY_RUN"
EOF
chmod +x "$START"
echo "Wrote $START"
echo "Set KEEL_RUNNER_TOKEN then run that script. Auto-update is not provided."

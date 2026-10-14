#!/usr/bin/env bash
# B10 (OPS §12.5 Part A item 1): fail the build on hard-coded hosts/paths in production code.
# The scan is HardcodedLiteralCheckTest (also part of the normal test run); reviewed exceptions live in
# src/test/resources/ci/hardcoded-allowlist.txt as file|literal|reason (shrink-only).
set -euo pipefail
cd "$(dirname "$0")/../.."
exec mvn -B -pl keel-portal -am test "-Dtest=HardcodedLiteralCheckTest" "-Dsurefire.failIfNoSpecifiedTests=false" "$@"

#!/usr/bin/env python3
"""M0-06 coverage gate: every MobileErrorCode from mobile-error-codes.txt must have a fixture stub,
and every declared DTO schema must have valid+invalid fixtures. Exit 1 on gaps.

Also cross-checks that contracts.md still mentions each code (drift detection).
Stdlib only. No network. No device.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
CODES_FILE = ROOT / "mobile-error-codes.txt"
FIXTURE_DIR = ROOT / "fixtures" / "error-codes"
SCHEMA_DIR = ROOT / "schemas"
SCHEMA_FIX = ROOT / "fixtures" / "schema"
CONTRACTS = Path(
    "/workspace/testpilot/mobile-plan-v2/superpowers/plans/2026-09-27-android-mobile/contracts.md"
)
DTO_NAMES = ["MobileCaseIr", "MobileStep"]


def load_codes() -> list[str]:
    out = []
    for line in CODES_FILE.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        out.append(line)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--contracts", type=Path, default=CONTRACTS)
    args = ap.parse_args()
    codes = load_codes()
    missing_fix = []
    missing_contracts = []
    bad_stub = []
    contracts_text = args.contracts.read_text(encoding="utf-8") if args.contracts.is_file() else ""

    for code in codes:
        path = FIXTURE_DIR / f"{code}.json"
        if not path.is_file():
            missing_fix.append(code)
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as e:
            bad_stub.append(f"{code}: invalid JSON ({e})")
            continue
        if data.get("code") != code:
            bad_stub.append(f"{code}: stub code field mismatch")
        cov = data.get("coverage") or {}
        if cov.get("status") not in ("stub", "covered"):
            bad_stub.append(f"{code}: coverage.status must be stub|covered")
        if cov.get("status") == "covered" and not cov.get("raisedByTest"):
            bad_stub.append(f"{code}: covered requires raisedByTest")
        if code not in contracts_text:
            missing_contracts.append(code)

    schema_gaps = []
    for dto in DTO_NAMES:
        sch = SCHEMA_DIR / f"{dto}.schema.json"
        valid = SCHEMA_FIX / f"{dto}.valid.json"
        invalid = SCHEMA_FIX / f"{dto}.invalid.json"
        for p in (sch, valid, invalid):
            if not p.is_file():
                schema_gaps.append(str(p.relative_to(ROOT)))

    print(f"codes: {len(codes)}")
    print(f"error-code fixtures missing: {len(missing_fix)}")
    for c in missing_fix:
        print(f"  MISSING_FIXTURE {c}")
    print(f"bad stubs: {len(bad_stub)}")
    for b in bad_stub:
        print(f"  BAD_STUB {b}")
    print(f"absent from contracts.md: {len(missing_contracts)}")
    for c in missing_contracts:
        print(f"  DRIFT {c}")
    print(f"schema/fixture gaps: {len(schema_gaps)}")
    for g in schema_gaps:
        print(f"  SCHEMA_GAP {g}")

    # Summary coverage counts
    covered = stub = 0
    for code in codes:
        path = FIXTURE_DIR / f"{code}.json"
        if not path.is_file():
            continue
        st = (json.loads(path.read_text()).get("coverage") or {}).get("status")
        if st == "covered":
            covered += 1
        elif st == "stub":
            stub += 1
    print(f"coverage: covered={covered} stub={stub}")

    if missing_fix or bad_stub or missing_contracts or schema_gaps:
        print("FAIL: M0-06 coverage gate", file=sys.stderr)
        return 1
    print("OK: M0-06 coverage gate (all codes have stubs; schemas+fixtures present; contracts.md mentions each code)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

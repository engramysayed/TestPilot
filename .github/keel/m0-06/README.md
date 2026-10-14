# M0-06 — Mobile error-code & schema coverage gate

Implements the contracts.md rule: CI fails if any `MobileErrorCode` is absent from the failure-codes section or lacks coverage, and every DTO has schema + valid/invalid fixtures.

## Layout
- `mobile-error-codes.txt` — canonical catalog (41 codes)
- `fixtures/error-codes/<CODE>.json` — per-code coverage stub (`status: stub|covered`)
- `schemas/*.schema.json` — MobileCaseIr, MobileStep (draft)
- `fixtures/schema/*.valid.json` / `*.invalid.json`
- `check-m0-06-coverage.py` — exit 1 on gaps

## Run
```bash
python3 /workspace/testpilot/mobile-m0/m0-06/check-m0-06-coverage.py
```

When a real Java `MobileErrorCode` enum + test exists, set the stub's `coverage.status` to `covered` and `raisedByTest` to the test method FQCN.

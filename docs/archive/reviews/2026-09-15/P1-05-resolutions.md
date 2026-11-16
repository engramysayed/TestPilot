# P1-05 — unify test-case identity validation (F05)

**Date:** 2026-09-16

## Behavior

- One contract: `TcIdentity` (`TC_` + digits or A-Z/digits/underscore). Portal generate, Excel ingest, CSV parse, CLI, Automate, Execute, and dry-run all use it.
- Invalid IDs such as `TC/1` get the same message: `Invalid tcId '…': must match TC_<digits> or TC_<ALNUM_UNDERSCORE>`.
- On-disk names use `TcIdentity.storageKey` (`~HH` for every non-alphanumeric), so `TC/1` and `TC_1` cannot share a file. IR JSON still stores the display `tcId`.
- `read()` will not treat a legacy `TC_1.json` whose JSON `tcId` is `TC/1` as `TC_1`; that is `ID_STORAGE_COLLISION`. A matching legacy file still loads until rewritten to the encoded name.
- Occurrence folders use the same storage key (`{storageKey}__occ_{n}`). Evidence lookup accepts encoded, display, and legacy folder names.

## Verification

`mvn -B "-Dtest=delivery/**/*Test,drivers/**/*Test,!FacebookSubmitLiveRebindTest,!*LiveSmokeTest" test`

**1108 tests, 0 failures, 1 skip.**

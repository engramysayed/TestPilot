# P0-03 sequence 10 — concurrent same-host (committed)

**Revision:** `f44c76abfc35053af09c26c47fcd77c120cad839`  
**When:** 2026-09-17T12:14:37+03:00  
**Host:** `Ramy-Sayed` (Windows 11, this workspace — not a deployed shared/dedicated install)

## Command

```
mvn "-Dtest=ConcurrentSameHostProveEmitTest" test
```

## Outcome

| Field | Value |
|-------|--------|
| Tests run | 1 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Time | 18.89 s (suite 20.599 s) |
| Maven | BUILD SUCCESS |

Two hosted ProvePhase+emit jobs on the same local fixture origin kept `ALICE_SENTINEL` and `BOB_SENTINEL` apart in IR and in the published ZIPs. Chrome ran headless. No live LLM.

This **closes the owed committed-revision re-run** of sequence 10 from a tree that includes ProvePhase `WorkerNetworkGuard` and per-thread `WorkerPac`. It does **not** close P2-01/P2-02 customer isolation, and it does not prove worker-network isolation on a deployed host.

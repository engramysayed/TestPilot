# QA gate: generate-from-knowledge-fe (proxy)

- **Date:** 2026-11-02 (Africa/Cairo, UTC+2)
- **Slice:** generate-from-knowledge-fe
- **Live tree:** `D:\Work\projects\Keel\keel\TestPilot` (machine Ramy)
- **Tip SHA:** `b422366` (`b422366de94529e578e10da35eda975283dec418`)
  - Note: expected ~b41c774-ish; actual tip is **b422366**.
- **Command (recipe #2 proxy ONLY):**
  ```
  mvn -B -pl keel-portal -am "-Dtest=GenerateMvcTest,GenerateTcApiTest,GenerateAsyncApiTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
  ```
- **Portal/engine:** not started/restarted. Sonic/Appium ignored.
- **VERDICT:** **GREEN**

## Surefire summary

| Metric | Value |
|--------|-------|
| Exit code | 0 |
| BUILD | SUCCESS |
| Tests run | 20 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Elapsed (surefire suite) | ~19.01 s |
| Total Maven time | ~34.1 s |
| Finished at | 2026-11-02 17:27:50 Africa/Cairo |

### Classes exercised (proxy)

- `delivery.portal.api.GenerateAsyncApiTest`
- `delivery.portal.api.GenerateTcApiTest`
- `delivery.portal.web.GenerateMvcTest`

### Compile

- No compile / test-compile failure this run (prior health-check symbol issues did **not** recur).

## Scope note

FE Generate-from-knowledge has **no dedicated tests yet**. Proxies above (`GenerateMvc` / `GenerateTc` / `GenerateAsync`) are the current stand-in gate.

**Next:** swap this recipe to real Knowledge Generate tests when they land.

## Non-actions

- Did not push, commit, start portal, or touch Sonic.

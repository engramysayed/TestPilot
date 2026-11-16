# P1-03 — regenerate a complete generated layer (F04)

**Date:** 2026-09-16

## Behavior

- Emit codegens **every** IR draft, including eligible `REUSED` proof. Incremental “changed cases only” emit is gone.
- `CodeWriter` rewrites pages, generated tests, TODO tests, and `delivery-testdata.properties` from that full set, then deletes obsolete `.java` files.
- UPDATE copies the **template**, then overlays customer-editable `src/test/resources/config/*` (except `*.example`) from the previous framework. `pom.xml`, pages, and tests are generated.
- `EmitCompileCheck` still runs **before** `saveVersion`. A failed compile throws and leaves the previous published package in place.

PASS→TODO removes `generated/TC_X.java` and writes `todo/TC_XTodo.java`. Removed cases disappear from tests, page methods, and test-data keys.

Original probe: [LaunchAuditProbe.java](LaunchAuditProbe.java). Regression: `GeneratedLayerSyncTest`, `EmitGeneratedLayerContractTest`, `FrameworkPackagerTest.overlayCustomerConfigPreservesWebappProperties`.

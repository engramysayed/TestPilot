# LD-02 IT DONE — credits IT seed (flag-off for default ITs)

**Tip:** `0eb96182eb8f72b8ac23a5010e7032abd305d062` (keel tip zip / `impl-ld02-tip`)  
**Patch:** `patches/ld/LD-02-credits-it-seed.patch`  
**Box worktrees:** `impl-ld02-tip` (baseline, read-only), `impl-ld02-it` (patched)  
**Date:** 2026-10-15 00:26 Africa/Cairo (EEST)  
**Status:** box patch only — **not** live-applied, **no** push/commit.

## Problem
Tip main `application.properties` has `delivery.credits.enabled=true` with starting balance 0.
Portal SpringBootTest / WebMvcTest ITs that start billable jobs hit **402 INSUFFICIENT_CREDITS**
(~41 failures reported). Product policy must stay: credits **on**, balance **0**, admin grants
manually — **no** approve auto-grant.

## Fix (IT only)
1. **`src/test/resources/application.properties`** — copy of main with
   `delivery.credits.enabled=false` (full file so classpath readers such as
   `MobileFeatureFlagDefaultsTest` / `H2HardeningTest` still see H2 + mobile defaults).
2. **`TestCreditsOffEnvironmentPostProcessor`** (test classpath only) — forces
   `System.setProperty("delivery.credits.enabled","false")` at each Spring context start.
   Needed because `utils.PropertyReader` (via `CursorHealClient`) copies
   `src/main/resources/*.properties` into System properties mid-suite and would otherwise
   re-enable credits for later `@SpringBootTest` contexts in the same JVM.
3. Registered via test `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor`
   and `META-INF/spring.factories`.

**Unchanged (product):** main `delivery.credits.enabled=true`; no approve auto-grant;
LD-01 mobile product profile / dual opt-in untouched.

**Intentional credits suites** keep opting in with
`@SpringBootTest(properties={"delivery.credits.enabled=true",...})` /
`@TestPropertySource` (higher precedence than system props) and still seed/grant via
`CreditService` where needed.

## Apply (Maestro / live — not done here)
```bash
cd /path/to/TestPilot   # at tip 0eb9618
git apply --ignore-whitespace patches/ld/LD-02-credits-it-seed.patch
mvn -B -o -pl keel-portal -am \
  -Dtest=ExecuteRunApiTest,HuntApiTest,GenerateAsyncApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## Test results (box, offline, `impl-ld02-it`)

| Check | Result |
|-------|--------|
| BEFORE tip focused `ExecuteRunApiTest,HuntApiTest,GenerateAsyncApiTest` | **8/7/0** (7× expected 202 got **402**) |
| AFTER focused same suite | **8/0/0** BUILD SUCCESS |
| Credits intentional suite (`CreditsApiTest,CreditsFlagOffApiTest,CreditsHttpStatusLockTest,CreditsGateLockTest,CreditsPipelineNoDebitTest,CreditsEnabledDefaultTest,CreditsMvcTest,AccountCreditsMvcTest,AccountCreditsFlagOffMvcTest`) | **21/0/0** BUILD SUCCESS |
| Broader sample (`PortalApiTest,JobCompareApiTest,PrivateRunnerApiTest,MobileFeatureFlagDefaultsTest,H2HardeningTest,AuthoringEngineApiTest,UseGeneratedWorkbookApiTest,ExecuteRunDeleteApiTest,GenerateBatchApiTest`, …) | **37/0/0** BUILD SUCCESS |
| `git apply --ignore-whitespace --check` onto clean tip copy | **OK** |

## One-line summary
Test classpath turns credits **off** for default ITs (props + test EPP vs PropertyReader sysprop leak); product main stays **on** / balance 0 / manual grant.

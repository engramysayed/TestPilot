# Explicit assertions: real prove and downloaded replay

## Scope and status

Validated the uncommitted working tree on 2026-09-21. Launch remains HOLD;
candidate `2721e6d` is unchanged. This is controlled local shop acceptance,
not representative customer-app or deployment certification.

## Changes in this pass

- Downloaded `WaitHandler.waitUntilBodyTextContains` retries stale element references
  within the existing timeout, locating the body again on each poll. Dead-session,
  invalid-selector and transport errors still surface.
- Explicit assertion parsing rejects malformed operation syntax, unsupported locator
  strategies and comparison modes other than exact text. Signed-out assertions now
  accept compound CSS selectors containing spaces.
- The local acceptance harness copies the existing Java shop fixture into a fresh
  `target/local-acceptance/<uuid>` directory. It no longer deletes the shared shop
  store. No installations or edits to the original sibling shop were needed.
- Added `explicitAssertionsProveAndReplay`: updated Excel -> real NEW conversion ->
  persisted IR -> emitted ZIP -> clean downloaded replay of ORD/SES only. Required
  prerequisite cases are proven first. No old IR is patched for this test.

The older `CheckoutChainAssertionReplayTest` remains a synthetic re-emission test;
it was not used as proof-path evidence here.

## Focused verification

Command:

```text
mvn -B -Dtest=CustomerRuntimeHelpersTest,ExplicitAssertionOpsTest,CaptureCompareTest,CheckoutChainAssertionCodegenTest,CheckoutChainAssertionRuntimeTest test
```

37 tests, 0 failures/errors/skips. BUILD SUCCESS at 2026-09-21T21:32:41+03:00.
Log: `target/explicit-assertions-focused.log`.

Added regressions cover stale body replacement, permanently stale timeout, fatal
browser errors, malformed explicit syntax, unsupported comparison/locator modes,
and compound CSS for signed-out assertions.

## Real prove and replay

Command:

```text
mvn -B -Dtest=delivery.acceptance.LocalControlledAcceptanceLiveSmokeTest#explicitAssertionsProveAndReplay -Dkeel.localAcceptance=true test
```

The sandbox attempt failed before proving because Chrome could not start and
Selenium cache access was denied. The outside-sandbox retry completed successfully
at 2026-09-21T21:36:48+03:00 in 3:18. One successful scoped browser run; no UPDATE,
vision, Cursor, full acceptance pack or second replay.

Prove: COMPLETED, 5 PASSED, 0 TODO. Cases: TC_ACC_02, TC_CART_01, TC_CHK_02,
TC_ORD_01, TC_SES_01. TC_CART_01 used Ollama healing; other cases recorded no heal.
Shop: `http://127.0.0.1:6880` (test-owned process stopped afterward).

Persisted ORD IR contains `captureText`, source `id=order-id`, variable `orderId`,
rule `exactText`, then `capturedEquals`, target `css=#order-list li`, same variable
and rule. Persisted SES IR contains `signedOut` for `id=session-email` with `empty`
and `id=account-email` with `text:Not signed in`.

Downloaded Maven: 2 tests, 0 failures/errors/skips, exit 0. TestNG XML confirms
both test methods, both setUp methods and both tearDown methods PASS.

- Order_on_account captured and compared `Order KLA-1001` within its own test.
- Session_expiry_overlay independently captured and compared `Order KLA-1002`
  in setup, then passed both explicit signed-out checks after expiry.
- Generated source clears captured values at test start/end, retaining them across
  setup assertAll. No concrete order ID is hardcoded into the generated tests.

## Artifact identity

Run root:
`target/local-acceptance/63bc67cf-0fa3-4666-ba77-d77f5c55f344`

Evidence under that root:
`evidence/20260921T183332Z-7e2181d0/`

- `run-log.txt`: prove verdicts and replay statuses.
- `replay-scoped-chain-mvn.log`: capture/compare, signed-out and Maven outcomes.
- `scoped-chain-dom-new.zip`: real emitted package.
- `keel-store/127-0-0-1/kla_chain/ir`: persisted proof IR (under run root).
- `replay-scoped-chain`: unzipped package and TestNG reports (under run root).

ZIP SHA-256:
`82d8d96a9a9239d4fbc6fb71cb29956a4ed4792fcd85550c2cee296cdbf6b995`

Outer browser-run log: `target/explicit-assertions-acceptance-browser.log`.

## Limits

Ordinary CHK_02 confirmation remains a prefix assertion as specified; exact order
comparison occurs in ORD. Local test results do not establish live Cursor behavior,
vision accuracy, production isolation or full redaction. No full deterministic
suite was repeated in this pass. Nothing was committed or published.

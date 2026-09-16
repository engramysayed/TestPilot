# P0-03 expected outcomes

Recorded **before** running the benchmark. Do not change these rows to make a run look green.

Metrics are counted separately:

- **Proof coverage** — live-proven steps vs authored intents (not Maven green).
- **Assertion correctness** — false expectations fail TestNG; true expectations pass.
- **Replay success** — downloaded ZIP reproduces the same verdict without the portal.
- **Stability** — repeated UPDATE of unchanged inputs does not flip TODO→PASS or drop PASS methods.

IDs use the P1-05 `TC_*` contract (`TC-LOGIN-OK` is rejected at ingest). Verdicts are unchanged.

| Case ID | Intent | Expected live IR | Expected downloaded TestNG | Notes |
|---------|--------|------------------|----------------------------|-------|
| TC_LOGIN_OK | Valid login lands on Dashboard | PASSED | pass | Shared login method. |
| TC_LOGIN_BAD | Invalid password shows Invalid credentials | PASSED | pass | Negative login. |
| TC_ASSERT_FALSE | Home heading equals `Admin Home` | PASSED at prove only if the engine asserts the **page** text; replay **must fail** | **fail**, Maven nonzero | F00 / P1-01 gate. Heading is `Dashboard`. |
| TC_ASSERT_TRUE | Home heading contains `Dashboard` | PASSED | pass | Control for TC-ASSERT-FALSE. |
| TC_URL_OK | After login, URL contains `home.html` | PASSED | pass | URL assertion path. |
| TC_SELECTED | Shop size select remains `M` | PASSED | pass | selected-state assertion path. |
| TC_CART | Add widget to cart, cart shows 1 item | PASSED | pass | Shared shop methods. |
| TC_CHECKOUT | Checkout after cart | PASSED if prerequisite emitted; otherwise not advertised replay-ready | pass only after P1-04 | Stateful non-login prerequisite. First release: prove-only (P0-01). After P1-04, downloaded ZIP must pass. |
| TC_CHECKOUT_EMPTY | Checkout with empty cart shows Cannot checkout | PASSED | pass | Negative stateful path. |
| TC_SECRET | Login with canary password `CANARY-SECRET-001` | PASSED | pass | Password must not appear in prompts, DOM dumps, or exported packs after P2-04. |

## Sequence expectations

| Sequence | Expected |
|----------|----------|
| NEW of the CSV above | TC_ASSERT_FALSE compile+replay fails TestNG. Other replayable PASSED cases pass. TC_CHECKOUT is replay-ready after P1-04. |
| Downloaded replay #1 | Same TestNG verdicts as NEW emit. |
| UPDATE unchanged | Eligible PASS keeps complete methods (after P1-02). Unchanged TODO stays TODO, never becomes PASS by reuse. |
| UPDATE change TC_CART text | TC_CART and TC_CHECKOUT proof invalidated. |
| UPDATE remove TC_LOGIN_OK | Generated class for TC_LOGIN_OK gone; dependents that needed login are TODO or blocked. |
| PASS→TODO on TC_URL_OK | Stale passed class must not remain (P1-03). |
| Changed environment (different BASE_WEB) | Prior PASS not reused. |
| Concurrent same-host jobs, distinct sentinels | Independent work dirs after P2-02. **Until then this sequence is an expected isolation failure**, not a pass. |

Every run records: git revision, template/library revision, base URL, artifact identity (job/version id), and the four metrics above.

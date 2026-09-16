# P0-03 expected outcomes

Recorded **before** running the benchmark. Do not change these rows to make a run look green.

Metrics are counted separately:

- **Proof coverage** — live-proven steps vs authored intents (not Maven green).
- **Assertion correctness** — false expectations fail TestNG; true expectations pass.
- **Replay success** — downloaded ZIP reproduces the same verdict without the portal.
- **Stability** — repeated UPDATE of unchanged inputs does not flip TODO→PASS or drop PASS methods.

| Case ID | Intent | Expected live IR | Expected downloaded TestNG | Notes |
|---------|--------|------------------|----------------------------|-------|
| TC-LOGIN-OK | Valid login lands on Dashboard | PASSED | pass | Shared login method. |
| TC-LOGIN-BAD | Invalid password shows Invalid credentials | PASSED | pass | Negative login. |
| TC-ASSERT-FALSE | Home heading equals `Admin Home` | PASSED at prove only if the engine asserts the **page** text; replay **must fail** | **fail**, Maven nonzero | F00 / P1-01 gate. Heading is `Dashboard`. |
| TC-ASSERT-TRUE | Home heading contains `Dashboard` | PASSED | pass | Control for TC-ASSERT-FALSE. |
| TC-URL-OK | After login, URL contains `home.html` | PASSED | pass | URL assertion path. |
| TC-SELECTED | Shop size select remains `M` | PASSED | pass | selected-state assertion path. |
| TC-CART | Add widget to cart, cart shows 1 item | PASSED | pass | Shared shop methods. |
| TC-CHECKOUT | Checkout after cart | PASSED if prerequisite emitted; otherwise not advertised replay-ready | pass only after P1-04 | Stateful non-login prerequisite. First release: prove-only (P0-01). |
| TC-CHECKOUT-EMPTY | Checkout with empty cart shows Cannot checkout | PASSED | pass | Negative stateful path. |
| TC-SECRET | Login with canary password `CANARY-SECRET-001` | PASSED | pass | Password must not appear in prompts, DOM dumps, or exported packs after P2-04. |

## Sequence expectations

| Sequence | Expected |
|----------|----------|
| NEW of the CSV above | TC-ASSERT-FALSE compile+replay fails TestNG. Other replayable PASSED cases pass. TC-CHECKOUT is not advertised as replay-ready until P1-04. |
| Downloaded replay #1 | Same TestNG verdicts as NEW emit. |
| UPDATE unchanged | Eligible PASS keeps complete methods (after P1-02). Unchanged TODO stays TODO, never becomes PASS by reuse. |
| UPDATE change TC-CART text | TC-CART and TC-CHECKOUT proof invalidated. |
| UPDATE remove TC-LOGIN-OK | Generated class for TC-LOGIN-OK gone; dependents that needed login are TODO or blocked. |
| PASS→TODO on TC-URL-OK | Stale passed class must not remain (P1-03). |
| Changed environment (different BASE_WEB) | Prior PASS not reused. |
| Concurrent same-host jobs, distinct sentinels | Independent work dirs after P2-02. **Until then this sequence is an expected isolation failure**, not a pass. |

Every run records: git revision, template/library revision, base URL, artifact identity (job/version id), and the four metrics above.

# Grounding review and realistic test target

> Subsequent implementation and validation are recorded in [the fixes log](2026-09-20-dom-vision-fixes.md). The findings below describe the pre-fix audit.

Reviewed 20 September 2026 at HEAD `a4dc94e9fc4faffff4c8915693dd5fd2817cf7ef`, with pre-existing documentation edits preserved. This review adds a report and four characterization tests; production code and the launch candidate are unchanged.

## Decision

Keep UI-TARS as the screenshot grounding model. The architecture is appropriate: propose a location, recover a DOM element, generate a locator, then execute and verify through Keel. The current acceptance boundaries need strengthening. The earlier explanation that the Login failure is merely model jitter is insufficient: deterministic code can accept and misidentify wrong targets too.

Choose **Medusa DTC Starter**, running locally with its storefront, backend, admin and PostgreSQL. It is a real commerce application suitable for repeatable tests without accessing a third-party production shop. Use the current monorepo, not the deprecated standalone Next.js storefront.

Official sources checked:
- https://docs.medusajs.com/resources/nextjs-starter
- https://github.com/medusajs/dtc-starter
- https://docs.medusajs.com/learn/installation/docker

The documented default storefront is localhost:8000 and admin localhost:9000/app. Setup needs Node/PostgreSQL or the documented Docker approach. Installation and laptop capacity have not been tested in this review. No payment service should be connected to real charges; configure a test/manual payment path and seeded disposable data.

## Actual workflow

1. `ProvePhase` first tries ordinary DOM authoring (or the Precision path). The early vision hook applies when DOM binding fails, Precision is not selected, the intent is eligible, and grounding is enabled.
2. `VisionProveHook` extracts candidates from the captured HTML and removes failed locators.
3. `VisionGroundingEngine` invokes `ViewportSweep`; the passed `healPng` is not used by that engine. Sweep captures fresh screenshots and viewport metrics, with up to eight attempts and downward scrolling.
4. `SeleniumGroundingBrowser` redacts screenshot captures. `UiTarsImagePrep` downsizes the longest dimension to at most 1280 and later maps model coordinates back to original pixels.
5. `UiTarsPrompt` requests a native click-shaped response on a 0–1000 grid. This is a grounding output format: UI-TARS does not directly execute the browser action.
6. `UiTarsResponseParser` accepts native actions and several JSON representations. `VisionBboxQualityGate` filters size/confidence/description. The provider may retry Login/Submit requests based on the model's description.
7. `ViewportSweep` maps the box center to viewport CSS coordinates, calls `elementFromPoint`, and accepts an enabled/displayed interactive element.
8. `ElementGrounder` performs another DOM hit-test, matches the node to the old candidate table or builds a locator.
9. `AuthoringService.stepsPreferringCandidate(..., true)` builds validated steps with relaxed distinctive matching. The healing path also calls `VisionHealSupport` and the same relaxed binding route.
10. `TcExecutionService` executes the locator and runs assertions. Its post-click DOM-change heuristic is non-strict by default; logged change/miss is not proof of the requested business outcome.
11. Proven IR supplies generated code. Vision proposals should never be treated as runtime proof just because a locator passes syntax validation.

Grounding defaults to UI-TARS, visual assertions to Qwen; their flags are separate. Grounding is configurable and defaults off in `VisionGroundingConfig`; this is not evidence that a currently running portal has it disabled.

## Findings, in repair order

### G1 — High: wrong input can become a validated Login click (reproduced)

`ViewportSweep.firstHit` checks interactiveness without the requested intent. A username textbox passes. `VisionGroundingEngine` then requests relaxed binding. `AuthoringService` disables some named-action checks in relaxed mode, and the CLICK_LOGIN path does not receive all CLICK checks. The submit-navigation guard rejects certain navigation links, not arbitrary text inputs.

The audit test injects a model candidate labeled Login over `input#username`. The real engine returns a validated `click` step with locator `username`. This reproduces a component-level acceptance defect; it does not claim a complete customer job was falsely marked PASS in this run.

Fix: enforce action/type and target compatibility against the actual DOM node before accepting it. Relaxation may help discover unlabeled controls but must not erase action semantics. Retry using actual observed mismatch feedback, not just the model's self-description. Reject or return uncertainty when compatibility cannot be established. Cover both prove and heal paths, including CLICK_LOGIN and named CLICK.

### G2 — High: matched DOM identity can be replaced by another same-label element (reproduced)

`ElementGrounder.matches` falls through from a mismatched ID to equal visible text. A visual hit on `delete-second` can therefore select existing candidate `delete-first` when both say Delete. CSS/XPath matching also uses substring containment of data-test values, which is not an identity check.

Fix: retain the observed node identity, resolve candidate locators in the browser, require unique resolution and equality to that node. Preserve frame/shadow context and the actual data attribute name. Do not let text equality override conflicting stable identifiers. A syntactically valid locator alone is insufficient.

### G3 — Medium: inconsistent coordinate conventions and shifted edge points (reproduced)

Native action points always use the 0–1000 conversion, but native four-coordinate boxes special-case unit-normalized values. Thus `(0.5,0.5)` and `(0.49,0.49,0.51,0.51)` map near the corner versus the center for a 1000×800 image. The prompt requests 0–1000, so unit-form model output is out-of-contract; the defect is accepting conflicting formats without an explicit contract. This does not establish the cause of the earlier live Login misses.

Additionally, padding a native point at x=0 and clamping its left edge produces a rectangle whose center is x=12. The grounding point changes. A test reproduces this without a model.

Fix: select an explicit coordinate space per response format/model adapter; reject ambiguity and nonfinite/out-of-bounds positions. Preserve the original point independently of a display box. Test corners, near edges, 0–1/0–1000/pixels, resizing, browser zoom and DPR combinations.

### G4 — Medium: screenshot-to-DOM consistency is not enforced (static risk)

Sweep captures a screenshot, then metrics, then waits for inference, then hit-tests the current DOM; the engine hit-tests again. The candidate table came from earlier HTML. No page/version or observed-node continuity token prevents animation, scrolling, navigation or overlays changing the target between these steps.

Fix: carry screenshot hash, viewport/scroll coordinates, timestamp and node identity through one observation. Revalidate layout/node before execution and recapture when changed. Avoid trusting stale table labels. This needs a controlled moving-overlay browser test; no race was reproduced here.

### G5 — Medium: recovery can repeat expensive failures and change page position (static)

Sweep keeps trying after provider exceptions/unavailability and scrolls only downward; it neither detects unchanged bottom-of-page captures nor restores the starting position after failure. The existing exception test confirms eight provider attempts. Combined with the provider's retry, a sweep can issue up to sixteen model calls. There is no explicit cancellation/deadline parameter on this sweep interface.

Fix: stop on policy denial/unavailability, propagate cancellation, set an elapsed-time/call budget, wait for scroll settlement, deduplicate unchanged frames, and restore the initial scroll after a miss. Add nested-scroll support separately from page scrolling.

### G6 — Medium: generic DOM change is not semantic success (static)

`DomPostClickValidator` treats any URL/title change or removed login form as OK, and hashes only the first 400 normalized body characters. It is non-strict by default. Correct checkbox/tab/cart changes can also produce no detectable change in those signals.

Fix: verify an intent-specific outcome (selected state, expanded dialog, matching route, cart count, validation error, explicit expected result). Keep generic changes as supporting evidence. Do not simply enable strict mode globally; it would reject valid actions whose effects are not covered by the heuristic.

### G7 — Medium: grounding diagnostics need tenant scope and sanitation (static)

`VisionMissJournal` defaults to a global `delivery-store/vision-misses` directory and writes intent, descriptions, target hints and optional raw snippets without a local sanitation call or tenant/job identity. It is a distinct sink from screenshot redaction. Exposure depends on actual contents and access; no real secret leak is claimed.

Fix: inject tenant/project/job evidence paths, sanitize before writing, apply retention and access controls, and test canaries. Keep useful diagnostic records (raw coordinate form, transformed point, actual DOM identity, mismatch reason) attached to the right attempt.

## Further enhancements

- Preserve the correct existing actual-PNG dimension handling in `ViewportSweep`; do not blindly multiply by DPR again.
- Calibrate confidence. Native action parsing assigns 0.85, so passing a 0.7 threshold is not measured model certainty. Record synthetic versus model confidence separately.
- Test wide mobile controls: the 22% viewport-width quality threshold can reject a legitimate full-width button represented by a real bounding box. Point-shaped output and box-shaped output currently face different practical acceptance behavior.
- Add iframe/shadow-root context support. Current top-document hit-testing and ancestor traversal cannot identify arbitrary controls inside those contexts.
- Base retries on observed DOM mismatch. Current retry inspects the model description, so a confidently mislabeled Login point on username does not trigger that retry.
- Freeze grounding model, preprocessing version, coordinate contract and generation parameters with the job. Include them in proof-reuse invalidation where applicable; verify the current snapshot integration before implementing another parallel mechanism.
- Measure correct-element grounding rate, wrong-target rejection rate, abstention rate, locator replay survival, and latency separately. Do not infer grounding accuracy from final suite pass rate.

## Verification

Command: `mvn -B "-Dtest=GroundingAuditCharacterizationTest,delivery/vision/**/*Test,!*LiveSmokeTest" test`

Result: **118 tests, 0 failures, 0 skipped**, BUILD SUCCESS, 20 September 2026 13:47:58 +03:00. Install drills were skipped as configured. The four new audit tests all ran.

`GroundingAuditCharacterizationTest` asserts current faulty behavior to reproduce G1/G2/G3. A passing characterization test is evidence of a defect, not a safety gate. When fixing these paths, replace the assertions with expected safe behavior. No production grounding code was changed. No fresh UI-TARS inference, Medusa installation, live browser race test or full customer replay was performed in this audit.

## Medusa benchmark design

Pin a Medusa starter revision and seed repeatable disposable users, several products with size/color variants, stock states, and shipping/payment test configuration. Reset test data between destructive scenarios. Keep local fixture access in an explicitly isolated development setup; do not weaken shared-production destination restrictions globally.

First workflow pack:
1. Account creation, login/logout, invalid login, duplicate registration, required-field validation.
2. Browse collections and choose the correct variant among repeated option labels.
3. Add two products, change quantities, remove the second of two similarly labeled cart controls.
4. Complete address/shipping/test-payment checkout and verify the exact order summary.
5. Open account/order history and confirm the correct order, not just a changed page.
6. Admin edits product/stock; storefront reflects the change (separate credentials and explicit origin scope).
7. Trigger a modal/overlay, scroll below the fold, and repeat at multiple viewport sizes/zoom settings.
8. Change a fixture locator while retaining the visible target; confirm vision recovers the correct element and the emitted locator survives downloaded replay.

Run DOM baseline and deliberately triggered vision recovery separately. Keep labeled expected target identities outside the model input as the evaluation oracle. Save sanitized screenshots, model output, coordinate transforms, actual hit element, emitted locator and execution result for every attempt. Include rejected/uncertain cases and repeated runs; successful cherry-picked attempts do not establish reliability.

## Recommended implementation batches

1. Correct intent compatibility and DOM identity (G1/G2), plus fail-safe tests of both prove/heal integration.
2. Explicit coordinate contracts, preserved points and snapshot revalidation (G3/G4).
3. Bounded recovery, diagnostic scoping and semantic outcome verification (G5/G6/G7).
4. Run the Medusa workflow pack, then tune prompts/preprocessing from measured failures. Keep UI-TARS in its intended grounding role throughout.

These improvements require a new development revision and fresh acceptance evidence. They do not silently replace the pinned launch candidate or close deployment gates.

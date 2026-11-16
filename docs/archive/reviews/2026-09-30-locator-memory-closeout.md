# Locator-memory assertion reuse

Completed locally on the working tree; no commit or launch certification.

Simple named-field visibility/absence assertions can reuse a saved typing locator for the same field, host and path. The operation is freshly constructed as `assert` with `visible` or `notVisible`; stored typing values/actions are not carried into the assertion. Conflicting targets reject reuse. Compound/text assertions still bind normally. Legacy untagged CSS and input-tagged aliases prefer the narrower input selector.

Validation on 2026-09-30:
- Focused deterministic regression: 71 tests, zero failures/errors/skips (`target/locator-memory-complete.log`).
- Local headless Chrome: one test, zero failures/errors/skips (`target/locator-memory-live.log`). Real `TcExecutionService` asserted visible successfully, rejected notVisible while the input was visible, then passed notVisible after the fixture hid the input. No model or remote app call.
- Chrome could not start inside the sandbox; the approved outside-sandbox rerun passed. Existing Chrome/CDP mismatch warning remains.

Portal restart is required. This closes simple field-assertion reuse, not a general semantic cache for arbitrary compound assertions or a guarantee that every model call disappears. Existing jobs retain their frozen inputs and loaded code.

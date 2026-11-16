# Live UI-TARS smoke — root cause (2026-09-17)

Candidate **`2721e6d` is unchanged.** These failures are **not** a first-release support gate.

## What failed

On a clean `23f9351` default `mvn test` (LiveSmoke **included**; unlike CI `-Pdeterministic`):

| Test | Observation |
|------|-------------|
| `UiTarsLiveSmokeTest.uitarsFindsLoginControlAndHonestAssertDoesNotPlaceholderPass` | `elementFromPoint` hit `input#username` |
| `VisionRoleSplitLiveSmokeTest.uitarsGroundsLoginAndQwenAssertsHonestly` | same miss |

Ollama was healthy (`ui-tars`, `qwen2.5vl:3b`). Intent was “Click Login” on `https://the-internet.herokuapp.com/login`.

## Root cause

The mapper is deterministic (`CoordinateMapper.center` of the model bbox, then screenshot→CSS scale). The **model click box is not**. The same tests have also **passed** on this workstation (notes at 2026-09-17T16:44Z / 16:46Z): bbox ~36×36 around the Login control, `elementFromPoint` = `button` “Login”. Hours later the model returned a box whose center landed on the username field while still labeling the action “Login”.

That is live spatial jitter from `ui-tars`, not a shared-host isolation or G→E→A contract break. Execute/Automate’s default path is DOM/locator proof, not live vision click-grounding.

## First-release configuration (restriction)

**Supported for first launch:** DOM Execute / Automate on an authorized public origin in **shared** mode; CI `-Pdeterministic` (excludes `*LiveSmoke*`).

**Not supported for first launch (implementation preserved):** live UI-TARS click-grounding and live vision role-split smoke as a product guarantee. Do not advertise herokuapp Login grounding as a launch acceptance criterion. Do not treat a default `mvn test` LiveSmoke miss as a reason to replace `2721e6d`.

A code “fix” (retries, username rejection heuristics) would be a new candidate SHA. That is deferred unless product later promotes live vision grounding into the support contract.

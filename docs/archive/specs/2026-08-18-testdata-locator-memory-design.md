# Test data column, locator memory, and Facebook-run honesty

**Date:** 2026-08-18  
**Status:** Locked (user approved: implement after plan)  
**Evidence job:** `facebook-com-20260818-153010` (2 PASSED / 5 PARTIAL)

## Root causes from that run

1. **Submit CTA** — Excel “Click the Submit button” has distinctive token `submit`. Facebook’s primary CTA is Sign Up / `websubmit`, so bind refused. Vision Layer 1.5 then grounded “I already have an account”. Heal retry clicked `a[href='https://web.facebook.com/reg/']` (same-page nav). Semantic gate only rejected `/login`, so REG_02/05 PASSED without submitting.
2. **Visual assert demotion** — Body steps succeeded on `/reg/`. Qwen returned UNCERTAIN (conf 0.5) or FAIL (conf 0.0). `applyVisualAssertion` demotes any non-PASS, which is why five TCs are PARTIAL.
3. **Dummy names** — REG_02 Excel said `Enter Test` / `Enter User`. Inventor treats `Test` as a literal. User lock: steps will not embed Alice/Test; optional **TestData** column holds values; blank → Faker / `*@example.com`.
4. **No locator reuse** — `locator-map.json` is written at emit and not consulted during prove. REG_02 rediscovered First name after REG_01 already proved it.

## Locked decisions

| Topic | Decision |
|---|---|
| Form Submit | Bind/heal/vision may only land on a **form submit control** (submit / websubmit / Sign Up / create account / register **button**). Reject login hrefs, “already have an account”, and `<a href>` to the Excel open-path (`/reg/`) unless the label is a submit CTA. |
| Semantic gate | Submit click whose locator is a non-submit nav (login **or** self-path `/reg/`) → reject PASS. |
| Visual assert | **FAIL with contradiction** (e.g. registration claim on `/login`, or FAIL with confidence ≥ 0.7) demotes. **UNCERTAIN** and **low-confidence FAIL** keep PASSED; evidence is still written. |
| TestData column | Optional Excel column `TestData` (aliases: `Test Data`, `StepData`). Line-aligned with `Steps`. Not required. |
| Typed values | Column line if concrete → else invent from field identity. `Test` / `User` / `foo` / `fname` are never typed. Select options may still appear in the step (`Select 15 from the Day dropdown`) or in TestData. |
| Locator memory | Key = registrable **host + Excel open-path**. After a successful execute, remember `intentKey → strategy/locator/action`. Next intent/TC/job: try memory first; on execute fail, drop slot and run bind → vision → heal; then overwrite. |

## Non-goals

- UI-TARS native action schema.
- Pixel `moveByOffset` in generated tests.
- Dual-VLM ensemble.
- Committing unless the user asks.

# Codegen page reuse, void actions, and assertion helpers

**Date:** 2026-08-17  
**Status:** Approved  
**Scope:** Customer ZIP codegen + customer-framework validation helpers (generic; no site hardcoding)

## Problem

1. **TC login click fails** when emit picks a non-control locator (e.g. form `id="login"`) while another proven step already has a working submit button locator (`button[type='submit']`).
2. **Duplicate page classes** (`Login_*` and `LoginForm_*`) for the same username/password/submit screen — TC body vs `@BeforeMethod` prelude use different page-name stamps.
3. **Fluent page methods** (`return this`) even though generated tests never chain; user wants **void** action/assert methods.
4. **Invented assertion bodies** in Freemarker (inline `body.getText()` + `contains`) instead of reusing / extending `project.validations.Assertion` and `ElementsHandler`.

## Goals

- No duplicated page classes for the same screen/controls.
- Page action and assert methods are `void`.
- Generated asserts are one-liners that call validation helpers (existing when enough; new helpers only when needed).
- Prefer the **interactive** locator when two candidates describe the same named control (button/submit over wrapping form).

## Non-goals

- Changing prove/heal cascade scoring beyond what is required to stamp/normalize page names and prefer button locators at emit.
- Rewriting hand-written customer tests outside the template.
- Fluent API support (explicitly removed).

## Decisions

| Topic | Decision |
|-------|----------|
| Page naming | **B:** Keep URL/path stem from prove; alias legacy names (`Login`, `LoginForm`, `TargetLogin`, blank/`Page`) into that stem so prelude + TC body share one page. |
| Dedup | Emit-time merge by locator identity (`strategy` + `value`); never emit a second page for the same controls. |
| Competing click locators | When merging fields that share the same semantic button role (same action method / field stem collision), keep the locator that looks like a **control** (button/submit/input[type=submit]/ over a container/form id. |
| Method returns | All generated action and assertion methods are `void`. |
| Assert helpers | Use existing `elementVisable`, `Equals`, `verifyUrl`, `softTrue` where they fit; add thin helpers on `Assertion` for `textContains`, `urlContains`, selected/unchecked/notVisible as needed. Generated page methods only call helpers. |

## Design

### 1. Canonical page stem

Before `PageAccumulator` runs (or at the start of emit):

- Resolve each step’s page name through a single normalizer:
  - If name is blank, `Page`, `Login`, `LoginForm`, or `TargetLogin` (case-insensitive for Login*), replace with the TC’s proven URL stem (`PageClusterer.pageNameFromUrl`), falling back to `Login` only if the stem is empty/`Page`/`Home` **and** the step is login-shaped (has username/password/submit fields in the same TC cluster). Prefer URL stem always when available.
- Apply the same normalizer to `loginSteps` and `provenSteps` so `@BeforeMethod` and body share one stem.
- Remove the special case that **renames** `"Login"` away from a shared login page while leaving `"LoginForm"` as a different stem (current `stampPageNames` / `stampLoginNames` split).

**Invariant:** One URL stem → one `Stem_Locators` + one `Stem_Actions`.

### 2. Locator merge preference

In `PageAccumulator` (or a small helper used by it):

- Continue dedupe by identical `strategy`+`value`.
- When two **different** locators would produce the same field/method name for a **click** (e.g. `login_Btn_Locator` vs uniquified `login_2_Btn_Locator`), prefer keeping a single field and choose the better locator:
  - Prefer: tag/role/css suggesting button or `type=submit` / `type=button`.
  - Deprioritize: locator that matches only a form/container id equal to a common form name without button semantics.
- Do not invent site-specific ids; heuristics stay generic (submit/button/input types, role=button).

### 3. Void codegen

Update `customer-framework-template/templates/PageActions.java.ftl`:

- Action methods: `public void name(...)` — no `return this`.
- Assert methods: `public void name()` — no `return this`.
- Tests already call methods as statements; no Freemarker test template change required beyond ensuring no chaining is generated (today there is none).

### 4. Assertion helpers

Extend `customer-framework-template/.../validations/Assertion.java` (and mirror in main TAF if the same class exists for local runs):

| Assertion type | Helper |
|----------------|--------|
| `visible` | existing `elementVisable(By)` |
| `textContains` | **new** `textContains(By locator, String expected)` — use `ElementsHandler.getText` (and/or wait) + contains; optional overload `bodyTextContains(String)` if prove still validates primarily via body text |
| `urlContains` | **new** `urlContains(String expected)` (or soft variant) — thin wrapper over current URL check; keep `verifyUrl` for exact match |
| `selected` / `checked` | **new** `elementSelected(By, String expectedOptionOrEmpty)` |
| `unchecked` | **new** `elementUnchecked(By)` |
| `notVisible` | **new** `elementNotVisible(By)` |

`PageActions.java.ftl` assert branches become single helper calls (plus `softTrue` only inside helpers, not inlined in the page).

Prove-phase `TcExecutionService` may keep its own wait/body logic for honesty gates; **emit** must not copy that logic into pages.

### 5. Tests

- Unit: page-name normalizer aliases → one stem.
- Unit: PageAccumulator prefers submit-button locator over form id when field names collide.
- Unit/template smoke: generated action signature is `void` (string assert on processed template or small golden).
- Unit: Assertion helpers behave for textContains / urlContains (no Selenium if mockable; otherwise keep helpers thin and test naming/codegen wiring).

## Out of scope for follow-ups

- Portal UI toggle for fluent vs void.
- Auto-refactor of already-emitted ZIPs in `delivery-work/` (re-run conversion).

## Success criteria

- Re-running the-internet (or equivalent) job yields **one** login page class pair, not `Login_*` + `LoginForm_*`.
- Login click field uses a submit/button locator when both were seen.
- Generated `*_Actions` methods are all `void`.
- No Freemarker block that reads `By.tagName("body")` for asserts; helpers own that if needed.
- TC that types wrong password and clicks login can reach the invalid-password text assert when the site shows it (prove + emit aligned on the working button).

## Spec self-review

- [x] No TBD/placeholder sections left for implementers.
- [x] No contradiction: void + helper-based asserts + URL-stem merge all align.
- [x] Scope limited to codegen templates, page naming stamp/normalize, accumulator merge preference, Assertion helpers.
- [x] Ambiguity resolved: body-text vs element text — helper may include body overload; pages call helper only.

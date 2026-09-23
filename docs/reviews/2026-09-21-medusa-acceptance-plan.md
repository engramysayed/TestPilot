# Medusa local acceptance plan — 21 September 2026

Plan only. **Not executed.** No shop was installed. No Automate/Execute job was run against Medusa. No optional product features were added.

This is the next representative-app acceptance after the codegen batch was marked locally validated. It does **not** close launch gates, replace validation candidate `2721e6d`, or move first-release off **HOLD**.

Related: `docs/reviews/2026-09-20-grounding-review.md` (shop choice and broader workflow pack). This document is the concrete first slice: account/login, variants, cart, checkout validation, order confirmation — plus engine setup.

## Constraints

- Synthetic customers, products, and addresses only. No production Medusa Cloud. No third-party shop.
- **No real payments.** Do not set live Stripe keys. Do not enter real cards. Use Medusa’s built-in **system / manual** provider (`pp_system_default`).
- Keep Keel destination restrictions. Local `http://127.0.0.1:8000` is an isolated development origin, not a reason to weaken shared-production policy.
- Record engine, providers used, and Precision fallback on every job. A PASS after unreported Cursor fallback is not a Precision result.

## Local prerequisite check (this workstation, 21 September 2026)

Checked on the same machine that ran `mvn -B -Pdeterministic test`.

| Prerequisite | Status | Detail |
|---|---|---|
| Java | **Ready** | `21.0.12.1` LTS |
| Maven | **Ready** | `3.9.16` |
| Git | **Ready** | `2.55.0.windows.2` |
| Chrome | **Ready** | `153.0.8010.53` (`C:\Program Files\Google\Chrome\Application\chrome.exe`) |
| Node | **Ready with caveat** | `v24.21.0` / npm `11.19.0`. Medusa docs ask for Node **20.19+ or 22.12+ LTS, &lt; 25**. 24 is allowed by the upper bound but is not the listed LTS. If `create-medusa-app` fails, install Node **22 LTS** and retry. |
| Ollama CLI | **Ready** | `0.34.2` |
| Ollama HTTP | **Ready** | `127.0.0.1:11434` listening |
| Ollama models | **Ready** | `ui-tars:latest`, `qwen2.5vl:3b`, `gemma4:e2b`, `qwen2.5:latest` |
| `tools/cursor-heal/heal.mjs` | **Ready** | Present in the repo |
| Corepack | **Ready** | `0.36.0` (pnpm not on PATH yet; enable when installing the shop) |
| winget | **Ready** | Present (use for PostgreSQL if Docker stays unused) |
| PostgreSQL | **Missing** | `psql` not on PATH; no postgres Windows service found; port **5432** not listening |
| Docker / Compose | **Missing** | `docker` not on PATH |
| pnpm on PATH | **Missing** | Needed only if the cloned starter’s lockfile is pnpm; `corepack enable` / `corepack prepare pnpm@latest --activate` |
| Medusa checkout | **Missing** | No `dtc-starter` / Medusa monorepo next to this repo |
| Storefront :8000 | **Not running** | |
| Backend / admin :9000 | **Not running** | |
| Keel portal :8080 | **Not running** at check time | |
| `CURSOR_API_KEY` | **Unset** | Precision and Cursor invent **cannot** call Cursor until this is set in the **portal process** environment. Value not printed. |
| `AGENTROUTER_API_KEY` | **Unset** | Expected: unused. `delivery.final-revise.enabled=false`. Do not enable for this pack. |

Keel `application.properties` already points at local Ollama (`delivery.llm-base-url=http://127.0.0.1:11434`), `delivery.vision.grounding.enabled=true` / model `ui-tars`, assert model `qwen2.5vl:3b`, `delivery.authoring.precision.enabled=true`, `delivery.cursor-heal.enabled=true`. Flags being true is **not** proof that a live job used those providers.

## Shop setup (do this before any Keel job)

Prefer **PostgreSQL + `create-medusa-app`**. Docker is optional; it is **not** installed here.

1. Install PostgreSQL 16 locally (example): `winget install PostgreSQL.PostgreSQL`. Create role/database `medusa` with a **synthetic** password. Confirm `127.0.0.1:5432`.
2. If Node 24 fails the installer, switch to Node 22 LTS.
3. From a directory **outside** this git repo (do not dump `node_modules` into TestPilot):

   ```text
   npx create-medusa-app@latest keel-medusa-acceptance --with-nextjs-starter
   ```

   Official sources: [create-medusa-app](https://docs.medusajs.com/resources/create-medusa-app), [Next.js starter / monorepo](https://docs.medusajs.com/resources/nextjs-starter), [dtc-starter](https://github.com/medusajs/dtc-starter). Record the created app git SHA / package versions in the run log.

4. Start backend (`apps/backend`, typically `:9000`) and storefront (`apps/storefront`, `:8000`). Admin: `http://localhost:9000/app`.
5. **Payments (mandatory):** do **not** set `NEXT_PUBLIC_STRIPE_KEY` or a live Stripe secret. In Admin → Settings → Regions, enable only the **system / manual** provider. Storefront checkout should use `pp_system_default` (“manual payment; no additional customer charge”). If the starter still offers Stripe UI, leave Stripe unpublished and confirm checkout never reaches a card form.
6. Seed **synthetic** catalog and users (names below). Reset between destructive cart/checkout runs.

### Synthetic seed (no real PII, no real money)

| Kind | Value |
|---|---|
| Shop origin | `http://127.0.0.1:8000` (Keel project base URL) |
| Admin (shop) | `admin@keel-medusa.test` / generated local password |
| Customer A | `buyer.a@example.test` / `SyntheticPass_A1!` |
| Customer B (duplicate-register) | same email as A |
| Invalid login | `buyer.a@example.test` / `WrongPass_xxx` |
| Address | `1 Test Pilot Lane`, `Testville`, `00000`, country matching the seeded region |
| Products | At least two SKUs; one with **size and color** variants that repeat the same option labels; enough stock for two cart lines |
| Payment | system/manual only |
| Order assert | exact confirmation id / “Order #…” / email `buyer.a@example.test` — not merely “URL changed” |

## Acceptance pack (first slice)

Hand-authored Excel/CSV in the Keel project. Do not rely on Generate for the scored run (Generate is a separate Ollama path). Expected results must name the **business outcome**, not “page changed.”

| ID | Flow | Must prove |
|---|---|---|
| M-ACC-01 | Create account | New customer can register with synthetic email; success lands in an authenticated storefront state. |
| M-ACC-02 | Login | Same user signs in; account/header shows that identity. |
| M-ACC-03 | Invalid login | Wrong password stays unauthenticated; visible validation, not a later-page false PASS. |
| M-ACC-04 | Duplicate register | Existing email is rejected. |
| M-VAR-01 | Variant pick | On a product with repeated size/color controls, select a **specific** size+color (oracle recorded outside the model). Cart line must match that variant, not the first same-label control. |
| M-CART-01 | Add two products | Two distinct lines. |
| M-CART-02 | Quantity change | Change qty on one line; line total and cart count match. |
| M-CART-03 | Remove the second similar control | Remove the **second** of two similarly labeled remove/qty controls; the other line remains. |
| M-CHK-01 | Checkout validation | Submit address/shipping with a required field blank; stay on checkout; error visible. |
| M-CHK-02 | Place order (manual pay) | Valid address + shipping + **system payment**; no card UI. |
| M-ORD-01 | Confirmation | Order confirmation shows the synthetic email and the expected lines/totals. Account order history shows **that** order, not only a generic account page. |

Out of this first slice (still **open** on the grounding review pack): admin stock edits, overlays/zoom matrix, locator-break + vision recovery + downloaded replay of a broken locator. Do not quietly expand into those during the first run.

After a green DOM automations ZIP: extract and `mvn -B clean test` against the same local shop (downloaded replay). Then an UPDATE that changes one cart/checkout step and one unchanged login case, and replay again. **That** UPDATE is production reuse + downloaded replay — still not the fixture ZIP benchmark.

## Engine matrix (three scored modes)

Use **one Keel project** (base URL `http://127.0.0.1:8000`). Change only the project authoring engine and the vision flag. Re-seed data between modes. Chrome may be headed (`delivery.browser.headless=false` in current properties) — keep the shop and portal on this machine.

| Mode | Project setting | Vision flags for the run | Goal |
|---|---|---|---|
| **A — Keel DOM** | Authoring engine **Keel** | `delivery.vision.grounding.enabled=false` for this process (override / temp properties). Assertions off unless a case explicitly needs Qwen. | Baseline locators from DOM bind. Heal still allowed but **do not** treat Cursor invent as Keel-DOM success if it fired — log providers. Prefer cases that bind without heal. |
| **B — UI-TARS grounding** | Authoring engine **Keel** | Grounding **on** (`uitars` / `ui-tars`). This is Layer 1.5 after a **weak/failed DOM bind**, plus heal bbox. It is not a separate radio in the UI. | Force or capture at least one vision-grounded step (weak bind or heal). Keep a labeled oracle of the real DOM id/test-id. A generic DOM-change post-click is not confirmation. |
| **C — Precision + reported fallback** | Authoring engine **Precision** | Grounding **off** for the first Precision pass so Cursor vs Keel fallback is not mixed with UI-TARS. | With `CURSOR_API_KEY` set: Cursor `groundRank` / `solve`, then browser execute. If Cursor is missing/fails/cap: Keel bind **and** job message / status banner / IR `precisionFallback` + reason (`PROVIDER_UNAVAILABLE`, `PROVIDER_ERROR`, `CAP_EXCEEDED`, …). |

### Mode C fallback drill (required even if Cursor later works)

1. Run Precision **with `CURSOR_API_KEY` unset** (current machine state). Expect Keel takeover and a visible `PRECISION_FALLBACK` notice (`PrecisionFallbackMessage`, status page banner “Precision engine fell back to Keel…”). Record `providers used` and Fallback on `/status`.
2. Optionally set the key and rerun the same workbook. Compare providers used. Do not call (1) a Precision-bound result.

## Which modes require which model calls

“External” here means **off-box vendor HTTP** (Cursor Auto sidecar / AgentRouter). Local Ollama is still a **model call**, but it is not a cloud vendor.

| Path | Model calls? | Where |
|---|---|---|
| Keel DOM **bind** (`AuthoringService` / candidate table) | **No** | Deterministic DOM |
| Keel **Generate** stories → CSV | **Yes, local** | Ollama `gemma4:e2b` / `qwen2.5:latest` at `127.0.0.1:11434`. Skip for the scored pack; hand-write cases. |
| Keel **heal invent** (default `delivery.heal.invent.provider=cursor`) | **Yes, external** if the sidecar is runtime-ready (`CURSOR_API_KEY` + allowlist) | `tools/cursor-heal/heal.mjs` → Cursor Auto |
| Keel heal invent if Cursor not ready | **No Cursor call**; invent skipped or fails closed depending on cascade | Current machine: key unset → Cursor **not** runtime-ready |
| UI-TARS grounding / heal bbox | **Yes, local** | Ollama `ui-tars` vision chat (PNG). Not Cursor. |
| Qwen visual assertions | **Yes, local** | Ollama `qwen2.5vl:3b`. Leave off unless a case needs a visual expected. |
| Precision `groundRank` / `solve` | **Yes, external** | Same Cursor sidecar. Requires `CURSOR_API_KEY`. |
| Precision **fallback bind** | **No** for the Keel bind itself | Then the usual Keel heal/vision rules apply if those flags are on |
| AgentRouter / final revise | **Yes, external** | **Do not enable** (`delivery.final-revise.enabled=false`, key unset) |
| Downloaded ZIP `mvn test` | **No authoring models** | Replay of proven locators against the shop |

Mode A can complete **without any model** if every step DOM-binds and execute passes. Mode B **requires local UI-TARS** for the grounding attempts you score. Mode C **requires Cursor** for a true Precision bind; without the key it is a **fallback drill**, which is still in scope.

## Run order

1. Finish shop setup and a **manual** walk of M-ACC through M-ORD with system payment (human oracle).
2. Start Keel (`mvn -q spring-boot:run` or the usual portal command) with the intended property overrides for that mode.
3. Create project base URL `http://127.0.0.1:8000`. Import the workbook. Set engine radio (Keel vs Precision) on project settings.
4. Automate **NEW** → wait COMPLETED → download ZIP → `mvn -B clean test` in the extract against the still-running shop.
5. Repeat for modes A, B, C (including C with key unset).
6. One UPDATE + download + `mvn -B clean test` on mode A at minimum.
7. File a results note (new review doc). Do **not** treat success as a launch un-HOLD. Do **not** replace `2721e6d`.

## Evidence to capture per job

- Engine radio, vision flags, whether `CURSOR_API_KEY` was set (boolean only).
- Job message, `PRECISION_FALLBACK` presence, IR `precisionFallback` / reason, status “providers used.”
- For vision steps: sanitized screenshot, model text, transformed point, actual hit element, emitted locator, execute result, oracle id.
- Nested Maven result of the downloaded ZIP.
- Confirmation that checkout never opened Stripe/live card UI.

## Non-goals

- Implementing Medusa inside this repository.
- Enabling real Stripe, AgentRouter, or final revise.
- Closing iframe/shadow, naming polish, suffix allocation, or complete redaction (those stay **open / out of scope** on the codegen close-out).
- Commit, publish, candidate replace, or launch-gate close.

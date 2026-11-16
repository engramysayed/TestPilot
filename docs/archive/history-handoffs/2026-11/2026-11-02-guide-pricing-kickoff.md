# Kickoff — Portal Guide + Pricing refresh (2026-11-02)

**Tip base:** c93aa6d (confirm HEAD before edit)  
**Assign:** FE (primary) + Backend (pricing seeds / covers)  
**Constraints:** no push/merge/force-push without user OK; Keel ≠ Sonic; serialize Maven; live tree `D:\Work\projects\Keel\keel\TestPilot`; everyday English (match plain-English UI copy pass); handoffs under `docs/handoff/`.

## Where things live today

| Surface | Path | Route |
|--------|------|-------|
| Product Guide | `src/main/resources/templates/guide.html` | `/guide` |
| TC authoring (embedded in Guide) | same file `#tc-authoring` … `#visual` | `/guide#…` (`/tc-guide` redirects) |
| Pricing (customer) | `src/main/resources/templates/pricing.html` | `/pricing` |
| Admin prices | `src/main/resources/templates/admin-pricing.html` | `/admin/pricing` |
| Prices API | `delivery.portal.api.PricingController` | `GET /api/pricing` |
| Price rows | `ServicePrice` + `ServicePriceService.ensureSeeded` | codes `GENERATE` `EXECUTE` `AUTOMATE` `HUNT` |
| Default costs | `DeliveryCreditsProperties.Cost` | generate=10, execute=5, automate=25, hunt=15 |
| Billing kinds | `CreditsBillingKind` | only those four (job-gated) |

**Gap:** Guide TOC/overview still centers Generate / Record / Execute / Automate / Hunt. Missing product sections for shipped work: Generate-from-knowledge, plain-English cases, visual diffs (approve UX), Evidence heal Accept/Reject + flaky, Suites / schedules / alerts / monitor, Automate codegen W1–W3 notes. `#visual` today is only **authoring** “visual check” wording, not the Prove/compare approve flow.

**Pricing gap:** Customer page is a flat 4-row table. Covers/detail text do not mention GFK, suites/monitor, heal review, or visual diffs. No tier / add-on presentation.

---

## FE (primary) — definition of done

1. **Guide (`guide.html`)** — everyday English:
   - Update overview lede + product cards / TOC with new sections (keep existing sections; do not delete authoring deep-dive).
   - Add concise sections (with anchors) covering at least:
     - **Generate from knowledge** → `/generate-from-knowledge` (separate from classic Generate).
     - **Plain-English cases** → `/plain-cases`.
     - **Evidence** — heal Accept/Reject, flaky tags (link from Execute / runs).
     - **Visual diffs** — prove/compare + approve UX (distinct from authoring `#visual`; use a new id e.g. `#visual-diffs`).
     - **Suites** — tags, schedules, alerts, promote-to-monitor → `/suites`.
     - **Automate / packages** — short note that builds include multi-locator heal, semantic ladder, NL By cache (W1–W3) without dumping engineer jargon.
   - Match tone of the plain-English UI copy pass (short sentences, “you”, no help-desk speak).
   - Keep AI→CSV prompt block working if present.

2. **Pricing (`pricing.html`)** — clear tiers / add-ons:
   - Keep the live `prices` table as source of truth for billed units.
   - Add a short **Plans / what’s included** block above or beside the table that maps new capabilities into everyday language (which base service includes them vs optional add-on rows Backend seeds).
   - If Backend adds draft add-on codes, show them with a visible **Draft** badge / note that Owner can edit credits on Admin → Prices; do **not** present draft numbers as final.
   - Light CSS only; reuse existing panel/table styles.

3. Handoff: `docs/history/handoffs/2026-11/2026-11-02-guide-pricing-fe.md` (paths changed, how to verify: `/guide`, `/pricing`).

---

## Backend — definition of done

1. Refresh **covers / labels / detail** strings in `ServicePriceService` for the four core codes so they mention included capabilities in plain English (GFK under Generate where accurate; heal/flaky/evidence under Execute; stronger Automate wording for codegen quality; Hunt unchanged unless needed).
2. Propose **draft add-on rows** (seed via `ensureSeeded` / rename helpers) for capabilities that are not already a JobKind charge — suggested codes (adjust if better names fit existing style):
   - `VISUAL_DIFF` — visual prove/compare reviews  
   - `SUITES_MONITOR` — scheduled suite runs / monitor alerts  
   - `HEAL_REVIEW` — heal Accept/Reject assist (if not folded into Execute)  
   - `GFK` — generate-from-knowledge job (if billed separately from classic Generate; otherwise fold into GENERATE covers only and skip a row)
3. **Placeholder credit costs** — pick sensible drafts relative to current defaults (e.g. near execute=5 / automate=25). In owner handoff, list every new code + draft cost and mark **DRAFT — user must confirm before treating as final**. Do **not** wire new codes into `CreditsBillingKind.fromJobKind` / job start charging unless a real JobKind already exists; display + admin-editable is enough for this slice.
4. Admin pricing page should list new rows automatically via existing `prices` model (no FE admin rewrite unless broken).
5. Handoff: `docs/history/handoffs/2026-11/2026-11-02-guide-pricing-backend.md` including the draft price table.

---

## Out of scope / hold
- Prompt-file extraction (separate locate-only inventory; wait for user green-light).
- Postman/API product surface.
- Sonic.
- Push / merge.

## Verify
- Tip still on live tree; no concurrent Maven with QA.
- After both GREEN: Maestro portal jar refresh (`start-portal.cmd`, serialize Maven `-Dmaven.test.skip=true`), smoke `/guide` + `/pricing` (and `/admin/pricing` as Owner).
- Report GREEN + handoff paths to Maestro; Maestro → Leader.

## Parallel
OK alongside prompts inventory (locate only). FE and Backend may work in parallel on tip base; coordinate if both touch `ServicePriceService` vs templates only.

---

## CORRECTION 2026-11-02 (user via Leader) — Credits only

- **Credits, NOT dollars.** No USD amounts, no paid dollar tiers/plans.
- Pricing is **admin-controlled credit unit costs** on `/admin/pricing` (`ServicePrice` rows: label, charged-as, covers, credits each).
- Customer `/pricing` shows the same credit table.
- Guide + pricing UI updates must explain **which actions cost credits** and that Owner edits prices/grants balance in admin — everyday English.
- Backend: refresh covers for GENERATE/EXECUTE/AUTOMATE/HUNT; optional DRAFT credit add-on seeds only (flag for user tweak). **Do not** invent USD; **do not** wire new codes into job debit without a real JobKind.

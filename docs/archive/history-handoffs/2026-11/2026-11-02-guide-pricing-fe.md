# Guide + Pricing FE refresh (credits-only) — 2026-11-02

**Status:** GREEN (templates only; portal jar refresh owned by Maestro after Backend GREEN too)  
**Bot:** Frontend (live-apply on Ramy)  
**Keel ≠ Sonic**  
**Tip SHA before edit:** `c93aa6d092f516e9e47aad8bd5233d23863d8be8`  
**Tip SHA after edit (working tree dirty OK; no commit/push):** `c93aa6d092f516e9e47aad8bd5233d23863d8be8`  
**Backup:** `D:\Work\projects\Keel\keel\TestPilot\_backup\backup-pre-guide-pricing-20261102-211346\` (`guide.html`, `pricing.html`)  
**Time (Africa/Cairo):** 2026-11-02 ~21:13–21:25  

**Constraints:** no push / merge / commit; templates only (no Java/API renames); credits only — no dollars / USD tiers / paid plans.

## Kickoff
`docs/history/handoffs/2026-11/2026-11-02-guide-pricing-kickoff.md` (+ Leader correction: credits-only).

## What changed

### 1) `src/main/resources/templates/guide.html`
- Hero lede + overview intro/cards/TOC updated for shipped surfaces.
- **Kept** all existing sections including TC authoring deep-dive (`#tc-authoring` … `#visual`) and AI→CSV prompt (`#ai`, `guide-ai-copy-btn`, `/prompts/keel-tc-rewrite-to-csv.txt`).
- **New sections / anchors:**
  | Anchor | Topic | Route / notes |
  |--------|--------|----------------|
  | `#generate-from-knowledge` | Generate from knowledge | `/generate-from-knowledge` (separate from classic Generate) |
  | `#plain-cases` | Plain-English cases | `/plain-cases` |
  | `#evidence` | Evidence heal Accept/Reject + flaky | links Execute / runs / Evidence |
  | `#visual-diffs` | Visual prove/compare + approve | distinct from authoring `#visual` |
  | `#suites` | Tags, schedules, alerts, promote-to-monitor | `/suites` |
- **Updated** `#automate` / `#packages` with everyday note on stronger builds (multi-way finds, clearer names, remembered plain-language locators) — no engineer jargon dump.
- Credits callouts map features → Generate / Execute / Automate / Hunt; Owner edits credit prices on Admin → Prices.
- Light reuse of existing guide panels/callouts only.

### 2) `src/main/resources/templates/pricing.html`
- Credits-only lede (Credits each; not dollars; Owner edits Admin → Prices).
- New **What uses which credits** block mapping GFK / plain cases / Evidence heal / visual diffs / suites-monitor / stronger packages onto Generate / Execute / Automate / Hunt.
- Live `th:each="row : ${prices}"` table kept as source of truth.
- **Draft badge** when `row.code` is `VISUAL_DIFF` / `SUITES_MONITOR` / `HEAL_REVIEW` / `GFK`, or label contains “draft”; draft credit amounts marked provisional — not presented as final.
- Note under table: no dollar/USD pricing; draft rows for Owner tweak.
- Light scoped CSS only.

## Model note / limitation
`ServicePrice` / `ServicePriceService.toMap` expose `code`, `label`, `unit`, `detail`, `cost` — **no `draft` boolean**. FE detects draft-ish add-ons via code/label patterns above. When Backend seeds those codes, Draft badge appears automatically. Core GENERATE/EXECUTE/AUTOMATE/HUNT unchanged.

## How to verify
1. Maestro portal jar refresh after Backend GREEN too (`start-portal.cmd`, serialize Maven `-Dmaven.test.skip=true`).
2. Ctrl+F5 `/guide` — TOC + cards include new anchors; jump `#generate-from-knowledge`, `#plain-cases`, `#evidence`, `#visual-diffs`, `#suites`; authoring + Copy prompt still work.
3. Ctrl+F5 `/pricing` — credit mapping block + live Credits each table; if draft add-on rows seeded, Draft badge shows; no dollar amounts.
4. Optional Owner: `/admin/pricing` still edits the same table.

## What’s next
- Backend handoff `docs/history/handoffs/2026-11/2026-11-02-guide-pricing-backend.md` (covers refresh + optional draft seeds).
- Maestro jar refresh when FE + Backend both GREEN; smoke `/guide` + `/pricing` (+ `/admin/pricing`).
- Do **not** push.

## Files touched
1. `src/main/resources/templates/guide.html`
2. `src/main/resources/templates/pricing.html`
3. **NEW** `docs/history/handoffs/2026-11/2026-11-02-guide-pricing-fe.md` (this file)

**Not touched:** Java controllers/services, admin-pricing.html (auto-lists rows), Sonic, no portal restart from FE.

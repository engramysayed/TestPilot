# Post-fix enhancements — All (A→B→C→D)

**Date:** 2026-08-25  
**Status:** Approved in chat (2026-08-25); awaiting file review  
**Depends on:** Generate Ollama, KeelPath filtering, Execute progress/temp cleanup fixes  

---

## 1. Goal

Ship the full enhancement program after recent Generate / Automate / Execute work — **no silent omissions** — as four sequenced slices. Each slice is independently testable and usable before the next starts.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| Delivery order | **A → B → C → D** in one program; one slice complete (tests green) before the next |
| AgentRouter (D1) | **Option C:** `delivery.final-revise.enabled=false` by default; document how to re-enable with env/key + Client delivery checkbox. No fake revise success |
| Parallel worktrees | Not used for this program (merge risk on portal HTML/workers) |
| Generate output | Prefer **JSON** from LLM; keep **CSV/Excel export** for Keel; CSV repair remains fallback |
| Quality gate | Validate TC_ID, KeelPath enum, non-empty steps, no literal `\n` in step text; **one** auto-retry then surface errors |
| Single Generate async | Same job UX as batch (progress poll); may reuse `GENERATE_BATCH` with one story or add `GENERATE` kind — prefer reuse if cheaper |
| Mid-run Execute | After each TC, copy that TC’s IR (+ evidence if present) into `execute-runs/{jobId}`; UI polls `/tcs` while `RUNNING` |
| Cancel | New status `CANCELLED`; cooperative check between TCs/stories; no hard kill of JVM |
| Execute fail policy | Continue past failed TCs by default (explicit in UI); stop-on-first-fail is out of slice C unless trivial |
| Pipeline | Orchestrated chain Generate → Automate (AUTOMATE+blank) → Execute (EXECUTE/VISION_ONLY+blank) with stage status |
| Retention | TTL sweeper for `execute-runs` and work dirs; default **14 days**; configurable |
| Model compare | Two models, side-by-side counts + preview; user picks which workbook to save |
| Editable KeelPath | Generate preview row dropdown; persists into saved generated workbook |

---

## 3. Out of scope

- Real AgentRouter 401/client-type unblock (needs vendor/credentials; D1 is disable-by-default + docs only)
- Figma / Jira integrations
- Stop-on-first-fail Execute toggle (unless free during C)
- Rewriting Automate ProvePhase architecture
- Public multi-tenant SaaS billing

---

## 4. Slice A — UX, progress, mid-run

### A1 — KeelPath counts on Automate / Execute

- Extend generated-workbook meta (and upload preview if present) with:
  `keelPathCounts: { AUTOMATE, EXECUTE, VISION_ONLY, MANUAL, BLANK }`
- `upload.html` / `execute.html` banners show these counts (not total-only).

### A2 — Surface mismatch guard

- **Hard block** submit when Automate and `AUTOMATE + BLANK == 0`, or Execute and `EXECUTE + VISION_ONLY + BLANK == 0` (message explains MANUAL/other surface).
- **Soft warn** (confirm dialog) when the intended surface is a minority of rows.

### A3 — Editable KeelPath on Generate preview

- Per-row KeelPath `<select>` on `generate.html`.
- Persist via API that updates preview rows + regenerates `latest.xlsx` / meta counts.

### A4 — Automate Phase2 progress reserve

- Same pattern as Execute design-compare fix: `ConversionJobRunner` sets total = proveUnits + emitStepCount; `EmitPhase` uses `progress.effectiveTotal(...)` so bar does not hit 100% at “Phase2 starting”.

### A5 — Mid-run Execute TC list

- After each TC draft write in Execute prove, durable-copy that TC under `execute-runs/{jobId}/ir` (and evidence if any).
- `execute.html`: while job `RUNNING`, poll `/api/execute-runs/{jobId}/tcs` and render partial list.

**A acceptance:** Banner counts correct; wrong-surface blocked; Phase2 progress &lt; 100% until emit finishes; Execute UI shows TCs before job completes.

---

## 5. Slice B — Generate quality

### B1 — Structured JSON

- Update prompt to request a JSON array of TC objects (fields aligned with Keel columns).
- Parser: try JSON first; on failure fall back to existing CSV extract + repair.

### B2 — Quality gate + retry

- Gate checks: TC_ID pattern, KeelPath ∈ enum (or blank), steps non-empty after `\n` normalize, no step text embedded in TC_ID.
- On gate fail: one regenerate with repair hints; if still fail, return structured errors to UI (do not silently save garbage).

### B3 — Async single Generate

- Single-story Generate can run as a background job with the same progress polling pattern as generate-batch (reuse batch runner with one story preferred).

**B acceptance:** JSON path covered by unit tests; bad CSV/JSON fails gate; retry once; async job reaches COMPLETED with workbook saved.

---

## 6. Slice C — Control plane

### C1 — Cancel

- `JobRecord.Status.CANCELLED`.
- API `POST /api/jobs/{id}/cancel` (owned jobs only) sets cancel requested.
- Workers check between TCs/stories; persist CANCELLED; cleanup temp uploads still runs.

### C2 — Skip-failed-and-continue

- Execute already continues after TC failure in ProvePhase; document in UI (“failed TCs become TODO; run continues”).
- No behavioral change required unless a stop-on-fail path exists — remove/avoid introducing stop-on-fail in C.

### C3 — One-click pipeline

- New API + minimal UI: start pipeline for a project (stories or use generated workbook).
- Stages: Generate (if needed) → CONVERT for Automate surface → EXECUTE for Execute surface.
- Stage failures mark pipeline FAILED with last stage message; prior stage artifacts kept.

**C acceptance:** Cancel stops a multi-TC job before all TCs finish; pipeline creates linked jobs and reports stage status.

---

## 7. Slice D — Ops

### D1 — Final revise off by default (locked Option C)

- `delivery.final-revise.enabled=false` in `application.properties`.
- Portal: if user checks Client delivery while disabled, show message that final-revise is off until enabled via props/env + API key.
- `delivery.properties.example` / ops note: set enabled=true, `AGENTROUTER_API_KEY`, model/base-url.

### D2 — Retention sweeper

- Config: `delivery.retention.days=14` (0 = disabled).
- Scheduled task deletes aged dirs under project `execute-runs/` and aged folders under `delivery.work-dir` matching job work patterns.
- Never delete `generated/` latest workbook via this sweeper.

### D3 — Model comparison

- API: generate twice (model A, model B) for same stories; return `{ modelA: {counts, rows}, modelB: {...} }` without auto-saving both.
- UI: side-by-side counts + short preview; “Save as generated workbook” picks one.

**D acceptance:** Final-revise default off; retention dry-run/unit testable; compare returns two payloads and save applies one.

---

## 8. Data / API sketch

| Endpoint / artifact | Purpose |
|---------------------|---------|
| Generated workbook meta `keelPathCounts` | A1–A2 |
| `PATCH/PUT` generated preview KeelPath (or save-rows) | A3 |
| Execute IR copy per TC + `/tcs` while RUNNING | A5 |
| Generate JSON parse + `quality` errors in response | B1–B2 |
| `POST .../cancel` | C1 |
| `POST /api/projects/{id}/pipeline` + status GET | C3 |
| `delivery.final-revise.enabled=false` | D1 |
| `delivery.retention.days` + scheduler | D2 |
| `POST .../generate/compare` | D3 |

---

## 9. Testing strategy

- Unit: KeelPath counts, quality gate, JSON parse, progress `effectiveTotal` for emit, cancel flag, retention age filter, compare response shape.
- API/UI smoke: banner counts, hard-block submit, mid-run `/tcs` non-empty before COMPLETED, cancel transitions, final-revise checkbox message when disabled.
- No requirement for live AgentRouter or live browser for CI of these slices (dry-run / mocked where needed).

---

## 10. Implementation notes

- Prefer small focused types (`KeelPathCounts`, `GenerateQualityGate`, `JobCancelFlag`, `RetentionSweeper`) over growing mega-services.
- Reuse `JobProgressTracker.effectiveTotal` from Execute fix for Automate emit.
- Reuse `JobUploadCleanup` after cancel/fail/success.
- Do not commit secrets; api-key stays env/example only.

---

## 11. Coverage checklist (must not drop)

- [ ] A1 KeelPath counts on Automate/Execute banners  
- [ ] A2 Hard block + soft warn  
- [ ] A3 Editable KeelPath → saved workbook  
- [ ] A4 Automate Phase2 progress reserve  
- [ ] A5 Mid-run Execute TC streaming  
- [ ] B1 JSON generate path + CSV fallback  
- [ ] B2 Quality gate + one retry  
- [ ] B3 Async single Generate job UX  
- [ ] C1 Cancel  
- [ ] C2 Continue-on-fail explicit in UI  
- [ ] C3 Pipeline Generate→Automate→Execute  
- [ ] D1 Final-revise off by default + how to enable  
- [ ] D2 Retention sweeper  
- [ ] D3 Model compare  

---

## 12. Next step

After user approves this file: invoke **writing-plans** to produce `docs/superpowers/plans/2026-08-25-post-fix-enhancements-all.md`, then implement A→B→C→D with verification before claiming each slice done.

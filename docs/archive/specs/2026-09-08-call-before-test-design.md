# Call-before test + library/projects UI polish

Date: 2026-09-08  
Status: Approved in chat; awaiting written-spec review before implementation plan

## Problem

1. **TCs library toolbar** — search/actions misaligned; **Select all** lives in the toolbar instead of the table header.
2. **Projects hub** — **Open Automate** is redundant; **Delete all** is easy to miss (ghost style, page-title cluster).
3. **Shared setup reuse** — many cases need the same login (or other) TC first. Duplicating those steps in every case wastes Automate prove/codegen and creates many near-identical login flows. Operators want to **reference** another library TC as “call before test,” in order, and have Execute/Automate expand that chain at run time without rewriting the leaf case’s steps.

## Goals

- Polish library toolbar + projects page as specified below.
- Add ordered **Call before test** references on each library TC.
- At job materialization, expand call-before chains so selected leaves run with prerequisites **before each leaf** (not batch-deduped).
- Expand recursively; reject cycles with a clear error.
- Do **not** inject prerequisite steps into the leaf TC in the library; Automate continues to treat Login as its own reusable case.

## Non-goals (this pass)

- Auto-suggesting CallBefore from Generate prompts / LLM.
- Cross-project TC references.
- Batch-dedupe of shared prerequisites across multiple selected leaves.
- Changing KeelPath eligibility rules for prerequisites.

## Decisions (from brainstorming)

| Topic | Choice |
|--------|--------|
| Expansion timing | Runtime only when materializing Automate/Execute jobs |
| Nesting | Recursive |
| Multi-select leaves sharing Login | Re-run call-before **before each** selected leaf |
| Persistence | New workbook column `CallBefore` (survives Excel/CSV) |

---

## Part A — UI polish

### A1. Projects page (`projects.html`)

- Remove the **Open Automate** button from the page title actions.
- Move **Delete all** into the **Your projects** panel header (beside **Show archived**).
- Style **Delete all** as a danger control (solid red / existing `.button.danger` pattern) so it is noticeable. Keep the existing confirm modal.

### A2. Create project row alignment

- Ensure **Create project** form controls sit on one aligned row (name, base URL, submit) via existing `.create-row` CSS fixes if misaligned on current viewport.

### A3. TCs library (`project-detail.html` tab `tcs`)

- Toolbar: search, Upload Excel/CSV, Refresh, Delete selected — single flex row, vertically centered.
- Move **Select all** into the library table `<thead>` first column (checkbox). Wire “select all” to visible (non-filtered-hidden) rows only, same as today.
- Keep row expand/edit UX from prior work (collapsed rows; step table with Expected column).

---

## Part B — Call before test

### B1. Data model

Extend `ManualTestCase` with:

```text
String callBefore   // ordered comma-separated TC_IDs, e.g. "TC_01,TC_02"
```

Empty string = no prerequisites.

**Workbook / CSV**

- Optional column header (normalized): `CALLBEFORE` / `CallBefore` / `Call Before`.
- Missing column ⇒ empty for all rows (backward compatible).
- Writer always emits the column so round-trips preserve references.
- JSON import: camelCase `callBefore` (string).

**Validation (save / import / update)**

- Each ID must exist in the **same** workbook (or be present in the same save payload).
- No self-reference.
- On cycle detection at **run expand** time: fail the job start with a clear message (e.g. `CALL_BEFORE_CYCLE: TC_06 → TC_01 → TC_06`).
- Unknown ID at save: reject with `BAD_REQUEST` listing the ID (prefer fail-closed over silent drop).

### B2. Edit UI (library + Generate edit modal)

Under **Preconditions**:

- Label: **Call before test**
- Searchable picker over other cases in the project library (ID + title).
- Selected items as **ordered chips**; support remove and reorder (up/down or drag — up/down is enough).
- Persist via existing case update API (`callBefore` field) and Generate save path.

Display on expanded library preview: a short line under the step table when non-empty, e.g. `Call before: TC_01 → TC_02`.

### B3. Runtime expansion

Hook: `WorkbookJobMaterializer` / `GeneratedWorkbookService.materializeForJob` (and any path that builds the job workbook from library selection).

**Important:** today’s materializer filters the library **down to selected IDs first**, then returns that map’s values. Call-before needs the **full** case map to resolve references, then emit an **ordered list** that may include non-selected prerequisite IDs (and may repeat IDs across leaves).

Algorithm for selected leaf IDs `L1…Ln` (preserve selection order):

```text
byId = all library cases (+ upload wins on ID)
out = []
for each leaf L in L1…Ln:
  if L not in byId → error UNKNOWN_TC
  chain = expandRecursive(L, byId, pathStack)
  append chain to out
return out   // List<ManualTestCase>, duplicates allowed as separate rows
```

`expandRecursive(L)`:

1. Push L on path stack; if already on stack → `CALL_BEFORE_CYCLE`.
2. For each id in `callBefore(L)` (split on comma, trim): resolve in `byId` or `UNKNOWN_CALL_BEFORE`; recursively expand; append.
3. Append L.
4. Pop path stack.

**Within one leaf’s chain:** if overlapping nested refs would insert the same ID twice, keep the **first** occurrence only (avoids `Login → … → Login` from messy nesting). Across leaves, Login still repeats (decision 2).

Upload-only / mixed jobs: resolve against the merged map (upload wins on ID).

Empty selection with “use whole library”: treat every library TC as a leaf in workbook order; still expand each leaf’s call-before before that leaf (so a Login listed as its own row may also run again before dependents — acceptable; operators can exclude Login from selection and only select leaves).

### B4. Automate implications

- Automate does **not** copy Login steps into leaf TCs.
- Proving TC_01 once yields reusable IR/codegen for Login; leaves that call TC_01 get TC_01 executed first in the job workbook.
- Codegen / IR remain per-TC as today; no new “suite” artifact required this pass.

### B5. API surface

- `UpdateCaseFieldsRequest` / row maps: add `callBefore`.
- `toRowMap` / list cases: include `callBefore`.
- Excel/CSV parsers & writers: read/write column.
- Quality gate: optional light checks (unknown IDs) aligned with save validation; do not require CallBefore to be non-empty.

---

## Testing

- Unit: expand recursive, cycle error, before-each duplication across two leaves, within-leaf nest dedupe.
- Unit: Excel round-trip preserves `CallBefore`.
- MVC/API: PATCH/PUT case with `callBefore`; reject self and unknown IDs.
- UI smoke (manual): picker order, select-all in header, Delete all placement/color, Open Automate gone.

## Risks

- Old downloads without the column still import fine; new downloads gain a column (template docs / TC guide may need a one-line note later — optional this pass).
- Deep chains can make long jobs; acceptable; operators own ordering.

## Success criteria

- Operator can set Call before = Login on a feature TC, select only that TC on Execute/Automate, and the job runs Login then the feature TC.
- Selecting two such leaves re-runs Login before each.
- Library rows do not grow duplicated login steps.
- Projects/TCs polish items above are done.

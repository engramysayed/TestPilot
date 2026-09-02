# P1 — Guide AI rewrite → CSV prompt

**Date:** 2026-08-23  
**Status:** Approved (2026-08-23)  
**Roadmap:** [`2026-08-23-keel-product-roadmap.md`](./2026-08-23-keel-product-roadmap.md) phase **P1**  
**Surface:** `/tc-guide` §9 only  

---

## 1. Goal

Give authors a **copyable prompt** for any external AI chat that rewrites rough test cases into **CSV with exact Keel headers**, so they can open the result in Excel / save as `.xlsx` and Upload — without Keel calling an LLM in-portal.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| Placement | **A** — replace §9 only (no sticky / hero copy) |
| Prompt job | **Rewrite only** (not Generate-from-stories; that is P6) |
| Large suites | Soft guidance: **5–15 TCs** (or one feature/tag) per chat message |
| Prompt storage | Static file under `static/prompts/` |
| UX approach | How-to + on-page preview + **Copy prompt** + Upload link |
| True `.xlsx` from ChatGPT | Not required |

---

## 3. Scope

### In scope

- Rewrite §9 title/copy: **AI rewrite → CSV**
- Update TOC label for §9 to match
- Numbered how-to including batch size guidance
- Static prompt: `src/main/resources/static/prompts/keel-tc-rewrite-to-csv.txt`
- Scrollable prompt preview + **Copy prompt** (brief “Copied” feedback)
- Fetch prompt into the page; clipboard copy; fallbacks for fetch/clipboard failure
- Light §9 layout/CSS only (prompt card) — not full-guide motion (P5)

### Out of scope

- In-portal LLM / Generate TCs (P6)
- Hard refuse if batch &gt; N
- Download `.txt` button
- Sticky/floating copy control
- CSV validator or Excel converter in the portal
- Full guide animation polish (P5)

---

## 4. User flow

1. User opens `/tc-guide`, scrolls to §9 (or TOC **AI rewrite → CSV**).
2. Clicks **Copy prompt**.
3. Pastes prompt into any AI chat.
4. Pastes **one batch** of rough TCs (recommend 5–15).
5. Copies AI **CSV only** → Excel → Save as `.xlsx` if needed.
6. Spot-checks a few rows → **Upload**.

For large workbooks: repeat steps 4–5 per batch; merge sheets or append rows in Excel manually.

---

## 5. UX (section 9)

**Layout (top → bottom):**

1. Section head: **AI rewrite → CSV** + short intro (AI outputs CSV, not `.xlsx`).
2. **How to use** — five numbered steps (copy → paste AI → paste batch → CSV to Excel → spot-check → Upload).
3. **Prompt card** — scrollable `<pre>` preview + primary **Copy prompt**.
4. Secondary: **Ready — open Upload** → `/upload`.

**Behavior:**

- On load (or first interaction): `GET /prompts/keel-tc-rewrite-to-csv.txt` → fill preview.
- Copy uses Clipboard API with the fetched text.
- Fetch fail: visible error; preview remains selectable if any text loaded; user may open the URL manually.
- Clipboard fail: instruct select-all in preview + Ctrl/Cmd+C.

---

## 6. Prompt contract (`keel-tc-rewrite-to-csv.txt`)

The file is the full instruction the user pastes. It must require:

### Output

- **CSV only** — first row exactly:  
  `TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData`
- RFC-style escaping (quote fields that contain commas or newlines)
- No markdown fences or commentary outside the CSV

### Fidelity (anti-hallucination)

- Rewrite **only** cases present in the user’s message; **no extra TCs**
- Preserve existing `TC_ID`s; do not renumber, merge, or drop unless asked
- Large/incomplete input: rewrite this batch only; ask for the next batch; do not invent missing cases
- Soft note that batches of ~5–15 work best

### Authoring rules (mirror guide)

- Exact visible UI labels; numbered Steps
- ExpectedResult preferred per-step / aligned numbering
- Typed values in **TestData**, line-aligned with Steps (blank lines count)
- No file-upload-only cases
- No invented real credentials; shared login → Preconditions (`Login required.` etc.) / placeholders
- Optional columns may be empty if unknown
- Include a **tiny** 1–2 row CSV example for shape only

---

## 7. Components

| Piece | Responsibility |
|-------|----------------|
| `static/prompts/keel-tc-rewrite-to-csv.txt` | Canonical prompt text (edit without HTML churn) |
| `templates/tc-guide.html` §9 + TOC | How-to, preview host, Copy + Upload |
| Page script | Fetch → preview → clipboard |
| CSS (guide styles) | Prompt card, preview scroll, button state |

No new Java services or Spring endpoints beyond static resource serving.

---

## 8. Error handling

| Failure | User-visible behavior |
|---------|------------------------|
| Prompt fetch fails | Error message in §9; Copy disabled or no-ops with same message until retry |
| Clipboard denied | Message: select preview text and copy manually |
| Empty / corrupt prompt file | Treat as fetch failure |

Do not swallow errors silently.

---

## 9. Testing

- Manual: `/tc-guide` §9 preview matches file contents; Copy pastes correctly into a text editor.
- Optional automated: `GET /prompts/keel-tc-rewrite-to-csv.txt` returns 200 and expected header line (if project already has MVC/resource smoke tests).
- No requirement for browser automation of clipboard in P1.

---

## 10. Success criteria

1. User can copy a complete rewrite→CSV prompt in one click from §9.
2. Prompt encodes Keel headers + guide rules + batch/fidelity constraints.
3. How-to on the page explains Excel path and large-suite chunking.
4. No in-portal AI dependency for P1.

---

## 11. Spec self-review

- [x] No TBD for locked decisions
- [x] Consistent with roadmap P1 / decision A (CSV, not xlsx-from-ChatGPT)
- [x] Scope limited to §9 + static prompt (not P5/P6)
- [x] Batch handling explicit (soft 5–15; no hard refuse)
- [x] Ambiguity resolved: rewrite-only; static file path named

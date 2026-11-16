# P6.5 — Generate TCs with Ollama + KeelPath routing

**Date:** 2026-08-23  
**Status:** Approved — implemented 2026-08-23  
**Roadmap:** Extends P6 Generate; prerequisite for smart handoff to Automate / Execute  
**Depends on:** P2 Projects, P6 prompt-first Generate, `LocalLlmClient`, Excel reader  

---

## 1. Goal

From **user stories / PRD / acceptance criteria** pasted on `/generate`, call **local Ollama** to produce a **Keel-format workbook** where every row includes **`KeelPath`** — how Keel can run it:

| KeelPath | Meaning | Handoff |
|----------|---------|---------|
| `AUTOMATE` | DOM steps Keel can codegen (conversion pipeline) | Automate → Upload |
| `EXECUTE` | Live prove: DOM + vision + visual assert; not ideal for codegen | Execute |
| `VISION_ONLY` | Primarily `VisualAssertion` / design compare; thin or no DOM interaction | Execute (assert-heavy) |
| `MANUAL` | Not runnable in Keel today | Export only; author runs manually |

Authors still **review** output. Prompt-first “copy to external AI” remains as fallback.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| LLM | **Ollama only** via existing `LocalLlmClient` (self-hosted; no cloud on delivery path) |
| “Skills” | **Prompt manifest files** on classpath — not Cursor Agent Skills, not Ollama Modelfile in v1 |
| Routing column | **`KeelPath`** — new required column in generated CSV |
| Coverage | **Coverage report** in API response (techniques + requirement IDs); not a literal “100%” guarantee |
| Generation UX | **Sync** on page (timeout up to 120s); async job later if needed |
| Output | Preview table + **download CSV**; `.xlsx` export if trivial reuse, else CSV-only v1 |
| Batch size | Soft 5–15 TCs per request; large PRDs = multiple generate clicks |
| Backward compat | `KeelPath` **optional** on read — empty defaults to `EXECUTE` for legacy sheets |

---

## 3. What Ollama “knows” (prompt stack)

Three files concatenated as **system** context (order matters):

1. **`keel-tc-generate-from-stories-to-csv.txt`** (existing) — CSV shape, authoring rules  
2. **`keel-capability-manifest.txt`** (new) — what Automate / Execute / vision can and cannot do  
3. **`keel-testing-techniques.txt`** (new) — coverage checklist for the model  

### 3.1 Capability manifest (summary)

**Automate + Execute CAN:** click/type/select by label, navigate by URL, login with credentials, visual assertion on screenshot, design reference compare, DOM post-checks, heal on miss.

**CANNOT (→ `MANUAL` or rework steps):**
- File upload (choose file, drag-drop, attach document)
- CAPTCHA / OTP / magic-link email flows
- Native OS dialogs, printer, Bluetooth, SMS
- Multi-browser matrix in one TC
- API-only or DB-only verification with no UI
- Download file content verification (unless UI shows filename only)

**Heuristics for `KeelPath`:**
- `AUTOMATE`: linear UI flow, stable labels, no upload, good codegen candidate  
- `EXECUTE`: needs live exploration, timing, multi-page, or weak locators but still UI-driven  
- `VISION_ONLY`: assertion is mostly “screen looks like X”; steps may be “open page” + assert  
- `MANUAL`: any hard blocker above  

### 3.2 Testing techniques playbook (summary)

Instruct model to apply, and tag in **`Tags`** where useful:

- Equivalence partitioning  
- Boundary values  
- Negative / error paths  
- State transitions  
- Role / permission variants  
- Regression / smoke tagging  
- Visual/regression hooks → prefer `VisualAssertion` + `VISION_ONLY` or `EXECUTE`  

Model outputs a **`coverageNotes`** block in JSON metadata (API only, not CSV row) listing techniques applied and **gaps** (“no AC for logout”, “mobile not specified”).

---

## 4. CSV schema (v2)

**Header row (exact order):**

```text
TC_ID,Title,Steps,ExpectedResult,Preconditions,Priority,Tags,VisualAssertion,TestData,KeelPath
```

- `KeelPath` values: `AUTOMATE` | `EXECUTE` | `VISION_ONLY` | `MANUAL` (uppercase)  
- Validator rejects unknown values  
- `ExcelTcReader` accepts optional `KeelPath` / `Keel Path` alias  

---

## 5. API

### `POST /api/projects/{projectId}/generate-tcs`

**Request:**

```json
{
  "stories": "As a user I want to...",
  "options": {
    "reviewPass": false
  }
}
```

**Response (200):**

```json
{
  "projectId": "prj_...",
  "rows": [ { "tcId": "TC_01", "title": "...", "keelPath": "AUTOMATE", ... } ],
  "coverageNotes": "Applied: boundary, negative. Gap: no mobile AC.",
  "csv": "TC_ID,Title,...\n...",
  "counts": { "AUTOMATE": 3, "EXECUTE": 5, "VISION_ONLY": 2, "MANUAL": 1 }
}
```

**Errors:**
- `503` — Ollama unreachable  
- `422` — model returned unparseable CSV  
- `408` — timeout  

**Optional `reviewPass: true`:** second Ollama call with generated CSV + original stories → add missing cases only (same schema).

### Config (`delivery.*`)

| Property | Default | Purpose |
|----------|---------|---------|
| `generate-enabled` | `true` | Kill switch |
| `generate-model` | `qwen2.5:latest` | Text model (not vision) |
| `generate-timeout-seconds` | `120` | Sync cap |

Reuse `llmBaseUrl` unless `generate-base-url` override added later.

---

## 6. UI (`/generate`)

**Keep:** project picker, copy-prompt fallback, link to Guide.

**Add:**
1. **Stories** textarea (required for Ollama path)  
2. **Generate with Ollama** button + spinner  
3. **Coverage notes** panel (from `coverageNotes`)  
4. **Preview table** — columns TC, Title, KeelPath badge, Priority, Tags  
5. **Filters** — All | Automate | Execute | Vision | Manual  
6. **Download CSV**  
7. **Handoff buttons:**  
   - “Open Automate” (unchanged)  
   - “Open Execute” with hint: upload sheet filtered to EXECUTE+VISION_ONLY rows (client-side filter download or server `?keelPath=` export)  

**Session:** store last `generateProjectId` + last preview in `sessionStorage` (optional).

---

## 7. Backend components

| Component | Role |
|-----------|------|
| `TcGenerateService` | Build prompt, call Ollama, parse CSV, validate |
| `KeelPath` enum | Parse + validate |
| `GeneratedTcCsvParser` | Strict CSV → `List<ManualTestCase>` + keelPath |
| `GenerateTcController` | REST under `/api/projects/{id}/generate-tcs` |
| `ManualTestCase` | Add optional `keelPath` field |
| `ExcelTcReader` | Read optional KeelPath column |

**No new job kind** in v1 (sync only).

---

## 8. Ollama setup (ops doc, not code)

For operators — add to `docs/ops/`:

1. Pull text model: `ollama pull qwen2.5:latest` (or configured model)  
2. Set `delivery.llm-base-url`, `delivery.generate-model`  
3. **Not required in v1:** custom Modelfile; prompt files are enough  
4. **Future:** optional `keel-tester` Modelfile that bakes manifest + techniques into model system template  

---

## 9. Testing

| Test | Assert |
|------|--------|
| `TcGenerateServiceTest` | Stub `LocalLlmClient` returns sample CSV with KeelPath → parsed rows |
| `GeneratedTcCsvParserTest` | Header validation, bad KeelPath rejected |
| `GenerateTcApiTest` | Mock Ollama or stub bean; 200 + counts |
| `ExcelTcReaderTest` | Optional KeelPath column round-trip |
| `GenerateMvcTest` | Page has textarea + Generate button + KeelPath in preview markup |

---

## 10. Phasing

| Phase | Deliverable |
|-------|-------------|
| **6.5a** | Prompt files, API, parser, `KeelPath` column, sync generate, preview UI |
| **6.5b** | Review pass, filtered CSV export per path, Execute/Automate deep links with filtered download |
| **6.5c** | Async job for large PRDs; optional PRD file text extraction |

---

## 11. Out of scope

- Cursor / cloud LLM in portal  
- Guaranteed 100% requirements coverage (report only)  
- Auto-run after generate without human review  
- Figma / API test generation  
- Training custom Ollama models  

---

## 12. Spec self-review

- [x] KeelPath enum matches Keel capabilities (upload → MANUAL)  
- [x] Ollama “skills” = prompt manifests, explicitly not Cursor skills  
- [x] Excel backward compat (optional column)  
- [x] Sync v1; async deferred  
- [x] Prompt-first path preserved  
- [x] No TBD on locked decisions  

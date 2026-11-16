# P6 — Generate TCs v1 (prompt-first)

**Date:** 2026-08-23  
**Status:** Approved (2026-08-23)  
**Roadmap:** [`2026-08-23-keel-product-roadmap.md`](./2026-08-23-keel-product-roadmap.md) phase **P6**  
**Depends on:** P2 Projects hub, P3 Nav shell  

---

## 1. Goal

Ship **Generate test cases** as a prompt-first workflow on `/generate`: authors pick a project, copy a ready AI prompt, paste user stories / acceptance criteria into any external chat, receive **Keel-format CSV**, then open in Excel and hand off to **Automate** — no in-portal LLM.

---

## 2. Locked decisions

| Topic | Decision |
|-------|----------|
| v1 approach | **Prompt-first** (mirror P1 Guide §9 pattern); no in-portal generator |
| Input to AI | User pastes stories/PRD/AC in the **external** chat after the prompt |
| Output | CSV with exact Keel headers (same as P1) |
| Project picker | Required UX; copies **project context** (name, base URL) appended to prompt |
| Storage | `static/prompts/keel-tc-generate-from-stories-to-csv.txt` |
| Session | `sessionStorage` key `keel.generateProjectId` (shared pattern with Automate hub) |
| Coming soon | **Remove** from `/generate` and dashboard Generate verb card |
| Doc upload | **Out** — paste-only via external AI |
| Batch size | Soft **5–15 TCs** per message (one feature/epic) |
| Credentials | Prompt forbids inventing secrets; use placeholders + Preconditions |

---

## 3. Scope

### In scope

- Replace `generate.html` coming-soon with full Generate page
- Static prompt file + `GET /prompts/keel-tc-generate-from-stories-to-csv.txt` (already public via SecurityConfig)
- Project picker (`GET /api/projects`, non-archived list)
- How-to steps + prompt preview + **Copy prompt** (prompt + project context block)
- CTAs: **Open Automate**, link to TC Guide §9 (rewrite path)
- `GeneratePromptResourceTest`, `GenerateMvcTest`
- Dashboard: Generate card live (no coming-soon badge)

### Out of scope

- In-portal LLM / story file upload
- CSV validator, Excel export in portal
- P7 Execute implementation
- Changing rewrite prompt (`keel-tc-rewrite-to-csv.txt`)

---

## 4. User flow

1. Open `/generate`, select **Project** (or create one).
2. Click **Copy prompt** (includes project name + base URL context).
3. Paste into any AI chat.
4. Paste **one batch** of user stories / acceptance criteria.
5. Copy AI **CSV only** → Excel → save as `.xlsx` if needed.
6. Spot-check rows → **Automate** → Upload for selected project.

---

## 5. Page layout

1. **Title** — Generate test cases + lede  
2. **Project picker** — select + empty state → `/projects`  
3. **How to use** — numbered steps (mirror guide §9)  
4. **Prompt card** — preview + Copy prompt  
5. **Actions** — Open Automate (with `?projectId=`), TC Guide rewrite link  

---

## 6. Prompt rules (generate vs rewrite)

| | Rewrite (P1) | Generate (P6) |
|---|-------------|---------------|
| Input | Rough existing TCs | Stories / PRD / AC |
| TC_ID | Preserve existing | Assign `TC_01`, `TC_02`, … |
| Invent cases | **No** | **Yes**, from requirements only |
| Hallucinate UI | No | Only when app context is thin; prefer given labels |

---

## 7. Testing

| Test | Assert |
|------|--------|
| `GeneratePromptResourceTest` | Classpath + HTTP prompt; header; generate-specific rules |
| `GenerateMvcTest` | 200; project picker; Copy prompt; no "Coming soon"; `/automate` CTA |
| `DashboardMvcTest` | Generate verb card has no coming-soon badge (update test) |

Run: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=GenerateMvcTest,GeneratePromptResourceTest,DashboardMvcTest" test`

---

## 8. Spec self-review

- [x] Prompt-first only; no LLM in portal
- [x] Project context in copy flow
- [x] Same CSV header as P1
- [x] Dashboard + generate page de-coming-soon
- [x] Test plan included

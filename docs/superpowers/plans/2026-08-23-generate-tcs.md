# Generate TCs v1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended).  
> **Do not commit unless the user asks.**

**Goal:** Replace `/generate` coming-soon with prompt-first Generate page + static stories→CSV prompt.

**Spec:** `docs/superpowers/specs/2026-08-23-generate-tcs-design.md`

## Global Constraints

- Prompt file: `static/prompts/keel-tc-generate-from-stories-to-csv.txt`
- Copy = static prompt + PROJECT CONTEXT block from selected project
- No in-portal LLM; no doc upload
- Remove coming-soon from generate page + dashboard Generate card
- Do not commit unless asked

---

### Task 1: Generate prompt file + resource test

**Files:**
- Create: `src/main/resources/static/prompts/keel-tc-generate-from-stories-to-csv.txt`
- Create: `src/test/java/delivery/portal/web/GeneratePromptResourceTest.java`

Run: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=GeneratePromptResourceTest" test`

---

### Task 2: generate.html full page

**Files:**
- Modify: `src/main/resources/templates/generate.html`

Pattern: automate.html project picker + tc-guide §9 prompt card/copy JS.

---

### Task 3: Dashboard Generate card live

**Files:**
- Modify: `src/main/resources/templates/dashboard.html`
- Modify: `src/test/java/delivery/portal/web/DashboardMvcTest.java`

Remove coming-soon badge from Generate verb card; link stays `/generate`.

---

### Task 4: GenerateMvcTest

**Files:**
- Create: `src/test/java/delivery/portal/web/GenerateMvcTest.java`

Run: `mvn -q "-Dmaven.compiler.release=21" "-Dtest=GenerateMvcTest,GeneratePromptResourceTest,DashboardMvcTest" test`

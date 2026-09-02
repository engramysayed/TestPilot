# Keel

**Keel** turns manual test intent into live browser evidence and downloadable Selenium/TestNG automation.

Paste user stories or import structured test cases, prove them against a real site with bind → heal → recovery, then package a customer-ready framework ZIP — or run cases directly on **Execute**.

---

## What you get

| Surface | Purpose |
|---------|---------|
| **Generate** | Stories or external AI JSON/CSV → quality-gated workbook (`latest.xlsx` / CSV) |
| **Automate** | Workbook → live prove + heal → Java Selenium TestNG ZIP |
| **Execute** | Run selected cases against the live site with evidence, without full codegen packaging |
| **Compare** | Same stories through two local models; save the better set into the project |
| **KeelPath** | Per-case routing: `AUTOMATE` · `EXECUTE` · `VISION_ONLY` · `MANUAL` |

Supporting capabilities:

- Authoring quality gate (leave-empty / vague asserts / field naming)
- Editable TC preview (step × test-data grid before Automate/Execute)
- Heal cascade: DOM bind → Ollama → optional Cursor invent → structured **recovery** plans
- Domains, projects, credentials, design references, and retention under a local store

---

## Stack

- **Java 21** · Spring Boot portal
- **Local Ollama** for generate / authoring (configurable models)
- Optional **Cursor Auto** heal sidecar (`tools/cursor-heal/`)
- Optional vision models (e.g. UI-TARS / Qwen-VL) for grounding and visual asserts
- Filesystem store: `delivery-store/` · H2 or Postgres for portal accounts
- Customer TAF template: `customer-framework-template/`

---

## Quick start

### Prerequisites

- JDK 21+
- Maven 3.9+
- Chrome or Edge
- [Ollama](https://ollama.com/) with at least one generate model pulled (see `application.properties`)
- Node.js 18+ if you enable the Cursor heal sidecar

### Run the portal

```bat
start-portal.bat
```

With Cursor heal (requires `CURSOR_API_KEY` via env or a local BAT — never commit keys):

```bat
start-portal-with-cursor-heal.bat
```

Open **http://localhost:8080**.

Default admin credentials are set in `src/main/resources/application.properties` — **change them before any shared or remote use**.

### Configuration

Primary knobs live in `src/main/resources/application.properties`:

| Key | Role |
|-----|------|
| `delivery.llm-base-url` / `delivery.llm-model` | Ollama endpoint and default model |
| `delivery.generate-model` / `delivery.generate-models` | Generate + Compare model list |
| `delivery.cursor-heal.enabled` | Enable Cursor invent/solve sidecar |
| `delivery.store-root` | Project artifact store (default `./delivery-store`) |
| `delivery.browser.headless` | Headless prove/execute browser |

API clients should send header: `X-Keel-Requested-With: Keel`.

After Generate UI, prompt, or gate changes: **restart the portal** and hard-refresh the browser.

### CLI

Batch conversion without the UI: `delivery.cli.DeliveryCli` — see `docs/ops/` for local Ollama and ops notes.

---

## How Automate works

```text
Excel / generated workbook
        │
        ▼
   ProvePhase  ──► bind intents to live DOM
        │              │
        │              └─ fail ► HealCascade (Ollama → Cursor → recovery)
        ▼
   IR (TcDraft) → Revise → Emit (pages + tests) → Framework ZIP
```

**Recovery** (when page state mismatches the intent, e.g. a field that should be empty is filled): Cursor/invent may return structured JSON (`mode: "recovery"`, `recoverySteps`, `automationNotes`). Keel validates locators on the live page, executes the plan, records evidence (`heal-recovery.json`), retries the same intent, and when recovery clears a field may patch the saved generated workbook (leave-empty Steps + blank TestData) plus coverage / meta notes for Automate.

---

## Repository layout

```text
src/main/java/delivery/     Portal, jobs, authoring, heal, codegen, Excel
src/main/resources/         application.properties, templates, static UI
customer-framework-template/  Shipped TAF core inside every Automate ZIP
tools/cursor-heal/          Cursor Auto heal sidecar
docs/                       Specs, plans, and ops guides
scripts/                    Setup helpers (e.g. vision models)
```

Engineering depth (flows, quality gates, historical workstream notes): [`docs/ENGINEERING_HANDOFF.md`](docs/ENGINEERING_HANDOFF.md).

---

## Security notes

- Do not commit API keys. Use env vars (`CURSOR_API_KEY`, `AGENTROUTER_API_KEY`, …) or gitignored local BAT files (`cursor-api-key.local.bat`).
- Change the portal admin password before exposing the app beyond localhost.
- `delivery-store/` and `delivery-work/` hold runtime data and are gitignored.

---

## License

Proprietary — all rights reserved unless a license file is added to this repository.

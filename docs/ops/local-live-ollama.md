# Live local conversion (Ollama on this PC)

## Prerequisites

1. Ollama running with model `gemma4:e2b`  
   `curl.exe http://127.0.0.1:11434/api/tags`
2. Chrome installed  
3. Portal config (`src/main/resources/application.properties`):
   - `delivery.dry-run=false`
   - `delivery.llm-model=gemma4:e2b`
   - `delivery.llm-base-url=http://127.0.0.1:11434`

To go back to packaging-only mode: set `delivery.dry-run=true`.

## Heal cascade (DOM → Ollama → Cursor Auto)

When a step **bind** or **Selenium execute/assert** fails, ProvePhase heals in order:

1. **DOM binder** — pick from live candidate table only (no invented CSS/XPath).
2. **Ollama** (local, free) — vision/text heal: pick one `candidateId` from a shortlist (+ screenshot when available).
3. **Cursor Auto** — Node sidecar `tools/cursor-heal/heal.mjs` via stdin/stdout JSON. Model is hard-locked to **Auto** (`model: { id: "auto" }`). Never pin composer/opus.

Cap: **Ollama once + Cursor once** per intent attempt. If all layers fail → `HEAL_EXHAUSTED: …` (real product bugs still fail).

### Cursor sidecar setup

```bat
cd tools\cursor-heal
npm install
```

Easiest: copy `api-keys.local.bat.example` → `api-keys.local.bat` (gitignored), set `CURSOR_API_KEY` (and optionally `AGENTROUTER_API_KEY`), then run `start-portal.bat` or `start-portal-with-cursor-heal.bat`.

Legacy: `cursor-api-key.local.bat.example` → `cursor-api-key.local.bat` still works.

```bat
set CURSOR_API_KEY=your_key_here
set AGENTROUTER_API_KEY=your_agentrouter_key_here
```

Config (no model pin in Java):

- `delivery.cursor-heal.enabled=true`
- `delivery.cursor-heal.command=node tools/cursor-heal/heal.mjs`

Without `CURSOR_API_KEY`, conversion continues and logs `CURSOR_HEAL_SKIPPED` (does not block the job).

Contract: sidecar stdout is only `{"candidateId":"cN"}`; Java rejects any id not in the live shortlist.

## CLI smoke (SauceDemo)

From repo root (`--project-id` optional; defaults to host slug). Work folder is `saucedemo-com-yyyyMMdd-HHmmss`:

```bat
mvn -Dmaven.compiler.release=21 -q exec:java -Dexec.mainClass=delivery.cli.DeliveryCli -Dexec.args="--excel src/test/resources/delivery/saucedemo-smoke.xlsx --base-url https://www.saucedemo.com/ --username standard_user --password secret_sauce --mode NEW --llm-base-url http://127.0.0.1:11434 --llm-model gemma4:e2b"
```

Practice-test pack (login + negative login + exceptions):

```bat
mvn -Dmaven.compiler.release=21 -q exec:java -Dexec.mainClass=delivery.cli.DeliveryCli -Dexec.args="--excel src/test/resources/delivery/practice-test-automation-4tc.xlsx --base-url https://practicetestautomation.com/ --username student --password Password123 --mode NEW --llm-base-url http://127.0.0.1:11434 --llm-model gemma4:e2b"
```

Expect JSON with `status=COMPLETED`, `zipPath`, `passedCount` / `todoCount`.  
Open `docs/AUTOMATION_SCORE.md` inside the unzipped project.

Authoring is **DOM-first**: Ollama and Cursor only pick locators from a live candidate table (cannot invent selectors).

Excel cases that include login steps get login in generated `@BeforeMethod`; inventory-only cases do not. Negative login cases keep type/click intents in the body and do **not** run the authenticated-session prelude.

## Portal smoke

1. `start-portal.bat`
2. Login → Users/Account as needed  
3. Upload → project → Excel `saucedemo-smoke.xlsx`  
4. Base URL `https://www.saucedemo.com/`  
5. Username `standard_user` / Password `secret_sauce`  
6. Mode NEW → wait → download ZIP  

## Codegen rules

See `docs/superpowers/specs/2026-08-05-codegen-rules.md` (locator order, POM pages, soft asserts).

## Later: Windows VPS

1. Install JDK, Maven, Chrome, Ollama on the VPS  
2. `ollama pull gemma4:e2b`  
3. Copy this repo; keep the same `application.properties` values (point LLM to `127.0.0.1` on that machine)  
4. Run `start-portal.bat` or Windows Service  

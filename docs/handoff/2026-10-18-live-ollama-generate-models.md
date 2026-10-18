# 2026-10-18 — Live Ollama models on Generate

Shipped in the working tree. The 17 Oct portal jar does **not** include this until rebuild + restart + Ctrl+F5.

## What shipped

- Generate → AI model, Compare two, and Review list **chat** names from Ollama `GET {baseUrl}/api/tags` (same URL/token as Admin → LLM & API keys).
- Vision-like names containing `vl`, `llava`, `ui-tars`, or `vision` are omitted from Generate. Account → Vision radios stay UI-TARS vs Qwen.
- Last full tag list is stored as `cachedOllamaModels` in `delivery-store/llm-settings.json` (gitignored). Secrets are unchanged (encrypted `*Enc` fields).
- `GET /api/generate/models` is cache-first. `?refresh=1` hits Ollama. JSON includes `source` (`live` | `cache` | `fallback`) and `unreachable`.
- No cache: loader “Loading models from Ollama…”. Cache present: show list, then “Updating…”. Ollama down: last list plus “Ollama unreachable — using last list.” Hardcoded fallback pair is `gemma4:e2b` / `qwen2.5:latest` only when cache is empty.
- Admin Save still sets the install default. Env still wins for `delivery.generate-model`.

## How to use

1. Rebuild and restart the portal (`start-portal.cmd` or `java -jar keel-portal\target\keel-portal-1.0-SNAPSHOT.jar --server.port=8080`).
2. Open Generate, pick a project. The AI model dropdown should fill from local Ollama.
3. `ollama pull` a new **chat** model, then refresh Generate (or Admin Refresh models, then Generate). The new name should appear without a code edit.

## Known defects

- Tests for this pack were not re-run in the implement session because CrowdStrike blocks Cursor PowerShell temp scripts on this PC. Re-run: `mvn "-Dmaven.compiler.release=21" "-Dtest=GenerateModelListTest,GenerateModelServiceTest,LlmSettingsServiceTest,GenerateConfigApiTest,GenerateMvcTest" test`
- Live UI not verified: portal jar on disk was still the 17 Oct build; 8080 was not serving this code.
- `isVisionModel` uses substring `vl` (spec). A chat tag that happens to contain those letters would be dropped.
- Generate fetch failure still paints a single `gemma4:e2b` option so the page is usable.

## Spec / plan

- `docs/superpowers/specs/2026-10-18-live-ollama-generate-models-design.md`
- `docs/superpowers/plans/2026-10-18-live-ollama-generate-models.md`

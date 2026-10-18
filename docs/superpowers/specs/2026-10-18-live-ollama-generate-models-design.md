# Live Ollama models on Generate (no hardcoded list)

**Date:** 2026-10-18  
**Status:** Approved in chat; waiting for spec review before the implementation plan  
**Plan:** (none yet)

## Problem

Generate → AI model (and Compare / Review) reads a hardcoded portal list (`gemma4:e2b`, `qwen2.5:latest` in `DeliveryPortalProperties.generateModels`). Pulling a new Ollama model does not appear until someone edits code. Admin → LLM & API keys already lists live `/api/tags`. Those two pickers must stay in sync without a rebuild per model.

## Goal

Any signed-in user opening Generate sees **chat** models from live Ollama (same base URL and optional Bearer as LLM Settings). Admin Save still sets the install **default**. New `ollama pull` names show up after a refresh. Last good full tag list is saved in `delivery-store/llm-settings.json` so a reboot with Ollama down still has a dropdown. No code edit per model. Account → Vision stays UI-TARS vs Qwen.

## Non-goals

- Account vision radios becoming Ollama tag names.
- Excel rewrite, portal CSS reskin, Midscene.
- Editing `api-keys.local.bat`.
- Changing ApiKeySource order (env → Settings props → DEV loader).
- Changing OWNER-only on `/api/admin/llm-settings`.
- Waiting on a long chat completion to fill the dropdown (tags only, ~8s cap).

## Architecture

```text
Ollama GET {baseUrl}/api/tags  (Bearer if OLLAMA_API_KEY resolves)
        │
        ├─ success → write cachedOllamaModels (all names) to llm-settings.json
        │            return live names
        │
        └─ fail    → if cachedOllamaModels non-empty → that list
                     else → [gemma4:e2b, qwen2.5:latest] + unreachable
```

Shared helper lives on `LlmSettingsService` (already parses tags and talks to Ollama). `GenerateModelService` / `GET /api/generate/models` uses it. Admin Refresh already calls `listOllamaModels`; on success it also writes the cache.

Store the **full** tag list. Filter at the reader:

| Surface | Names shown |
|---------|-------------|
| Admin Chat + Vision | All tags |
| Generate AI model, Compare two, Review | Chat only: drop a name if (case-insensitive) it contains `vl`, `llava`, `ui-tars`, or `vision` |

If live tags succeed but the chat filter is empty, treat as fail-for-Generate: use cached **chat** names if any, else the two fallback names + unreachable.

## Default model

1. If env `delivery.generate-model` / `DELIVERY_GENERATE_MODEL` is set, that wins (existing env-wins rule).
2. Else Admin saved `ollamaChatModel` if it is in the **chat** list.
3. Else first chat name.
4. Generate page may still prefer `sessionStorage` last pick **if that name is still in the chat list**.

`GenerateModelService.resolve(requested)` must accept any name in the current served chat list (live, cache, or fallback). It must not reject a newly pulled chat model because it is missing from the old hardcoded pair.

## Loader

- **No cached list:** dropdown disabled, copy “Loading models from Ollama…”, Generate / Compare disabled until the tags call finishes (success, cache, or fallback).
- **Cached list exists:** show it immediately; small “Updating…” on the row; replace when live returns. On live fail, keep the cache and “Ollama unreachable — using last list.”
- Admin Refresh: keep the button; while in flight, disable it and show the same loading vs updating idea.

## API

`GET /api/generate/models` (any signed-in user):

```json
{
  "defaultModel": "gemma4:e2b",
  "models": ["gemma4:e2b", "qwen2.5:latest"],
  "source": "live",
  "unreachable": false
}
```

`source` is `live` | `cache` | `fallback`. Never return API keys or Authorization headers. Model **names** only.

Admin `GET /api/admin/llm-settings/ollama/models` stays OWNER-only and returns **all** tags (unchanged auth). On success, write `cachedOllamaModels`.

## File shape

Existing `llm-settings.json` fields stay. Add:

- `cachedOllamaModels`: JSON array of strings (Ollama tag names). Empty or omitted means no cache.

Do not put secrets in this array. Gitignore of `delivery-store/` already covers the file.

## Tests (no live Ollama)

- Parse fixture tags → Generate chat list drops vision-like names; Admin list keeps them.
- Success path writes `cachedOllamaModels` (assert names, never assert key ciphertext).
- Fail + cache → cache; fail + empty cache → two fallback names and `unreachable: true`.
- `resolve()` accepts a chat name that is not `gemma4:e2b` / `qwen2.5:latest` when it is in the served list.
- Existing `LlmSettingsServiceTest`, `AdminLlmSettingsApiTest`, `GenerateConfigApiTest` stay green.

## Out of scope leftovers

Appium keyboard, COUNT/PERMISSION/iframe/shadow/canvas, public origin, push until asked.

## Done when

Generate lists a newly pulled chat model after Ollama has it (or after cache write), without editing Java defaults. Ollama down still shows last list when cache exists. Account vision radios unchanged.

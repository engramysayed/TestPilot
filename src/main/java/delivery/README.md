# Delivery engine

Authoring for Delivery Platform jobs uses **local Ollama** by default (bind heal, generate, hunt planner).

**Precision** (`AuthoringEngine.PRECISION`) is an opt-in per-job path: Cursor `groundRank` / `solve` for initial bind on Automate and Execute, with Keel fallback. Requires `CURSOR_API_KEY` and `delivery.cursor-heal.enabled=true`.

Do **not** call `llmLayer` Gemini / cloud LLM clients from this package for core authoring. Claude / Anthropic models are banned for this project.

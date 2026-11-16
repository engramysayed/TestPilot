# D-QA-12 Nightly LiveSmoke (opt-in)

Workflow: `.github/workflows/nightly-livesmoke.yml`

| Mode | Runs LiveSmoke? |
|---|---|
| PR / release / settle-smoke | **No** (deterministic) |
| `schedule` 02:00 UTC | Yes, **only if** `secrets.KEEL_LIVESMOKE=1` |
| `workflow_dispatch` | Same secret gate |

Required secrets: `KEEL_LIVESMOKE=1`, optional `OLLAMA_BASE_URL` / `OLLAMA_MODEL`.

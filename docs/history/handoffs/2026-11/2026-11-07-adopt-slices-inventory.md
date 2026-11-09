# Adopt slices A1–A6 — repo inventory

**Observed:** 2026-11-07 (updated end of session) (after [Explore adopt slice status](ee53af8f-317c-464c-a1b6-800539834298)).  
**Plan source (out of tree):** `keel-cursor-implementation-handoff/handoffs/2026-11-04-adopt-implementation-plan.md`.

Suites **E/F** are separate; see `E-contract-map.md`, `2026-11-07-slice-e-cursor.md`, `2026-11-07-slice-f-cursor.md`.

| Slice | Status (2026-11-07 end of session) | Remaining gaps |
|-------|----------------------------------------|----------------|
| **A1** failure pack | **Shipped** — ZIP + manifest (`usedVision`), `GET .../jobs/{jobId}/failure-pack`, `FailurePackApiTest` | FE download polish; cross-job size limits |
| **A3** plans | **Shipped** — structured plans API + UI, suite pin, job snapshot (`StructuredPlanApiTest`) | Plan editor UX depth; revision diff view |
| **A2** CLI | **Shipped** — `KeelPortalCli` (doctor, status auth, suites list, run, failure get, tunnel enroll/status/start), `-Pkeel-cli` fat jar, `.cursor/skills/keel-portal-cli` | No native `keel.exe`; `keel auth login` flow |
| **A6** vision | **Shipped** — vision policy API, job + failure-pack vision summary (`VisionPolicyApiTest`) | FE chip coverage on all job views |
| **A4** memory | **Shipped** — run memory CRUD + heal injection (Ollama + Cursor JSON) (`RunMemoryApiTest`) | `memoryUsedCount` metrics surfaced in UI |
| **A5** tunnel | **Shipped** — broker, agent poll, `/tunnel/p/{token}/**`, job `baseUrl` binding, CLI relay, badge; enrollment reuse + heartbeat TTL (2026-11-07 follow-up) | mTLS, WebSocket transport, edge rate limits for token-in-URL |

**Suggested order (per plan):** E+F gates → **A1** → A3 → A2 → A6 → A4 → A5 → routing truth.

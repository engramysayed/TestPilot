# Constitution supersession (1.2.0)

`.specify/memory/constitution.md` is gitignored in this repo. The 1.2.0 amendment is recorded here so launch docs stay reviewable.

**Historical 1.1.0 claims retained.** The following are **explicitly superseded** for current Keel/portal releases:

- **Local-only AI is not a runtime guarantee.** Authoring may use any provider on `delivery.provider.allowlist` (Ollama, Cursor sidecar, AgentRouter, vision). Empty allowlist fails closed. Engine choice (Keel vs Precision) is not a privacy control. Cloud vs local-only remains a commercial/P0-01 decision.
- **Excel is not the only input.** Generate and import accept the project Excel template plus CSV and structured JSON. Word/PDF remain unsupported.
- **Validate-before-persist still holds** for live proof. Dry-run jobs are **SIMULATED** and must not be advertised as browser proof.

Product owner ratification of this amendment remains a **P0-01 release blocker**.

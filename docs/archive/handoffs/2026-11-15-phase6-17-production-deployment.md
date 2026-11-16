# Phase 6.17 — First production deployment (blocked)

**Date:** 2026-11-15  
**Outcome:** Production deployment **not completed** from this session. No invented hosts, credentials, or verification.

---

## 1. Is Keel deployed?

**No.** There is no reachable production Keel instance under operator control in this session.

- `GET http://localhost:8080/api/health` — **unreachable** (no portal listening on this workstation at audit time).
- Repository docs list production/shared host as **unfilled** (`docs/ops/shared-staging.md` — hostname/DNS **missing**).
- No CI/CD job deploys to a production host (`.github/workflows/release.yml` runs tests only).

---

## 2. Where is it running?

**Not running in production anywhere verified here.**

| Environment | Status |
|-------------|--------|
| Documented production / shared host | **Unknown / not configured in repo** |
| This developer workstation | Portal **not** started; not a production install (`delivery.install.production=false` in base `application.properties`) |

---

## 3. What was verified (local, pre-deploy only)

| Check | Result |
|-------|--------|
| Harness + repair flags in shipped defaults | **All false** — `application.properties` lines 49–59 |
| Deployable artifact build | `mvn -pl keel-portal -am -DskipTests package` **succeeded**; `keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` present |
| Deployment docs inspected | `docs/ops/windows-vps-setup.md`, `docs/ops/deployment.md`, `docs/ops/shared-staging.md`, `start-portal.cmd` |
| Local env hooks exist | `keel-env.local.bat`, `api-keys.local.bat` (not used for remote deploy) |

**Not verified:** Production startup, DB on prod volume, `/api/health` on HTTPS, Generate/Execute/Convert on a live production URL, customer data unchanged.

---

## 4. What failed / what is missing

| Missing requirement | Why deployment stopped |
|---------------------|-------------------------|
| **Production host identity** | DNS/IP not recorded in ops docs; no target URL in workspace |
| **Operator access** | RDP/SSH (or equivalent) to production server — **not available to the agent** |
| **Production configuration overlay** | Real deploy needs `delivery.install.production=true`, HTTPS `delivery.public-base-url`, non-default `delivery.admin-password`, `KEEL_DB_PASSWORD`, provider allowlist, etc. (see `deployment.md` Production configuration) |
| **Deploy permissions** | No scripted remote deploy in repo; manual VPS procedure requires human operator on the host |

**Repair flags:** Requirements 3–4 are satisfied **in source defaults** for the artifact to be built; they are **not** confirmed on a running production JVM until deploy completes.

---

## 5. Exact production deployment procedure (from repo — for operator)

1. **Host:** Windows Server (or JDK 21 host per `windows-vps-setup.md`); clone/copy repo to e.g. `C:\apps\TestPilot` at desired **git SHA** (record in deployment log).
2. **Secrets (on host only, not committed):** `keel-env.local.bat` — `JAVA_HOME`, `KEEL_DB_PASSWORD`, optional `KEEL_PORTAL_HOME`, mail/Slack as needed.
3. **Properties:** Production overlay on `application.properties` or external config:
   - `delivery.install.production=true`
   - `delivery.install.mode=shared` (or dedicated per policy)
   - HTTPS `delivery.public-base-url`
   - Strong `delivery.admin-password`, datasource password, `delivery.provider.allowlist`
   - **Keep disabled:** `delivery.agent-harness.enabled=false`, `delivery.agent-harness.cursor.enabled=false`, `delivery.agent-harness.automate-framework-repair.enabled=false`
4. **Build (on host or CI artifact):** `mvn -pl keel-portal -am -DskipTests package` (or deploy pre-built jar).
5. **Start:** `start-portal.cmd` or `java -jar keel-portal\target\keel-portal-1.0-SNAPSHOT.jar` with store/work dirs on persistent volume.
6. **TLS:** Win-ACME / Caddy → `localhost:8080` per setup doc.
7. **Verify:**
   - `GET /api/health` → `installMode`, `production=true`
   - `GET /api/ready` → `storeWritable=true`
   - Smoke: login, existing project loads, **Convert with repair disabled** completes or fails only on normal business rules (no `agent-runs` repair tasks if contract passes; if repair needed → `REPAIR_NOT_ENABLED`)
   - Confirm no new `FRAMEWORK_ADAPTATION_V1` tasks while flags off
8. **Record:** git SHA, jar timestamp, startup log snippet, health/ready JSON, one Convert job id (if run).

**Service persistence:** NSSM/WinSW per `windows-vps-setup.md`.

---

## 6. One next action — first repair pilot (after deploy)

When production Keel is **live** with harness/repair **disabled**:

**Enable the first repair pilot** only after ops picks pilot scope (per Phase 6.16: dedicated instance or monitored shared prod) and then, on **that production host**, set all three:

```properties
delivery.agent-harness.enabled=true
delivery.agent-harness.cursor.enabled=true
delivery.agent-harness.automate-framework-repair.enabled=true
```

plus production `CURSOR_API_KEY`, `delivery.provider.allowlist` including `cursor`, and restart the portal. Run **one** monitored CONVERT that requires repair; archive `framework-acceptance/framework-repair-operational.json`.

**Single next action before that:** **Provide production host access and deploy the current jar with harness flags false**, then attach health/ready evidence from the live URL.
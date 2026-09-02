# Gap analysis — TestPilot Delivery (refreshed 2026-08-06)

**Date**: 2026-08-06 (supersedes stale 2026-08-05 claims)  
**Method**: Spec Kit `001` + `002` + `003` + current code  
**Status**: Living checklist after two-phase conversion landing

## Favicon noise (answered)

`NoResourceFoundException: favicon.ico` is **not a product bug**. Mitigation: `/favicon.ico` → **204**.

---

## What is already done (keep)

| Area | Status |
|------|--------|
| Excel ingest + validation | Done |
| Local LLM client (Ollama-only) + validators | Done |
| Live login prelude + cold start per TC | Done (`JobLoginService`) |
| Stepwise binder + RequiredControlFiller | Done |
| **Two-phase prove → emit** | Done (`ProvePhase` / `EmitPhase`) |
| IR drafts under `delivery-work/.../ir/` | Done |
| Step retry ≤2 | Done |
| Page clustering by URL | Done |
| Locator map + project `ir/` persistence | Done |
| Partial TODO tests with proven steps | Done |
| Invite auth, dashboard, upload/status/download | Done |
| **Job history `/jobs` + re-download** | Done |
| Windows VPS runbook + `start-portal.bat` | Done |
| Secrets not in ZIP | Done |

---

## Remaining gaps

| ID | Gap | Priority |
|----|-----|----------|
| T041 | Live **NEW** smoke against any reachable demo/customer app + Ollama — release gate | **P0** |
| T049 | Live **UPDATE** smoke | **P1** |
| T068 | Full quickstart checklist outcomes | **P1** |
| SMTP invites in prod | Ops | **P1** |
| Evidence screenshots in portal UI | Product | **P2** |
| Admin all-tenant jobs view | Product | **P2** |
| Login lockout / rate limit | Security | **P2** |
| HTTPS + Windows Service | Ops | **P2** |
| Cancel / requeue job | Product | **P2** |
| Assertion depth (textContains / expected mapping) | Engine | **P2** |
| Intent coverage (iframe, upload, tables) | Engine | **P3** |

## Explicitly out of scope (YAGNI)

- Open self-registration, billing, OAuth  
- Docker/Linux migration as requirement  
- GitHub PR delivery  
- Cloud LLM fallback  

---

## Recommended next

1. Run `docs/ops/e2e-conversion-gate.md` (T041/T049) and record results.  
2. SMTP + HTTPS on first VPS.  
3. Evidence gallery on status page (optional).  

**Track A (conversion quality) is largely implemented** via `003-two-phase-conversion`. Remaining P0 is the live golden gate.

# Adopt A5 — cloud tunnel broker

**Date:** 2026-11-07  
**Status:** Shipped (broker + job binding + CLI relay). Live smoke script + `TunnelBrokerApiTest`.

## What shipped

| Piece | Location |
|-------|----------|
| HTTP relay queue | `TunnelRelayService` |
| Public proxy | `GET/POST … /tunnel/p/{token}/**` → `TunnelPublicProxyController` |
| Agent API | `POST /api/tunnel/agent/poll`, `/complete` → `TunnelAgentController` (`agentSecret`) |
| Enrollment | `POST /api/projects/{id}/tunnel/enroll` → token + `agentSecret`; disk `tunnel/enrollment.json` + `tunnel-index/{token}.json` |
| Heartbeat | `POST …/tunnel/heartbeat` → status **active** + `listenUrl` |
| Execute base URL | `TunnelJobBaseUrlService` → `{public-base-url}/tunnel/p/{token}/` when active |
| CLI | `keel tunnel start` → `TunnelLocalProxy` + `TunnelAgentExecutor` relay loop |
| Fat CLI jar | `mvn -pl keel-portal -Pkeel-cli package` → `keel-portal/target/keel-cli.jar` |

## Enable (cloud or local)

```properties
delivery.tunnel.enabled=true
delivery.tunnel.public-base-url=https://portal.example.com
```

Local dev: set `public-base-url` to the portal origin (e.g. `http://127.0.0.1:8080`).

```bash
keel tunnel start --project <id> --target http://127.0.0.1:<app> [--listen 4040]
```

Execute/suite runs then hit the relay URL; the agent forwards to `--target` (via local proxy listen port).

## Security

- Public proxy: token in URL only (rate-limit at edge in prod).
- Agent: `agentSecret` on poll/complete; must send `X-Keel-Requested-With: Keel` on POST (D11).
- Heartbeat: project owner auth + matching enroll token.

## Verification

| Check | Command |
|-------|---------|
| Offline broker | `mvn -pl keel-portal -Dtest=TunnelBrokerApiTest test` |
| Live (local JVM) | `powershell -File scripts/tunnel-live-smoke.ps1` |

## Known defects / gaps

1. **`tunnel start` re-enrolls** every run (new token); does not reuse prior enrollment.
2. **Stale `tunnel-index/*.json`** after re-enroll (harmless; old token fails token match).
3. **No mTLS**; long-poll only (no WebSocket).
4. **Quality gate** still runs against effective base URL (relay); localhost-only apps need tunnel active or a reachable `baseUrl`.

## Bugs fixed (2026-11-07)

1. Agent relay **403** — missing `X-Keel-Requested-With: Keel` on poll/complete.
2. Local proxy **502** — `TunnelLocalProxy` forwarded forbidden hop-by-hop headers to `HttpClient`.
3. Public relay clients — strip hop-by-hop headers on responses (`TunnelPublicProxyController`, `TunnelAgentExecutor`).

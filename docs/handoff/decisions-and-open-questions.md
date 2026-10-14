# Decisions & open questions

## Locked decisions (from your Grok answers)

| Topic | Decision |
|-------|----------|
| **Committing** | Parked until you ask |
| **Company PC** | No network/firewall/proxy/VPN automation changes |
| **Split now** | Same machine, **separate folders/apps**, `.cmd` launchers; real split on own RDP later |
| **Portal port (local)** | **8080** |
| **Database (future servers)** | **Microsoft SQL Server** (Express OK); not Mongo |
| **Engine count** | One now; scale later |
| **Axis VPN** | Manual FortiClient when running Axis jobs |
| **Chrome** | Headless OK; **1920×1080**; headful fallback if vision needs it — **you approved** |
| **Assertion models** | **UI-TARS default**; switch to **qwen2.5vl:7b** when strong GPU server |
| **UI-TARS license** | **Apache 2.0** (UI-TARS-2B-SFT) — OK commercial |
| **Alerts (future)** | Telegram + email + Slack (optional) |
| **RDP security (future)** | Prefer VPN before RDP; not enforced on company PC now |
| **UX overhaul** | Yes to all five Frontend items (on reskin) |
| **Web research ideas** | Assigned (tap-effect, settle table, evidence, loop guards, flaky reporting) |
| **Mobile** | After Keel web stable |
| **Theme prototype** | Reference only; compare to current Keel reskin before adopting |

## Open / verify

| # | Question | Why it matters |
|---|----------|----------------|
| 1 | **UI-TARS Ollama version 1.0 vs 1.5?** (`ollama show ui-tars`) | Coordinate parsing mode |
| 2 | **Did qwen2.5vl:7b finish downloading?** | Assertion switch |
| 3 | **Are all 45 Grok patches really in your tree?** | `mvn test`, Axis TC_02 |
| 4 | **Export Grok `/workspace/research/*.md` and SPLIT_DESIGN** | Not in git today |
| 5 | **DB backup before first portal start after patch 40** | Irreversible column migration |
| 6 | **Which assertion model for production long-term** | 3b non-commercial; 7b needs GPU |
| 7 | **Canvas test URL** | A5 validation |
| 8 | **Keel GitHub issues #1 #2** | Track password-at-rest + H2 |

## Backups Grok mentioned (check disk)

- `D:\priv\testpilot\backup-before-patches-20261013-170125.zip`
- `D:\priv\testpilot\backup-before-integration-20261013-194717.zip`
- `D:\priv\testpilot\db-backup-20261013-214547\`
- `backup-uncommitted.diff` (if created)
- Temp: `%TEMP%\tp-kb.zip`, `tp-sidebar.zip` — safe to delete

## If Grok data is not enough

The chat you pasted **is enough** for planning and handoff. It is **not** enough to recover exact patch diffs — those lived on Grok’s `/workspace/`. Next steps:

1. Use this handoff + **your uncommitted git diff** as truth.
2. Re-run targeted research in Cursor (Midscene/Maestro/e2e) into `docs/research/` if exports are lost.
3. One acceptance test: **Axis TC_02 Support** after portal restart.

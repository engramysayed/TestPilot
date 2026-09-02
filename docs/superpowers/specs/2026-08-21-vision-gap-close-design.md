# Gap close: role split defaults + DOM post-click + miss journal — design

**Date:** 2026-08-21  
**Status:** Implementing (user directed: close high/med/low gaps; no commits)

## Decisions

| Item | Choice |
|------|--------|
| Ground | `uitars` / `ui-tars` |
| Assert | `qwen` / `qwen2.5vl:3b` |
| Config | Only `grounding.*` / `assert.*` in `application.properties` |
| DOM post-click | After successful `click` in `TcExecutionService`; compare URL/title/body/login-form |
| Strictness | Enabled by default; **non-strict** (log + warn, do not fail TC) unless `delivery.vision.dom-post-click.strict=true` |
| Miss journal | JSONL append under `delivery.vision.miss-log.dir` (default `delivery-store/vision-misses`) |
| Echo gate | Keep softened paraphrase rule; evidence uses assert provider/model |
| Commits | Never (user rule) |

## Components

- `DomPostClickValidator` + config flags  
- `VisionMissJournal` + richer records from heal/sweep  
- Props + evidence/attempt provider fields  
- Live smoke: recommended split (TARS ground + Qwen assert)

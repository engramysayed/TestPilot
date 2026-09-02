# Tasks: 003-two-phase-conversion

## Phase 1 IR + prove

- [x] `TcDraft` / `TcDraftStatus` / `TcDraftStore` / `ProvenStepCodec`
- [x] `ProvePhase` with max 2 retries and IR write after each TC
- [x] Blocker stops remaining intents; PARTIAL vs TODO

## Phase 2 emit

- [x] `PageClusterer` URL → page stem
- [x] `EmitPhase` load IR → cluster → CodeWriter → score → ZIP
- [x] `LocatorMapBuilder` + persist to project store + ZIP docs
- [x] Copy IR drafts into project store `ir/`

## Portal

- [x] `/jobs` history UI + nav link
- [x] `GET /api/jobs` list
- [x] Progress messages from ProvePhase (`Phase1 … step … retry …`)

## Verification

- [x] Unit tests: TcDraftStore, PageClusterer, LocatorMapBuilder, EmitPhase mapping
- [ ] Live NEW + UPDATE gate against a real app (see `docs/ops/e2e-conversion-gate.md`)

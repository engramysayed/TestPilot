# Support scope (first release engineering)

This is the support contract **as implemented locally**, scoped to **shared hosting**. Product/privacy sign-off (P0-01) and **shared staging** isolation/backup drills remain release blockers. Do not advertise those as guarantees.

Dedicated installations and private runners are **implemented** and **out of first-release support**. Do not sell or staff them as launch features.

**Validation candidate:** `2721e6d` (unchanged). Public rollout: HOLD.

## Supported paths (first release, once gates close)

- Invite-only portal accounts on a **shared** install (`delivery.install.mode=shared`); MEMBER read-only; OWNER/ADMIN operate; OWNER-only project delete
- Generate (stories / CSV / JSON) → project library with immutable revisions
- Execute and Automate against a project **public** base URL, with Keel or Precision authoring (DOM/locator proof)
- Bug Hunter packs (no auto-merge into the library)
- Downloaded Selenium/TestNG ZIP replay of **fixture-proven** cases (P0-03)
- Shared-mode configuration only (`private-cidrs` must stay empty)

## Not a first-release support guarantee

- Dedicated install / private CIDRs (code preserved; [dedicated-install.md](reviews/2026-09-15/dedicated-install.md))
- Private runners (code preserved; [private-runner.md](ops/private-runner.md))
- Live UI-TARS click-grounding or live vision role-split as product proof ([vision-live-smoke-rca.md](reviews/2026-09-15/vision-live-smoke-rca.md))
- Kernel/worker network isolation on a **deployed shared staging** host (P2-03). Isolated local Spring Boot drills are not that host.
- Named privacy policy covering screenshots and providers (P2-04 engineering exists locally; sign-off is unsigned)
- Restored backups on **shared staging** storage (P4-02)
- Live LLM proof coverage beyond the authorized representative-app acceptance once that app is named (P0-03 certified fixture replay only until then)

## Data flow (high level)

Browser DOM / URLs / JS results / Hunt packs / sidecar JSON pass through `SecretSanitizer` before model dispatch and persistence. Password-field pixels in PNG captures are painted over by `ScreenshotRedactor`. Provider allowlist is fail-closed. Passwords are job-scoped and not written into customer ZIPs. Hunt credentials use `$TARGET_*` tokens in planner prompts.

Retention: `delivery.retention.days` (default 14). Deletion tombstones then purges credentials, jobs, and disk. Admin domain-folder and admin-user delete use the same purge.

## Result labels

| Kind | Meaning |
|------|---------|
| FRESH | Live browser proof for this job |
| REUSED | Eligible prior proof, not a new execution |
| BLOCKED | Incomplete / soft-block |
| UNCHECKED | TODO / not executed |
| SIMULATED | Dry-run; not live proof |
| INTERRUPTED | Browser-stage interruption; review before replay |

## Recovery

See [shared staging](ops/shared-staging.md), [deployment](ops/deployment.md), [backup/restore](ops/backup-restore.md), and [runbooks](ops/runbooks.md).

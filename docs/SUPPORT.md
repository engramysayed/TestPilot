# Support scope (first release engineering)

This is the support contract **as implemented locally**. Product/privacy sign-off (P0-01) and deployed isolation/backup drills remain release blockers. Do not advertise those as guarantees.

## Supported paths

- Invite-only portal accounts; MEMBER read-only; OWNER/ADMIN operate; OWNER-only project delete
- Generate (stories / CSV / JSON) → project library with immutable revisions
- Execute and Automate against a project base URL, with Keel or Precision authoring
- Bug Hunter packs (no auto-merge into the library)
- Downloaded Selenium/TestNG ZIP replay of **fixture-proven** cases (P0-03)
- Shared and dedicated **configuration** (same identity model)

## Not yet a support guarantee

- Customer isolation on a shared host (P2-01 / P2-02)
- Kernel/worker network isolation on a **deployed** shared or dedicated host (P2-03)
- Named privacy policy covering screenshots and providers (P2-04 engineering exists locally; sign-off is unsigned)
- Restored backups on a production host (P4-02)
- Live LLM proof coverage (P0-03 certified fixture replay only)

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

See [deployment](ops/deployment.md), [backup/restore](ops/backup-restore.md), and [runbooks](ops/runbooks.md).

# Credits model inventory (tip c93aa6d) — 2026-11-02

Read-only snapshot before guide/pricing credit-aware updates. **No USD in product.**

## Defined (unit credit prices)

| Code | Default credits | Unit | Job kinds |
|------|-----------------|------|-----------|
| GENERATE | 10 | Each writing job | GENERATE_BATCH, GENERATE_COMPARE |
| EXECUTE | 5 | Each test case | EXECUTE |
| AUTOMATE | 25 | Each test case | CONVERT |
| HUNT | 15 | Each step | HUNT |

- Seeds / list: `ServicePriceService` → table `service_prices`
- Config fallbacks: `DeliveryCreditsProperties` (`delivery.credits.cost.*`)
- Enum: `CreditsBillingKind`
- Flag: `delivery.credits.enabled` (default false → grants work, job start not gated)

## Charged (debit)

- `CreditService.tryDebitForJob(JobRecord)` at job **start** when enabled
- Quantity: `BillableQuantity` — Generate=1 job; Execute/Automate=runnable cases; Hunt=iterations×cycles×actionCap
- Ledger: append-only `CreditLedgerEntry`; idempotent key `debit:job:{jobId}`
- Insufficient → blocks job (`InsufficientCreditsException`)

## Shown (UI / API)

- Customer pricing table: `templates/pricing.html` → `/pricing` (Credits each)
- Guide `#credits`: balance chip, debit on job start, admin grant/set
- Chrome balance: account credits when enabled (`GET /api/account/credits`)
- Costs DTO: Owner `GET /api/admin/credits/costs`

## Edited (admin)

- **Unit prices:** `/admin/pricing` (`admin-pricing.html`) → POST saves via `ServicePriceService.save` (label, charged-as, covers, cost credits)
- **User balances:** Owner `POST /api/admin/credits/{userId}` SET_ABSOLUTE / GRANT_DELTA + note (admin-users / Set credits UX). Approving signup does **not** grant credits.
- Role: account **owner** only for grants (`AdminCreditsController`); Admin role cannot grant

## Guide / pricing update intent (this wave)

Map new features into this credits model in copy (GFK → Generate credits; plain cases/Evidence heal/visual diffs as part of Execute story unless separately metered later; suites/monitor scheduling noted; Automate W1–W3 under Automate credits). No dollar tiers.

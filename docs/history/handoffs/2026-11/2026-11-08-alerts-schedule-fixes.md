# 2026-11-08 — Real alert senders, per-suite min severity, early-start fix

## Problems this closes

1. **Suite alert channels were stubs.** Slack, Email and Telegram subclasses of `StubAlertChannel`
   only logged `ALERT_STUB` and never left the machine. The portal reported them as "registered",
   which was misleading.
2. **Per-suite `alertMinSeverity` was ignored.** The disk store carried it (`bindAlert`), but no
   code read it before sending.
3. **Runs started 1–2 minutes early.** Both the portal worker (`within-seconds: 60`) and the
   API claim (`tryClaimDue(..., 120)`) picked up slots whose `nextRunAt` was still in the future.

## Fix

- `SlackAlertChannel` — real incoming-webhook POST. URL from
  `keel.alerts.channels.slack.webhook-url` or `KEEL_SLACK_WEBHOOK_URL`.
- `EmailAlertChannel` — real `JavaMailSender` send. Requires `delivery.mail-enabled=true`,
  `spring.mail.*`, and `keel.alerts.channels.email.recipients` (or `KEEL_ALERT_EMAIL_RECIPIENTS`).
- `TelegramAlertChannel` — real `api.telegram.org/bot<TOKEN>/sendMessage` for each chat id.
  Token reused from the account-alert key `keel.alerts.telegram.bot-token` / `KEEL_TELEGRAM_BOT_TOKEN`.
  Chat ids from `keel.alerts.channels.telegram.chat-ids` / `KEEL_TELEGRAM_CHAT_IDS`.
- `AdminAlertsController` — `GET /api/admin/alerts/channels` and
  `POST /api/admin/alerts/channels/{id}/test` for head-admin only. Readiness lists each channel's
  missing pieces.
- `SuiteRunContext` gained `alertMinSeverity` (new 8-arg constructor). `SuiteRunContextFiles` and
  `SuiteRunService` persist and restore it. `SuiteAlertNotifier.send` now uses
  `max(channel.minSeverity, suite.alertMinSeverity)` — the per-suite setting can RAISE the floor
  but never LOWER it. `SuiteService.listDueForAllProjects` surfaces the field too.
- Early-start window closed. `SuitePortalScheduleWorker.within-seconds` default and the
  `SuiteRunService.tryClaimDue` call are both now `0`. A slot is only due once `nextRunAt <= now`.

## What the user still has to provide

Nothing new to code. To actually send alerts:

- **Slack**: an incoming-webhook URL (Slack app → _Incoming Webhooks_).
- **Email**: SMTP host/port/username/password in `spring.mail.*`, plus a comma-separated recipient
  list in `keel.alerts.channels.email.recipients`.
- **Telegram**: bot token from BotFather and the chat id(s) the bot should post to. The easiest way
  to get a chat id is to message the bot once, then
  `curl https://api.telegram.org/bot<TOKEN>/getUpdates`.

Set the matching `keel.alerts.channels.{slack,email,telegram}.enabled=true` and hit
`POST /api/admin/alerts/channels/<id>/test` to confirm delivery.

## Slack dropped, Telegram off by default (follow-up, same day)

- Removed `SlackAlertChannel` and every Slack path: the account page input, the admin readiness
  entry, the suites UI label, the `Prefs.slackOn/slackWebhook` fields, and the Slack tests. The
  `postJson` method on `AttentionAlertSender.Transport` was dropped with it (no caller left).
- `keel.alerts.channels.telegram.enabled` and `.slack.enabled` had been flipped to `true` by the
  earlier channel work; put Telegram back to `false` and deleted the Slack keys.
- Only email is meant to work now. Gmail: set `KEEL_MAIL_ENABLED=true`,
  `KEEL_MAIL_HOST=smtp.gmail.com`, `KEEL_MAIL_PORT=587`, `KEEL_MAIL_USERNAME=<address>` and
  `KEEL_MAIL_PASSWORD=<app password>` (a Gmail app password, not the account password).
- Alert tests (20/20) pass: `AttentionAlertSenderTest`, `AlertEvaluatorTest`, `AlertWiringTest`,
  `SuiteAlertNotifierTest`. Full suite not rerun.

## Suite alerts reach project members (follow-up, same day)

Before, only the admin's server-wide list got suite alerts. Now each suite alert also goes to every
project member who turned on account alerts, using the targets they already saved (email address,
Telegram chat id, Slack webhook). It works whether or not the suite is bound to a server channel.

- `SuiteAlertNotifier.registerMemberAlerts(BiConsumer<projectId, Alert>)`. `send(...)` now takes the
  run context explicitly, so `notifyError` also respects the suite's `alertMinSeverity` (before, it
  read the thread-local, which may be empty there).
- `PortalStore.projectMemberIds(projectId)`: workspace members, or the owner of a personal project.
- `AccountAlertService.notifySuiteAlert(memberIds, projectId, alert)`: `@Async("webhookExecutor")`,
  one failing user never blocks the others.
- Wired in `SuiteScheduleWiring`.
- Test: `SuiteAlertNotifierTest.membersGetAlertEvenWhenUnboundAndSuiteFloorApplies`.
  `SuiteAlertNotifierTest`, `AlertWiringTest` and `AttentionAlertSenderTest` pass (16/16).

Not done: no per-suite opt-out for a member (account alerts on = all suites in your projects), and no
Telegram one-tap `/start` link (users still paste a numeric chat id).

## Mobile toolchain check

`scripts/check-mobile-toolchain.cmd` checks `adb` (Android platform-tools) and `appium` (plus the
uiautomator2 driver) on the portal machine. It does not install, only reports. Run before starting
the mobile runner.

## Dev-tool installs

`scripts/install-dev-tools.cmd` is a one-shot installer for Agent Skills, Context7, Ponytail,
Graphify, spec-kit and superpowers.

## Build / test

- `mvn -pl keel-portal -am -DskipTests package`
- `mvn -pl keel-portal test`

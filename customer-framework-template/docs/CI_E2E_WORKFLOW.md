# The E2E workflow (`.github/workflows/E2E.yml`)

The framework ships one always-on workflow, **E2E.yml**. It runs `LoginTest` headless on Edge and
Chrome (Windows runners):
- on every push to `master`;
- on every pull request targeting `master`;
- on a schedule, once a day at 21:00 UTC (`cron: '0 21 * * *'`, midnight Cairo summer time);
- manually, from **Actions > test pipeline > Run workflow** (`workflow_dispatch`).

It reads no secrets. (The separate PR report workflow is opt-in; see `CI_PR_REPORT.md`.)

## Disable it
Pick one:
1. **GitHub UI (reversible, no commit):** Actions > *test pipeline* > "..." > **Disable workflow**.
   Re-enable it the same way.
2. **Only stop the nightly run:** delete the `schedule:` block (the `- cron:` line and its parent)
   from `E2E.yml`. Push, PR and manual runs keep working.
3. **Only run it manually:** replace the whole `on:` block with
   ```yaml
   on:
     workflow_dispatch:
   ```
4. **Remove it:** delete `.github/workflows/E2E.yml`.

GitHub also disables scheduled workflows automatically in public repositories after 60 days without
repository activity. Re-enable them from the Actions tab.

## Change the time
The cron is in UTC: `minute hour day-of-month month day-of-week`. For example, `0 3 * * 1-5` runs
at 03:00 UTC on weekdays. Never use `*` in the minute field: that runs every minute of the hour.

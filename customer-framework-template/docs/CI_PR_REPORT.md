# Optional CI report and sticky PR comment

This framework ships an **opt-in** GitHub Actions workflow, `.github/workflows/keel-pr-report.yml`,
and a summariser, `.github/keel/summarize_results.py`. **It is off by default**: the job is skipped
unless you enable it. Nothing is ever posted without your opt-in.

## What it reports
- Passed / **Flaky** / Failed / Skipped counts, retries used and the first-pass rate.
  A test that only passed after a retry is listed as **Flaky**, never as a clean pass.
  Only read-only tests (`@KeelReadOnly`) are ever retried.
- Failed tests with the first line of the error.
- **Fallback locator wins**: elements whose main locator no longer resolved, so a ranked fallback
  was used. These pages should be re-proved or updated.

The data comes from `test-output/keel-results/*.jsonl` (written by the framework) and, as a
fallback, from the JUnit XML in `target/surefire-reports`.

## Enable it
1. In GitHub, open **Settings > Secrets and variables > Actions > Variables** (variables, not
   secrets) and add `KEEL_PR_REPORT` = `true`. PRs now run the tests, write the job summary and
   upload the results as an artifact.
2. Optional: add `KEEL_PR_COMMENT` = `true` to also keep **one** sticky comment on the PR. It is
   found by a hidden marker and edited in place on every rerun.
3. Optional: add `KEEL_TEST_ARGS` (e.g. `-Dtest="TC_*" -DEXECUTION_TYPE=HEADLESS -DBROWSER_TYPE=CHROME`).
4. If your organisation restricts workflow token permissions, allow "Read and write permissions"
   for pull requests or the comment step will get a 403. The job only asks for
   `pull-requests: write` and `contents: read`.

To disable it, delete the variable or set it to anything other than `true`.

## Security
- No secrets are read, required or embedded. Only the built-in, short-lived `GITHUB_TOKEN` is used.
- It runs on `pull_request`, not `pull_request_target`, so PR code never runs with write tokens
  from the base repository. Comments are skipped for PRs from forks.
- Application credentials (TARGET_USERNAME/PASSWORD) are never part of the report. If your tests
  need them in CI, supply them as GitHub secrets mapped to env vars in your own workflow; do not
  commit them.

## Run the summariser locally
```
python3 .github/keel/summarize_results.py --surefire target/surefire-reports --keel test-output/keel-results
```

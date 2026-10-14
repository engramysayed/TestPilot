#!/usr/bin/env python3
"""Summarise a Keel framework test run as Markdown (job summary / sticky PR comment).

Inputs (all optional; missing inputs are reported, never fatal):
  --surefire DIR   JUnit XML from maven-surefire (TEST-*.xml)
  --keel DIR       test-output/keel-results (verdicts.jsonl, locator-events.jsonl)
Output: Markdown on stdout or --out FILE, starting with the hidden marker used to find and
update the single sticky PR comment. Standard library only; reads no secrets or env vars.
"""
import argparse
import glob
import html
import json
import os
import sys
import xml.etree.ElementTree as ET

MARKER = "<!-- keel-pr-report v1 -->"
MAX_ROWS = 20


def esc(text, limit=160):
    text = " ".join(str(text).split())
    if len(text) > limit:
        text = text[: limit - 1] + "\u2026"
    return html.escape(text).replace("|", "&#124;").replace("`", "&#96;")


def read_jsonl(path):
    rows = []
    if not os.path.isfile(path):
        return rows
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                try:
                    rows.append(json.loads(line))
                except ValueError:
                    pass
    return rows


def surefire_totals(directory):
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "files": 0}
    failed = []
    for path in sorted(glob.glob(os.path.join(directory or "", "TEST-*.xml"))):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        totals["files"] += 1
        suites = [root] if root.tag == "testsuite" else root.findall("testsuite")
        for suite in suites:
            for key in ("tests", "failures", "errors", "skipped"):
                totals[key] += int(suite.get(key, "0") or 0)
            for case in suite.findall("testcase"):
                bad = case.find("failure")
                if bad is None:
                    bad = case.find("error")
                if bad is not None:
                    failed.append((case.get("classname", "") + "." + case.get("name", ""),
                                   bad.get("message") or bad.get("type") or ""))
    return totals, failed


def final_verdicts(rows):
    """Last non-RETRIED verdict per test (a retried attempt is not a result)."""
    final = {}
    for row in rows:
        if row.get("verdict") != "RETRIED":
            final[row.get("test", "?")] = row
    return final


def build(surefire_dir, keel_dir):
    out = [MARKER, "## Keel test report", ""]
    verdict_rows = read_jsonl(os.path.join(keel_dir or "", "verdicts.jsonl"))
    events = read_jsonl(os.path.join(keel_dir or "", "locator-events.jsonl"))
    totals, sure_failed = surefire_totals(surefire_dir)
    final = final_verdicts(verdict_rows)
    failed_count = None
    if final:
        counts = {}
        for row in final.values():
            counts[row.get("verdict")] = counts.get(row.get("verdict"), 0) + 1
        failed_count = counts.get("FAILED", 0)
        retries = sum(1 for r in verdict_rows if r.get("verdict") == "RETRIED")
        first_pass = counts.get("PASSED", 0)
        executed = sum(counts.get(k, 0) for k in ("PASSED", "FLAKY", "FAILED"))
        status = "\u274c failed" if failed_count else ("\u26a0\ufe0f passed with flaky tests" if counts.get("FLAKY") else "\u2705 passed")
        out += [f"**Result:** {status}", "",
                "| Passed | Flaky | Failed | Skipped | Retries used | First-pass rate |",
                "|---:|---:|---:|---:|---:|---:|",
                f"| {counts.get('PASSED', 0)} | {counts.get('FLAKY', 0)} | {failed_count} | {counts.get('SKIPPED', 0)} "
                f"| {retries} | {(100.0 * first_pass / executed) if executed else 0:.0f}% |", ""]
        flaky = [r for r in final.values() if r.get("verdict") == "FLAKY"]
        if flaky:
            out += ["### Flaky (passed only after a retry, not a clean pass)", ""]
            out += [f"- `{esc(r.get('test'))}` passed on attempt {int(r.get('attempt', 0))}" for r in flaky[:MAX_ROWS]]
            out.append("")
        failed = [r for r in final.values() if r.get("verdict") == "FAILED"]
        if failed:
            out += ["### Failed", ""]
            out += [f"- `{esc(r.get('test'))}`: {esc(r.get('error', ''))}" for r in failed[:MAX_ROWS]]
            out.append("")
    elif totals["files"]:
        failed_count = totals["failures"] + totals["errors"]
        out += ["_No Keel verdicts found; counts come from JUnit XML (retried attempts appear as skipped)._", "",
                "| Tests | Failures | Errors | Skipped |", "|---:|---:|---:|---:|",
                f"| {totals['tests']} | {totals['failures']} | {totals['errors']} | {totals['skipped']} |", ""]
        if sure_failed:
            out += ["### Failed", ""] + [f"- `{esc(n)}`: {esc(m)}" for n, m in sure_failed[:MAX_ROWS]] + [""]
    else:
        out += ["_No test results were found (did the test step run?)._", ""]

    if events:
        grouped = {}
        for e in events:
            key = (e.get("locator", "?"), int(e.get("index", 0)), e.get("target", ""))
            grouped[key] = grouped.get(key, 0) + 1
        out += ["### Fallback locator wins", "",
                "The main locator did not resolve and a ranked fallback was used. Re-prove or update these pages.", "",
                "| Locator | Fallback # | Target | Times |", "|---|---:|---|---:|"]
        for (loc, idx, target), n in sorted(grouped.items(), key=lambda kv: -kv[1])[:MAX_ROWS]:
            out.append(f"| `{esc(loc, 60)}` | {idx} | `{esc(target, 120)}` | {n} |")
        out.append("")
    else:
        out += ["_No fallback locator wins._", ""]
    return "\n".join(out) + "\n", (failed_count or 0)


def _utf8_stdio():
    """Windows consoles default to cp1252 and crash on the status emoji: force UTF-8 (Python 3.7+)."""
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            try:
                stream.reconfigure(encoding="utf-8", errors="replace")
            except (ValueError, OSError):
                pass


def main(argv=None):
    _utf8_stdio()
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--surefire", default="target/surefire-reports")
    ap.add_argument("--keel", default="test-output/keel-results")
    ap.add_argument("--out")
    args = ap.parse_args(argv)
    text, failed = build(args.surefire, args.keel)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(text)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())

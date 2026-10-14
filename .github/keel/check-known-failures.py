#!/usr/bin/env python3
"""Exit 0 iff Surefire/TestNG failures are a subset of the known-failure allowlist.

Errors (not failures) always fail the check. Skips are ignored.
Looks under keel-*/target/surefire-reports/ and target/surefire-reports/.
Optional --maven-log: parse Maven stdout and require the LAST "Tests run:" summary
to match XML-derived counts (guards against mid-log flaky reprints like 2029/152 then 2028/9).
Stdlib only. No network.
"""
from __future__ import annotations

import argparse
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

SUMMARY_RE = re.compile(
    r"Tests run:\s*(\d+)\s*,\s*Failures:\s*(\d+)\s*,\s*Errors:\s*(\d+)\s*,\s*Skipped:\s*(\d+)"
)


def load_allowlist(path: str) -> set[str]:
    out: set[str] = set()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "#" not in line and "." in line:
                cls, _, meth = line.rpartition(".")
                out.add(f"{cls}#{meth}")
            else:
                out.add(line)
    return out


def iter_report_files(roots: list[str]) -> list[str]:
    files: list[str] = []
    for root in roots:
        files.extend(glob.glob(os.path.join(root, "TEST-*.xml")))
        files.extend(glob.glob(os.path.join(root, "**", "TEST-*.xml"), recursive=True))
        files.extend(glob.glob(os.path.join(root, "testng-results.xml")))
        files.extend(glob.glob(os.path.join(root, "**", "testng-results.xml"), recursive=True))
    seen: set[str] = set()
    uniq = []
    for f in files:
        if f not in seen:
            seen.add(f)
            uniq.append(f)
    return uniq


def failures_from_junit(path: str) -> tuple[set[str], set[str]]:
    fails: set[str] = set()
    errors: set[str] = set()
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return fails, errors
    for tc in root.iter("testcase"):
        cls = tc.get("classname") or ""
        name = tc.get("name") or ""
        key = f"{cls}#{name}" if cls else name
        if tc.find("error") is not None:
            errors.add(key)
        elif tc.find("failure") is not None:
            fails.add(key)
    return fails, errors


def failures_from_testng(path: str) -> tuple[set[str], set[str]]:
    fails: set[str] = set()
    errors: set[str] = set()
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return fails, errors
    for tm in root.iter("test-method"):
        status = (tm.get("status") or "").upper()
        if status not in ("FAIL", "FAILURE"):
            continue
        sig = tm.get("signature") or ""
        name = tm.get("name") or ""
        cls = ""
        if sig and "." in sig:
            head = sig.split("(")[0]
            cls, _, meth = head.rpartition(".")
            name = meth or name
        key = f"{cls}#{name}" if cls else name
        fails.add(key)
    return fails, errors


def last_maven_summary(path: str) -> tuple[int, int, int, int] | None:
    """Return (run, failures, errors, skipped) from the LAST matching line."""
    last = None
    with open(path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            m = SUMMARY_RE.search(line)
            if m:
                last = tuple(int(x) for x in m.groups())
    return last  # type: ignore[return-value]


def junit_totals(reports: list[str]) -> tuple[int, int, int]:
    """Approximate run/fail/error counts from JUnit XML (skips omitted)."""
    run = fails = errors = 0
    for path in reports:
        if path.endswith("testng-results.xml"):
            continue
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        # suite-level attrs if present
        if root.tag == "testsuite":
            run += int(root.get("tests") or 0)
            fails += int(root.get("failures") or 0)
            errors += int(root.get("errors") or 0)
            continue
        for suite in root.iter("testsuite"):
            run += int(suite.get("tests") or 0)
            fails += int(suite.get("failures") or 0)
            errors += int(suite.get("errors") or 0)
    return run, fails, errors


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument(
        "--allowlist",
        default=".github/keel/known-failures-product.txt",
        help="Path to allowlist file (FQCN#method per line)",
    )
    ap.add_argument(
        "--surefire-root",
        action="append",
        default=[],
        help="Surefire reports root (repeatable). Default: keel-*/target/surefire-reports and target/surefire-reports",
    )
    ap.add_argument(
        "--maven-log",
        default=None,
        help="Optional Maven/full-suite log; LAST 'Tests run:' summary must match XML failure+error totals",
    )
    ap.add_argument(
        "--allow-summary-mismatch",
        action="store_true",
        help="Warn instead of fail when --maven-log last summary disagrees with XML",
    )
    args = ap.parse_args(argv)
    roots = args.surefire_root
    if not roots:
        roots = sorted(glob.glob("keel-*/target/surefire-reports")) + ["target/surefire-reports"]
    allow = load_allowlist(args.allowlist)
    fails: set[str] = set()
    errors: set[str] = set()
    reports = iter_report_files(roots)
    if not reports:
        print("check-known-failures: no surefire reports found under:", roots, file=sys.stderr)
        return 2
    for path in reports:
        if path.endswith("testng-results.xml"):
            f, e = failures_from_testng(path)
        else:
            f, e = failures_from_junit(path)
        fails |= f
        errors |= e
    unexpected = sorted(f for f in fails if f not in allow)
    allowed_hit = sorted(f for f in fails if f in allow)
    print(f"reports: {len(reports)}")
    print(f"failures: {len(fails)} (allowed {len(allowed_hit)}, unexpected {len(unexpected)})")
    print(f"errors: {len(errors)}")
    for f in allowed_hit:
        print(f"  ALLOWED  {f}")
    for f in unexpected:
        print(f"  NEW      {f}")
    for e in sorted(errors):
        print(f"  ERROR    {e}")

    if args.maven_log:
        summary = last_maven_summary(args.maven_log)
        if summary is None:
            print(f"WARN: no Tests run: summary in {args.maven_log}", file=sys.stderr)
        else:
            run, log_fail, log_err, skipped = summary
            print(f"maven-log LAST summary: run={run} failures={log_fail} errors={log_err} skipped={skipped}")
            xml_run, xml_fail, xml_err = junit_totals(reports)
            # Prefer comparing fail+error counts; mid-log reprints caused D-QA-06 noise
            if (log_fail, log_err) != (len(fails), len(errors)) and (log_fail, log_err) != (xml_fail, xml_err):
                msg = (
                    f"summary mismatch: log last failures/errors=({log_fail},{log_err}) "
                    f"vs xml named=({len(fails)},{len(errors)}) suite-attr=({xml_fail},{xml_err})"
                )
                if args.allow_summary_mismatch:
                    print("WARN: " + msg, file=sys.stderr)
                else:
                    print("FAIL: " + msg, file=sys.stderr)
                    print("Use LAST summary only; if log has multiple Tests run: lines, earlier ones are ignored.", file=sys.stderr)
                    return 1

    if errors:
        print("FAIL: surefire errors are never allowlisted", file=sys.stderr)
        return 1
    if unexpected:
        print("FAIL: unexpected failures (not in allowlist)", file=sys.stderr)
        return 1
    print("OK: failures are within the known allowlist (or zero)")
    return 0


if __name__ == "__main__":
    sys.exit(main())

# Mobile M5 / M6 acceptance report

**Date:** 2026-10-15  
**Machine:** company PC. No emulator or physical device was used.  
**Live Cursor / UI-TARS acceptance:** not run.

## What this report is

M5 and M6 started as deterministic code and tests. The device matrix in `docs/superpowers/plans/2026-09-27-android-mobile/acceptance.md` was not executed. This file does not change any public launch decision.

## Implemented and tested here

| Item | State |
|------|--------|
| M5-01 WebView match by package, URL, and title; one result; no saved process id; system dialog stays native | Local unit tests |
| M5-02 Chromedriver version and path check; runtime download blocked; native selector rejected inside WebView; mixed native/WebView plan | Local unit tests |
| M5-03 Vision tap only on a fresh matching frame; stale size, rotation, insets, low confidence, blank or secure screen, missing redaction, missing model, stored coordinates, and uncertain result do not tap or retry | Local unit tests |
| M5-04 One Precision candidate; bad id, wrong context, secret text, code text, and batch replies are rejected; unavailable, disallowed, and exhausted Cursor falls back to Keel and is not counted as Cursor | Local unit tests |
| M5-05 Provider and context fields on the result record and in the generated mobile runtime | Local unit tests |
| M6-05 Operator limits | `docs/ops/mobile-operator-limits.md` |
| M6-06 This report | This file |

## Not done

| Item | Why it stays open |
|------|-------------------|
| M5-06 Real Cursor and local vision on a device | Needs a permitted app, a vision model call, and Cursor. A mock is not this gate. |
| M6-01 Full A–F matrix on emulator and a physical phone | No device run in this session. |
| M6-02 Two tenants, crash, kill, cancel, fence | Needs two jobs and a real device lock. |
| M6-03 Secret canary over a live worker, screenshots, Allure, and ZIP | Needs a live run. Unit checks only reject a canary in the Precision reply and a WebView process id in stored fields. |
| M6-04 Full web regression plus portal browser walkthrough | Not run for this change. |

## How to read a later live run

A later run may close a row above only with the command, the device, the app identity, and the result written down. A green unit test does not close M5-06 or M6-01.

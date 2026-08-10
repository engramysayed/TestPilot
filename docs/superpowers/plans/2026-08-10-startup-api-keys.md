# Startup API Keys Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load `AGENTROUTER_API_KEY` (and optional `CURSOR_API_KEY`) from a gitignored local BAT whenever the portal starts.

**Architecture:** `start-portal.bat` optionally calls `api-keys.local.bat`. An example file documents the format. Secrets stay out of Git. The Cursor heal launcher uses the same local file and no longer hardcodes a key.

**Tech Stack:** Windows `.bat`, `.gitignore`

## Global Constraints

- Never print API key values.
- Never commit real keys.
- Portal must still start if `api-keys.local.bat` is missing.
- Remove hardcoded Cursor key from tracked BAT files.

---

### Task 1: Gitignored local keys + BAT wiring

**Files:**
- Create: `api-keys.local.bat.example`
- Create: `api-keys.local.bat` (local only; user fills real keys)
- Modify: `.gitignore`
- Modify: `start-portal.bat`
- Modify: `start-portal-with-cursor-heal.bat`
- Modify: `docs/ops/local-live-ollama.md` (brief pointer if it documents Cursor key setup)

**Interfaces:**
- Consumes: none
- Produces: env vars `AGENTROUTER_API_KEY`, `CURSOR_API_KEY` in the portal process when set in local BAT

- [ ] **Step 1: Add example file**

Create `api-keys.local.bat.example`:

```bat
@echo off
REM Copy to api-keys.local.bat and paste real keys (gitignored).
set "AGENTROUTER_API_KEY=PASTE_AGENTROUTER_KEY_HERE"
set "CURSOR_API_KEY=PASTE_CURSOR_KEY_HERE"
```

- [ ] **Step 2: Gitignore local secrets file**

Add to `.gitignore`:

```
api-keys.local.bat
```

Keep `cursor-api-key.local.bat` ignored for backward compatibility.

- [ ] **Step 3: Wire `start-portal.bat`**

After `cd /d "%~dp0"` and before killing port 8080, load keys:

```bat
if exist "%~dp0api-keys.local.bat" (
  call "%~dp0api-keys.local.bat"
)

if not "%AGENTROUTER_API_KEY%"=="" if /I not "%AGENTROUTER_API_KEY%"=="PASTE_AGENTROUTER_KEY_HERE" (
  echo AGENTROUTER_API_KEY is set for this window ^(value not printed^).
) else (
  echo AGENTROUTER_API_KEY not set — Client delivery final revise will skip Opus until configured.
)

if not "%CURSOR_API_KEY%"=="" if /I not "%CURSOR_API_KEY%"=="PASTE_CURSOR_KEY_HERE" (
  echo CURSOR_API_KEY is set for this window ^(value not printed^).
)
```

- [ ] **Step 4: Clean Cursor heal BAT**

Replace hardcoded key in `start-portal-with-cursor-heal.bat` with:

```bat
if exist "%~dp0api-keys.local.bat" (
  call "%~dp0api-keys.local.bat"
)
if exist "%~dp0cursor-api-key.local.bat" (
  call "%~dp0cursor-api-key.local.bat"
)

if "%CURSOR_API_KEY%"=="" goto :missing_key
if /I "%CURSOR_API_KEY%"=="PASTE_CURSOR_KEY_HERE" goto :missing_key
if /I "%CURSOR_API_KEY%"=="PASTE_YOUR_KEY_HERE" goto :missing_key
```

Update missing-key message to point at `api-keys.local.bat.example`.

- [ ] **Step 5: Create local file for this machine**

Create `api-keys.local.bat` from the example. Leave placeholders unless the user pastes keys in chat; do not invent keys. Tell the user to paste AgentRouter key into that file.

- [ ] **Step 6: Verify**

Run:

```bat
findstr /I /C:"sk-" /C:"crsr_" start-portal.bat start-portal-with-cursor-heal.bat api-keys.local.bat.example
```

Expected: no real secret matches in tracked files (example placeholders only).

Confirm `.gitignore` contains `api-keys.local.bat`.

- [ ] **Step 7: Commit tracked files only**

```bash
git add api-keys.local.bat.example .gitignore start-portal.bat start-portal-with-cursor-heal.bat docs/ops/local-live-ollama.md docs/superpowers/specs/2026-08-10-startup-api-keys-design.md docs/superpowers/plans/2026-08-10-startup-api-keys.md
git commit -m "$(cat <<'EOF'
Load portal API keys from a gitignored local BAT.

EOF
)"
```

Do **not** `git add api-keys.local.bat`.

---

## Spec coverage

| Spec item | Task |
|-----------|------|
| Example file with placeholders | Task 1 Step 1 |
| Gitignore local BAT | Task 1 Step 2 |
| `start-portal.bat` loads local keys | Task 1 Step 3 |
| No key values printed | Task 1 Step 3 |
| Start works without local file | Task 1 Step 3 |
| Remove hardcoded Cursor key | Task 1 Step 4 |
| Local file for machine | Task 1 Step 5 |

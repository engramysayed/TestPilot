# Startup API keys design

**Status:** Draft for approval  
**Date:** 2026-08-10

## Goal

Make AgentRouter and Cursor API keys available whenever `start-portal.bat` runs, without depending on the PowerShell session and without committing secrets.

## Design

1. Add `api-keys.local.bat.example` with placeholders:
   - `AGENTROUTER_API_KEY`
   - `CURSOR_API_KEY` (optional)
2. Add `api-keys.local.bat` to `.gitignore`.
3. Make `start-portal.bat` call `api-keys.local.bat` when present.
4. Print only whether each key is configured; never print key values.
5. Keep startup working when the local file is absent. AgentRouter final revise remains unavailable until its key is configured.
6. Remove the hardcoded Cursor key from `start-portal-with-cursor-heal.bat`; that script uses the same local key file.

## Security

- Local secrets never enter Git.
- Existing hardcoded Cursor credentials must be revoked and replaced.
- No key values appear in logs or generated ZIPs.

## Verification

- Start portal from a fresh Command Prompt or by double-clicking the BAT.
- Confirm the BAT reports AgentRouter configured.
- Confirm the Java process receives `AGENTROUTER_API_KEY`.
- Confirm `.gitignore` excludes `api-keys.local.bat`.
- Confirm no tracked BAT contains a real API key.

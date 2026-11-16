# Track A — Local Ollama live conversion (then server later)

**Date**: 2026-08-05  
**Status**: Awaiting your OK on smoke app + “build”

## What you already have (verified on this PC)

- Ollama is reachable at `http://127.0.0.1:11434`
- Installed model: **`gemma4:e2b`**
- Portal/engine already speak to Ollama via `delivery.llm-base-url` / `delivery.llm-model` (today default is still **dry-run**)

---

## What YOU do (explained)

### 1) Keep Ollama running
Leave the Ollama Windows app/service on while we test.

Check anytime in PowerShell:

```powershell
curl http://127.0.0.1:11434/api/tags
```

You should see `gemma4:e2b`.

### 2) Have Chrome installed
Live authoring opens a real browser. Install Chrome if you don’t have it.

### 3) Choose the first test website (answer needed)
**Recommended:** [https://www.saucedemo.com/](https://www.saucedemo.com/)

| Field | Value |
|-------|--------|
| Base URL | `https://www.saucedemo.com/` |
| Username | `standard_user` |
| Password | `secret_sauce` |

Or tell us **your own app**: base URL + username + password (+ any login notes).

### 4) After we change the code/config — restart portal
```bat
start-portal.bat
```
Then: login (admin) → project → upload Excel → NEW → wait → download ZIP.

### 5) Do **not** move to a VPS yet
We prove everything on your PC first. Server = same settings later + install Ollama + pull `gemma4:e2b` there.

---

## What I will do (detailed)

### Step 1 — Wire config to your model
In `application.properties`:

- `delivery.dry-run=false`
- `delivery.llm-base-url=http://127.0.0.1:11434`
- `delivery.llm-model=gemma4:e2b`

Keep an easy way to turn dry-run back on if Ollama is down.

### Step 2 — Fix login (critical gap)
Today `ConversionJobRunner.maybeLogin` does nothing. I will:

- Log into the smoke app with the job username/password (SauceDemo first)
- Fail the job clearly if login fails
- Never put the password into the ZIP

### Step 3 — Better generated tests
Improve `CodeWriter` / Freemarker so PASSED steps become real TAF page + test calls (not only placeholders). Failed cases stay TODO stubs.

### Step 4 — Local smoke tests
1. NEW with sample Excel + chosen app → COMPLETED job  
2. ZIP has TAF core + score + generated/TODO tests  
3. No password in ZIP  
4. Optional UPDATE with one new TC  
5. Record Spec Kit T041 / T049 / quickstart notes  

### Step 5 — Docs
Short “Live local run” guide + “how to repeat on Windows VPS later”.

---

## Honest expectations

- First runs can be **slow** (model + browser).
- Small models may create more **TODO** than PASSED — OK for MVP if every TC appears with a reason.
- If `gemma4:e2b` is too weak, we can switch model name later without redesign.

## Out of this slice

VPS deploy, HTTPS, SMTP invites, billing/OAuth.

## Locked codegen / locator rules (product owner)

See [`docs/superpowers/specs/2026-08-05-codegen-rules.md`](../specs/2026-08-05-codegen-rules.md):

- Locator order: id → data-test* → name → CSS `tag[attr='value']` → XPath `//tag[@attr='value']`
- Pages: POM (private locators → methods → soft asserts) in `project.pages` only
- Tests: TestNG lifecycle + call page methods; soft assert + log failures; `project.tests*` only
- Soft asserts via TAF `project.validations.Validation` + `LogsManager`

## Decision gate

Smoke app: **SauceDemo**. Rules: **locked** as above.

Reply **`build`** / **`go`** when you want me to start coding Track A Steps 1–5 (config, login, POM Freemarker, local smoke).

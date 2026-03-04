# Manual Testing Agent

An **AI-driven browser testing agent** that uses an LLM (Google Gemini) to plan test steps and Selenium to execute them. You describe a scenario in natural language; the agent runs the flow in a real browser, captures state (HTML + screenshots), and continues until the scenario is complete or a cycle limit is reached.

## What it does

- **Scenario in plain language** — Define test goals in `src/main/resources/scenario.txt` (e.g. “log in, create dashboard, add widget, drag-and-drop, save”).
- **LLM planning** — Gemini receives current page state (current HTML, screenshot, URL, execution history) and returns the next batch of steps in a strict JSON schema.
- **Selenium execution** — Steps are executed in a real browser (Chrome/Edge): clicks, typing, navigation, iframe switching, drag-and-drop, etc.
- **State feedback loop** — After each batch, the agent slims the DOM, takes screenshots, and sends updated state back to the LLM for the next batch.
- **Structured execution memory** — The planner receives compact `historySummary` memory (recent cycles, completed steps, failed attempts, last URL/screenshot) to reduce repetition while keeping prompts efficient.
- **Bug reporting** — On completion, the LLM can report bugs (with evidence) in the final batch.

## High-level flow

1. **Pre-start**: Open base URL, optional login (configurable), initial screenshot.
2. **Loop** (until done or max cycles):
   - Build state: current URL + slimmed HTML + last screenshot ref + history summary.
   - Send **PlannerStart** JSON to Gemini (scenario + state + output schema).
   - Parse **PlannerBatch** response: `steps[]` (actionType, action, selector, value, waits, etc.).
   - Execute each step via Selenium; take screenshots; record results.
   - Update state and repeat.

## Tech stack

- **Java 24**, Maven, TestNG
- **Selenium 4** (Chrome/Edge)
- **Google Gemini API** (text + optional image) for step planning
- **Jsoup** for HTML slimming; **org.json** for JSON; **Log4j2** for logging

## Prerequisites

- JDK 24
- Maven 3.x
- Chrome or Edge (for browser automation)
- [Gemini API key] (set in config)

## Configuration

Main config is loaded from `src/main/resources/webapp.properties`:

| Key | Description |
|-----|-------------|
| `BROWSER_TYPE` | `CHROME` or `EDGE` |
| `BASE_WEB` | Starting URL |
| `ISLOGIN` | `TRUE` to run login before the scenario (uses credentials below) |
| `USERNAME`, `PASSWORD` | Login credentials |
| `userNameLocator`, `passwordLocator`, `clickLocator` | Selectors for login (e.g. `cssSelector:input[placeholder='Username']`) |
| `GEMINI_API_KEY` | Your Gemini API key |
| `GEMINI_MODEL` | Model name (e.g. `gemini-2.5-flash`) |
| `HTML_MAX_CHARS` | Max characters for slimmed HTML sent to planner |
| `DEFAULT_WAIT`, `DEFAULT_SCREENSHOT_WAIT` | Default waits for actions and screenshots |
| `MaxSteps` | Max steps per LLM batch (e.g. `3`) |
| `MaxCycles` | Max planner cycles (e.g. `25`) |

Scenario is loaded from:

- `src/main/resources/scenario.txt`

Configuration notes:

- The project uses a typed Owner-based config layer for most values.
- `GEMINI_API_KEY` is read via `PropertyReader` .

## How to run

```bash
mvn clean test
```

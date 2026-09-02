# TestPilot end-to-end flow

Four views, from widest to narrowest. Every box maps to real code, cited beside it.
Rendered PNGs of all four sit in `docs/ops/diagrams/` for pasting into slides or chat.

## 1. Whole job

```mermaid
flowchart TD
    subgraph P1["Phase 1 — Prove in a real browser · ProvePhase"]
        direction TB
        A1["For each test case:<br/>clear cookies + storage, open base URL"]
        A2["Login prelude if the case needs a session"]
        A3["Parse Excel prose into typed intents"]
        A4["Per intent: bind → execute → heal → re-execute"]
        A5["Write IR draft JSON + screenshots"]
        A1 --> A2 --> A3 --> A4 --> A5
    end

    subgraph P2["Phase 2 — Emit · EmitPhase"]
        direction TB
        B1["Cluster steps into page objects"]
        B2["Honesty pass: demote thin PASSes"]
        B3["Client delivery only:<br/>final revise audit"]
        B4["CodeWriter → page objects + TestNG tests"]
        B5["Compile smoke check"]
        B6["Static Excel vs emitted code check · RevisePhase"]
        B7["Score report, heal metrics, locator map"]
        B1 --> B2 --> B3 --> B4 --> B5 --> B6 --> B7
    end

    U["QA uploads Excel + base URL<br/>optional credentials, optional client delivery"] --> R{"Mode"}
    R -->|NEW| T["Copy framework template"]
    R -->|UPDATE| DF["Hash-diff test cases<br/>re-author only changed ones"]
    DF --> T2["Copy stored framework"]
    T --> P1
    T2 --> P1
    P1 --> P2
    P2 --> Z["package.zip + version saved to project store"]
    Z --> UI["Portal shows PASSED / TODO counts,<br/>verdict, per-step evidence"]
```

Phase numbering in the code is historical: `RevisePhase` (static check) runs *inside* Phase 2, after codegen.

## 2. One test case in Phase 1

```mermaid
flowchart TD
    S["Start test case"] --> N["Navigate to base URL<br/>cookies and web storage cleared"]
    N -->|navigation throws| TODO1["TODO: browser navigate failed"]
    N --> OP["Follow Excel open-path if present"]
    OP --> Q{"Needs an authenticated session?"}
    Q -->|no| INT
    Q -->|yes| LF{"Login form reachable?"}
    LF -->|no| TODO2["TODO: no login form found"]
    LF -->|yes| LB["Author login prelude from the login DOM"]
    LB -->|locator invalid| TODO3["TODO: login binding failed"]
    LB --> LE["Execute login<br/>fall back to JobLoginService"]
    LE -->|still failing| TODO4["TODO: LOGIN_FAILED"]
    LE --> STRIP["Strip login lines from the case body<br/>login moves to @BeforeMethod"]
    STRIP --> INT

    INT["Parse intents: CLICK, TYPE, ASSERT, ordinals, state asserts"] --> E{"Any intents parsed?"}
    E -->|no| BATCH["Batch fallback authoring<br/>invent disabled here"]
    E -->|yes| LOOP["For each intent → step attempt loop, view 3"]

    LOOP -->|any intent unrecoverable| PART["PARTIAL or TODO<br/>keeps proven prefix + failure reason + evidence"]
    LOOP -->|all intents pass| GATE{"Semantic pass gate:<br/>do the steps actually match the Excel case?"}
    GATE -->|reject| PART2["PARTIAL: semantic-gate"]
    GATE -->|accept| PASS["PASSED draft<br/>tagged with the highest heal tier used"]
```

## 3. One step: bind, execute, heal, re-execute

This is `attemptIntentWithRetry`. The outer loop runs up to 3 times; within a single attempt, Ollama may fire once and Cursor once — that cap is what stops heal loops from running forever.

```mermaid
flowchart TD
    A["Slim the live page HTML"] --> AF{"CLICK with required empty fields ahead of it?"}
    AF -->|yes| AF2["Auto-fill those fields first, then re-slim"]
    AF -->|no| B
    AF2 --> B

    B["Bind intent to a DOM candidate<br/>deterministic, no LLM"] --> C{"Bound and locator validated?"}
    C -->|no| H1["HEAL CASCADE, view 4<br/>Ollama allowed"]
    C -->|yes| X

    H1 -->|heals| X
    H1 -->|exhausted| RETRY["Next attempt, up to 3"]

    X["Execute the step in Selenium"] --> Y{"Passed?"}
    Y -->|yes| OK["Step proven, recorded with evidence"]
    Y -->|no| H2{"Which tiers are still unused?"}

    H2 -->|nothing used yet| H3["Re-capture HTML + screenshot<br/>HEAL CASCADE with Ollama"]
    H3 -->|heals| X2["Re-execute healed steps"]
    X2 -->|passes| OK
    X2 -->|fails| H4
    H3 -->|exhausted| RETRY

    H2 -->|Ollama already used| H4["HEAL CASCADE, Ollama skipped<br/>Cursor and invent only"]
    H4 -->|heals| X3["Re-execute"]
    X3 -->|passes| OK
    X3 -->|fails| RETRY
    H4 -->|exhausted| RETRY

    H2 -->|Cursor already used| RETRY
    RETRY -->|attempts left| A
    RETRY -->|out of attempts| FAIL["Step fails<br/>HEAL_EXHAUSTED reason kept for the report"]
```

Every heal call carries the **action history** — a summary of the steps already proven in this case — so the model knows what the page has been through.

## 4. Inside the heal cascade

`HealCascade.heal`. Each tier is cheaper and safer than the one below it, and the result is always re-executed for real before it counts.

```mermaid
flowchart TD
    IN["Failed intent + slim HTML + screenshot<br/>+ failure reason + action history"] --> EX["Extract interactive DOM candidates"]
    EX --> C0{"Any candidates at all?"}
    C0 -->|no| INV1["LAST HOPE: free-invent"]

    C0 -->|yes| DP["Keep only candidates carrying the intent's<br/>distinctive tokens"]
    DP --> W{"Pool empty on a CLICK?"}
    W -->|no| SL["Shortlist: top 12 by relevance"]
    W -->|yes, vision widen ON| WIDE["Widened shortlist: top 16 interactive controls<br/>tier becomes 'vision'"]
    W -->|yes, vision widen OFF| FAIL1["HEAL_EXHAUSTED: no distinctive match"]

    SL --> SE{"Shortlist empty?"}
    WIDE --> SE
    SE -->|yes| INV2["LAST HOPE: free-invent"]
    SE -->|no| OL

    OL{"Ollama allowed on this call?"} -->|yes| OL2["Local vision model picks a candidate id<br/>Thought first, then the id"]
    OL -->|no| CU
    OL2 --> OLV{"Pick binds and survives validation?"}
    OLV -->|yes| OKO["Healed · tier = ollama, or vision if widened"]
    OLV -->|no| CU

    CU["Cursor sidecar picks a candidate id<br/>same shortlist, HTML excerpt, screenshot, history"] --> CUV{"Pick binds and survives validation?"}
    CUV -->|yes| OKC["Healed · tier = cursor, or vision if widened"]
    CUV -->|no| INV3["LAST HOPE: free-invent"]

    subgraph INV["Free-invent — one stateless shot, Cursor by default"]
        direction TB
        I3["Gate 1 — budget: max 2 invents per test case"]
        I1["Prompt carries everything: intent, failure reason,<br/>action history, slim HTML, screenshot"]
        I2["Model writes locators from scratch, max 3 steps"]
        I4["Gate 2 — locator shape and stability"]
        I5["Gate 3 — locator must exist in the captured HTML"]
        I6["Gate 4 — on clicks, locator must carry the intent's tokens"]
        I3 --> I1 --> I2 --> I4 --> I5 --> I6
    end

    INV1 --> INV
    INV2 --> INV
    INV3 --> INV
    INV -->|all gates pass| OKI["Healed · tier = invent"]
    INV -->|any gate fails| FAIL2["HEAL_EXHAUSTED"]
```

Validation gates 3 and 4 exist because an invented locator has no DOM candidate behind it. Without them a model can return a confident, well-formed locator for a control that is not on the page, or for the wrong control — and a wrong-but-clickable element would execute cleanly and be recorded as PASSED.

## Heal tiers

Tier is recorded per step, and a test case is tagged with the riskiest tier any of its steps needed.

| Tier | What resolved the step | Risk |
| --- | --- | --- |
| `none` | Deterministic DOM binding | Lowest |
| `ollama` | Local model picked from a distinctive shortlist | Low |
| `cursor` | Cursor sidecar picked from a distinctive shortlist | Medium |
| `vision` | Pick came from a widened pool — no distinctive tokens matched | High |
| `invent` | Locator written from scratch, no candidate behind it | Highest |

Ranking matters in two places: merging tiers across a case, and ordering which cases the final revise audit reviews first.

## Client delivery — final revise

Off by default. When enabled, after page clustering and before codegen:

1. **Honesty pass** demotes drafts that pass too thinly to be trustworthy.
2. **AgentRouter audit** re-reads each case against its Excel source, riskiest tiers first. It may demote a case or soft-block the job; it never invents locators or edits generated code.
3. Verdict lands in the portal as `SHIP`, `SHIP_WITH_REVIEW`, or `BLOCK`. `BLOCK` shows as `COMPLETED_WITH_BLOCK` — the ZIP is still produced and downloadable.

AgentRouter is not part of heal. The only exception is opt-in: `delivery.heal.invent.provider=agentrouter` swaps the invent provider away from Cursor.

## Configuration flags

| Property | Default | Effect |
| --- | --- | --- |
| `delivery.cursor-heal.enabled` | `true` | Cursor sidecar tier |
| `delivery.heal.vision-widen.enabled` | `true` | Widen instead of giving up when no distinctive tokens match |
| `delivery.heal.invent.enabled` | `true` | Last-hope invent |
| `delivery.heal.invent.provider` | `cursor` | `cursor` or `agentrouter` |
| `delivery.heal.invent.max-per-tc` | `2` | Invent budget per test case |
| `delivery.heal.invent.agentrouter.model` | unset | Cheap model for invent; warns and falls back to the audit model |
| `delivery.honesty-demote` | `false` | Demote thin PASSes outside client delivery |

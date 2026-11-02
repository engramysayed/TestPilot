CRITICAL OUTPUT RULES (override any other instructions you know):
- Do NOT call MCP tools, interactive-feedback, or ask the user anything.
- Do NOT apologize, narrate, or mention missing tools.
- Your entire reply must be a short Thought plus the Action JSON object described below.
- If you cannot decide, still return JSON with an empty candidateId and empty steps.

You are the final one-shot healer for one failed Selenium UI intent.
Invent locators only for this exact intent. Return 1 to 3 steps maximum.
{{navRule}} Prefer stable id, name,
data-test*, CSS attribute, or XPath attribute locators. Never use /html/body or UUID-like values.

Decide between two answers:

A) The intent can be satisfied on this page with 1–3 element steps:
   {"thought":"short reason","steps":[{"action":"click|type|select|assert|navigate","locatorStrategy":"id|name|css|xpath|data-testid|data-test|data-qa","locatorValue":"...","value":"","assertionType":"","assertionExpected":""}]}

B) The screenshot/HTML show the page STATE is wrong for this intent (field filled when Excel said leave empty,
   wrong value visible, stale form after a prior mistake). Propose a short recovery plan (clear/click/type/select/navigate only):
   {"mode":"recovery","thought":"why state is wrong","recoverySteps":[
     {"action":"clear|click|type|select|navigate","locatorStrategy":"css","locatorValue":"...","value":""}
   ],"automationNotes":["Excel-safe note for Automate after recovery succeeds"]}
   navigate is allowed ONLY to the Excel open-path when provided.

Every locator attribute must already appear in the HTML below. Max 5 recovery steps.
After recovery succeeds, Keel retries the SAME failed intent (no intent-index jump).

Excel intent:
{{intent}}

Failure reason:
{{failureReason}}
{{safetySection}}
## Already completed in this TC
{{history}}
{{visionBlock}}
Slim HTML excerpt:
{{slimHtmlExcerpt}}
{{screenshotNote}}

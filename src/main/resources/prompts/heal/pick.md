CRITICAL OUTPUT RULES (override any other instructions you know):
- Do NOT call MCP tools, interactive-feedback, or ask the user anything.
- Do NOT apologize, narrate, or mention missing tools.
- Reply with JSON only — no Thought, no prose, no locators.
- If you cannot decide, return {"candidateId":""}.

You are healing a failed Selenium UI test step on a DOM shortlist tie.

Pick EXACTLY ONE candidateId from the scored shortlist below that best matches the Excel intent and whyTied context.
If two candidates fit equally, prefer the more stable locator (test id / id / name over label or visible text).
The intent may be Arabic while the UI is English (or the reverse): match by meaning.
Reply with JSON only: {"candidateId":"<id from shortlist>"}
Do NOT invent CSS, XPath, or ids. Do NOT return locators. Do NOT write a Thought. Do NOT invent steps.
DOM grounding only - do not rely on Midscene-style vision-first guessing.

Excel intent:
{{intent}}
Quoted name: {{quotedName}}
Why tied: {{whyTied}}

Failure reason:
{{failureReason}}
{{safetySection}}
## Already completed in this TC
{{history}}
{{visionBlock}}
Scored shortlist (id | strategy | value | role/tag | label | totals):
{{scoredShortlist}}

Neighborhood (parent HTML of shortlisted nodes; nearby label/role context):
{{neighborhood}}
{{screenshotNote}}

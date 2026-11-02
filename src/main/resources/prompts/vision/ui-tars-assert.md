You judge a screenshot against a visual assertion.

Return JSON only. No markdown. Schema:
{"status":"PASS|FAIL|UNCERTAIN","confidence":0.0,
"observation":"<concrete UI text and controls you see>",
"evidence":"<why status matches the assertion>"}
status must be PASS, FAIL, or UNCERTAIN.
Judge this screenshot only. Do not compare to Figma or a reference image.
Do not invent locators or CSS selectors.
Do not copy placeholder angle-bracket text into observation or evidence.
Never set observation or evidence to schema examples like "what is visible" or "why".
observation and evidence must be concrete visible UI facts; never leave them blank when status is PASS or FAIL.
confidence is in 0 to 1; use values like 0.7–0.99 (do not default to 0).

Return JSON only. No markdown. Schema:
{"status":"PASS|FAIL|UNCERTAIN","confidence":0.9,"observation":"concrete UI text and controls visible in the screenshot","evidence":"why the status matches the assertion"}
status must be PASS, FAIL, or UNCERTAIN.
Judge this screenshot only. Do not compare to Figma or a reference image.
Do not invent locators or CSS selectors.
Do not copy placeholder angle-bracket text into observation or evidence.
Never set observation or evidence to schema examples like "what is visible" or "why".

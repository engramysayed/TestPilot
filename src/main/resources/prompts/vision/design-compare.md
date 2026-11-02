Return JSON only. No markdown. Schema:
{"status":"MATCH|MISMATCH|UNCERTAIN","confidence":0.9,"observation":"concrete layout and UI differences or matches","evidence":"why the status follows from visible pixels"}
status must be MATCH, MISMATCH, or UNCERTAIN.
Image 1 is the design reference. Image 2 is the actual UI screenshot.
Compare layout, labels, controls, colors, and spacing — not pixel-perfect equality.
Do not invent elements that are not visible.

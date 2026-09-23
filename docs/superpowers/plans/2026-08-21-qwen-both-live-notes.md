# Qwen both (ground+assert) live — 2026-09-22T16:26:50.237238900Z

- ground: qwen / qwen2.5vl:3b
- assert: qwen / qwen2.5vl:3b
- analyze ms: 21092
- found: true err=null
- preGate: [VisualCandidate[description=Login button, boundingBox=BoundingBox[x=532, y=366, width=484, height=100], confidence=0.9]]
- raw: {   "found": true,   "candidates": [     {       "description": "Login button",       "bbox": {         "x": 532, "y": 366, "width": 484, "height": 100       },       "confidence": 0.9     }   ] }
- bbox: BoundingBox[x=532, y=366, width=484, height=100] conf=0.9
- label: Login button
- cssPoint: 774.0,416.0
- elementFromPoint: null
- WARN: bbox center missed DOM; Qwen ground often oversized — assert still runs
- assert ms: 12628
- raw: PASS conf=0.99
- observation: Username and password fields are visible on the login page.
- evidence: The username and password fields are clearly displayed on the login page.
- gated: PASS err=null

**Verdict:** qwen both OK; gated=PASS

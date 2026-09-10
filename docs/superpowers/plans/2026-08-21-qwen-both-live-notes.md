# Qwen both (ground+assert) live — 2026-09-08T15:25:26.306786Z

- ground: qwen / qwen2.5vl:3b
- assert: qwen / qwen2.5vl:3b
- analyze ms: 21840
- found: true err=null
- preGate: [VisualCandidate[description=Login button, boundingBox=BoundingBox[x=532, y=366, width=484, height=100], confidence=0.9]]
- raw: {   "found": true,   "candidates": [     {       "description": "Login button",       "bbox": {         "x": 532, "y": 366, "width": 484, "height": 100       },       "confidence": 0.9     }   ] }
- bbox: BoundingBox[x=758, y=400, width=32, height=32] conf=0.9
- label: Login button
- cssPoint: 774.0,416.0
- elementFromPoint: null
- WARN: bbox center missed DOM; Qwen ground often oversized — assert still runs
- assert ms: 12893
- raw: PASS conf=0.99
- observation: Username and password fields are visible on the login page.
- evidence: The username and password fields are clearly displayed on the login page.
- gated: PASS err=null

**Verdict:** qwen both OK; gated=PASS

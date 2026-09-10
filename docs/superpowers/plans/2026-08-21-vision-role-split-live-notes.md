# Vision role-split live — 2026-09-08T15:28:34.676418300Z

- grounding: uitars / ui-tars
- assert: qwen / qwen2.5vl:3b
- found: true
- candidates: 1
- bbox: BoundingBox[x=507, y=358, width=36, height=36]
- conf: 0.85
- elementFromPoint: GroundedNode[tag=button, id=, name=, dataTest=, ariaLabel=, role=, displayed=true, enabled=true, outerFingerprint=button, visibleText=Login]
- raw assert: PASS conf=0.9
- observation: Username and password fields are visible on the login page.
- evidence: The username and password fields are clearly displayed on the login page, indicating that the page is fully loaded and functional.
- gated: PASS err=null

**Verdict:** split OK; gated=PASS

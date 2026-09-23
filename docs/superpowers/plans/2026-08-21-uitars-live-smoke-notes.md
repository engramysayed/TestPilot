# UI-TARS live smoke — 2026-09-22T16:27:28.862123700Z

- png: 1902x984
- analyze ms: 44542
- analyze error: null
- found: true
- candidates: 1
- raw analyze (clip): Thought: Click on the Login/Submit button in the center of the screen. Action: click(start_box='(280,380)')
- top bbox: BoundingBox[x=514, y=357, width=36, height=36]
- top conf: 0.85
- top label: Click on the Login/Submit button in the center of the screen.
- elementFromPoint: GroundedNode[tag=button, id=, name=, dataTest=, ariaLabel=, role=, displayed=true, enabled=true, outerFingerprint=button, visibleText=Login, dataTestAttribute=data-qa]
- assert ms: 89817
- raw status: PASS
- raw observation: The login page is a white background with a centered heading that reads 'Login Page'. Below the heading, there is a paragraph of text instructing the user to 'Enter tomsmith' for the username and 'SuperSecretPassword!' for the password. There are two input fields labeled 'Username' and 'Password', and a blue 'Login' button located below the input fields. At the bottom of the page, there is a text 'Powered by Elemental Selenium'. There is also a green vertical bar with a white text that reads 'Fi
- raw evidence: Visible UI matches assertion: The login page is a white background with a centered heading that reads 'Login Page'. Below the heading, there is a paragraph of text instructing the user to...
- raw confidence: 0.92
- gated status: PASS
- gated error: null

**Verdict:** live analyze OK (Login control); assert raw=PASS gated=PASS

Review generated test cases against {{contractId}}.
Fix violations without inventing requirements or weakening mandatory rules.
Preserve user story intent.

The cases are in generate-context.json. Return the repaired cases in the result payload as JSON
with a testCases array. Keep every case. Each case uses tcId, title, preconditions, steps,
expectedResult, priority, tags, visualAssertion, testData, keelPath, and callBefore.
Do not drop a case. Do not invent a requirement that is not in the story.

Findings: {{findingsCount}}.
{{storyBlock}}

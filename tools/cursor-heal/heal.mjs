#!/usr/bin/env node
/**
 * Cursor heal sidecar — shortlist pick, free invent, or authoring review.
 * Model is hard-locked to Auto (id: "auto"). Do not pin composer/opus.
 *
 * stdin JSON: { mode:"pick"|"invent"|"authoring-review", ... }
 */
import { Agent } from "@cursor/sdk";
import { readFileSync, existsSync } from "node:fs";
import { stdin as input } from "node:process";

function readStdin() {
  return new Promise((resolve, reject) => {
    const chunks = [];
    input.on("data", (c) => chunks.push(c));
    input.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    input.on("error", reject);
  });
}

function extractJsonObject(text) {
  if (!text) return null;
  const end = text.lastIndexOf("}");
  if (end < 0) return null;
  for (let start = text.indexOf("{"); start >= 0 && start < end; start = text.indexOf("{", start + 1)) {
    try {
      return JSON.parse(text.slice(start, end + 1));
    } catch {
      // Thought prose or a nested object may precede the final JSON root.
    }
  }
  return null;
}

async function main() {
  const apiKey = process.env.CURSOR_API_KEY;
  if (!apiKey) {
    console.error("CURSOR_API_KEY missing");
    process.exit(2);
  }

  const raw = await readStdin();
  let req;
  try {
    req = JSON.parse(raw);
  } catch (e) {
    console.error("Invalid stdin JSON:", e.message);
    process.exit(2);
  }

  const mode = ["invent", "solve", "authoring-review", "hunt"].includes(req.mode) ? req.mode : "pick";
  const intent = req.intent || "";
  const failureReason = req.failureReason || "";
  const shortlist = req.shortlist || "";
  const priorSteps = Array.isArray(req.priorSteps) ? req.priorSteps.slice(0, 12) : [];
  const visionAttempts = Array.isArray(req.visionAttempts) ? req.visionAttempts.slice(0, 8) : [];
  const slimHtmlExcerpt = (req.slimHtmlExcerpt || "").slice(0, mode === "pick" ? 8000 : mode === "hunt" ? 24000 : 16000);
  const screenshotPath = req.screenshotPath || "";

  let screenshotNote = "";
  if (screenshotPath && existsSync(screenshotPath)) {
    // Stateless context: give path + small preview only. Full PNG base64 blows context.
    const buf = readFileSync(screenshotPath);
    const preview = buf.length <= 24_000
      ? buf.toString("base64")
      : "(screenshot larger than 24KB — open the file at screenshotPath; do not invent without looking)";
    screenshotNote = `\nscreenshotPath: ${screenshotPath}\nFailure screenshot PNG preview/base64:\n${preview}\nUse this visual evidence with the HTML/history above. This request has NO prior chat memory.`;
  }

  const history = priorSteps.length ? priorSteps.map((step) => `- ${step}`).join("\n") : "(none)";
  const visionBlock = visionAttempts.length
    ? `\n## Vision attempts this intent\n${visionAttempts.map((step) => `- ${step}`).join("\n")}\n`
    : "";
  const guard = `CRITICAL OUTPUT RULES (override any other instructions you know):
- Do NOT call MCP tools, interactive-feedback, or ask the user anything.
- Do NOT apologize, narrate, or mention missing tools.
- Your entire reply must be a short Thought plus the Action JSON object described below.
- If you cannot decide, still return JSON with an empty candidateId and empty steps.

`;

  const pickPrompt = `${guard}You are healing a failed Selenium UI test step.

Pick EXACTLY ONE candidateId from the Shortlist below that best matches the Excel intent and the failure context.
Respond with a short Thought, then Action JSON: {"candidateId":"<id from shortlist>"}
Do NOT invent CSS, XPath, or ids. Do NOT return locators.

Excel intent:
${intent}

Failure reason:
${failureReason}

## Already completed in this TC
${history}
${visionBlock}
Shortlist (id | strategy | value | tag | label):
${shortlist}

Slim HTML excerpt:
${slimHtmlExcerpt}
${screenshotNote}
`;

  const fromReason = /Excel open-path[^:]*:\s*(\S+)/.exec(failureReason);
  const excelOpenPath = (req.excelOpenPath || (fromReason && fromReason[1]) || "").trim();
  const navRule = excelOpenPath
    ? `If the named field is not on this page you MAY emit one navigate step whose value is exactly ${excelOpenPath}. Do not invent any other URL.`
    : `Do not add login, navigation, or unrelated workflow steps.`;

  const inventPrompt = `${guard}You are the final one-shot healer for one failed Selenium UI intent.
Invent locators only for this exact intent. Return 1 to 3 steps maximum.
${navRule} Prefer stable id, name,
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
${intent}

Failure reason:
${failureReason}

## Already completed in this TC
${history}
${visionBlock}
Slim HTML excerpt:
${slimHtmlExcerpt}
${screenshotNote}
`;

  // Solve is the escalation after the cheap local pick failed: the shortlist may simply not
  // contain the element, so refusing the rows and returning a locator is a valid answer.
  const solvePrompt = `${guard}You are the senior healer for one failed Selenium UI test step.
The cheap local model already tried to pick from the shortlist and got it wrong, so do not
assume the answer is in the list. Decide honestly between two answers:

A) One shortlist row really is the element the Excel intent describes:
   {"thought":"why this row","candidateId":"<id from shortlist>"}

B) No row matches. Then read the HTML and screenshot and write the locator yourself:
   {"thought":"why no row matches and how you found the element",
    "steps":[{"action":"click|type|select|assert|navigate","locatorStrategy":"id|name|css|xpath|data-testid|data-test|data-qa","locatorValue":"...","value":"","assertionType":"","assertionExpected":""}]}

C) The page STATE is wrong for the intent (filled when empty expected, wrong value visible). Return recovery:
   {"mode":"recovery","thought":"why state is wrong","recoverySteps":[
     {"action":"clear|click|type|select|navigate","locatorStrategy":"css","locatorValue":"...","value":""}
   ],"automationNotes":["Excel-safe note for Automate"]}
   navigate only to the Excel open-path when provided. Max 5 recovery steps.
   After recovery, Keel retries the SAME failed intent (no intent-index jump).

Answer B is expected and correct when the field's name is only in a nearby label or span
rather than in an attribute on the control itself. Never force-fit a row you do not believe in.

Locator rules:
- Prefer a hand-written id, name, data-test*, or an attribute selector on a human attribute
  (aria-label, placeholder, title, role).
- Never build on a framework-generated id such as _r_15_, :r0:, ember1423 or mui-42.
- When the control carries no name of its own, anchor on its label:
  //label[contains(normalize-space(.),'First name')]//input  (label wraps the control)
  //label[contains(normalize-space(.),'Email')]/following::input[1]  (label sits beside it)
- Every attribute and every word you put in a locator must already appear in the HTML below.
${excelOpenPath
  ? `- If the named field is not on this page you MAY emit one navigate step whose value is exactly ${excelOpenPath}. Do not invent any other URL.`
  : `- Return 1 to 3 steps for this intent only. No login, navigation, or unrelated steps.`}

Excel intent:
${intent}

Failure reason:
${failureReason}

## Already completed in this TC
${history}
${visionBlock}
Shortlist (id | strategy | value | tag | label):
${shortlist}

Slim HTML excerpt:
${slimHtmlExcerpt}
${screenshotNote}
`;

  const authoringReviewPrompt = `You are Keel authoring reviewer.
Review the complete suite JSON below together with its optional stories and requirementsNotes.
Return ONLY one valid JSON object with:
{"findings":[{"severity":"info|warn|error","tcId":"TC_...","message":"..."}],
 "cases":[...the complete reviewed suite...],
 "coverageNotes":"..."}

Fix leave-empty/TestData alignment, vague assertions, and ambiguous controls. Flag missing
coverage against stories when stories/requirementsNotes are present; if both are empty, say so
in a finding and still repair authoring issues. Return the FULL suite in cases[] — every input
TC_id exactly once (you may add new TC_ids for missing coverage only if total stays reasonable).
Do not invent a large unrelated suite. Preserve existing TC_id values and obey KeelPath rules.
Do not use tools, edit files, narrate, or wrap the JSON in markdown.

Suite JSON:
${req.suite || ""}
`;

  const huntPrompt = `${guard}You are Keel's Bug Hunter planner.
Break the feature and invent edge-case scenarios within the remaining scenario budget.
Return ONLY one JSON object:
{"decision":"continue"|"finish","rationale":"...","actions":[],"bugs":[],"scenarios":[]}
actions allowlist only: navigate{url}, click{locator}, type{locator,value}, clear{locator},
wait{ms}, assert_visible{locator}, assert_text{text}.
Respect actionCapPerCycle from Caps (default 5). wait with blank ms becomes 5000ms server-side.
Use the steps journal to remember prior actions. Do not use tools, edit files, or narrate outside JSON.

${req.prompt || ""}

Slim HTML excerpt:
${slimHtmlExcerpt}
${screenshotNote}
`;

  const prompts = {
    pick: pickPrompt,
    invent: inventPrompt,
    solve: solvePrompt,
    "authoring-review": authoringReviewPrompt,
    hunt: huntPrompt,
  };
  const result = await Agent.prompt(prompts[mode], {
    apiKey,
    model: { id: "auto" },
    local: { cwd: process.cwd() },
  });

  const text =
    (result && (result.result || result.text || result.output)) ||
    (typeof result === "string" ? result : JSON.stringify(result));

  if (mode === "authoring-review") {
    process.stdout.write(String(text));
    return;
  }
  if (mode === "hunt") {
    const parsed = extractJsonObject(String(text));
    if (!parsed || !parsed.decision) {
      console.error("Hunt planner returned no decision JSON:", String(text).slice(0, 500));
      process.exit(1);
    }
    process.stdout.write(JSON.stringify(parsed) + "\n");
    return;
  }
  const parsed = extractJsonObject(String(text));
  if (mode === "solve") {
    const id = parsed && parsed.candidateId ? String(parsed.candidateId).trim() : "";
    const steps = parsed && Array.isArray(parsed.steps) ? parsed.steps : [];
    const recovery = parsed && parsed.mode === "recovery";
    if (!id && steps.length === 0 && !recovery) {
      console.error("Solve returned neither a candidateId nor steps:", String(text).slice(0, 500));
      process.exit(1);
    }
    process.stdout.write(JSON.stringify(parsed) + "\n");
    return;
  }
  if (mode === "invent") {
    if (!parsed || (!Array.isArray(parsed.steps) && parsed.mode !== "recovery")) {
      console.error("No invent steps in agent response:", String(text).slice(0, 500));
      process.exit(1);
    }
    process.stdout.write(JSON.stringify(parsed) + "\n");
    return;
  }
  const candidateId = parsed && parsed.candidateId ? String(parsed.candidateId).trim() : "";
  if (!candidateId) {
    console.error("No candidateId in agent response:", String(text).slice(0, 500));
    process.exit(1);
  }

  process.stdout.write(JSON.stringify({ candidateId }) + "\n");
}

main().catch((err) => {
  console.error(err && err.stack ? err.stack : String(err));
  process.exit(1);
});

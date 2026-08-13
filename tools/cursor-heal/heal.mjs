#!/usr/bin/env node
/**
 * Cursor heal sidecar — shortlist pick or one-shot free invent.
 * Model is hard-locked to Auto (id: "auto"). Do not pin composer/opus.
 *
 * stdin JSON: { mode:"pick"|"invent", intent, priorSteps, slimHtmlExcerpt, ... }
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

  const mode = req.mode === "invent" ? "invent" : "pick";
  const intent = req.intent || "";
  const failureReason = req.failureReason || "";
  const shortlist = req.shortlist || "";
  const priorSteps = Array.isArray(req.priorSteps) ? req.priorSteps.slice(0, 12) : [];
  const slimHtmlExcerpt = (req.slimHtmlExcerpt || "").slice(0, mode === "invent" ? 16000 : 8000);
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
  const pickPrompt = `You are healing a failed Selenium UI test step.

Pick EXACTLY ONE candidateId from the Shortlist below that best matches the Excel intent and the failure context.
Respond with a short Thought, then Action JSON: {"candidateId":"<id from shortlist>"}
Do NOT invent CSS, XPath, or ids. Do NOT return locators.

Excel intent:
${intent}

Failure reason:
${failureReason}

## Already completed in this TC
${history}

Shortlist (id | strategy | value | tag | label):
${shortlist}

Slim HTML excerpt:
${slimHtmlExcerpt}
${screenshotNote}
`;

  const inventPrompt = `You are the final one-shot healer for one failed Selenium UI intent.
Invent locators only for this exact intent. Return 1 to 3 steps maximum.
Do not add login, navigation, or unrelated workflow steps. Prefer stable id, name,
data-test*, CSS attribute, or XPath attribute locators. Never use /html/body or UUID-like values.
Return strict JSON:
{"thought":"short reason","steps":[{"action":"click|type|select|assert","locatorStrategy":"id|name|css|xpath|data-testid|data-test|data-qa","locatorValue":"...","value":"","assertionType":"","assertionExpected":""}]}

Excel intent:
${intent}

Failure reason:
${failureReason}

## Already completed in this TC
${history}

Slim HTML excerpt:
${slimHtmlExcerpt}
${screenshotNote}
`;

  const result = await Agent.prompt(mode === "invent" ? inventPrompt : pickPrompt, {
    apiKey,
    model: { id: "auto" },
    local: { cwd: process.cwd() },
  });

  const text =
    (result && (result.result || result.text || result.output)) ||
    (typeof result === "string" ? result : JSON.stringify(result));

  const parsed = extractJsonObject(String(text));
  if (mode === "invent") {
    if (!parsed || !Array.isArray(parsed.steps)) {
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

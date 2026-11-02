#!/usr/bin/env node
/**
 * Cursor heal sidecar — shortlist pick, free invent, or authoring review.
 * Model is hard-locked to Auto (id: "auto"). Do not pin composer/opus.
 *
 * stdin JSON: { mode:"pick"|"invent"|"authoring-review", ... }
 *
 * Prompt SoT: src/main/resources/prompts/ (ids heal/pick, heal/invent, ...).
 * Override: env KEEL_PROMPTS_DIR (mirrors Spring keel.prompts.dir) — same relative paths.
 */
import { Agent } from "@cursor/sdk";
import { readFileSync, existsSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { stdin as input } from "node:process";

const __dirname = dirname(fileURLToPath(import.meta.url));

/** Resolve prompt id (e.g. heal/pick) from KEEL_PROMPTS_DIR then repo resources/prompts. */
function promptRoots() {
  const roots = [];
  if (process.env.KEEL_PROMPTS_DIR && String(process.env.KEEL_PROMPTS_DIR).trim()) {
    roots.push(resolve(String(process.env.KEEL_PROMPTS_DIR).trim()));
  }
  // tools/cursor-heal -> repo root src/main/resources/prompts
  roots.push(resolve(__dirname, "../../src/main/resources/prompts"));
  roots.push(resolve(process.cwd(), "src/main/resources/prompts"));
  return roots;
}

const promptCache = new Map(); // id -> { text, mtimeMs, path }

function loadPrompt(id) {
  const key = String(id || "").replace(/\\/g, "/").replace(/\.(md|txt)$/, "");
  for (const root of promptRoots()) {
    for (const ext of [".md", ".txt"]) {
      const p = join(root, key + ext);
      if (!existsSync(p)) continue;
      let mtimeMs = 0;
      try { mtimeMs = statSync(p).mtimeMs; } catch { /* ignore */ }
      const cached = promptCache.get(key);
      const fromOverride = !!(process.env.KEEL_PROMPTS_DIR && String(process.env.KEEL_PROMPTS_DIR).trim()
        && resolve(root) === resolve(String(process.env.KEEL_PROMPTS_DIR).trim()));
      if (cached && cached.path === p && (!fromOverride || cached.mtimeMs === mtimeMs)) {
        return cached.text;
      }
      const text = readFileSync(p, "utf8");
      promptCache.set(key, { text, mtimeMs, path: p });
      return text;
    }
  }
  throw new Error(`Prompt not found: ${key} (set KEEL_PROMPTS_DIR or run from repo with src/main/resources/prompts)`);
}

function formatPrompt(id, vars) {
  let text = loadPrompt(id);
  if (!vars) return text;
  return text.replace(/\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g, (_, name) => {
    const v = vars[name];
    return v == null ? "" : String(v);
  });
}



function buildSafetySection(req) {
  if (req.safetyBlock && String(req.safetyBlock).trim()) {
    return "\n" + String(req.safetyBlock).trim() + "\n";
  }
  const secret = !!req.secret;
  const se = req.sideEffects || "unknown";
  const banned = Array.isArray(req.bannedLocators) ? req.bannedLocators : [];
  const crash = !!req.crash;
  let crashBlock = "";
  if (crash) {
    crashBlock = "- CRASH/ERROR PAGE DETECTED. Return ONLY {\"mode\":\"abort\",\"thought\":\"crash or fatal error page — do not invent locators\"} — do not pick, invent, or recover.\n";
  }
  const secretClause = secret
    ? ": NEVER echo, print, or invent real secret/password/OTP values; use empty value or ${TARGET_PASSWORD}/${TARGET_OTP} tokens only.\n"
    : ".\n";
  let sideEffectsClause;
  if (se === "none") {
    sideEffectsClause = ": read-only — recovery retries on the same control are allowed.\n";
  } else {
    sideEffectsClause = ": do NOT invent mutating retries (no re-submit/delete/pay/logout). Recovery may only clear/dismiss or navigate to the Excel open-path. Mark invented steps with \"sideEffects\":\"" + se + "\".\n";
  }
  let bannedBlock;
  if (banned.length) {
    bannedBlock = "- Banned locators (already failed — do not return these):\n";
    for (const b of banned) bannedBlock += "  - " + b + "\n";
  } else {
    bannedBlock = "- Banned locators: (none)\n";
  }
  const block = formatPrompt("heal/safety", {
    crashBlock,
    secretFlag: secret ? "true" : "false",
    secretClause,
    sideEffectsWire: se,
    sideEffectsClause,
    bannedBlock,
  });
  return "\n" + block.trimEnd() + "\n";
}

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
  const prepareStarted = performance.now();
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

  const mode = ["invent", "solve", "authoring-review", "hunt", "groundRank", "groundRankBatch"].includes(req.mode)
    ? req.mode
    : "pick";
  const intent = req.intent || "";
  const failureReason = req.failureReason || "";
  const shortlist = req.shortlist || "";
  const quotedName = req.quotedName || "";
  const whyTied = req.whyTied || "";
  const scoredShortlist = req.scoredShortlist || shortlist;
  const neighborhood = req.neighborhood || "";
  const priorSteps = Array.isArray(req.priorSteps) ? req.priorSteps.slice(0, 12) : [];
  const visionAttempts = Array.isArray(req.visionAttempts) ? req.visionAttempts.slice(0, 8) : [];
  const defaultCap = mode === "pick" ? 8000 : mode === "groundRank" ? 12000 : mode === "groundRankBatch" ? 16000 : mode === "hunt" ? 24000 : 16000;
  const budgetCap = Number(req.observationBudgetChars) > 1024 ? Number(req.observationBudgetChars) : defaultCap;
  const slimHtmlExcerpt = (req.slimHtmlExcerpt || "").slice(0, budgetCap);
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

  const safetySection = buildSafetySection(req);

  const history = priorSteps.length ? priorSteps.map((step) => `- ${step}`).join("\n") : "(none)";
  const visionBlock = visionAttempts.length
    ? `\n## Vision attempts this intent\n${visionAttempts.map((step) => `- ${step}`).join("\n")}\n`
    : "";
  const fromReason = /Excel open-path[^:]*:\s*(\S+)/.exec(failureReason);
  const excelOpenPath = (req.excelOpenPath || (fromReason && fromReason[1]) || "").trim();
  const navRuleInvent = excelOpenPath
    ? `If the named field is not on this page you MAY emit one navigate step whose value is exactly ${excelOpenPath}. Do not invent any other URL.`
    : `Do not add login, navigation, or unrelated workflow steps.`;
  const navRuleSolve = excelOpenPath
    ? `- If the named field is not on this page you MAY emit one navigate step whose value is exactly ${excelOpenPath}. Do not invent any other URL.`
    : `- Return 1 to 3 steps for this intent only. No login, navigation, or unrelated steps.`;
  const batchIntents = req.intents || "";

  const varsCommon = {
    intent,
    failureReason,
    safetySection,
    history,
    visionBlock,
    screenshotNote,
    shortlist,
    slimHtmlExcerpt,
    scoredShortlist,
    neighborhood,
    quotedName,
    whyTied,
    batchIntents,
    maxIntents: String(req.maxIntents || 5),
    navRule: navRuleInvent,
    prompt: req.prompt || "",
    suite: req.suite || "",
  };

  const prompts = {
    pick: formatPrompt("heal/pick", varsCommon),
    groundRank: formatPrompt("heal/ground-rank", varsCommon),
    groundRankBatch: formatPrompt("heal/ground-rank-batch", varsCommon),
    invent: formatPrompt("heal/invent", { ...varsCommon, navRule: navRuleInvent }),
    solve: formatPrompt("heal/solve", { ...varsCommon, navRule: navRuleSolve }),
    "authoring-review": formatPrompt("authoring/authoring-review-cursor", {
      suite: req.suite || "",
    }),
    hunt: formatPrompt("heal/hunt-cursor", {
      ...varsCommon,
      prompt: req.prompt || "",
    }),
  };
  console.error(`PERF cursor_prepare elapsedMs=${Math.round(performance.now() - prepareStarted)}`);
  const agentStarted = performance.now();
  let result;
  try {
    result = await Agent.prompt(prompts[mode], {
    apiKey,
    model: { id: "auto" },
    local: { cwd: process.cwd() },
    });
  } finally {
    console.error(`PERF cursor_agent elapsedMs=${Math.round(performance.now() - agentStarted)}`);
  }

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
  if (mode === "groundRank") {
    const id = parsed && parsed.candidateId ? String(parsed.candidateId).trim() : "";
    const confidence = parsed && parsed.confidence ? String(parsed.confidence).trim().toLowerCase() : "low";
    if (!id) {
      process.stdout.write(JSON.stringify({ candidateId: "", confidence: "low", rationale: "no pick", ranked: [] }) + "\n");
      return;
    }
    process.stdout.write(JSON.stringify({
      candidateId: id,
      confidence: confidence || "low",
      rationale: parsed && parsed.rationale ? String(parsed.rationale) : "",
      ranked: parsed && Array.isArray(parsed.ranked) ? parsed.ranked : [],
    }) + "\n");
    return;
  }
  if (mode === "groundRankBatch") {
    const picks = parsed && Array.isArray(parsed.picks) ? parsed.picks : [];
    process.stdout.write(JSON.stringify({
      picks,
      rationale: parsed && parsed.rationale ? String(parsed.rationale) : "",
    }) + "\n");
    return;
  }
  if (mode === "solve") {
    if (parsed && parsed.mode === "abort") {
      process.stdout.write(JSON.stringify(parsed) + "\n");
      return;
    }
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
    if (parsed && parsed.mode === "abort") {
      process.stdout.write(JSON.stringify(parsed) + "\n");
      return;
    }
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

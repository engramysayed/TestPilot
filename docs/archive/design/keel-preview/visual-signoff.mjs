#!/usr/bin/env node
/**
 * Capture prod vs mock screenshots for login, overview, and projects.
 * Requires: portal on http://127.0.0.1:8080, mock on http://127.0.0.1:8769 (serve.ps1).
 * Usage: node visual-signoff.mjs [--prod-url URL] [--mock-url URL]
 */
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(__dirname, 'signoff-screenshots');

const args = process.argv.slice(2);
function arg(name, fallback) {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
}

const prodBase = arg('--prod-url', 'http://127.0.0.1:8080');
const mockBase = arg('--mock-url', 'http://127.0.0.1:8769');
const adminEmail = arg('--email', process.env.KEEL_SIGNOFF_EMAIL || 'admin@testpilot.local');
const adminPass = arg('--password', process.env.KEEL_SIGNOFF_PASSWORD || 'ChangeMeAdmin1!');

async function loadPlaywright() {
  try {
    return await import('playwright');
  } catch {
    console.error('Install Playwright: npm install -D playwright && npx playwright install chromium');
    process.exit(1);
  }
}

async function shot(page, file, url, opts = {}) {
  await page.goto(url, { waitUntil: 'networkidle', timeout: 60_000 });
  if (opts.click) await page.click(opts.click);
  await page.waitForTimeout(opts.waitMs ?? 400);
  await page.screenshot({ path: path.join(outDir, file), fullPage: opts.fullPage ?? false });
}

async function main() {
  const { chromium } = await loadPlaywright();
  await mkdir(outDir, { recursive: true });

  const report = {
    capturedAt: new Date().toISOString(),
    prodBase,
    mockBase,
    files: [],
  };

  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await ctx.newPage();

  await shot(page, 'mock-login.png', mockBase + '/', { click: '[data-action="go:signin"]', waitMs: 600 });
  report.files.push('mock-login.png');
  await shot(page, 'mock-overview.png', mockBase + '/');
  report.files.push('mock-overview.png');

  await page.goto(prodBase + '/login', { waitUntil: 'networkidle' });
  await page.screenshot({ path: path.join(outDir, 'prod-login.png') });
  report.files.push('prod-login.png');

  await page.fill('input[name="username"]', adminEmail);
  await page.fill('input[name="password"]', adminPass);
  await page.click('button[type="submit"]');
  await page.waitForURL('**/dashboard**', { timeout: 30_000 });
  await page.screenshot({ path: path.join(outDir, 'prod-overview.png') });
  report.files.push('prod-overview.png');

  await page.goto(prodBase + '/projects', { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);
  await page.screenshot({ path: path.join(outDir, 'prod-projects.png') });
  report.files.push('prod-projects.png');

  await browser.close();

  report.note = 'Compare PNG pairs manually or with your diff tool. Mock uses illustrative data.';
  await writeFile(path.join(outDir, 'signoff-report.json'), JSON.stringify(report, null, 2));
  console.log('Wrote screenshots to', outDir);
  console.log(JSON.stringify(report, null, 2));
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});

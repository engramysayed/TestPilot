@echo off
REM One-shot installer for the Claude Code extras. Run from an elevated terminal.
REM Each line is independent; skip ones you already have.

echo === Agent Skills (Anthropic skills marketplace) ===
claude plugin marketplace add anthropics/skills
claude plugin install document-skills@anthropic-agent-skills
claude plugin install example-skills@anthropic-agent-skills

echo === Context7 (MCP server) ===
REM npx is pulled on first use. For an API key set CONTEXT7_API_KEY first.
claude mcp add --scope user context7 -- npx -y @upstash/context7-mcp

echo === Ponytail (plugin) ===
claude plugin marketplace add DietrichGebert/ponytail
claude plugin install ponytail@ponytail

echo === Graphify ===
where py >NUL 2>&1 && (py -3.12 -m pip install --upgrade graphifyy) || (python -m pip install --upgrade graphifyy)
graphify install
graphify claude install

echo === spec-kit ===
where uv >NUL 2>&1 || (echo "uv is required: winget install astral-sh.uv" && exit /b 1)
uv tool install specify-cli --from git+https://github.com/github/spec-kit.git
REM In the repo root, run: specify init --here --ai claude

echo === superpowers ===
claude plugin marketplace add obra/superpowers-marketplace
claude plugin install superpowers@superpowers-marketplace

echo Done. Open a new Claude Code session so the new skills, MCP servers and plugins load.

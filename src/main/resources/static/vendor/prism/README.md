# PrismJS — vendored for Keel's VS Code-style previews

These five files are a frozen copy of **PrismJS 1.29.0** (released 2023-07-14, well past any CDN two-week freshness window). They power the syntax-highlighted code preview on `/projects/{id}` (automation tab). Served from `/vendor/prism/*` (allowlisted in `SecurityConfig`), so they work offline and in networks that block `cdnjs.cloudflare.com`.

## Files

| File | Source URL |
| --- | --- |
| `prism.min.js` | https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/prism.min.js |
| `prism-java.min.js` | https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-java.min.js |
| `prism-line-numbers.min.js` | https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/line-numbers/prism-line-numbers.min.js |
| `prism-tomorrow.min.css` | https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css |
| `prism-line-numbers.min.css` | https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/line-numbers/prism-line-numbers.min.css |

Referenced from `src/main/resources/templates/project-detail.html` via `th:href="@{/vendor/prism/...}"`.

## Updating

PrismJS is a stable library that ships infrequently. When you want to pull a newer release:

```bash
cd src/main/resources/static/vendor/prism
VER=1.30.0
curl -sSL -o prism.min.js               "https://cdnjs.cloudflare.com/ajax/libs/prism/$VER/prism.min.js"
curl -sSL -o prism-java.min.js          "https://cdnjs.cloudflare.com/ajax/libs/prism/$VER/components/prism-java.min.js"
curl -sSL -o prism-line-numbers.min.js  "https://cdnjs.cloudflare.com/ajax/libs/prism/$VER/plugins/line-numbers/prism-line-numbers.min.js"
curl -sSL -o prism-tomorrow.min.css     "https://cdnjs.cloudflare.com/ajax/libs/prism/$VER/themes/prism-tomorrow.min.css"
curl -sSL -o prism-line-numbers.min.css "https://cdnjs.cloudflare.com/ajax/libs/prism/$VER/plugins/line-numbers/prism-line-numbers.min.css"
```

Then bump the version comment in `project-detail.html` and this README, open the automate tab for any project, click **Preview** on a Java file, and confirm syntax colors + line numbers still render. No other code changes should be needed — the markup contract (`<pre class="line-numbers language-java"><code>...</code></pre>`) has been stable across every Prism 1.x release.

## Why we vendor instead of using cdnjs

- **Offline / air-gapped dev** — Keel runs on laptops with intermittent or restricted internet; a hard CDN dependency breaks the preview.
- **Supply-chain determinism** — pinned bytes on disk; no silent swaps if cdnjs rewrites a path.
- **Load latency** — five same-origin requests are cheaper than five CDN TLS handshakes on cold load.
- **CSP friendliness** — avoids whitelisting `cdnjs.cloudflare.com` in future Content-Security-Policy.

Size on disk: ~26 KB total (gzipped by Spring Boot's static resource handler). Not worth splitting lazily.

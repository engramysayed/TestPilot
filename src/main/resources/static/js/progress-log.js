/**
 * Reusable live progress-log widget.
 *
 * Usage:
 *   KeelProgressLog.mount({ container, jobId, pollMs, onTerminal });
 *
 *   container : CSS selector OR Element — where to inject the widget
 *   jobId     : the job to watch (required)
 *   pollMs    : optional, default 2000 — poll interval in ms
 *   onTerminal: optional callback (status) invoked once when job reaches terminal state
 *
 * Returns { stop() } so callers can kill the poll (e.g. when navigating away or starting a new job).
 * Multiple mounts on the same container are safe — previous widget is stopped & removed first.
 */
(function (global) {
    'use strict';

    const CSS = `
.kplw { border: 1px solid var(--muted-border, #e4e4e7); border-radius: 10px; background: var(--panel-alt, #fafafa); padding: .9rem 1rem; }
.kplw__head { display: flex; justify-content: space-between; align-items: baseline; gap: .5rem; margin: 0 0 .5rem; flex-wrap: wrap; }
.kplw__title { margin: 0; font-size: .95rem; font-weight: 600; }
.kplw__meta { color: var(--muted-fg, #6b6b73); font-size: .82rem; }
.kplw__status { font-size: .88rem; margin: .1rem 0 .5rem; }
.kplw__status.ok { color: #137a39; }
.kplw__status.fail { color: #b32d2d; }
.kplw__status.running { color: #1b5caa; }
.kplw__bar-track { background: var(--muted-border, #e4e4e7); border-radius: 6px; height: 6px; overflow: hidden; margin: 0 0 .6rem; }
.kplw__bar-fill { background: #1b5caa; height: 100%; transition: width .4s ease; }
.kplw__list { list-style: none; margin: 0; padding: .35rem .5rem; max-height: 220px; overflow: auto; border: 1px solid var(--muted-border, #e4e4e7); border-radius: 6px; background: var(--panel, #fff); }
.kplw__row { display: grid; grid-template-columns: 5.5rem 1fr; gap: .5rem; padding: .25rem 0; border-bottom: 1px solid var(--muted-border, #eee); font-size: .88rem; }
.kplw__row:last-child { border-bottom: 0; }
.kplw__ts { color: var(--muted-fg, #6b6b73); font-size: .82rem; font-family: ui-monospace, monospace; }
.kplw__msg { word-break: break-word; }
.kplw__empty { padding: .4rem 0; color: var(--muted-fg, #6b6b73); }
.kplw__foot { margin: .5rem 0 0; font-size: .82rem; }
html[data-theme='dark'] .kplw { background: #26272c; border-color: #3a3b43; }
html[data-theme='dark'] .kplw__list { background: #1f2024; border-color: #3a3b43; }
html[data-theme='dark'] .kplw__row { border-bottom-color: #35363d; }
html[data-theme='dark'] .kplw__bar-track { background: #3a3b43; }
`;

    function injectCssOnce() {
        if (document.getElementById('kplw-css')) return;
        const s = document.createElement('style');
        s.id = 'kplw-css';
        s.textContent = CSS;
        document.head.appendChild(s);
    }

    function esc(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
    }

    function formatLocalTime(ms) {
        if (!ms || typeof ms !== 'number') return '';
        return new Date(ms).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
    }

    function terminal(status) {
        return status === 'COMPLETED' || status === 'COMPLETED_WITH_BLOCK'
            || status === 'FAILED' || status === 'CANCELLED';
    }

    function resolveContainer(c) {
        if (!c) return null;
        if (typeof c === 'string') return document.querySelector(c);
        if (c instanceof Element) return c;
        return null;
    }

    function render(root, data) {
        const status = data.status || '—';
        const running = status === 'RUNNING' || status === 'QUEUED' || status === 'CANCELLING';
        const ok = status === 'COMPLETED' || status === 'COMPLETED_WITH_BLOCK';
        const cls = running ? 'running' : (ok ? 'ok' : (terminal(status) ? 'fail' : ''));
        const cur = data.progressCurrent || 0;
        const tot = data.progressTotal || 0;
        const pct = tot > 0 ? Math.min(100, Math.round(100 * cur / tot)) : 0;
        const message = data.message || '—';
        const log = Array.isArray(data.progressLog) ? data.progressLog : [];

        const rows = log.slice().reverse().map(e => `
<li class="kplw__row">
    <span class="kplw__ts">${esc(formatLocalTime(e && e.ts))}</span>
    <span class="kplw__msg">${esc(e && e.message ? e.message : '')}</span>
</li>`).join('') || '<li class="kplw__empty">Live steps will appear here as the job runs.</li>';

        root.innerHTML = `
<div class="kplw">
    <div class="kplw__head">
        <p class="kplw__title">Live progress</p>
        <span class="kplw__meta">Job ${esc(data.jobId || '')} · ${esc(status)} · ${cur}/${tot}</span>
    </div>
    <div class="kplw__bar-track"><div class="kplw__bar-fill" style="width:${pct}%"></div></div>
    <p class="kplw__status ${cls}">${esc(message)}</p>
    <ul class="kplw__list">${rows}</ul>
    <p class="kplw__foot"><a href="/status?jobId=${encodeURIComponent(data.jobId || '')}">Open full status page →</a></p>
</div>`;
    }

    function renderError(root, text) {
        root.innerHTML = `<div class="kplw"><p class="kplw__status fail">${esc(text)}</p></div>`;
    }

    const INSTANCES = new WeakMap();

    function mount(opts) {
        injectCssOnce();
        const root = resolveContainer(opts && opts.container);
        if (!root) return { stop: function () {} };
        const jobId = opts && opts.jobId;
        if (!jobId) {
            renderError(root, 'No job id to watch.');
            return { stop: function () {} };
        }
        // Stop any previous widget on this container.
        const prev = INSTANCES.get(root);
        if (prev) prev.stop();

        let stopped = false;
        let timer = null;
        const pollMs = Math.max(500, (opts && opts.pollMs) || 2000);

        root.hidden = false;
        renderError(root, 'Loading…');

        function tick() {
            if (stopped) return;
            fetch('/api/jobs/' + encodeURIComponent(jobId), {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            }).then(r => {
                if (r.status === 401 || r.status === 403) {
                    renderError(root, 'Session expired. Please sign in again.');
                    stopped = true;
                    return null;
                }
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            }).then(data => {
                if (!data || stopped) return;
                render(root, data);
                if (terminal(data.status)) {
                    stopped = true;
                    if (opts && typeof opts.onTerminal === 'function') {
                        try { opts.onTerminal(data.status); } catch (e) { /* ignore */ }
                    }
                    return;
                }
                timer = setTimeout(tick, pollMs);
            }).catch(e => {
                if (stopped) return;
                renderError(root, 'Could not fetch job status: ' + (e && e.message ? e.message : e));
                timer = setTimeout(tick, pollMs * 2); // back off on error
            });
        }

        const instance = {
            stop: function () {
                stopped = true;
                if (timer) { clearTimeout(timer); timer = null; }
            }
        };
        INSTANCES.set(root, instance);
        tick();
        return instance;
    }

    global.KeelProgressLog = { mount: mount };
})(window);

/* M2-07 execute mobile results — Final GET …/jobs/{jobId}/mobile-result */
(function () {
    const root = document.getElementById('execute-root');
    const mobileEnabled = root && root.dataset.mobileEnabled === 'true';
    const panel = document.getElementById('mobile-results-panel');
    if (!mobileEnabled || !panel) return;

    function labelMap(map, key, fallback) {
        var k = (key == null || key === '') ? '' : String(key).toUpperCase();
        if (k && Object.prototype.hasOwnProperty.call(map, k)) return map[k];
        return fallback != null ? fallback : key;
    }

    const OUTCOME_LABELS = {
        PASSED: ['Passed', 'pass'],
        FAILED: ['Failed', 'fail'],
        BLOCKED: ['Blocked — setup could not finish', 'blocked'],
        TODO: ['Not run yet', 'todo'],
        INTERRUPTED_UNCERTAIN: ['Interrupted — outcome unclear', 'interrupted'],
        EXCLUDED_SIMULATED: ['Skipped (simulated)', 'excluded']
    };
    const SETUP_LABELS = {
        NOT_REQUIRED: 'Setup not needed',
        PASSED: 'Setup ok',
        FAILED: 'Setup failed',
        BLOCKED: 'Setup blocked',
        SKIPPED: 'Setup skipped',
        UNKNOWN: 'Setup unknown'
    };
    const PROOF_LABELS = {
        NONE: 'No proof',
        HIERARCHY: 'Screen hierarchy',
        ASSERTION: 'Assertion',
        VISION: 'Vision',
        PRECISION: 'Precision',
        USER_LOCATOR: 'Your locator',
        SETUP_ONLY: 'Setup only'
    };
    const PROVIDER_LABELS = {
        KEEL: 'Keel',
        PRECISION: 'Precision',
        VISION: 'Vision',
        USER: 'Your locator',
        MEMORY: 'Memory',
        NONE: 'None'
    };
    const ORIGIN_LABELS = {
        USER: 'Your locator',
        HIERARCHY: 'Screen hierarchy',
        PRECISION: 'Precision',
        MEMORY: 'Memory'
    };
    const FAILURE_PLAIN = {
        ELEMENT_NOT_FOUND: 'Could not find the control on the screen',
        DEVICE_LOST: 'Lost connection to the phone',
        APP_CRASHED: 'The app crashed',
        SETUP_FAILED: 'Setup did not complete',
        ASSERTION_FAILED: 'A check did not match',
        TIMEOUT: 'Timed out waiting'
    };

    function esc(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }
    function outcomeMeta(code) {
        const c = (code || 'TODO').toUpperCase();
        return OUTCOME_LABELS[c] || [c, 'todo'];
    }
    function chip(text, tone) {
        const span = document.createElement('span');
        span.className = 'mobile-chip mobile-outcome-chip';
        span.dataset.tone = tone || 'muted';
        span.textContent = text;
        return span;
    }
    function plainFailure(code) {
        if (!code) return '';
        return FAILURE_PLAIN[code] || code.replace(/_/g, ' ').toLowerCase();
    }
    function fingerprintLabel(fp) {
        if (!fp) return '';
        const s = String(fp);
        return 'Device fingerprint ' + (s.length > 12 ? (s.slice(0, 8) + '…') : s);
    }

    function hideAll() {
        panel.hidden = true;
        ['mobile-results-err','mobile-results-empty','mobile-results-summary',
         'mobile-results-runtime','mobile-results-table','mobile-case-detail']
            .forEach(id => { const el = document.getElementById(id); if (el) el.hidden = true; });
    }

    function renderLocatorCell(list) {
        if (!Array.isArray(list) || !list.length) return '<span class="muted">—</span>';
        const first = list[0];
        const req = labelMap(ORIGIN_LABELS, first.requestedOrigin, first.requestedOrigin || '?');
        const sel = labelMap(ORIGIN_LABELS, first.selectedOrigin, first.selectedOrigin || '?');
        let html = '<span class="mobile-locator-cell">' + esc(req) + ' → ' + esc(sel);
        if (first.fallbackReason) {
            html += ' <span class="muted">(' + esc(String(first.fallbackReason).replace(/_/g, ' ').toLowerCase()) + ')</span>';
        }
        html += '</span>';
        if (list.length > 1) html += ' <span class="muted">+' + (list.length - 1) + '</span>';
        return html;
    }

    function renderCaseDetail(c) {
        const body = document.getElementById('mobile-case-detail-body');
        const title = document.getElementById('mobile-case-detail-title');
        const wrap = document.getElementById('mobile-case-detail');
        if (!body || !wrap) return;
        title.textContent = 'Case ' + (c.caseId || '');
        let html = '';
        const [olabel, otone] = outcomeMeta(c.outcome);
        html += '<p><span class="mobile-chip mobile-outcome-chip" data-tone="' + otone + '">' + esc(olabel) + '</span>';
        if (c.timingMs) html += ' <span class="muted">' + esc(c.timingMs) + ' ms</span>';
        html += '</p>';
        if (c.failureCode) html += '<p><strong>Why:</strong> ' + esc(plainFailure(c.failureCode)) + '</p>';
        html += '<p><strong>Setup:</strong> ' + esc(labelMap(SETUP_LABELS, c.setupOutcome, c.setupOutcome || '—')) + '</p>';
        html += '<p><strong>Proof:</strong> ' + esc(labelMap(PROOF_LABELS, c.proofKind, c.proofKind || '—')) + '</p>';
        html += '<p><strong>Provider:</strong> ' + esc(labelMap(PROVIDER_LABELS, c.providerUsed, c.providerUsed || '—'));
        if (c.providerFallbackReason) {
            html += ' <span class="muted">(fell back: ' + esc(String(c.providerFallbackReason).replace(/_/g, ' ').toLowerCase()) + ')</span>';
        }
        html += '</p>';
        const locs = c.locatorEvidence || [];
        if (locs.length) {
            html += '<h4>Locator evidence</h4><ul class="mobile-locator-list">';
            locs.forEach(function (L) {
                const req = labelMap(ORIGIN_LABELS, L.requestedOrigin, L.requestedOrigin);
                const sel = labelMap(ORIGIN_LABELS, L.selectedOrigin, L.selectedOrigin);
                const val = L.valueRedacted ? '(redacted)' : (L.value || '');
                html += '<li><span class="mono">' + esc(L.stepId || '') + '</span>: '
                    + esc(req) + ' → ' + esc(sel)
                    + (L.strategy ? (' · ' + esc(L.strategy)) : '')
                    + (val ? (' = ' + esc(val)) : '')
                    + (L.fallbackReason ? (' <span class="muted">(' + esc(String(L.fallbackReason).replace(/_/g, ' ').toLowerCase()) + ')</span>') : '')
                    + (typeof L.matchCount === 'number' ? (' · matches ' + L.matchCount) : '')
                    + '</li>';
            });
            html += '</ul>';
        }
        const steps = c.steps || [];
        if (steps.length) {
            html += '<h4>Steps</h4><ol class="mobile-step-list">';
            steps.forEach(function (s) {
                const [sl, st] = outcomeMeta(s.outcome);
                html += '<li><span class="mobile-chip mobile-outcome-chip" data-tone="' + st + '">' + esc(sl) + '</span> '
                    + '<span class="mono">' + esc(s.stepId || '') + '</span>';
                if (s.failureCode) html += ' — ' + esc(plainFailure(s.failureCode));
                if (s.actionOutcome) html += ' <span class="muted">' + esc(s.actionOutcome) + '</span>';
                if (s.providerUsed) html += ' · ' + esc(labelMap(PROVIDER_LABELS, s.providerUsed, s.providerUsed));
                html += '</li>';
            });
            html += '</ol>';
        }
        body.innerHTML = html;
        wrap.hidden = false;
    }

    window.keelLoadMobileJobResult = async function (projectId, jobId) {
        if (!projectId || !jobId) { hideAll(); return; }
        try {
            const projRes = await fetch('/api/projects/' + encodeURIComponent(projectId), {
                credentials: 'same-origin', headers: { 'X-Keel-Requested-With': 'Keel' }
            });
            const proj = await projRes.json().catch(() => ({}));
            const platform = (proj.platform || 'WEB').toUpperCase();
            if (!projRes.ok || platform !== 'ANDROID' || proj.mobileEnabled === false) {
                hideAll();
                return;
            }
            const res = await fetch(
                '/api/projects/' + encodeURIComponent(projectId)
                + '/jobs/' + encodeURIComponent(jobId) + '/mobile-result',
                { credentials: 'same-origin', headers: { 'X-Keel-Requested-With': 'Keel' } }
            );
            const err = document.getElementById('mobile-results-err');
            const empty = document.getElementById('mobile-results-empty');
            if (res.status === 404) {
                // Flag off, or no mobile result published yet — keep panel quiet
                panel.hidden = false;
                if (err) err.hidden = true;
                if (empty) { empty.hidden = false; empty.textContent = 'No phone results for this run yet.'; }
                ['mobile-results-summary','mobile-results-runtime','mobile-results-table','mobile-case-detail']
                    .forEach(id => { const el = document.getElementById(id); if (el) el.hidden = true; });
                return;
            }
            if (!res.ok) {
                panel.hidden = false;
                if (empty) empty.hidden = true;
                if (err) { err.hidden = false; err.textContent = 'Could not load phone results (HTTP ' + res.status + ').'; }
                return;
            }
            const data = await res.json();
            panel.hidden = false;
            if (err) err.hidden = true;
            if (empty) empty.hidden = true;

            const summary = document.getElementById('mobile-results-summary');
            const aggRow = document.getElementById('mobile-results-agg-row');
            const counts = document.getElementById('mobile-results-counts');
            if (summary && aggRow && counts) {
                summary.hidden = false;
                aggRow.innerHTML = '';
                const [label, tone] = outcomeMeta(data.aggregateOutcome);
                aggRow.appendChild(chip(label, tone));
                counts.textContent = [
                    (data.passedCount || 0) + ' passed',
                    (data.failedCount || 0) + ' failed',
                    (data.blockedCount || 0) + ' blocked',
                    (data.todoCount || 0) + ' not run',
                    (data.interruptedCount || 0) + ' interrupted'
                ].join(' · ') + (data.timingMs ? (' · ' + data.timingMs + ' ms') : '');
            }

            const rtWrap = document.getElementById('mobile-results-runtime');
            const rt = data.runtime || {};
            const app = rt.app || {};
            const device = rt.device || {};
            const tool = rt.toolchain || {};
            if (rtWrap) {
                rtWrap.hidden = false;
                const appEl = document.getElementById('mobile-rt-app');
                const devEl = document.getElementById('mobile-rt-device');
                const toolEl = document.getElementById('mobile-rt-toolchain');
                if (appEl) {
                    appEl.textContent = [
                        app.packageName,
                        app.versionName ? ('v' + app.versionName) : null,
                        app.versionCode != null ? ('(' + app.versionCode + ')') : null,
                        app.launchActivity ? ('opens ' + app.launchActivity) : null
                    ].filter(Boolean).join(' · ') || '—';
                }
                if (devEl) {
                    // serialFingerprint only — never raw serial
                    devEl.textContent = [
                        device.model,
                        device.apiLevel != null ? ('Android API ' + device.apiLevel) : null,
                        device.abi,
                        fingerprintLabel(device.serialFingerprint)
                    ].filter(Boolean).join(' · ') || '—';
                }
                if (toolEl) {
                    toolEl.textContent = [
                        tool.appiumVersion ? ('Appium ' + tool.appiumVersion) : null,
                        tool.uiautomator2Version ? ('UIAutomator2 ' + tool.uiautomator2Version) : null,
                        tool.chromedriverVersion ? ('ChromeDriver ' + tool.chromedriverVersion) : null,
                        tool.runnerProtocolVersion ? ('Runner ' + tool.runnerProtocolVersion) : null
                    ].filter(Boolean).join(' · ') || '—';
                }
            }

            const table = document.getElementById('mobile-results-table');
            const tbody = document.getElementById('mobile-results-body');
            const cases = data.cases || [];
            if (table && tbody) {
                tbody.innerHTML = '';
                table.hidden = cases.length === 0;
                cases.forEach(function (c) {
                    const tr = document.createElement('tr');
                    tr.className = 'mobile-result-row';
                    tr.dataset.caseId = c.caseId || '';
                    const [olabel, otone] = outcomeMeta(c.outcome);
                    tr.innerHTML =
                        '<td class="mono">' + esc(c.caseId || '') + '</td>' +
                        '<td><span class="mobile-chip mobile-outcome-chip" data-tone="' + otone + '">' + esc(olabel) + '</span></td>' +
                        '<td>' + esc(plainFailure(c.failureCode) || '—') + '</td>' +
                        '<td>' + esc(labelMap(SETUP_LABELS, c.setupOutcome, c.setupOutcome || '—')) + '</td>' +
                        '<td>' + esc(labelMap(PROOF_LABELS, c.proofKind, c.proofKind || '—')) + '</td>' +
                        '<td>' + esc(labelMap(PROVIDER_LABELS, c.providerUsed, c.providerUsed || '—'))
                            + (c.providerFallbackReason
                                ? (' <span class="muted">(' + esc(String(c.providerFallbackReason).replace(/_/g, ' ').toLowerCase()) + ')</span>')
                                : '') + '</td>' +
                        '<td>' + renderLocatorCell(c.locatorEvidence) + '</td>';
                    tr.addEventListener('click', function () { renderCaseDetail(c); });
                    tbody.appendChild(tr);
                });
            }
            const detail = document.getElementById('mobile-case-detail');
            if (detail) detail.hidden = true;
        } catch (e) {
            panel.hidden = false;
            const err = document.getElementById('mobile-results-err');
            if (err) { err.hidden = false; err.textContent = 'Network error loading phone results.'; }
        }
    };

    const closeBtn = document.getElementById('mobile-case-detail-close');
    if (closeBtn) {
        closeBtn.addEventListener('click', function () {
            const d = document.getElementById('mobile-case-detail');
            if (d) d.hidden = true;
        });
    }
})();

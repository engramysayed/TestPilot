/* Mobile Execute: discover phones, start a run, poll status. Loaded by project-detail.html. */
(function () {
    'use strict';
    const panel = document.getElementById('mobile-run-panel');
    const root = document.getElementById('project-root');
    if (!panel || !root) return;
    if (panel.dataset.mobileEnabled !== 'true' || (panel.dataset.platform || '').toUpperCase() !== 'ANDROID') return;
    panel.hidden = false;
    const canEdit = panel.dataset.canEdit === 'true';
    const projectId = root.getAttribute('data-project-id');
    const H = { 'Accept': 'application/json', 'Content-Type': 'application/json', 'X-Keel-Requested-With': 'Keel' };
    const base = '/api/projects/' + encodeURIComponent(projectId) + '/mobile';
    const sel = document.getElementById('mobile-run-device');
    const msg = document.getElementById('mobile-run-msg');
    const startBtn = document.getElementById('mobile-run-start');
    const cancelBtn = document.getElementById('mobile-run-cancel');
    const discoverBtn = document.getElementById('mobile-discover-btn');
    const table = document.getElementById('mobile-run-results');
    let runId = null;
    let timer = null;

    function say(text, isError) {
        msg.textContent = text || '';
        msg.className = isError ? 'error' : 'muted';
    }

    function esc(v) {
        return String(v == null ? '' : v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
    }

    async function loadDevices() {
        const res = await fetch(base + '/devices', { credentials: 'same-origin', headers: H });
        const data = await res.json().catch(() => ({}));
        sel.innerHTML = '';
        (data.devices || []).forEach(d => {
            const o = document.createElement('option');
            o.value = d.deviceId;
            o.textContent = (d.displayName || d.model || d.deviceId) + ' ' + (d.serialShort || '') + ' — ' + (d.busy || '');
            sel.appendChild(o);
        });
        if (!sel.options.length) say('No phones yet. Connect one over USB (or start an emulator) and press Find connected phones.');
    }

    discoverBtn.addEventListener('click', async () => {
        say('Looking for phones…');
        const res = await fetch(base + '/devices/discover', { method: 'POST', credentials: 'same-origin', headers: H, body: '{}' });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) { say(data.message || 'Could not list phones', true); return; }
        const skipped = (data.devices || []).filter(d => !d.registered);
        say(skipped.length ? skipped.map(d => (d.model || d.serialShort) + ': ' + d.reason).join(' · ') : 'Phones updated.');
        loadDevices();
    });

    function render(run) {
        say(run.status + ' — ' + (run.message || '') + (run.error ? ' (' + run.error + ')' : ''), run.status === 'FAILED');
        const rows = run.results || [];
        table.hidden = rows.length === 0;
        table.querySelector('tbody').innerHTML = rows.map(r =>
            '<tr><td>' + esc(r.tcId) + '</td><td>' + esc(r.outcome) + '</td><td>' + esc(r.detail || r.failureCode || '') + '</td></tr>').join('');
        const done = ['COMPLETED', 'FAILED', 'CANCELLED'].includes(run.status);
        cancelBtn.hidden = done;
        startBtn.disabled = !done || !canEdit;
        if (done && timer) { clearInterval(timer); timer = null; }
    }

    async function poll() {
        if (!runId) return;
        const res = await fetch(base + '/runs/' + encodeURIComponent(runId), { credentials: 'same-origin', headers: H });
        if (res.ok) render(await res.json());
    }

    const reviewToggle = document.getElementById('mobile-pre-run-review');
    const reviewPanel = document.getElementById('mobile-pre-run-review-panel');
    const reviewStatus = document.getElementById('mobile-pre-run-review-status');
    const reviewFindings = document.getElementById('mobile-pre-run-review-findings');
    const reviewErrors = document.getElementById('mobile-pre-run-review-errors');
    const reviewCases = document.getElementById('mobile-pre-run-review-cases');
    const reviewAcceptBtn = document.getElementById('mobile-pre-run-review-accept');
    const reviewCancelBtn = document.getElementById('mobile-pre-run-review-cancel');
    let reviewedCases = [];
    const reviewProjectBase = '/api/projects/' + encodeURIComponent(projectId);

    function resetReviewPanel() {
        if (!reviewPanel) return;
        reviewPanel.hidden = true;
        reviewStatus.textContent = '';
        reviewFindings.innerHTML = '';
        reviewErrors.innerHTML = '';
        reviewCases.innerHTML = '';
        reviewAcceptBtn.disabled = true;
        reviewedCases = [];
    }

    function renderReviewCases(cases) {
        reviewCases.innerHTML = '';
        if (!Array.isArray(cases) || !cases.length) {
            reviewCases.innerHTML = '<p class="muted">No proposed cases in this review.</p>';
            return;
        }
        cases.forEach(function (tc) {
            const card = document.createElement('article');
            card.className = 'review-case' + (tc.edited ? ' review-case-edited' : '');
            card.innerHTML = '<header class="review-case-head">'
                + '<span class="mono">' + esc(tc.tcId || '') + '</span>'
                + '<strong>' + esc(tc.title || '') + '</strong>'
                + (tc.edited ? '<span class="badge edited-badge">edited</span>' : '')
                + '</header>';
            reviewCases.appendChild(card);
        });
    }

    async function runPreReview(tcIds) {
        resetReviewPanel();
        reviewPanel.hidden = false;
        reviewStatus.textContent = 'Reviewing cases…';
        startBtn.disabled = true;
        const body = new FormData();
        body.append('useGenerated', 'true');
        body.append('provider', 'cursor');
        tcIds.forEach(id => body.append('tcIds', id));
        try {
            const res = await fetch(reviewProjectBase + '/pre-run-authoring-review', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'X-Keel-Requested-With': 'Keel' },
                body
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                reviewStatus.textContent = (data.error || 'REVIEW_FAILED') + ': ' + (data.message || 'Cursor review failed');
                startBtn.disabled = !canEdit;
                return;
            }
            (data.findings || []).forEach(f => {
                const li = document.createElement('li');
                li.textContent = '[' + (f.severity || 'info') + '] ' + (f.tcId ? f.tcId + ': ' : '') + (f.message || '');
                reviewFindings.appendChild(li);
            });
            (data.gateErrors || []).forEach(m => {
                const li = document.createElement('li');
                li.textContent = m;
                reviewErrors.appendChild(li);
            });
            reviewedCases = data.cases || [];
            renderReviewCases(reviewedCases);
            const ok = !!data.previewOk && reviewedCases.length > 0;
            reviewStatus.textContent = ok
                ? 'Review complete. Accept to save the library and start the run.'
                : 'Review found blocking issues. Fix them before running.';
            reviewAcceptBtn.disabled = !ok;
            startBtn.disabled = true; // keep disabled until Accept or Cancel
        } catch (e) {
            reviewStatus.textContent = 'Network error while running pre-run review.';
            startBtn.disabled = !canEdit;
        }
    }

    async function startMobileRun(deviceId, tcIds) {
        const res = await fetch(base + '/runs', {
            method: 'POST', credentials: 'same-origin', headers: H,
            body: JSON.stringify({ deviceId: deviceId, tcIds: tcIds })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) { say(data.message || 'Could not start the run', true); startBtn.disabled = !canEdit; return; }
        runId = data.runId;
        render(data);
        timer = setInterval(poll, 3000);
    }

    if (reviewCancelBtn) {
        reviewCancelBtn.addEventListener('click', () => {
            resetReviewPanel();
            if (reviewToggle) reviewToggle.checked = false;
            startBtn.disabled = !canEdit;
        });
    }
    if (reviewAcceptBtn) {
        reviewAcceptBtn.addEventListener('click', async () => {
            if (!reviewedCases.length) return;
            reviewAcceptBtn.disabled = true;
            reviewStatus.textContent = 'Saving reviewed cases to the library…';
            try {
                const res = await fetch(reviewProjectBase + '/generated-workbook/apply-review', {
                    method: 'POST', credentials: 'same-origin',
                    headers: H,
                    body: JSON.stringify({ cases: reviewedCases })
                });
                const data = await res.json().catch(() => ({}));
                if (!res.ok) {
                    reviewStatus.textContent = data.message || 'Could not save reviewed cases to the library.';
                    reviewAcceptBtn.disabled = false;
                    return;
                }
                reviewStatus.textContent = 'Starting phone run…';
                const tcIds = reviewedCases.map(tc => (tc.tcId || '').trim()).filter(Boolean);
                await startMobileRun(sel.value, tcIds);
                resetReviewPanel();
                if (reviewToggle) reviewToggle.checked = false;
            } catch (e) {
                reviewStatus.textContent = 'Network error while saving reviewed cases.';
                reviewAcceptBtn.disabled = false;
            }
        });
    }

    startBtn.disabled = !canEdit;
    startBtn.addEventListener('click', async () => {
        const tcIds = document.getElementById('mobile-run-tcs').value.split(/[\s,]+/).filter(Boolean);
        if (reviewToggle && reviewToggle.checked) {
            if (!tcIds.length) {
                say('Pick at least one TC id to review before running.', true);
                return;
            }
            await runPreReview(tcIds);
            return;
        }
        await startMobileRun(sel.value, tcIds);
    });

    cancelBtn.addEventListener('click', async () => {
        if (!runId) return;
        await fetch(base + '/runs/' + encodeURIComponent(runId) + '/cancel', { method: 'POST', credentials: 'same-origin', headers: H, body: '{}' });
        poll();
    });

    loadDevices();
})();

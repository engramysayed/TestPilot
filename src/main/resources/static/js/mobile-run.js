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

    startBtn.disabled = !canEdit;
    startBtn.addEventListener('click', async () => {
        const tcIds = document.getElementById('mobile-run-tcs').value.split(/[\s,]+/).filter(Boolean);
        const res = await fetch(base + '/runs', {
            method: 'POST', credentials: 'same-origin', headers: H,
            body: JSON.stringify({ deviceId: sel.value, tcIds: tcIds })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) { say(data.message || 'Could not start the run', true); return; }
        runId = data.runId;
        render(data);
        timer = setInterval(poll, 3000);
    });

    cancelBtn.addEventListener('click', async () => {
        if (!runId) return;
        await fetch(base + '/runs/' + encodeURIComponent(runId) + '/cancel', { method: 'POST', credentials: 'same-origin', headers: H, body: '{}' });
        poll();
    });

    loadDevices();
})();

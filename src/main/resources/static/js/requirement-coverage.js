/* Generate page: story → case coverage table for the selected project. */
(function () {
    'use strict';
    const sel = document.getElementById('project-select');
    const table = document.getElementById('req-coverage-table');
    const panel = document.getElementById('req-coverage-panel');
    const untraced = document.getElementById('req-coverage-untraced');
    if (!sel || !table || !panel) return;
    const esc = v => String(v == null ? '' : v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

    async function load() {
        if (!sel.value) return;
        const res = await fetch('/api/projects/' + encodeURIComponent(sel.value) + '/requirements-coverage', {
            credentials: 'same-origin', headers: { 'Accept': 'application/json', 'X-Keel-Requested-With': 'Keel' }
        });
        if (!res.ok) return;
        const data = await res.json();
        const rows = data.requirements || [];
        table.querySelector('tbody').innerHTML = rows.length
            ? rows.map(r => '<tr><td>' + esc(r.requirement) + '</td><td>' + esc(r.caseCount) + '</td><td>' + esc((r.tcIds || []).join(', ')) + '</td></tr>').join('')
            : '<tr><td colspan="3" class="muted">No story ids yet. New generations add them automatically.</td></tr>';
        const u = data.untracedTcIds || [];
        untraced.textContent = u.length ? 'Not linked to a story: ' + u.join(', ') : '';
    }

    sel.addEventListener('change', load);
    panel.addEventListener('toggle', () => { if (panel.open) load(); });
    document.addEventListener('keel:library-saved', load);
    setTimeout(load, 800);
})();

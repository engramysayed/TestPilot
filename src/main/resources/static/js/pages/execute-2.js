/* Extracted from templates/execute.html. Server values arrive via window.KEEL_PAGE (set inline by the template). */
/* M1-07 execute device diagnostics — shown for ANDROID projects when mobile enabled */
(function () {
    const root = document.getElementById('execute-root');
    const mobileEnabled = root && root.dataset.mobileEnabled === 'true';
    const panel = document.getElementById('mobile-run-devices-panel');
    if (!mobileEnabled || !panel) return;

    function readinessLabel(readiness) {
        const r = (readiness || 'UNKNOWN').toUpperCase();
        const map = {
            READY: ['Ready', 'ready'],
            BUSY: ['Busy — another run is using this phone', 'busy'],
            NOT_AUTHORIZED: ['Not authorized — unlock USB debugging on the phone', 'warn'],
            OFFLINE: ['Offline', 'muted'],
            MISSING: ['Missing', 'warn'],
            QUARANTINED: ['Quarantined', 'warn'],
            UNKNOWN: ['Unknown', 'muted']
        };
        const hit = map[r] || map.UNKNOWN;
        return { text: hit[0], tone: hit[1] };
    }

    function chip(label) {
        const span = document.createElement('span');
        span.className = 'mobile-chip';
        span.dataset.tone = label.tone;
        span.textContent = label.text;
        return span;
    }

    window.keelLoadMobileRunDevices = async function (projectId) {
        if (!projectId) {
            panel.hidden = true;
            return;
        }
        try {
            const projRes = await fetch('/api/projects/' + encodeURIComponent(projectId), {
                credentials: 'same-origin', headers: { 'X-Keel-Requested-With': 'Keel' }
            });
            const proj = await projRes.json().catch(() => ({}));
            const platform = (proj.platform || 'WEB').toUpperCase();
            if (!projRes.ok || platform !== 'ANDROID' || proj.mobileEnabled === false) {
                panel.hidden = true;
                return;
            }
            panel.hidden = false;
            const res = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/mobile/devices', {
                credentials: 'same-origin', headers: { 'X-Keel-Requested-With': 'Keel' }
            });
            if (res.status === 404) { panel.hidden = true; return; }
            const data = await res.json().catch(() => ({}));
            const err = document.getElementById('mobile-run-devices-err');
            const empty = document.getElementById('mobile-run-devices-empty');
            const list = document.getElementById('mobile-run-devices-list');
            const select = document.getElementById('mobile-device-select');
            const selectLabel = document.getElementById('mobile-device-select-label');
            if (!res.ok) {
                if (err) { err.hidden = false; err.textContent = data.message || 'Could not load phones'; }
                return;
            }
            if (err) err.hidden = true;
            list.innerHTML = '';
            if (select) {
                select.innerHTML = '<option value="">— Select phone —</option>';
            }
            const devices = data.devices || [];
            if (empty) empty.hidden = devices.length > 0;
            if (selectLabel) selectLabel.hidden = devices.length === 0;
            devices.forEach(d => {
                const card = document.createElement('article');
                card.className = 'mobile-device-card';
                const h = document.createElement('h3');
                h.textContent = d.displayName || 'Phone';
                card.appendChild(h);
                const meta = document.createElement('p');
                meta.className = 'muted';
                meta.textContent = [d.serialShort ? ('Device ID ' + d.serialShort) : null, d.model].filter(Boolean).join(' · ');
                card.appendChild(meta);
                const row = document.createElement('div');
                row.className = 'mobile-chip-row';
                row.appendChild(chip(readinessLabel(d.readiness)));
                card.appendChild(row);
                list.appendChild(card);
                if (select) {
                    const opt = document.createElement('option');
                    opt.value = d.deviceId || '';
                    opt.textContent = (d.displayName || 'Phone') + (d.serialShort ? (' (' + d.serialShort + ')') : '');
                    opt.disabled = (d.readiness || '').toUpperCase() !== 'READY';
                    select.appendChild(opt);
                }
            });
        } catch (e) {
            panel.hidden = true;
        }
    };

    // Hook project selection if the page exposes a select
    const projectSelect = document.getElementById('project-select');
    if (projectSelect) {
        projectSelect.addEventListener('change', () => {
            window.keelLoadMobileRunDevices(projectSelect.value);
        });
    }
    const launcherSelect = document.getElementById('launcher-project-select');
    if (launcherSelect) {
        launcherSelect.addEventListener('change', () => {
            /* devices load after continue when config shown */
        });
    }
    const params = new URLSearchParams(location.search);
    const qProject = params.get('projectId');
    if (qProject) {
        window.keelLoadMobileRunDevices(qProject);
    }
})();

/* Extracted from templates/bug-hunter.html. Server values arrive via window.KEEL_PAGE (set inline by the template). */
(function () {
    const API_HEADERS = { 'X-Keel-Requested-With': 'Keel' };
    const STORAGE_KEY = 'keel.bugHunterProjectId';
    const projectSel = document.getElementById('project-select');
    const formPanel = document.getElementById('hunt-form-panel');
    const startBtn = document.getElementById('start-hunt');
    const huntErr = document.getElementById('hunt-err');
    const huntStarting = document.getElementById('hunt-starting');
    const strategiesCb = document.getElementById('strategies-enabled');
    const strategyExplain = document.getElementById('strategy-explain');
    let projects = [];
    let huntWorkspaceRole = '';
    let huntPromoteJobId = '';

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function badgeClass(status) {
        const s = String(status || '').toUpperCase();
        if (s === 'COMPLETED') return 'ok';
        if (s === 'FAILED' || s === 'CANCELLED') return 'err';
        if (s === 'COMPLETED_WITH_BLOCK') return 'warn';
        return 'muted';
    }

    function syncStrategyUi() {
        strategyExplain.hidden = !strategiesCb.checked;
    }

    function syncOtpField() {
        const credSel = document.getElementById('credential-profile');
        const otpLabel = document.getElementById('otp-label');
        const credLabel = document.getElementById('cred-label');
        if (!credSel || !otpLabel) return;
        const showOtp = !credLabel.hidden && credSel.value !== '';
        otpLabel.hidden = !showOtp;
        if (!showOtp) {
            document.getElementById('hunt-otp').value = '';
        }
    }

    function setStarting(on) {
        huntStarting.hidden = !on;
        huntStarting.setAttribute('aria-busy', on ? 'true' : 'false');
        formPanel.classList.toggle('is-submitting', on);
        const label = startBtn.querySelector('.hunt-start-label');
        const busy = startBtn.querySelector('.hunt-start-busy');
        if (label) label.hidden = on;
        if (busy) busy.hidden = !on;
        startBtn.disabled = on || startBtn.disabled;
    }

    async function loadProjects() {
        const res = await fetch('/api/projects', { credentials: 'same-origin', headers: API_HEADERS });
        projects = res.ok ? await res.json() : [];
        const noProj = document.getElementById('no-projects');
        projectSel.innerHTML = '';
        if (!projects.length) {
            noProj.hidden = false;
            formPanel.hidden = true;
            projectSel.innerHTML = '<option value="">— None —</option>';
            return;
        }
        noProj.hidden = true;
        projects.forEach(p => {
            const opt = document.createElement('option');
            opt.value = p.projectId;
            opt.textContent = (p.name || p.projectId) + ' (' + p.projectId + ')';
            projectSel.appendChild(opt);
        });
        const saved = sessionStorage.getItem(STORAGE_KEY);
        const match = saved && projects.some(p => p.projectId === saved);
        projectSel.value = match ? saved : projects[0].projectId;
        await onProjectChange();
    }

    async function onProjectChange() {
        const projectId = projectSel.value;
        sessionStorage.setItem(STORAGE_KEY, projectId || '');
        formPanel.hidden = !projectId;
        huntErr.hidden = true;
        if (!projectId) return;
        document.getElementById('settings-link').href =
            '/projects/' + encodeURIComponent(projectId) + '?tab=settings';
        const proj = projects.find(p => p.projectId === projectId) || {};
        huntWorkspaceRole = proj.workspaceRole || '';
        const base = proj.baseUrl || '';
        document.getElementById('base-url-row').hidden = !base;
        document.getElementById('base-url-missing').hidden = !!base;
        document.getElementById('base-url-display').textContent = base;
        await loadCredentials(projectId);
        await loadLibrary(projectId);
        await loadRecent(projectId);
        document.getElementById('hunt-promote-panel').hidden = true;
        updateStartEnabled();
    }

    async function loadCredentials(projectId) {
        const label = document.getElementById('cred-label');
        const sel = document.getElementById('credential-profile');
        const res = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/credentials', {
            credentials: 'same-origin', headers: API_HEADERS
        });
        const list = res.ok ? await res.json() : [];
        sel.innerHTML = '';
        if (!list.length) {
            label.hidden = true;
            syncOtpField();
            return;
        }
        label.hidden = false;
        const none = document.createElement('option');
        none.value = '';
        none.textContent = '— Open site only (no auto-login) —';
        sel.appendChild(none);
        list.forEach(c => {
            const opt = document.createElement('option');
            opt.value = c.profileName;
            opt.textContent = c.profileName + ' (' + (c.username || '') + ')';
            sel.appendChild(opt);
        });
        sel.value = '';
        sel.onchange = syncOtpField;
        syncOtpField();
    }

    function shortMessage(msg) {
        const raw = String(msg == null ? '' : msg).trim();
        if (!raw) return '—';
        // Prefer the leading error code / first sentence over Selenium dump.
        const head = raw.split(/\r?\n/)[0];
        const cut = head.indexOf(' Build info:');
        const cleaned = (cut > 0 ? head.slice(0, cut) : head).trim();
        if (cleaned.length <= 120) return cleaned;
        return cleaned.slice(0, 117) + '…';
    }

    function refreshLibCount() {
        const chips = document.getElementById('hunt-tc-ms-chips');
        const summary = document.getElementById('hunt-tc-ms-summary');
        const countEl = document.getElementById('lib-count');
        if (!chips || !summary) return;
        const selected = Array.from(document.querySelectorAll('.hunt-tc:checked'));
        const total = document.querySelectorAll('.hunt-tc').length;
        chips.innerHTML = '';
        if (selected.length === 0) {
            summary.textContent = total ? 'Select cases (' + total + ')' : 'Select cases';
            summary.hidden = false;
        } else {
            selected.slice(0, 4).forEach(cb => {
                const chip = document.createElement('span');
                chip.className = 'tc-ms-chip';
                chip.textContent = cb.value;
                chips.appendChild(chip);
            });
            if (selected.length > 4) {
                const more = document.createElement('span');
                more.className = 'tc-ms-chip tc-ms-chip-more';
                more.textContent = '+' + (selected.length - 4);
                chips.appendChild(more);
            }
            summary.textContent = selected.length + ' of ' + total;
            summary.hidden = false;
        }
        countEl.textContent = total ? (selected.length + ' selected · ' + total + ' total') : '';
    }

    function openHuntMultiselect() {
        const drop = document.getElementById('hunt-tc-ms-dropdown');
        const trigger = document.getElementById('hunt-tc-ms-trigger');
        if (!drop || !trigger) return;
        drop.hidden = false;
        trigger.setAttribute('aria-expanded', 'true');
        document.getElementById('hunt-tc-ms')?.classList.add('is-open');
        const search = document.getElementById('hunt-tc-ms-search');
        if (search) {
            search.value = '';
            filterHuntMultiselect('');
            search.focus();
        }
    }

    function closeHuntMultiselect() {
        const drop = document.getElementById('hunt-tc-ms-dropdown');
        const trigger = document.getElementById('hunt-tc-ms-trigger');
        if (drop) drop.hidden = true;
        if (trigger) trigger.setAttribute('aria-expanded', 'false');
        document.getElementById('hunt-tc-ms')?.classList.remove('is-open');
    }

    function filterHuntMultiselect(q) {
        const needle = (q || '').trim().toLowerCase();
        document.querySelectorAll('#lib-pick .tc-ms-option').forEach(el => {
            const hay = el.dataset.search || '';
            el.hidden = needle !== '' && !hay.includes(needle);
        });
    }

    async function loadLibrary(projectId) {
        const pick = document.getElementById('lib-pick');
        const empty = document.getElementById('lib-empty');
        const ms = document.getElementById('hunt-tc-ms');
        pick.innerHTML = '';
        closeHuntMultiselect();
        const res = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/generated-workbook/cases', {
            credentials: 'same-origin', headers: API_HEADERS
        });
        if (!res.ok) {
            empty.hidden = false;
            ms.hidden = true;
            refreshLibCount();
            return;
        }
        const data = await res.json();
        const cases = data.cases || [];
        empty.hidden = cases.length > 0;
        ms.hidden = cases.length === 0;
        cases.forEach(tc => {
            const id = tc.tcId || tc.id;
            const lab = document.createElement('label');
            lab.className = 'tc-ms-option generate-check';
            lab.dataset.search = ((id || '') + ' ' + (tc.title || '')).toLowerCase();
            lab.innerHTML = '<input type="checkbox" class="hunt-tc" value="' + escapeHtml(id) + '"/> '
                + '<span class="mono">' + escapeHtml(id) + '</span> '
                + '<span class="tc-ms-option-title">' + escapeHtml(tc.title || '') + '</span>';
            lab.querySelector('input').addEventListener('change', () => {
                refreshLibCount();
                updateStartEnabled();
            });
            pick.appendChild(lab);
        });
        refreshLibCount();
    }

    function selectedTcIds() {
        return Array.from(document.querySelectorAll('.hunt-tc:checked')).map(cb => cb.value);
    }

    function updateStartEnabled() {
        if (formPanel.classList.contains('is-submitting')) {
            startBtn.disabled = true;
            return;
        }
        const hasBase = !document.getElementById('base-url-row').hidden;
        startBtn.disabled = !(hasBase && selectedTcIds().length > 0);
    }

    async function loadRecent(projectId) {
        const res = await fetch('/api/jobs?projectId=' + encodeURIComponent(projectId) + '&kind=HUNT', {
            credentials: 'same-origin', headers: API_HEADERS
        });
        const jobs = res.ok ? await res.json() : [];
        const empty = document.getElementById('recent-empty');
        const table = document.getElementById('recent-table');
        const body = document.getElementById('recent-body');
        body.innerHTML = '';
        empty.hidden = jobs.length > 0;
        table.hidden = jobs.length === 0;
        jobs.slice(0, 10).forEach(j => {
            const tr = document.createElement('tr');
            const busy = (j.status === 'RUNNING' || j.status === 'QUEUED')
                ? ' <span class="spinner spinner--badge" aria-hidden="true"></span>'
                : '';
            const dl = j.downloadable
                ? ' <a href="/api/jobs/' + encodeURIComponent(j.jobId) + '/download">Download</a>'
                : '';
            const review = (j.status === 'COMPLETED' || j.status === 'COMPLETED_WITH_BLOCK')
                ? ' <button type="button" class="button ghost hunt-review" data-job-id="'
                    + escapeHtml(j.jobId) + '">Review</button>'
                : '';
            tr.innerHTML =
                '<td><code class="mono" title="' + escapeHtml(j.jobId) + '">' + escapeHtml(j.jobId) + '</code></td>' +
                '<td><span class="badge ' + badgeClass(j.status) + ' badge--busy">' + escapeHtml(j.status) + busy + '</span></td>' +
                '<td>' + (j.passedCount != null ? j.passedCount : '—') + '</td>' +
                '<td>' + (j.todoCount != null ? j.todoCount : '—') + '</td>' +
                '<td class="clip muted" title="' + escapeHtml(j.message || '') + '">' + escapeHtml(shortMessage(j.message)) + '</td>' +
                '<td class="actions"><a href="/status?jobId=' + encodeURIComponent(j.jobId) + '">Status</a>' + dl + review + '</td>';
            body.appendChild(tr);
        });
    }

    startBtn.addEventListener('click', async () => {
        if (window.KeelCredits && KeelCredits.blockIfNotApproved(huntErr)) {
            huntErr.hidden = false;
            return;
        }
        huntErr.hidden = true;
        setStarting(true);
        const projectId = projectSel.value;
        const planner = (document.querySelector('input[name="planner"]:checked') || {}).value || 'ollama';
        const body = {
            tcIds: selectedTcIds(),
            userStory: document.getElementById('user-story').value || '',
            planner: planner,
            scenarioCap: Number(document.getElementById('scenario-cap').value) || 5,
            cycleCeiling: Number(document.getElementById('cycle-ceiling').value) || 8,
            iterationCeiling: Number(document.getElementById('iteration-ceiling').value) || 2,
            actionCapPerCycle: Number(document.getElementById('action-cap').value) || 5,
            domMode: document.getElementById('dom-mode').value || 'auto',
            strategiesEnabled: document.getElementById('strategies-enabled').checked
        };
        if (!document.getElementById('cred-label').hidden) {
            body.credentialProfile = document.getElementById('credential-profile').value || '';
        }
        const otpVal = (document.getElementById('hunt-otp').value || '').trim();
        if (otpVal) {
            body.otp = otpVal;
        }
        try {
            const res = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/hunt-runs', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { ...API_HEADERS, 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            });
            let data = {};
            try { data = await res.json(); } catch (e) { /* ignore */ }
            if (!res.ok) {
                huntErr.hidden = false;
                var gate = window.KeelCredits && KeelCredits.gateMessage(data);
                huntErr.textContent = gate || ((data && data.message) || 'Could not start Bug Hunter');
                setStarting(false);
                updateStartEnabled();
                return;
            }
            window.location.href = '/status?jobId=' + encodeURIComponent(data.jobId);
        } catch (e) {
            huntErr.hidden = false;
            huntErr.textContent = 'Network error starting Bug Hunter';
            setStarting(false);
            updateStartEnabled();
        }
    });

    async function openHuntPromote(jobId) {
        huntPromoteJobId = jobId;
        const panel = document.getElementById('hunt-promote-panel');
        const err = document.getElementById('hunt-promote-err');
        const msg = document.getElementById('hunt-promote-msg');
        const accept = document.getElementById('hunt-promote-accept');
        const submit = document.getElementById('hunt-promote-submit');
        panel.hidden = false;
        err.hidden = true;
        msg.hidden = true;
        accept.checked = false;
        const canOperate = huntWorkspaceRole === 'OWNER' || huntWorkspaceRole === 'ADMIN';
        accept.disabled = !canOperate;
        submit.disabled = true;
        document.getElementById('hunt-promote-job').textContent = jobId;
        const projectId = projectSel.value;
        const res = await fetch('/api/projects/' + encodeURIComponent(projectId)
            + '/hunt-runs/' + encodeURIComponent(jobId) + '/promote', {
            credentials: 'same-origin', headers: API_HEADERS
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            err.hidden = false;
            err.textContent = data.message || 'Could not load candidates. If the library moved, rebase and review again.';
            document.getElementById('hunt-promote-cases').innerHTML = '';
            return;
        }
        const dups = (data.duplicates || []).map(d =>
            (d.kind || '') + ': ' + (d.candidateTitle || '') + ' ~ ' + (d.libraryTcId || '')).join('; ');
        document.getElementById('hunt-promote-dups').textContent = dups
            ? ('Duplicates: ' + dups)
            : 'No exact or suggested semantic duplicates against the pinned revision.';
        const mount = document.getElementById('hunt-promote-cases');
        mount.innerHTML = '';
        (data.cases || []).forEach((c, i) => {
            const wrap = document.createElement('fieldset');
            wrap.className = 'hunt-promote-case';
            wrap.innerHTML =
                '<legend class="mono">' + escapeHtml(c.tcId || ('case-' + (i + 1))) + '</legend>' +
                '<label>Title <input type="text" class="hunt-edit-title" value="' + escapeHtml(c.title || '') + '"/></label>' +
                '<label>Steps <textarea class="hunt-edit-steps" rows="3">' + escapeHtml(c.steps || '') + '</textarea></label>' +
                '<label>Expected <textarea class="hunt-edit-expected" rows="2">'
                    + escapeHtml(c.expectedResult || c.expected || '') + '</textarea></label>';
            wrap.dataset.tcId = c.tcId || '';
            mount.appendChild(wrap);
        });
        if (!(data.cases || []).length) {
            mount.innerHTML = '<p class="muted">No candidate scenarios in this pack.</p>';
        }
    }

    document.getElementById('recent-body')?.addEventListener('click', (e) => {
        const btn = e.target.closest('.hunt-review');
        if (!btn) return;
        openHuntPromote(btn.getAttribute('data-job-id'));
    });
    document.getElementById('hunt-promote-accept')?.addEventListener('change', (e) => {
        const canOperate = huntWorkspaceRole === 'OWNER' || huntWorkspaceRole === 'ADMIN';
        document.getElementById('hunt-promote-submit').disabled = !e.target.checked || !canOperate;
    });
    document.getElementById('hunt-promote-submit')?.addEventListener('click', async () => {
        const err = document.getElementById('hunt-promote-err');
        const msg = document.getElementById('hunt-promote-msg');
        err.hidden = true;
        msg.hidden = true;
        const cases = Array.from(document.querySelectorAll('.hunt-promote-case')).map(fs => ({
            tcId: fs.dataset.tcId,
            title: fs.querySelector('.hunt-edit-title').value,
            steps: fs.querySelector('.hunt-edit-steps').value,
            expectedResult: fs.querySelector('.hunt-edit-expected').value
        }));
        const res = await fetch('/api/projects/' + encodeURIComponent(projectSel.value)
            + '/hunt-runs/' + encodeURIComponent(huntPromoteJobId) + '/promote', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { ...API_HEADERS, 'Content-Type': 'application/json' },
            body: JSON.stringify({ accept: true, cases })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            err.hidden = false;
            err.textContent = data.message || 'Promote failed';
            return;
        }
        msg.hidden = false;
        msg.textContent = 'Promoted ' + (data.caseCount || cases.length)
            + ' case(s). They still need normal proving before automation ready.';
        loadRecent(projectSel.value);
    });

    document.getElementById('hunt-tc-ms-trigger')?.addEventListener('click', (e) => {
        e.preventDefault();
        const drop = document.getElementById('hunt-tc-ms-dropdown');
        if (drop && drop.hidden) openHuntMultiselect();
        else closeHuntMultiselect();
    });
    document.getElementById('hunt-tc-ms-search')?.addEventListener('input', (e) => {
        filterHuntMultiselect(e.target.value);
    });
    document.getElementById('lib-pick-all')?.addEventListener('click', () => {
        document.querySelectorAll('#lib-pick .tc-ms-option:not([hidden]) .hunt-tc').forEach(cb => { cb.checked = true; });
        refreshLibCount();
        updateStartEnabled();
    });
    document.getElementById('lib-pick-none')?.addEventListener('click', () => {
        document.querySelectorAll('.hunt-tc').forEach(cb => { cb.checked = false; });
        refreshLibCount();
        updateStartEnabled();
    });
    document.addEventListener('click', (e) => {
        const root = document.getElementById('hunt-tc-ms');
        if (root && !root.contains(e.target)) closeHuntMultiselect();
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeHuntMultiselect();
    });
    strategiesCb.addEventListener('change', syncStrategyUi);
    syncStrategyUi();

    projectSel.addEventListener('change', onProjectChange);

    /* --- Hunt triage queue (flag-gated; AE shapes) --- */
    const triagePanel = document.getElementById('hunt-triage-panel');
    const triageEnabled = document.body.getAttribute('data-hunt-triage-enabled') === 'true'
        || (triagePanel && triagePanel.getAttribute('data-enabled') === 'true');
    const triageJobSel = document.getElementById('triage-job-select');
    const triageList = document.getElementById('triage-list');
    const triageSummary = document.getElementById('triage-summary');
    const triageErr = document.getElementById('triage-err');
    const triageMsg = document.getElementById('triage-msg');
    const triageEmpty = document.getElementById('triage-empty');

    function triageStatusLabel(status) {
        const s = String(status || '').toUpperCase();
        if (s === 'CONFIRMED') return 'Confirmed';
        if (s === 'DISMISSED') return 'Dismissed';
        if (s === 'FLAKY') return 'Flaky';
        return 'Needs review';
    }

    function setTriageErr(msg) {
        if (!triageErr) return;
        triageErr.hidden = !msg;
        triageErr.textContent = msg || '';
    }
    function setTriageMsg(msg) {
        if (!triageMsg) return;
        triageMsg.hidden = !msg;
        triageMsg.textContent = msg || '';
    }

    async function loadTriageJobs(projectId) {
        if (!triageEnabled || !triagePanel) return;
        triagePanel.hidden = !projectId;
        if (!projectId || !triageJobSel) return;
        triageJobSel.innerHTML = '<option value="">— Pick a completed hunt —</option>';
        try {
            const res = await fetch('/api/jobs?projectId=' + encodeURIComponent(projectId) + '&kind=HUNT', {
                credentials: 'same-origin', headers: API_HEADERS
            });
            let jobs = [];
            if (res.ok) {
                const data = await res.json();
                jobs = Array.isArray(data) ? data : (data.jobs || data.items || []);
            }
            // Fallback: reuse recent table rows if jobs API shape differs
            if (!jobs.length) {
                document.querySelectorAll('#recent-body tr[data-job-id]').forEach(function (tr) {
                    jobs.push({ jobId: tr.getAttribute('data-job-id'), status: tr.getAttribute('data-status') || '' });
                });
            }
            jobs.filter(function (j) {
                const st = String(j.status || j.jobStatus || '').toUpperCase();
                return !st || st === 'COMPLETED' || st === 'COMPLETED_WITH_BLOCK' || st === 'FAILED';
            }).forEach(function (j) {
                const id = j.jobId || j.id;
                if (!id) return;
                const opt = document.createElement('option');
                opt.value = id;
                opt.textContent = id + (j.status ? (' · ' + j.status) : '');
                triageJobSel.appendChild(opt);
            });
        } catch (e) {
            setTriageErr('Could not load hunt runs for triage.');
        }
    }

    async function loadTriageBugs() {
        if (!triageEnabled) return;
        const projectId = projectSel.value;
        const jobId = triageJobSel && triageJobSel.value;
        setTriageErr('');
        setTriageMsg('');
        triageList.innerHTML = '';
        triageEmpty.hidden = true;
        triageSummary.hidden = true;
        if (!projectId || !jobId) return;
        const base = '/api/projects/' + encodeURIComponent(projectId) + '/hunt/' + encodeURIComponent(jobId);
        try {
            const [bugsRes, sumRes] = await Promise.all([
                fetch(base + '/bugs', { credentials: 'same-origin', headers: API_HEADERS }),
                fetch(base + '/summary', { credentials: 'same-origin', headers: API_HEADERS })
            ]);
            if (bugsRes.status === 404) {
                setTriageErr('No triage data for this hunt yet (run not found, or triage still off on the server).');
                return;
            }
            if (!bugsRes.ok) {
                const b = await bugsRes.json().catch(function () { return {}; });
                setTriageErr(b.message || b.error || ('Could not load findings (' + bugsRes.status + ')'));
                return;
            }
            const payload = await bugsRes.json();
            const bugs = Array.isArray(payload) ? payload : (payload.bugs || []);
            if (sumRes.ok) {
                const s = await sumRes.json();
                triageSummary.hidden = false;
                triageSummary.textContent = 'Needs review: ' + (s.firstPass || 0)
                    + ' · Flaky: ' + (s.flaky || 0)
                    + ' · Confirmed: ' + (s.confirmed || 0)
                    + ' · Dismissed: ' + (s.dismissed || 0)
                    + (s.plateau ? ' · Coverage plateau' : '');
            }
            if (!bugs.length) {
                triageEmpty.hidden = false;
                return;
            }
            bugs.forEach(function (bug) { triageList.appendChild(renderTriageCard(projectId, jobId, bug)); });
        } catch (e) {
            setTriageErr(e && e.message ? e.message : 'Network error loading findings');
        }
    }

    function renderTriageCard(projectId, jobId, bug) {
        const card = document.createElement('article');
        card.className = 'hunt-triage-card';
        card.dataset.bugId = bug.bugId || '';
        const status = String(bug.status || 'FIRST_PASS').toUpperCase();
        const steps = Array.isArray(bug.reproduceSteps) ? bug.reproduceSteps : [];
        const stepsHtml = steps.slice(0, 5).map(function (st) {
            if (typeof st === 'string') return '<li>' + escapeHtml(st) + '</li>';
            return '<li>' + escapeHtml((st.action || '') + ' ' + (st.target || '') + (st.value ? (' → ' + st.value) : '')) + '</li>';
        }).join('');
        const canPromote = status === 'CONFIRMED' && !bug.promoted;
        const promotedLabel = bug.promoted
            ? (' · Draft ' + (bug.tcDraftId || 'promoted'))
            : '';
        card.innerHTML =
            '<header class="hunt-triage-card-head">'
            + '<h3>' + escapeHtml(bug.title || bug.bugId || 'Finding') + '</h3>'
            + '<span class="chip chip--' + (status === 'CONFIRMED' ? 'ok' : status === 'DISMISSED' ? 'muted' : 'warn') + '">'
            + escapeHtml(triageStatusLabel(status)) + '</span>'
            + '</header>'
            + '<p class="muted">Severity: ' + escapeHtml(bug.severity || '—')
            + ' · How found: ' + escapeHtml(bug.admission || '—')
            + (bug.toastObservedAfterSettle ? ' · Toast after settle' : '')
            + escapeHtml(promotedLabel)
            + '</p>'
            + (bug.url ? ('<p class="mono muted">' + escapeHtml(bug.url) + '</p>') : '')
            + (stepsHtml ? ('<ol class="hunt-triage-steps">' + stepsHtml + '</ol>') : '')
            + '<label class="hunt-triage-note">Note'
            + '<input type="text" class="triage-note" placeholder="Optional note for Needs more info…" value="'
            + escapeHtml(bug.note || '') + '"/></label>'
            + '<div class="actions hunt-triage-actions">'
            + '<button type="button" class="button" data-act="confirm">Mark confirmed</button>'
            + '<button type="button" class="button ghost" data-act="dismiss">Dismiss</button>'
            + '<button type="button" class="button ghost" data-act="needs">Needs more info</button>'
            + '<button type="button" class="button" data-act="promote" '
            + (canPromote ? '' : 'disabled ')
            + 'title="Adds a test case draft — separate from confirming">Promote to library</button>'
            + '</div>';
        card.querySelectorAll('[data-act]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                onTriageAction(projectId, jobId, bug, btn.getAttribute('data-act'), card);
            });
        });
        return card;
    }

    async function onTriageAction(projectId, jobId, bug, act, card) {
        const noteEl = card.querySelector('.triage-note');
        const note = noteEl ? noteEl.value.trim() : '';
        const base = '/api/projects/' + encodeURIComponent(projectId)
            + '/hunt/' + encodeURIComponent(jobId) + '/bugs/' + encodeURIComponent(bug.bugId);
        setTriageErr('');
        setTriageMsg('');
        try {
            if (act === 'promote') {
                const res = await fetch(base + '/promote', {
                    method: 'POST', credentials: 'same-origin',
                    headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                    body: '{}'
                });
                const body = await res.json().catch(function () { return {}; });
                if (!res.ok) {
                    setTriageErr(body.message || body.error || ('Promote failed (' + res.status + ')'));
                    return;
                }
                setTriageMsg(body.message || ('TC draft created/linked' + (body.tcDraftId ? (': ' + body.tcDraftId) : '')));
                await loadTriageBugs();
                return;
            }
            let status = bug.status || 'FIRST_PASS';
            if (act === 'confirm') status = 'CONFIRMED';
            else if (act === 'dismiss') status = 'DISMISSED';
            else if (act === 'needs') {
                if (!note) {
                    setTriageErr('Add a short note explaining what more info you need.');
                    return;
                }
                status = (String(bug.status).toUpperCase() === 'FLAKY') ? 'FLAKY' : 'FIRST_PASS';
            }
            const res = await fetch(base, {
                method: 'PATCH', credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: JSON.stringify({ status: status, note: note || undefined })
            });
            const body = await res.json().catch(function () { return {}; });
            if (!res.ok) {
                setTriageErr(body.message || body.error || ('Update failed (' + res.status + ')'));
                return;
            }
            setTriageMsg(act === 'confirm'
                ? 'Marked confirmed — use Promote when you want a test case draft. Nothing was added to the library yet.'
                : 'Saved.');
            await loadTriageBugs();
        } catch (e) {
            setTriageErr(e && e.message ? e.message : 'Network error');
        }
    }

    if (triageEnabled) {
        triageJobSel && triageJobSel.addEventListener('change', loadTriageBugs);
        const _onProjectChange = onProjectChange;
        onProjectChange = async function () {
            await _onProjectChange();
            await loadTriageJobs(projectSel.value);
            await loadTriageBugs();
        };
        // Tag recent rows for fallback job list
        const _loadRecent = loadRecent;
        if (typeof loadRecent === 'function') {
            loadRecent = async function (projectId) {
                await _loadRecent(projectId);
                document.querySelectorAll('#recent-body tr').forEach(function (tr) {
                    const link = tr.querySelector('a[href*="jobId="]');
                    if (link) {
                        const m = /jobId=([^&]+)/.exec(link.getAttribute('href') || '');
                        if (m) tr.setAttribute('data-job-id', decodeURIComponent(m[1]));
                    }
                    const st = tr.querySelector('.badge, .chip, [data-status]');
                    if (st) tr.setAttribute('data-status', (st.textContent || '').trim());
                });
                if (triageEnabled) await loadTriageJobs(projectId);
            };
        }
    } else if (triagePanel) {
        triagePanel.hidden = true;
    }

    let huntUnit = null;
    function creditDigits(n) {
        return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    }
    function paintHuntPrice() {
        const detail = document.getElementById('hunt-price-detail');
        const totalEl = document.getElementById('hunt-price-total');
        const unitEl = document.getElementById('hunt-price-unit');
        if (!detail || !totalEl) return;
        const iterations = Math.min(5, Math.max(1, Number(document.getElementById('iteration-ceiling').value) || 2));
        const cycles = Math.min(30, Math.max(1, Number(document.getElementById('cycle-ceiling').value) || 8));
        const actions = Math.min(15, Math.max(1, Number(document.getElementById('action-cap').value) || 5));
        const steps = iterations * cycles * actions;
        if (huntUnit == null) {
            detail.textContent = steps + ' steps';
            totalEl.textContent = creditDigits(steps);
            if (unitEl) unitEl.textContent = 'steps';
            return;
        }
        detail.textContent = steps + ' steps × ' + creditDigits(huntUnit);
        totalEl.textContent = creditDigits(steps * huntUnit);
        if (unitEl) unitEl.textContent = 'credits';
    }
    ['iteration-ceiling', 'cycle-ceiling', 'action-cap'].forEach(function (id) {
        const input = document.getElementById(id);
        if (input) input.addEventListener('input', paintHuntPrice);
    });
    paintHuntPrice();
    fetch('/api/account/credits', { credentials: 'same-origin', headers: API_HEADERS })
        .then(function (res) { return res.ok ? res.json() : null; })
        .then(function (data) {
            if (data && data.costs && typeof data.costs.hunt === 'number') {
                huntUnit = data.costs.hunt;
                paintHuntPrice();
            }
        })
        .catch(function () { /* hint already shows the action count */ });

    loadProjects();
})();

/* Extracted from templates/suites.html. Server values arrive via window.KEEL_PAGE (set inline by the template). */
(function () {
    const API_HEADERS = { 'X-Keel-Requested-With': 'Keel' };
    const STORAGE_KEY = 'keel.suitesProjectId';
    const LAST_SUITE_KEY = 'keel.suitesLastSuiteId';
    const BUILTIN_TAGS = ['smoke', 'regression', 'nightly'];
    const FALLBACK_CHANNELS = [
        { id: 'email', enabled: true, minSeverity: null },
        { id: 'telegram', enabled: true, minSeverity: null },
        { id: 'stub', enabled: true, minSeverity: null }
    ];
    const initialProjectId = (window.KEEL_PAGE && window.KEEL_PAGE.initialProjectId !== undefined && window.KEEL_PAGE.initialProjectId !== null) ? window.KEEL_PAGE.initialProjectId : '';

    const workspaceLauncher = document.getElementById('workspace-launcher');
    const workspaceConfig = document.getElementById('workspace-config');
    const launcherSel = document.getElementById('launcher-project-select');
    const launcherContinue = document.getElementById('launcher-continue');
    const launcherErr = document.getElementById('launcher-err');
    const launcherNoProjects = document.getElementById('launcher-no-projects');
    const projectSel = document.getElementById('project-select');
    const projectErr = document.getElementById('project-err');
    const noProjects = document.getElementById('no-projects');
    const pinnedName = document.getElementById('pinned-project-name');
    const changeProjectBtn = document.getElementById('change-project');
    const apiWaitNote = document.getElementById('api-wait-note');

    const createName = document.getElementById('create-name');
    const createTags = document.getElementById('create-tags');
    const createLibrarySearch = document.getElementById('create-library-search');
    const createLibraryStatus = document.getElementById('create-library-status');
    const createLibraryList = document.getElementById('create-library-list');
    const createSelectedSummary = document.getElementById('create-selected-summary');
    const createSelectedList = document.getElementById('create-selected-list');
    const createBtn = document.getElementById('create-suite');
    const refreshBtn = document.getElementById('refresh-suites');
    const createStatus = document.getElementById('create-status');
    const suitesEmpty = document.getElementById('suites-empty');
    const suitesList = document.getElementById('suites-list');
    const suitesMeta = document.getElementById('suites-meta');
    const suiteDetail = document.getElementById('suite-detail');

    const builtinChips = document.getElementById('builtin-chips');
    const customChips = document.getElementById('custom-chips');
    const customTagInput = document.getElementById('custom-tag-input');
    const addCustomTagBtn = document.getElementById('add-custom-tag');
    const saveTagsBtn = document.getElementById('save-tags');
    const tagsStatus = document.getElementById('tags-status');

    const detailLibrarySearch = document.getElementById('detail-library-search');
    const detailLibraryStatus = document.getElementById('detail-library-status');
    const detailLibraryList = document.getElementById('detail-library-list');
    const detailSelectedSummary = document.getElementById('detail-selected-summary');
    const detailSelectedList = document.getElementById('detail-selected-list');
    const detailUnresolvedNote = document.getElementById('detail-unresolved-note');
    const saveMembershipBtn = document.getElementById('save-membership');
    const membershipStatus = document.getElementById('membership-status');
    const saveStructuredPlanBtn = document.getElementById('save-structured-plan');
    const planLinkStatus = document.getElementById('plan-link-status');

    const scheduleEnabled = document.getElementById('schedule-enabled');
    const scheduleCron = document.getElementById('schedule-cron');
    const scheduleInterval = document.getElementById('schedule-interval');
    const scheduleTimezone = document.getElementById('schedule-timezone');
    const scheduleProfile = document.getElementById('schedule-profile');
    const cronRow = document.getElementById('cron-row');
    const intervalRow = document.getElementById('interval-row');
    const scheduleMeta = document.getElementById('schedule-meta');
    const saveScheduleBtn = document.getElementById('save-schedule');
    const clearScheduleBtn = document.getElementById('clear-schedule');
    const scheduleStatus = document.getElementById('schedule-status');

    const alertChannel = document.getElementById('alert-channel');
    const alertSeverity = document.getElementById('alert-severity');
    const saveAlertsBtn = document.getElementById('save-alerts');
    const clearAlertsBtn = document.getElementById('clear-alerts');
    const alertsStatus = document.getElementById('alerts-status');

    const monitorEnabled = document.getElementById('monitor-enabled');
    const monitorTarget = document.getElementById('monitor-target');
    const monitorFromJob = document.getElementById('monitor-from-job');
    const monitorMeta = document.getElementById('monitor-meta');
    const saveMonitorBtn = document.getElementById('save-monitor');
    const demoteMonitorBtn = document.getElementById('demote-monitor');
    const runSuiteBtn = document.getElementById('run-suite');
    const monitorStatus = document.getElementById('monitor-status');

    let projects = [];
    let suites = [];
    let channels = FALLBACK_CHANNELS.slice();
    let selectedSuiteId = null;
    let selectedSuite = null;
    let draftTags = [];
    let apiAvailable = false;
    let createInFlight = false;
    let libraryItems = [];
    let libraryLoadError = '';
    let libraryLoadedForProject = null;
    let createDraftTcIds = [];
    let detailDraftTcIds = [];
    let libraryFocusIndex = -1;

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function showApiWait(show) {
        if (apiWaitNote) apiWaitNote.hidden = !show;
    }

    function isStubStatus(status) {
        return status === 404 || status === 501;
    }

    async function readErrorMessage(res) {
        try {
            const text = await res.text();
            if (!text) return '';
            try {
                const body = JSON.parse(text);
                if (body && (body.message || body.error || body.detail)) {
                    return String(body.message || body.error || body.detail);
                }
            } catch (_) { /* plain text */ }
            return text.length > 300 ? (text.slice(0, 300) + '...') : text;
        } catch (_) {
            return '';
        }
    }

    function suiteIdOf(row) {
        if (!row) return null;
        return row.suiteId || row.id || null;
    }

    function normalizeList(data) {
        if (Array.isArray(data)) return data;
        if (!data || typeof data !== 'object') return [];
        if (Array.isArray(data.suites)) return data.suites;
        if (Array.isArray(data.items)) return data.items;
        if (Array.isArray(data.results)) return data.results;
        return [];
    }

    function normalizeChannels(data) {
        const rows = Array.isArray(data) ? data
            : (data && Array.isArray(data.channels) ? data.channels
                : (data && Array.isArray(data.items) ? data.items : []));
        return rows.map(function (c) {
            return {
                id: String(c.id || c.channelId || c.name || ''),
                enabled: c.enabled !== false,
                minSeverity: c.minSeverity || c.min_severity || null
            };
        }).filter(function (c) { return !!c.id; });
    }

    function parseCsv(raw) {
        return String(raw || '')
            .split(',')
            .map(function (s) { return s.trim(); })
            .filter(function (s) { return s.length > 0; });
    }

    function libraryBase(projectId) {
        return '/api/projects/' + encodeURIComponent(projectId) + '/test-case-library';
    }

    function titleForId(id) {
        const row = libraryItems.find(function (it) { return it.id === id; });
        return row && row.title ? String(row.title) : '';
    }

    function isExecutableInSuite(id) {
        const row = libraryItems.find(function (it) { return it.id === id; });
        if (row) return row.executableInSuite !== false;
        return false;
    }

    function unresolvedIds(ids) {
        return (ids || []).filter(function (id) {
            return !libraryItems.some(function (it) { return it.id === id && it.executableInSuite !== false; });
        });
    }

    function dedupeTcIds(ids) {
        const out = [];
        const seen = {};
        (ids || []).forEach(function (raw) {
            const id = String(raw || '').trim();
            if (!id || seen[id]) return;
            seen[id] = true;
            out.push(id);
        });
        return out;
    }

    function toggleIdInList(list, id) {
        const idx = list.indexOf(id);
        if (idx >= 0) list.splice(idx, 1);
        else list.push(id);
    }

    function moveIdInList(list, id, delta) {
        const idx = list.indexOf(id);
        if (idx < 0) return;
        const next = idx + delta;
        if (next < 0 || next >= list.length) return;
        const copy = list.slice();
        const tmp = copy[idx];
        copy[idx] = copy[next];
        copy[next] = tmp;
        return copy;
    }

    function filterLibraryItems(query) {
        const q = String(query || '').trim().toLowerCase();
        if (!q) return libraryItems.slice();
        return libraryItems.filter(function (it) {
            return String(it.id || '').toLowerCase().indexOf(q) >= 0
                || String(it.title || '').toLowerCase().indexOf(q) >= 0;
        });
    }

    function renderPickerList(container, selectedIds, searchInput, onToggle) {
        if (!container) return;
        const rows = filterLibraryItems(searchInput ? searchInput.value : '');
        if (libraryLoadError) {
            container.innerHTML = '<p class="muted">' + escapeHtml(libraryLoadError) + '</p>';
            return;
        }
        if (!rows.length) {
            container.innerHTML = '<p class="muted">' + (libraryItems.length ? 'No matches.' : 'No library cases yet.') + '</p>';
            return;
        }
        container.innerHTML = rows.map(function (it, index) {
            const checked = selectedIds.indexOf(it.id) >= 0;
            const plain = it.source === 'plain';
            const hint = plain ? ' <span class="muted tc-picker-plain-hint">(plain — run individually)</span>' : '';
            return '<div class="tc-picker-row" role="option" aria-selected="' + (checked ? 'true' : 'false') + '"'
                + ' data-lib-index="' + index + '" data-case-id="' + escapeHtml(it.id) + '">'
                + '<input type="checkbox" id="lib-chk-' + escapeHtml(it.id) + '" data-case-id="' + escapeHtml(it.id) + '"'
                + (checked ? ' checked' : '') + '/>'
                + '<label for="lib-chk-' + escapeHtml(it.id) + '"><strong>' + escapeHtml(it.id) + '</strong>'
                + (it.title ? ' — ' + escapeHtml(it.title) : '') + hint + '</label></div>';
        }).join('');
        Array.prototype.forEach.call(container.querySelectorAll('input[type="checkbox"][data-case-id]'), function (chk) {
            chk.addEventListener('change', function () {
                onToggle(chk.getAttribute('data-case-id'), chk.checked);
            });
        });
    }

    function renderSelectedList(container, summaryEl, selectedIds, onRemove, onMove) {
        if (summaryEl) {
            summaryEl.textContent = 'Selected: ' + selectedIds.length
                + (selectedIds.length === 1 ? ' case' : ' cases');
        }
        if (!container) return;
        if (!selectedIds.length) {
            container.innerHTML = '<li class="muted">None selected.</li>';
            return;
        }
        container.innerHTML = selectedIds.map(function (id, index) {
            const title = titleForId(id);
            const unresolved = !isExecutableInSuite(id);
            const warn = unresolved ? ' <span class="tc-picker-unresolved">(not in workbook — cannot run in pack)</span>' : '';
            return '<li><span><strong>' + escapeHtml(id) + '</strong>'
                + (title ? ' — ' + escapeHtml(title) : '') + warn + '</span>'
                + '<button type="button" class="button ghost" data-move-up="' + escapeHtml(id) + '"'
                + (index === 0 ? ' disabled' : '') + ' aria-label="Move up">↑</button>'
                + '<button type="button" class="button ghost" data-move-down="' + escapeHtml(id) + '"'
                + (index === selectedIds.length - 1 ? ' disabled' : '') + ' aria-label="Move down">↓</button>'
                + '<button type="button" class="button ghost" data-remove-id="' + escapeHtml(id) + '"'
                + ' aria-label="Remove">Remove</button></li>';
        }).join('');
        Array.prototype.forEach.call(container.querySelectorAll('[data-remove-id]'), function (btn) {
            btn.addEventListener('click', function () { onRemove(btn.getAttribute('data-remove-id')); });
        });
        Array.prototype.forEach.call(container.querySelectorAll('[data-move-up]'), function (btn) {
            btn.addEventListener('click', function () { onMove(btn.getAttribute('data-move-up'), -1); });
        });
        Array.prototype.forEach.call(container.querySelectorAll('[data-move-down]'), function (btn) {
            btn.addEventListener('click', function () { onMove(btn.getAttribute('data-move-down'), 1); });
        });
    }

    function refreshPickerViews() {
        renderPickerList(createLibraryList, createDraftTcIds, createLibrarySearch, function (id, on) {
            if (on && createDraftTcIds.indexOf(id) < 0) createDraftTcIds.push(id);
            else if (!on) createDraftTcIds = createDraftTcIds.filter(function (x) { return x !== id; });
            createDraftTcIds = dedupeTcIds(createDraftTcIds);
            refreshPickerViews();
        });
        renderPickerList(detailLibraryList, detailDraftTcIds, detailLibrarySearch, function (id, on) {
            if (on && detailDraftTcIds.indexOf(id) < 0) detailDraftTcIds.push(id);
            else if (!on) detailDraftTcIds = detailDraftTcIds.filter(function (x) { return x !== id; });
            detailDraftTcIds = dedupeTcIds(detailDraftTcIds);
            refreshPickerViews();
            updateRunSuiteEnabled();
        });
        renderSelectedList(createSelectedList, createSelectedSummary, createDraftTcIds,
            function (id) {
                createDraftTcIds = createDraftTcIds.filter(function (x) { return x !== id; });
                refreshPickerViews();
            },
            function (id, delta) {
                const moved = moveIdInList(createDraftTcIds, id, delta);
                if (moved) createDraftTcIds = moved;
                refreshPickerViews();
            });
        renderSelectedList(detailSelectedList, detailSelectedSummary, detailDraftTcIds,
            function (id) {
                detailDraftTcIds = detailDraftTcIds.filter(function (x) { return x !== id; });
                refreshPickerViews();
                updateRunSuiteEnabled();
            },
            function (id, delta) {
                const moved = moveIdInList(detailDraftTcIds, id, delta);
                if (moved) detailDraftTcIds = moved;
                refreshPickerViews();
            });
        updateRunSuiteEnabled();
    }

    function updateRunSuiteEnabled() {
        if (!runSuiteBtn) return;
        const bad = unresolvedIds(detailDraftTcIds);
        const disabled = !detailDraftTcIds.length || bad.length > 0;
        runSuiteBtn.disabled = disabled;
        runSuiteBtn.title = disabled
            ? (!detailDraftTcIds.length
                ? 'Add at least one workbook test case and save.'
                : 'Resolve or remove cases that are not in the workbook library.')
            : '';
        if (detailUnresolvedNote) {
            if (bad.length) {
                detailUnresolvedNote.hidden = false;
                detailUnresolvedNote.textContent = 'Cannot run: ' + bad.join(', ')
                    + ' missing from workbook or plain-only. Save after fixing.';
            } else {
                detailUnresolvedNote.hidden = true;
                detailUnresolvedNote.textContent = '';
            }
        }
    }

    async function loadTestCaseLibrary(projectId) {
        if (!projectId) return;
        libraryLoadError = '';
        if (createLibraryStatus) createLibraryStatus.textContent = 'Loading library…';
        if (detailLibraryStatus) detailLibraryStatus.textContent = 'Loading library…';
        try {
            const res = await fetch(libraryBase(projectId) + '?limit=500', {
                credentials: 'same-origin',
                headers: API_HEADERS
            });
            if (isStubStatus(res.status)) {
                libraryItems = [];
                libraryLoadError = 'Library API not available yet.';
                libraryLoadedForProject = projectId;
                refreshPickerViews();
                if (createLibraryStatus) createLibraryStatus.textContent = libraryLoadError;
                if (detailLibraryStatus) detailLibraryStatus.textContent = libraryLoadError;
                return;
            }
            if (res.status === 503) {
                libraryLoadError = 'Library temporarily unavailable — retry.';
                libraryItems = [];
                refreshPickerViews();
                if (createLibraryStatus) createLibraryStatus.textContent = libraryLoadError;
                if (detailLibraryStatus) detailLibraryStatus.textContent = libraryLoadError;
                return;
            }
            if (!res.ok) {
                libraryLoadError = 'Could not load library (HTTP ' + res.status + ').';
                libraryItems = [];
                refreshPickerViews();
                return;
            }
            const data = await res.json().catch(function () { return { items: [] }; });
            libraryItems = Array.isArray(data.items) ? data.items : [];
            libraryLoadedForProject = projectId;
            libraryLoadError = '';
            if (createLibraryStatus) createLibraryStatus.textContent = libraryItems.length
                ? (libraryItems.length + ' case(s) in library') : 'No cases in library yet.';
            if (detailLibraryStatus) detailLibraryStatus.textContent = createLibraryStatus
                ? createLibraryStatus.textContent : '';
            refreshPickerViews();
        } catch (_) {
            libraryLoadError = 'Could not load library.';
            libraryItems = [];
            refreshPickerViews();
            if (createLibraryStatus) createLibraryStatus.textContent = libraryLoadError;
            if (detailLibraryStatus) detailLibraryStatus.textContent = libraryLoadError;
        }
    }

    function isBuiltinTag(tag) {
        const t = String(tag || '').toLowerCase();
        return BUILTIN_TAGS.indexOf(t) >= 0;
    }

    function showWorkspaceLauncher() {
        if (workspaceLauncher) workspaceLauncher.hidden = false;
        if (workspaceConfig) workspaceConfig.hidden = true;
        if (suiteDetail) suiteDetail.hidden = true;
        updateCreateEnabled();
    }

    function pinWorkspaceProject(projectId, projectList) {
        projectSel.value = projectId;
        if (launcherSel) launcherSel.value = projectId;
        const proj = (projectList || []).find(function (p) { return p.projectId === projectId; });
        if (pinnedName) {
            if (proj) {
                pinnedName.textContent = (proj.name || proj.projectId) + ' (' + proj.projectId + ')';
            } else if (launcherSel && launcherSel.selectedIndex >= 0) {
                pinnedName.textContent = launcherSel.options[launcherSel.selectedIndex].textContent;
            } else {
                pinnedName.textContent = projectId;
            }
        }
        if (workspaceLauncher) workspaceLauncher.hidden = true;
        if (workspaceConfig) workspaceConfig.hidden = false;
        sessionStorage.setItem(STORAGE_KEY, projectId);
        selectedSuiteId = sessionStorage.getItem(LAST_SUITE_KEY + '.' + projectId) || null;
        selectedSuite = null;
        draftTags = [];
        apiAvailable = false;
        showApiWait(false);
        if (createStatus) createStatus.textContent = '';
        clearDetailStatuses();
        loadChannels();
        loadSuites(projectId);
        if (libraryLoadedForProject !== projectId) {
            loadTestCaseLibrary(projectId);
        } else {
            refreshPickerViews();
        }
        updateCreateEnabled();
    }

    function clearDetailStatuses() {
        if (tagsStatus) tagsStatus.textContent = '';
        if (scheduleStatus) scheduleStatus.textContent = '';
        if (alertsStatus) alertsStatus.textContent = '';
        if (monitorStatus) monitorStatus.textContent = '';
    }

    function updateCreateEnabled() {
        const projectId = projectSel && projectSel.value;
        const name = (createName && createName.value || '').trim();
        const tags = parseCsv(createTags && createTags.value);
        createBtn.disabled = !!(createInFlight || !projectId || !name || tags.length < 1);
    }

    function suitesBase(projectId) {
        return '/api/projects/' + encodeURIComponent(projectId) + '/suites';
    }

    function renderChannelSelect(selectedId) {
        const prev = selectedId != null ? selectedId : (alertChannel.value || '');
        alertChannel.innerHTML = '<option value="">- None -</option>';
        channels.forEach(function (c) {
            const opt = document.createElement('option');
            opt.value = c.id;
            opt.textContent = c.id + (c.enabled === false ? ' (disabled)' : '');
            alertChannel.appendChild(opt);
        });
        if (prev && Array.prototype.some.call(alertChannel.options, function (o) { return o.value === prev; })) {
            alertChannel.value = prev;
        }
    }

    async function loadChannels() {
        try {
            const res = await fetch('/api/alert-channels', {
                credentials: 'same-origin',
                headers: API_HEADERS
            });
            if (isStubStatus(res.status) || !res.ok) {
                channels = FALLBACK_CHANNELS.slice();
                renderChannelSelect();
                return;
            }
            const data = await res.json().catch(function () { return []; });
            const normalized = normalizeChannels(data);
            channels = normalized.length ? normalized : FALLBACK_CHANNELS.slice();
            renderChannelSelect();
        } catch (_) {
            channels = FALLBACK_CHANNELS.slice();
            renderChannelSelect();
        }
    }

    function renderSuitesList() {
        if (!suites.length) {
            suitesEmpty.hidden = false;
            suitesEmpty.textContent = apiAvailable ? 'No test packs yet.' : 'No test packs yet.';
            suitesList.hidden = true;
            suitesList.innerHTML = '';
            if (suitesMeta) {
                suitesMeta.textContent = apiAvailable ? '' : 'Waiting on Backend test-pack / schedule APIs.';
            }
            if (suiteDetail) suiteDetail.hidden = true;
            return;
        }
        suitesEmpty.hidden = true;
        suitesList.hidden = false;
        suitesList.innerHTML = suites.map(function (row) {
            const id = suiteIdOf(row) || '';
            const name = (row.name || '').trim() || '(unnamed)';
            const tags = Array.isArray(row.tags) ? row.tags : [];
            const builtIn = !!row.builtIn;
            const active = id && id === selectedSuiteId;
            const tagBits = tags.map(function (t) { return escapeHtml(t); }).join(', ');
            return '<li class="' + (active ? 'active-suite' : '') + '">'
                + '<button type="button" class="button ghost suite-pick" data-suite-id="' + escapeHtml(id) + '"'
                + ' style="padding:0.15rem 0.4rem">'
                + escapeHtml(name) + '</button>'
                + ' <span class="muted">' + escapeHtml(id)
                + (builtIn ? ' | built-in' : '')
                + (tagBits ? ' | ' + tagBits : '')
                + '</span>'
                + '</li>';
        }).join('');
        if (suitesMeta) {
            suitesMeta.textContent = suites.length + (suites.length === 1 ? ' test pack' : ' test packs')
                + (selectedSuiteId ? ' | selected ' + selectedSuiteId : '');
        }
        Array.prototype.forEach.call(suitesList.querySelectorAll('[data-suite-id]'), function (btn) {
            btn.addEventListener('click', function () {
                selectSuite(btn.getAttribute('data-suite-id'));
            });
        });
    }

    function syncScheduleKindUi() {
        const kind = (document.querySelector('input[name="schedule-kind"]:checked') || {}).value || 'cron';
        if (cronRow) cronRow.hidden = kind !== 'cron';
        if (intervalRow) intervalRow.hidden = kind !== 'interval';
    }

    function setScheduleKind(kind) {
        const want = kind === 'interval' ? 'interval' : 'cron';
        Array.prototype.forEach.call(document.querySelectorAll('input[name="schedule-kind"]'), function (el) {
            el.checked = el.value === want;
        });
        syncScheduleKindUi();
    }

    function renderTagChips() {
        const set = {};
        draftTags.forEach(function (t) { set[String(t).toLowerCase()] = String(t); });

        builtinChips.innerHTML = BUILTIN_TAGS.map(function (tag) {
            const on = !!set[tag];
            return '<button type="button" class="suite-chip' + (on ? ' active' : '') + '" data-builtin-tag="'
                + escapeHtml(tag) + '" aria-pressed="' + (on ? 'true' : 'false') + '">'
                + escapeHtml(tag) + '</button>';
        }).join('');

        const customs = draftTags.filter(function (t) { return !isBuiltinTag(t); });
        if (!customs.length) {
            customChips.innerHTML = '<span class="muted">No custom tags.</span>';
        } else {
            customChips.innerHTML = customs.map(function (tag) {
                return '<span class="suite-chip active">' + escapeHtml(tag)
                    + ' <button type="button" class="chip-x" data-remove-tag="' + escapeHtml(tag)
                    + '" aria-label="Remove ' + escapeHtml(tag) + '">&times;</button></span>';
            }).join('');
        }

        Array.prototype.forEach.call(builtinChips.querySelectorAll('[data-builtin-tag]'), function (btn) {
            btn.addEventListener('click', function () {
                const tag = btn.getAttribute('data-builtin-tag');
                toggleDraftTag(tag);
            });
        });
        Array.prototype.forEach.call(customChips.querySelectorAll('[data-remove-tag]'), function (btn) {
            btn.addEventListener('click', function () {
                removeDraftTag(btn.getAttribute('data-remove-tag'));
            });
        });
    }

    function toggleDraftTag(tag) {
        const lower = String(tag || '').toLowerCase();
        const idx = draftTags.findIndex(function (t) { return String(t).toLowerCase() === lower; });
        if (idx >= 0) draftTags.splice(idx, 1);
        else draftTags.push(tag);
        renderTagChips();
    }

    function removeDraftTag(tag) {
        const lower = String(tag || '').toLowerCase();
        draftTags = draftTags.filter(function (t) { return String(t).toLowerCase() !== lower; });
        renderTagChips();
    }

    function addCustomTag() {
        const raw = (customTagInput.value || '').trim();
        if (!raw) return;
        if (isBuiltinTag(raw)) {
            tagsStatus.textContent = 'Reserved built-in tag — toggle the chip instead.';
            return;
        }
        const lower = raw.toLowerCase();
        if (draftTags.some(function (t) { return String(t).toLowerCase() === lower; })) {
            tagsStatus.textContent = 'Already added.';
            return;
        }
        draftTags.push(raw);
        customTagInput.value = '';
        tagsStatus.textContent = '';
        renderTagChips();
    }

    function fillDetailFromSuite(suite) {
        selectedSuite = suite || null;
        if (!suite) {
            suiteDetail.hidden = true;
            return;
        }
        suiteDetail.hidden = false;
        draftTags = Array.isArray(suite.tags) ? suite.tags.slice() : [];
        renderTagChips();
        detailDraftTcIds = Array.isArray(suite.tcIds) ? suite.tcIds.slice() : [];
        if (membershipStatus) membershipStatus.textContent = '';
        refreshPickerViews();

        const sched = suite.schedule || null;
        scheduleEnabled.checked = !!(sched && sched.enabled);
        setScheduleKind(sched && sched.kind === 'interval' ? 'interval' : 'cron');
        scheduleCron.value = (sched && sched.cron) || '';
        scheduleInterval.value = (sched && sched.intervalMinutes != null) ? sched.intervalMinutes : '';
        if (scheduleTimezone) scheduleTimezone.value = (sched && sched.timezone) || '';
        if (scheduleProfile) scheduleProfile.value = (sched && sched.credentialProfile) || '';
        const when = (iso) => { try { return iso ? new Date(iso).toLocaleString() : ''; } catch (e) { return iso; } };
        const metaBits = [];
        if (sched && sched.enabled && sched.nextRunAt) metaBits.push('Next run: ' + when(sched.nextRunAt));
        if (sched && sched.lastFiredAt) metaBits.push('Last run: ' + when(sched.lastFiredAt)
            + (sched.lastJobStatus ? ' — ' + sched.lastJobStatus : '')
            + (sched.lastResult ? ' (' + sched.lastResult.passed + ' passed, ' + sched.lastResult.failed + ' failed)' : '')
            + (sched.lastJobId ? ' [' + sched.lastJobId + ']' : ''));
        if (sched && sched.lastFailure && sched.lastFailure.message) {
            metaBits.push('Problem: ' + (sched.lastFailure.code ? sched.lastFailure.code + ' — ' : '')
                + sched.lastFailure.message + (sched.lastFailure.at ? ' (' + when(sched.lastFailure.at) + ')' : ''));
        }
        const schedMonitor = suite.monitor || null;
        if (schedMonitor && schedMonitor.enabled) metaBits.push('Monitor (' + schedMonitor.target + '): '
            + (schedMonitor.lastStatus || 'unknown')
            + (schedMonitor.lastCheckedAt ? ' at ' + when(schedMonitor.lastCheckedAt) : ''));
        scheduleMeta.textContent = metaBits.join(' · ');

        renderChannelSelect(suite.alertChannelId || '');
        alertChannel.value = suite.alertChannelId || '';
        alertSeverity.value = suite.alertMinSeverity || '';

        const mon = suite.monitor || null;
        monitorEnabled.checked = !!(mon && mon.enabled);
        monitorTarget.value = (mon && mon.target === 'staging') ? 'staging' : 'prod';
        monitorFromJob.value = (mon && mon.promotedFromJobId) || '';
        const monBits = [];
        if (mon && mon.promotedAt) monBits.push('promotedAt ' + mon.promotedAt);
        if (mon && mon.lastStatus) monBits.push('lastStatus ' + mon.lastStatus);
        if (mon && mon.lastCheckedAt) monBits.push('lastCheckedAt ' + mon.lastCheckedAt);
        if (mon && mon.target) monBits.push('target ' + mon.target);
        monitorMeta.textContent = monBits.join(' | ');
        const planInput = document.getElementById('detail-structured-plan-id');
        if (planInput) planInput.value = suite.structuredPlanId || '';
        clearDetailStatuses();
    }

    async function selectSuite(suiteId) {
        if (!suiteId) return;
        const projectId = projectSel.value;
        selectedSuiteId = suiteId;
        if (projectId) sessionStorage.setItem(LAST_SUITE_KEY + '.' + projectId, suiteId);
        renderSuitesList();

        const cached = suites.find(function (s) { return suiteIdOf(s) === suiteId; });
        fillDetailFromSuite(cached || { suiteId: suiteId, tags: [] });

        if (!projectId) return;
        try {
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId), {
                credentials: 'same-origin',
                headers: API_HEADERS
            });
            if (isStubStatus(res.status)) {
                apiAvailable = false;
                showApiWait(true);
                return;
            }
            if (!res.ok) return;
            apiAvailable = true;
            showApiWait(false);
            const data = await res.json().catch(function () { return null; });
            if (data) {
                const idx = suites.findIndex(function (s) { return suiteIdOf(s) === suiteId; });
                if (idx >= 0) suites[idx] = data;
                else suites.push(data);
                fillDetailFromSuite(data);
                renderSuitesList();
            }
        } catch (_) {
            apiAvailable = false;
            showApiWait(true);
        }
    }

    async function loadSuites(projectId) {
        if (!projectId) return;
        suites = [];
        renderSuitesList();
        try {
            const res = await fetch(suitesBase(projectId), {
                credentials: 'same-origin',
                headers: API_HEADERS
            });
            if (isStubStatus(res.status)) {
                apiAvailable = false;
                showApiWait(true);
                if (suitesMeta) suitesMeta.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                return;
            }
            if (!res.ok) {
                apiAvailable = false;
                showApiWait(true);
                if (suitesMeta) suitesMeta.textContent = 'Could not load test packs (HTTP ' + res.status + ').';
                return;
            }
            apiAvailable = true;
            showApiWait(false);
            const data = await res.json().catch(function () { return []; });
            suites = normalizeList(data);
            if (selectedSuiteId && !suites.some(function (s) { return suiteIdOf(s) === selectedSuiteId; })) {
                selectedSuiteId = suites.length ? suiteIdOf(suites[0]) : null;
            } else if (!selectedSuiteId && suites.length) {
                selectedSuiteId = suiteIdOf(suites[0]);
            }
            renderSuitesList();
            if (selectedSuiteId) {
                await selectSuite(selectedSuiteId);
            } else if (suiteDetail) {
                suiteDetail.hidden = true;
            }
        } catch (_) {
            apiAvailable = false;
            showApiWait(true);
            if (suitesMeta) suitesMeta.textContent = 'Waiting on Backend test-pack / schedule APIs.';
        }
    }

    async function createSuite() {
        const projectId = projectSel.value;
        const name = (createName.value || '').trim();
        const tags = parseCsv(createTags.value);
        const tcIds = dedupeTcIds(createDraftTcIds);
        if (!projectId || !name || !tags.length) {
            createStatus.textContent = 'Name and at least one tag required.';
            return;
        }
        createInFlight = true;
        updateCreateEnabled();
        createStatus.textContent = 'Creating...';
        showApiWait(false);
        try {
            const res = await fetch(suitesBase(projectId), {
                method: 'POST',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: JSON.stringify({ name: name, tags: tags, tcIds: tcIds })
            });
            if (isStubStatus(res.status)) {
                apiAvailable = false;
                createStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                showApiWait(true);
                return;
            }
            if (!res.ok) {
                const msg = await readErrorMessage(res);
                createStatus.textContent = msg || ('Create failed (HTTP ' + res.status + ')');
                return;
            }
            apiAvailable = true;
            showApiWait(false);
            let data = null;
            try { data = await res.json(); } catch (_) { data = null; }
            const id = suiteIdOf(data);
            createStatus.textContent = id ? ('Created (' + id + ').') : 'Created.';
            createName.value = '';
            createTags.value = '';
            createDraftTcIds = [];
            if (createLibrarySearch) createLibrarySearch.value = '';
            if (id) selectedSuiteId = id;
            await loadSuites(projectId);
        } catch (_) {
            createStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
            showApiWait(true);
        } finally {
            createInFlight = false;
            updateCreateEnabled();
        }
    }

    async function saveTags() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        if (!projectId || !suiteId) {
            tagsStatus.textContent = 'Select a test pack first.';
            return;
        }
        if (!draftTags.length) {
            tagsStatus.textContent = 'At least one tag required.';
            return;
        }
        tagsStatus.textContent = 'Saving tags...';
        try {
            const body = { tags: draftTags.slice() };
            if (selectedSuite && selectedSuite.name) body.name = selectedSuite.name;
            body.tcIds = dedupeTcIds(detailDraftTcIds);
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId), {
                method: 'PUT',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: JSON.stringify(body)
            });
            if (isStubStatus(res.status)) {
                tagsStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                showApiWait(true);
                return;
            }
            if (!res.ok) {
                const msg = await readErrorMessage(res);
                tagsStatus.textContent = msg || ('Save failed (HTTP ' + res.status + ')');
                return;
            }
            tagsStatus.textContent = 'Tags saved.';
            showApiWait(false);
            await loadSuites(projectId);
        } catch (_) {
            tagsStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
            showApiWait(true);
        }
    }

    async function saveStructuredPlan() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        const planInput = document.getElementById('detail-structured-plan-id');
        if (!projectId || !suiteId || !planInput) {
            if (planLinkStatus) planLinkStatus.textContent = 'Select a test pack first.';
            return;
        }
        if (planLinkStatus) planLinkStatus.textContent = 'Saving…';
        try {
            const body = {
                structuredPlanId: (planInput.value || '').trim(),
                tcIds: dedupeTcIds(detailDraftTcIds),
                tags: draftTags.slice()
            };
            if (selectedSuite && selectedSuite.name) body.name = selectedSuite.name;
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId), {
                method: 'PUT',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: JSON.stringify(body)
            });
            if (!res.ok) {
                if (planLinkStatus) planLinkStatus.textContent = 'Save failed (HTTP ' + res.status + ')';
                return;
            }
            if (planLinkStatus) planLinkStatus.textContent = 'Plan link saved.';
            await loadSuites(projectId);
        } catch (_) {
            if (planLinkStatus) planLinkStatus.textContent = 'Save failed.';
        }
    }

    async function saveMembership() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        if (!projectId || !suiteId) {
            if (membershipStatus) membershipStatus.textContent = 'Select a test pack first.';
            return;
        }
        if (membershipStatus) membershipStatus.textContent = 'Saving test cases…';
        try {
            const body = { tcIds: dedupeTcIds(detailDraftTcIds) };
            if (selectedSuite && selectedSuite.name) body.name = selectedSuite.name;
            if (draftTags.length) body.tags = draftTags.slice();
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId), {
                method: 'PUT',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: JSON.stringify(body)
            });
            if (isStubStatus(res.status)) {
                if (membershipStatus) membershipStatus.textContent = 'Waiting on Backend test-pack APIs.';
                showApiWait(true);
                return;
            }
            if (!res.ok) {
                const msg = await readErrorMessage(res);
                if (membershipStatus) membershipStatus.textContent = msg || ('Save failed (HTTP ' + res.status + ')');
                return;
            }
            if (membershipStatus) membershipStatus.textContent = 'Test cases saved.';
            showApiWait(false);
            await selectSuite(suiteId);
        } catch (_) {
            if (membershipStatus) membershipStatus.textContent = 'Could not save test cases.';
            showApiWait(true);
        }
    }

    async function saveSchedule() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        if (!projectId || !suiteId) {
            scheduleStatus.textContent = 'Select a test pack first.';
            return;
        }
        const kind = (document.querySelector('input[name="schedule-kind"]:checked') || {}).value || 'cron';
        const body = {
            enabled: !!scheduleEnabled.checked,
            kind: kind
        };
        if (scheduleProfile && scheduleProfile.value.trim()) body.credentialProfile = scheduleProfile.value.trim();
        if (kind === 'cron') {
            body.cron = (scheduleCron.value || '').trim() || null;
            if (scheduleTimezone && scheduleTimezone.value.trim()) body.timezone = scheduleTimezone.value.trim();
        } else {
            const n = parseInt(scheduleInterval.value, 10);
            body.intervalMinutes = isNaN(n) ? null : n;
        }
        scheduleStatus.textContent = 'Saving schedule...';
        try {
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId) + '/schedule', {
                method: 'PUT',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: JSON.stringify(body)
            });
            if (isStubStatus(res.status)) {
                scheduleStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                showApiWait(true);
                return;
            }
            if (!res.ok) {
                const msg = await readErrorMessage(res);
                scheduleStatus.textContent = msg || ('Save failed (HTTP ' + res.status + ')');
                return;
            }
            scheduleStatus.textContent = 'Schedule saved.';
            showApiWait(false);
            await selectSuite(suiteId);
        } catch (_) {
            scheduleStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
            showApiWait(true);
        }
    }

    async function clearSchedule() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        if (!projectId || !suiteId) {
            scheduleStatus.textContent = 'Select a test pack first.';
            return;
        }
        scheduleStatus.textContent = 'Clearing schedule...';
        try {
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId) + '/schedule', {
                method: 'DELETE',
                credentials: 'same-origin',
                headers: API_HEADERS
            });
            if (isStubStatus(res.status)) {
                scheduleStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                showApiWait(true);
                return;
            }
            if (!res.ok) {
                const msg = await readErrorMessage(res);
                scheduleStatus.textContent = msg || ('Clear failed (HTTP ' + res.status + ')');
                return;
            }
            scheduleStatus.textContent = 'Schedule cleared.';
            showApiWait(false);
            await selectSuite(suiteId);
        } catch (_) {
            scheduleStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
            showApiWait(true);
        }
    }

    async function saveAlerts() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        if (!projectId || !suiteId) {
            alertsStatus.textContent = 'Select a test pack first.';
            return;
        }
        const channelId = (alertChannel.value || '').trim();
        if (!channelId) {
            alertsStatus.textContent = 'Pick a channel, or use Clear channel link.';
            return;
        }
        const body = { alertChannelId: channelId };
        const sev = (alertSeverity.value || '').trim();
        if (sev) body.alertMinSeverity = sev;
        alertsStatus.textContent = 'Saving alerts...';
        try {
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId) + '/alerts', {
                method: 'PUT',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: JSON.stringify(body)
            });
            if (isStubStatus(res.status)) {
                alertsStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                showApiWait(true);
                return;
            }
            if (!res.ok) {
                const msg = await readErrorMessage(res);
                alertsStatus.textContent = msg || ('Save failed (HTTP ' + res.status + ')');
                return;
            }
            alertsStatus.textContent = 'Alerts saved.';
            showApiWait(false);
            await selectSuite(suiteId);
        } catch (_) {
            alertsStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
            showApiWait(true);
        }
    }

    async function clearAlerts() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        if (!projectId || !suiteId) {
            alertsStatus.textContent = 'Select a test pack first.';
            return;
        }
        alertsStatus.textContent = 'Clearing channel link...';
        try {
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId) + '/alerts', {
                method: 'DELETE',
                credentials: 'same-origin',
                headers: API_HEADERS
            });
            if (isStubStatus(res.status)) {
                alertsStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                showApiWait(true);
                return;
            }
            if (!res.ok) {
                const msg = await readErrorMessage(res);
                alertsStatus.textContent = msg || ('Clear failed (HTTP ' + res.status + ')');
                return;
            }
            alertsStatus.textContent = 'Channel link cleared.';
            showApiWait(false);
            await selectSuite(suiteId);
        } catch (_) {
            alertsStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
            showApiWait(true);
        }
    }

    async function saveMonitor() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        if (!projectId || !suiteId) {
            monitorStatus.textContent = 'Select a test pack first.';
            return;
        }
        if (!monitorEnabled.checked) {
            monitorStatus.textContent = 'Turn on Always-on monitor, or use Demote.';
            return;
        }
        const body = { target: monitorTarget.value === 'staging' ? 'staging' : 'prod' };
        const fromJob = (monitorFromJob.value || '').trim();
        if (fromJob) body.fromJobId = fromJob;
        monitorStatus.textContent = 'Promoting...';
        try {
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId) + '/promote-monitor', {
                method: 'POST',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: JSON.stringify(body)
            });
            if (isStubStatus(res.status)) {
                monitorStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                showApiWait(true);
                return;
            }
            if (!res.ok) {
                const msg = await readErrorMessage(res);
                monitorStatus.textContent = msg || ('Promote failed (HTTP ' + res.status + ')');
                return;
            }
            monitorStatus.textContent = 'Promoted to always-on monitor.';
            showApiWait(false);
            await selectSuite(suiteId);
        } catch (_) {
            monitorStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
            showApiWait(true);
        }
    }

    async function demoteMonitor() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        if (!projectId || !suiteId) {
            monitorStatus.textContent = 'Select a test pack first.';
            return;
        }
        monitorStatus.textContent = 'Removing from monitor...';
        try {
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId) + '/monitor', {
                method: 'DELETE',
                credentials: 'same-origin',
                headers: API_HEADERS
            });
            if (isStubStatus(res.status)) {
                monitorStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                showApiWait(true);
                return;
            }
            if (!res.ok) {
                const msg = await readErrorMessage(res);
                monitorStatus.textContent = msg || ('Demote failed (HTTP ' + res.status + ')');
                return;
            }
            monitorStatus.textContent = 'Removed from always-on monitor.';
            monitorEnabled.checked = false;
            showApiWait(false);
            await selectSuite(suiteId);
        } catch (_) {
            monitorStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
            showApiWait(true);
        }
    }

    async function runSuiteNow() {
        const projectId = projectSel.value;
        const suiteId = selectedSuiteId;
        if (!projectId || !suiteId) {
            monitorStatus.textContent = 'Select a test pack first.';
            return;
        }
        if (!detailDraftTcIds.length || unresolvedIds(detailDraftTcIds).length) {
            monitorStatus.textContent = 'Add workbook test cases and save before running.';
            return;
        }
        monitorStatus.textContent = 'Starting test pack run...';
        try {
            const res = await fetch(suitesBase(projectId) + '/' + encodeURIComponent(suiteId) + '/runs', {
                method: 'POST',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: JSON.stringify({ trigger: 'manual' })
            });
            if (isStubStatus(res.status)) {
                monitorStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
                showApiWait(true);
                return;
            }
            if (res.status !== 202 && !res.ok) {
                const msg = await readErrorMessage(res);
                monitorStatus.textContent = msg || ('Run failed (HTTP ' + res.status + ')');
                return;
            }
            let data = null;
            try { data = await res.json(); } catch (_) { data = null; }
            const jobId = data && (data.jobId || data.id);
            if (jobId) {
                monitorStatus.innerHTML = 'Queued job <code>' + escapeHtml(jobId) + '</code>. '
                    + '<a href="/status?jobId=' + encodeURIComponent(jobId) + '">View status</a>';
            } else {
                monitorStatus.textContent = 'Run accepted (HTTP ' + res.status + ').';
            }
            showApiWait(false);
        } catch (_) {
            monitorStatus.textContent = 'Waiting on Backend test-pack / schedule APIs.';
            showApiWait(true);
        }
    }

    async function loadProjects() {
        if (projectErr) projectErr.hidden = true;
        const res = await fetch('/api/projects', {
            credentials: 'same-origin',
            headers: API_HEADERS
        });
        if (!res.ok) {
            if (projectErr) {
                projectErr.hidden = false;
                projectErr.textContent = 'Could not load projects.';
            }
            return;
        }
        projects = await res.json();
        projectSel.innerHTML = '';
        if (launcherSel) launcherSel.innerHTML = '';
        if (!projects.length) {
            if (noProjects) noProjects.hidden = false;
            if (launcherNoProjects) launcherNoProjects.hidden = false;
            showWorkspaceLauncher();
            return;
        }
        if (noProjects) noProjects.hidden = true;
        if (launcherNoProjects) launcherNoProjects.hidden = true;
        projects.forEach(function (p) {
            const label = (p.name || p.projectId) + ' (' + p.projectId + ')';
            const opt = document.createElement('option');
            opt.value = p.projectId;
            opt.textContent = label;
            projectSel.appendChild(opt);
            if (launcherSel) {
                const lopt = document.createElement('option');
                lopt.value = p.projectId;
                lopt.textContent = label;
                launcherSel.appendChild(lopt);
            }
        });
        const urlParams = new URLSearchParams(window.location.search);
        const urlProjectId = urlParams.get('projectId');
        const urlProject = (urlProjectId && projects.some(function (p) { return p.projectId === urlProjectId; }))
            ? urlProjectId
            : ((initialProjectId && projects.some(function (p) { return p.projectId === initialProjectId; }))
                ? initialProjectId : null);
        const saved = sessionStorage.getItem(STORAGE_KEY);
        const match = saved && projects.some(function (p) { return p.projectId === saved; });
        const pinned = urlProject || (match ? saved : null);
        if (pinned) {
            pinWorkspaceProject(pinned, projects);
        } else {
            showWorkspaceLauncher();
        }
    }

    launcherContinue && launcherContinue.addEventListener('click', function () {
        if (launcherErr) launcherErr.hidden = true;
        const projectId = launcherSel ? launcherSel.value : '';
        if (!projectId) {
            if (launcherErr) {
                launcherErr.hidden = false;
                launcherErr.textContent = 'Select a project to continue.';
            }
            return;
        }
        pinWorkspaceProject(projectId, projects);
    });

    changeProjectBtn && changeProjectBtn.addEventListener('click', function () {
        showWorkspaceLauncher();
    });

    createName && createName.addEventListener('input', updateCreateEnabled);
    createTags && createTags.addEventListener('input', updateCreateEnabled);
    createBtn && createBtn.addEventListener('click', createSuite);
    refreshBtn && refreshBtn.addEventListener('click', function () {
        const projectId = projectSel && projectSel.value;
        if (projectId) loadSuites(projectId);
    });
    addCustomTagBtn && addCustomTagBtn.addEventListener('click', addCustomTag);
    customTagInput && customTagInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            addCustomTag();
        }
    });
    saveTagsBtn && saveTagsBtn.addEventListener('click', saveTags);
    saveMembershipBtn && saveMembershipBtn.addEventListener('click', saveMembership);
    saveStructuredPlanBtn && saveStructuredPlanBtn.addEventListener('click', saveStructuredPlan);
    createLibrarySearch && createLibrarySearch.addEventListener('input', function () { refreshPickerViews(); });
    detailLibrarySearch && detailLibrarySearch.addEventListener('input', function () { refreshPickerViews(); });
    detailLibrarySearch && detailLibrarySearch.addEventListener('keydown', function (e) {
        const rows = filterLibraryItems(detailLibrarySearch.value);
        if (!rows.length) return;
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            libraryFocusIndex = Math.min(libraryFocusIndex + 1, rows.length - 1);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            libraryFocusIndex = Math.max(libraryFocusIndex - 1, 0);
        } else if (e.key === ' ' && libraryFocusIndex >= 0) {
            e.preventDefault();
            const id = rows[libraryFocusIndex].id;
            toggleIdInList(detailDraftTcIds, id);
            detailDraftTcIds = dedupeTcIds(detailDraftTcIds);
            refreshPickerViews();
            updateRunSuiteEnabled();
        }
    });
    Array.prototype.forEach.call(document.querySelectorAll('input[name="schedule-kind"]'), function (el) {
        el.addEventListener('change', syncScheduleKindUi);
    });
    saveScheduleBtn && saveScheduleBtn.addEventListener('click', saveSchedule);
    clearScheduleBtn && clearScheduleBtn.addEventListener('click', clearSchedule);
    saveAlertsBtn && saveAlertsBtn.addEventListener('click', saveAlerts);
    clearAlertsBtn && clearAlertsBtn.addEventListener('click', clearAlerts);
    saveMonitorBtn && saveMonitorBtn.addEventListener('click', saveMonitor);
    demoteMonitorBtn && demoteMonitorBtn.addEventListener('click', demoteMonitor);
    runSuiteBtn && runSuiteBtn.addEventListener('click', runSuiteNow);

    syncScheduleKindUi();
    renderChannelSelect();
    refreshPickerViews();
    loadProjects().catch(function () {
        if (projectErr) {
            projectErr.hidden = false;
            projectErr.textContent = 'Could not load projects.';
        }
        showWorkspaceLauncher();
    });
})();

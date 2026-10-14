/* M3-06 Final auth profiles + case requirements — /mobile/auth-profiles + /mobile/cases/.../requirements */
(function () {
    const root = document.getElementById('project-root');
    if (!root) return;
    const mobileEnabled = root.dataset.mobileEnabled === 'true';
    const platform = (root.dataset.projectPlatform || 'WEB').toUpperCase();
    const canEdit = root.dataset.mobileCanEdit === 'true';
    const projectId = root.dataset.projectId;
    const authPanel = document.getElementById('mobile-auth-profiles-panel');
    const casePanel = document.getElementById('mobile-case-req-panel');
    if (!mobileEnabled || platform !== 'ANDROID' || !projectId) return;
    if (authPanel) authPanel.hidden = false;
    if (casePanel) casePanel.hidden = false;

    const MODE_HELP = {
        ENSURE_LOGGED_IN: 'Stay signed in when possible; run login only if the session check fails.',
        FRESH_LOGIN: 'End the prior session and sign in again. This does not clear app data.',
        LOGGED_OUT: 'Leave the app signed out before the case runs.',
        LOGIN_UNDER_TEST: 'Do not run automatic sign-in; the case’s own steps handle login.'
    };

    function csrfHeaders() {
        const h = { 'X-Keel-Requested-With': 'Keel', 'Accept': 'application/json' };
        const meta = document.querySelector('meta[name="_csrf"]');
        const headerMeta = document.querySelector('meta[name="_csrf_header"]');
        if (meta && headerMeta) h[headerMeta.content] = meta.content;
        return h;
    }

    async function api(method, url, body) {
        const opts = { method: method, headers: csrfHeaders(), credentials: 'same-origin' };
        if (body !== undefined) {
            opts.headers['Content-Type'] = 'application/json';
            opts.body = JSON.stringify(body);
        }
        const res = await fetch(url, opts);
        let data = null;
        try { data = await res.json(); } catch (e) { data = null; }
        return { ok: res.ok, status: res.status, data: data };
    }

    function lines(text) {
        return String(text || '').split(/\r?\n/).map(function (s) { return s.trim(); }).filter(Boolean);
    }

    function parseCredRefs(text) {
        return lines(text).map(function (line) {
            var parts = line.split(':');
            return { credentialId: (parts[0] || '').trim(), logicalKey: (parts.slice(1).join(':') || '').trim() };
        }).filter(function (c) { return c.credentialId; });
    }

    function parseDecls(text) {
        return lines(text).map(function (line) {
            var parts = line.split(':');
            var key = (parts[0] || '').trim();
            var kind = ((parts[1] || 'OTHER').trim() || 'OTHER').toUpperCase();
            return { key: key, kind: kind, requiredPerRun: kind === 'OTP' || kind === 'PIN' };
        }).filter(function (d) { return d.key; });
    }

    function setErr(el, msg) {
        if (!el) return;
        if (msg) { el.hidden = false; el.textContent = msg; }
        else { el.hidden = true; el.textContent = ''; }
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    /* ——— Auth profiles ——— */
    const listEl = document.getElementById('mobile-auth-list');
    const emptyEl = document.getElementById('mobile-auth-empty');
    const errEl = document.getElementById('mobile-auth-err');
    const editor = document.getElementById('mobile-auth-editor');
    const form = document.getElementById('mobile-auth-form');
    const createBtn = document.getElementById('mobile-auth-create-btn');
    const cancelBtn = document.getElementById('mobile-auth-cancel');
    const readonlyHint = document.getElementById('mobile-auth-readonly');
    const revDatalist = document.getElementById('mobile-auth-rev-datalist');
    let libCasesCache = [];

    if (!canEdit) {
        if (createBtn) createBtn.hidden = true;
        if (readonlyHint) readonlyHint.hidden = false;
        if (form) {
            form.querySelectorAll('input, textarea, select, button').forEach(function (el) {
                if (el.tagName === 'BUTTON') el.disabled = true;
                else if (el.type === 'checkbox' || el.tagName === 'SELECT') el.disabled = true;
                else el.readOnly = true;
            });
        }
    }

    function baseUrl() {
        return '/api/projects/' + encodeURIComponent(projectId) + '/mobile/auth-profiles';
    }

    function fillForm(rev) {
        document.getElementById('mobile-auth-profile-id').value = rev && rev.profileId ? rev.profileId : '';
        document.getElementById('mobile-auth-expected-rev').value = rev && rev.revisionId ? rev.revisionId : '';
        document.getElementById('mobile-auth-login-tc').value = rev && rev.loginTcId ? rev.loginTcId : '';
        document.getElementById('mobile-auth-default-mode').value =
            (rev && rev.defaultMode) ? rev.defaultMode : 'ENSURE_LOGGED_IN';
        document.getElementById('mobile-auth-account-label').value =
            (rev && rev.account && rev.account.displayLabel) ? rev.account.displayLabel : '';
        document.getElementById('mobile-auth-account-id').value =
            (rev && rev.account && rev.account.accountId) ? rev.account.accountId : '';
        document.getElementById('mobile-auth-session-preds').value =
            (rev && rev.sessionPredicates) ? rev.sessionPredicates.join('\n') : '';
        document.getElementById('mobile-auth-signedout-preds').value =
            (rev && rev.signedOutPredicates) ? rev.signedOutPredicates.join('\n') : '';
        document.getElementById('mobile-auth-logout-tc').value = rev && rev.logoutTcId ? rev.logoutTcId : '';
        document.getElementById('mobile-auth-setup-tc').value = rev && rev.setupTcId ? rev.setupTcId : '';
        document.getElementById('mobile-auth-cred-refs').value =
            (rev && rev.protectedCredentialRefs)
                ? rev.protectedCredentialRefs.map(function (c) {
                    return c.credentialId + (c.logicalKey ? (':' + c.logicalKey) : '');
                }).join('\n') : '';
        document.getElementById('mobile-auth-input-decls').value =
            (rev && rev.inputDeclarations)
                ? rev.inputDeclarations.map(function (d) { return d.key + ':' + d.kind; }).join('\n') : '';
        var revHint = document.getElementById('mobile-auth-rev-id');
        if (revHint) {
            if (rev && rev.revisionId) {
                revHint.hidden = false;
                revHint.textContent = 'Active revision: ' + rev.revisionId;
            } else {
                revHint.hidden = true;
                revHint.textContent = '';
            }
        }
        document.getElementById('mobile-auth-editor-title').textContent =
            rev && rev.profileId ? 'Edit sign-in profile' : 'New sign-in profile';
    }

    function bodyFromForm() {
        var accountId = document.getElementById('mobile-auth-account-id').value.trim();
        var accountLabel = document.getElementById('mobile-auth-account-label').value.trim();
        var body = {
            loginTcId: document.getElementById('mobile-auth-login-tc').value.trim(),
            defaultMode: document.getElementById('mobile-auth-default-mode').value,
            sessionPredicates: lines(document.getElementById('mobile-auth-session-preds').value),
            signedOutPredicates: lines(document.getElementById('mobile-auth-signedout-preds').value),
            logoutTcId: document.getElementById('mobile-auth-logout-tc').value.trim(),
            setupTcId: document.getElementById('mobile-auth-setup-tc').value.trim(),
            inputDeclarations: parseDecls(document.getElementById('mobile-auth-input-decls').value),
            protectedCredentialRefs: parseCredRefs(document.getElementById('mobile-auth-cred-refs').value)
        };
        if (accountId) {
            body.account = { accountId: accountId, displayLabel: accountLabel };
        }
        // never send password/otp/pin values
        return body;
    }

    async function loadProfiles() {
        setErr(errEl, '');
        var res = await api('GET', baseUrl());
        if (!res.ok) {
            setErr(errEl, (res.data && res.data.message) || 'Could not load sign-in profiles');
            return;
        }
        var profiles = (res.data && res.data.profiles) || [];
        if (emptyEl) emptyEl.hidden = profiles.length > 0;
        if (listEl) listEl.innerHTML = '';
        if (revDatalist) revDatalist.innerHTML = '';
        profiles.forEach(function (p) {
            var card = document.createElement('div');
            card.className = 'mobile-auth-card';
            card.innerHTML =
                '<h3>' + escapeHtml(p.accountLabel || p.profileId) + '</h3>' +
                '<p class="mono muted">Profile ' + escapeHtml(p.profileId) + '</p>' +
                '<p>Active revision <span class="mono">' + escapeHtml(p.activeRevisionId) + '</span></p>' +
                '<p>Login case <span class="mono">' + escapeHtml(p.loginTcId || '—') + '</span></p>' +
                '<p>Default mode: ' + escapeHtml(modeLabel(p.defaultMode)) + '</p>' +
                (canEdit
                    ? '<button type="button" class="button ghost small mobile-auth-view" data-id="' +
                      escapeHtml(p.profileId) + '">View / edit</button>'
                    : '<button type="button" class="button ghost small mobile-auth-view" data-id="' +
                      escapeHtml(p.profileId) + '">View</button>');
            if (listEl) listEl.appendChild(card);
            if (revDatalist && p.activeRevisionId) {
                var opt = document.createElement('option');
                opt.value = p.activeRevisionId;
                opt.label = (p.accountLabel || p.profileId) + ' · ' + p.activeRevisionId;
                revDatalist.appendChild(opt);
            }
        });
        if (listEl) {
            listEl.querySelectorAll('.mobile-auth-view').forEach(function (btn) {
                btn.addEventListener('click', function () { openProfile(btn.getAttribute('data-id')); });
            });
        }
    }

    function modeLabel(mode) {
        var m = {
            ENSURE_LOGGED_IN: 'Ensure logged in',
            FRESH_LOGIN: 'Fresh login',
            LOGGED_OUT: 'Logged out',
            LOGIN_UNDER_TEST: 'Login under test'
        };
        return m[mode] || mode || '—';
    }

    async function openProfile(profileId) {
        setErr(document.getElementById('mobile-auth-form-err'), '');
        var res = await api('GET', baseUrl() + '/' + encodeURIComponent(profileId));
        if (!res.ok) {
            setErr(errEl, (res.data && res.data.message) || 'Could not open profile');
            return;
        }
        var rev = (res.data && res.data.revision) ? res.data.revision : res.data;
        if (rev && res.data && res.data.activeRevisionId && !rev.revisionId) {
            rev.revisionId = res.data.activeRevisionId;
        }
        fillForm(rev);
        if (editor) editor.hidden = false;
    }

    if (createBtn) {
        createBtn.addEventListener('click', function () {
            fillForm(null);
            if (editor) editor.hidden = false;
        });
    }
    if (cancelBtn) {
        cancelBtn.addEventListener('click', function () {
            if (editor) editor.hidden = true;
        });
    }
    if (form) {
        form.addEventListener('submit', async function (e) {
            e.preventDefault();
            if (!canEdit) return;
            var formErr = document.getElementById('mobile-auth-form-err');
            setErr(formErr, '');
            var profileId = document.getElementById('mobile-auth-profile-id').value.trim();
            var expected = document.getElementById('mobile-auth-expected-rev').value.trim();
            var body = bodyFromForm();
            var res;
            if (profileId) {
                body.expectedActiveRevisionId = expected;
                res = await api('POST', baseUrl() + '/' + encodeURIComponent(profileId) + '/revisions', body);
            } else {
                res = await api('POST', baseUrl(), body);
            }
            if (!res.ok) {
                var msg = (res.data && res.data.message) || 'Could not save profile';
                if (res.status === 409 || (res.data && res.data.error === 'REVISION_CONFLICT')) {
                    msg = 'Someone else updated this profile. Reload and try again.';
                }
                setErr(formErr, msg);
                return;
            }
            fillForm(res.data);
            await loadProfiles();
            var revHint = document.getElementById('mobile-auth-rev-id');
            if (revHint && res.data && res.data.revisionId) {
                revHint.hidden = false;
                revHint.textContent = 'Saved as revision: ' + res.data.revisionId;
            }
        });
    }

    /* ——— Case requirements ——— */
    const caseErr = document.getElementById('mobile-case-req-err');
    const caseForm = document.getElementById('mobile-case-req-form');
    const caseReadonly = document.getElementById('mobile-case-req-readonly');
    const caseList = document.getElementById('mobile-case-req-list');
    const modeSelect = document.getElementById('mobile-case-req-mode');
    const modeHelp = document.getElementById('mobile-case-req-mode-help');
    const negCheck = document.getElementById('mobile-case-req-negative');
    const negMsg = document.getElementById('mobile-case-req-negative-msg');
    const loadBtn = document.getElementById('mobile-case-req-load');

    if (!canEdit) {
        if (caseReadonly) caseReadonly.hidden = false;
        if (caseForm) {
            caseForm.querySelectorAll('input, textarea, select, button').forEach(function (el) {
                if (el.tagName === 'BUTTON') el.disabled = true;
                else if (el.type === 'checkbox' || el.tagName === 'SELECT') el.disabled = true;
                else el.readOnly = true;
            });
        }
    }

    function caseBase(tcId) {
        return '/api/projects/' + encodeURIComponent(projectId) + '/mobile/cases/' +
            encodeURIComponent(tcId) + '/requirements';
    }

    function syncModeHelp() {
        if (!modeHelp || !modeSelect) return;
        var v = modeSelect.value;
        modeHelp.textContent = MODE_HELP[v] ||
            'Fresh login ends the session and signs in again. Clearing app data is a separate explicit action.';
    }
    if (modeSelect) modeSelect.addEventListener('change', syncModeHelp);

    function syncNegMsg() {
        if (negMsg) negMsg.hidden = !(negCheck && negCheck.checked);
    }
    if (negCheck) negCheck.addEventListener('change', syncNegMsg);

    function parseCallBeforeHidden() {
        var h = document.getElementById('mobile-case-req-call-before');
        if (!h || !h.value) return [];
        return h.value.split(',').map(function (s) { return s.trim(); }).filter(Boolean);
    }

    function setCallBeforeHidden(ids) {
        var h = document.getElementById('mobile-case-req-call-before');
        if (h) h.value = (ids || []).join(',');
    }

    function renderChips(ids) {
        var chipsEl = document.getElementById('mobile-case-req-chips');
        if (!chipsEl) return;
        chipsEl.innerHTML = '';
        (ids || []).forEach(function (id, idx) {
            var chip = document.createElement('span');
            chip.className = 'call-before-chip';
            chip.setAttribute('data-id', id);
            chip.innerHTML =
                '<span class="call-before-chip-label mono">' + escapeHtml(id) + '</span>' +
                (canEdit
                    ? '<span class="call-before-chip-actions">' +
                      '<button type="button" class="button ghost small call-before-up" title="Move up">↑</button>' +
                      '<button type="button" class="button ghost small call-before-down" title="Move down">↓</button>' +
                      '<button type="button" class="button ghost small call-before-remove" title="Remove">×</button>' +
                      '</span>'
                    : '');
            chipsEl.appendChild(chip);
            if (!canEdit) return;
            chip.querySelector('.call-before-up').addEventListener('click', function () {
                var arr = parseCallBeforeHidden();
                if (idx <= 0) return;
                var t = arr[idx - 1]; arr[idx - 1] = arr[idx]; arr[idx] = t;
                setCallBeforeHidden(arr); renderChips(arr);
            });
            chip.querySelector('.call-before-down').addEventListener('click', function () {
                var arr = parseCallBeforeHidden();
                if (idx >= arr.length - 1) return;
                var t = arr[idx + 1]; arr[idx + 1] = arr[idx]; arr[idx] = t;
                setCallBeforeHidden(arr); renderChips(arr);
            });
            chip.querySelector('.call-before-remove').addEventListener('click', function () {
                var arr = parseCallBeforeHidden().filter(function (x) { return x !== id; });
                setCallBeforeHidden(arr); renderChips(arr);
            });
        });
    }

    async function ensureLibCases() {
        if (libCasesCache.length) return libCasesCache;
        try {
            var res = await api('GET',
                '/api/projects/' + encodeURIComponent(projectId) + '/generated-workbook/cases');
            if (res.ok && res.data) libCasesCache = res.data.cases || [];
        } catch (e) { /* ignore */ }
        return libCasesCache;
    }

    var searchEl = document.getElementById('mobile-case-req-search');
    var resultsEl = document.getElementById('mobile-case-req-results');
    if (searchEl && resultsEl && canEdit) {
        searchEl.addEventListener('input', async function () {
            var q = searchEl.value.trim().toLowerCase();
            resultsEl.innerHTML = '';
            if (!q) { resultsEl.hidden = true; return; }
            var cases = await ensureLibCases();
            var selected = parseCallBeforeHidden();
            var matches = cases.filter(function (tc) {
                if (!tc || !tc.tcId) return false;
                if (selected.indexOf(tc.tcId) >= 0) return false;
                var hay = (tc.tcId + ' ' + (tc.title || '')).toLowerCase();
                return hay.indexOf(q) >= 0;
            }).slice(0, 8);
            if (!matches.length) { resultsEl.hidden = true; return; }
            matches.forEach(function (tc) {
                var li = document.createElement('li');
                var btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'call-before-result-item';
                btn.textContent = tc.tcId + (tc.title ? (' — ' + tc.title) : '');
                btn.addEventListener('click', function () {
                    var arr = parseCallBeforeHidden();
                    if (arr.indexOf(tc.tcId) < 0) arr.push(tc.tcId);
                    setCallBeforeHidden(arr);
                    renderChips(arr);
                    searchEl.value = '';
                    resultsEl.hidden = true;
                });
                li.appendChild(btn);
                resultsEl.appendChild(li);
            });
            resultsEl.hidden = false;
        });
    }

    function fillCaseForm(req) {
        if (modeSelect) modeSelect.value = req && req.authMode ? req.authMode : '';
        document.getElementById('mobile-case-req-rev').value =
            req && req.authProfileRevisionId ? req.authProfileRevisionId : '';
        document.getElementById('mobile-case-req-start').value =
            req && req.startStateTcId ? req.startStateTcId : '';
        if (negCheck) negCheck.checked = !!(req && req.negativeLogin);
        setCallBeforeHidden((req && req.callBefore) || []);
        renderChips((req && req.callBefore) || []);
        syncModeHelp();
        syncNegMsg();
        if (caseForm) caseForm.hidden = false;
    }

    async function loadCaseReq() {
        setErr(caseErr, '');
        var tcId = document.getElementById('mobile-case-req-tc').value.trim();
        if (!tcId) {
            setErr(caseErr, 'Enter a test case ID');
            return;
        }
        var res = await api('GET', caseBase(tcId));
        if (!res.ok) {
            setErr(caseErr, (res.data && res.data.message) || 'Could not load requirements');
            return;
        }
        fillCaseForm(res.data);
    }

    if (loadBtn) loadBtn.addEventListener('click', loadCaseReq);

    if (caseForm) {
        caseForm.addEventListener('submit', async function (e) {
            e.preventDefault();
            if (!canEdit) return;
            var formErr = document.getElementById('mobile-case-req-form-err');
            setErr(formErr, '');
            var tcId = document.getElementById('mobile-case-req-tc').value.trim();
            if (!tcId) {
                setErr(formErr, 'Enter a test case ID');
                return;
            }
            var body = {
                authMode: modeSelect && modeSelect.value ? modeSelect.value : null,
                callBefore: parseCallBeforeHidden(),
                authProfileRevisionId: document.getElementById('mobile-case-req-rev').value.trim(),
                startStateTcId: document.getElementById('mobile-case-req-start').value.trim(),
                negativeLogin: !!(negCheck && negCheck.checked)
            };
            var res = await api('PUT', caseBase(tcId), body);
            if (!res.ok) {
                var msg = (res.data && res.data.message) || 'Could not save requirements';
                if (res.data && res.data.error === 'NEGATIVE_LOGIN_SETUP') {
                    msg = res.data.message ||
                        'A negative-login case cannot be used as setup for other cases.';
                    if (negMsg) negMsg.hidden = false;
                }
                setErr(formErr, msg);
                return;
            }
            fillCaseForm(res.data);
            await loadCaseReqList();
        });
    }

    async function loadCaseReqList() {
        if (!caseList) return;
        var res = await api('GET',
            '/api/projects/' + encodeURIComponent(projectId) + '/mobile/cases/requirements');
        if (!res.ok) return;
        var items = (res.data && (res.data.cases || res.data.requirements)) || [];
        caseList.innerHTML = '';
        if (!items.length) {
            caseList.innerHTML = '<p class="muted">No case requirements saved yet.</p>';
            return;
        }
        items.forEach(function (r) {
            var card = document.createElement('div');
            card.className = 'mobile-case-req-card';
            card.innerHTML =
                '<p><span class="mono">' + escapeHtml(r.tcId) + '</span> · ' +
                escapeHtml(modeLabel(r.authMode) || 'Profile default') + '</p>' +
                '<p class="muted">Revision <span class="mono">' +
                escapeHtml(r.authProfileRevisionId || '—') + '</span>' +
                (r.startStateTcId ? (' · Start ' + escapeHtml(r.startStateTcId)) : '') +
                (r.callBefore && r.callBefore.length
                    ? (' · Call before ' + escapeHtml(r.callBefore.join(' → '))) : '') +
                (r.negativeLogin ? ' · Negative login' : '') + '</p>';
            card.addEventListener('click', function () {
                document.getElementById('mobile-case-req-tc').value = r.tcId;
                fillCaseForm(r);
            });
            caseList.appendChild(card);
        });
    }

    loadProfiles();
    loadCaseReqList();
})();

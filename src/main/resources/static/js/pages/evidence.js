/* Extracted from templates/evidence.html. Server values arrive via window.KEEL_PAGE (set inline by the template). */
(function () {
    const root = document.getElementById('evidence-root');
    const jobId = root.getAttribute('data-job-id') || '';
    const initialTcId = root.getAttribute('data-tc-id') || '';
    const projectId = root.getAttribute('data-project-id') || '';
    const API_HEADERS = { 'X-Keel-Requested-With': 'Keel', 'Accept': 'application/json' };
    const tcSelect = document.getElementById('tc-select');
    const errEl = document.getElementById('evidence-err');
    let currentTcId = '';
    let currentFlaky = false;

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function qaBadgeClass(status) {
        if (status === 'PASS') return 'badge ok';
        return 'badge err';
    }

    function renderDesignBadge(status) {
        if (!status) return '<span class="badge muted">—</span>';
        const cls = status === 'MATCH' ? 'badge ok' : (status === 'MISMATCH' ? 'badge err' : 'badge warn');
        return '<span class="' + cls + '">' + escapeHtml(status) + '</span>';
    }

    function renderTimeline(timeline) {
        let html = '<ol class="step-timeline execute-timeline">';
        (timeline || []).forEach(step => {
            html += '<li class="' + escapeHtml(step.state || '') + '">';
            html += '<div class="step-main"><span class="step-state">' + escapeHtml(step.state) + '</span> ';
            html += '<span>' + escapeHtml(step.label || '') + '</span>';
            if (step.reason) {
                html += '<div class="error">' + escapeHtml(step.reason) + '</div>';
            }
            if (step.screenshotUrl) {
                html += '<div class="step-shot"><img src="' + escapeHtml(step.screenshotUrl)
                    + '" alt="Step screenshot" loading="lazy"/></div>';
            }
            html += '</div></li>';
        });
        html += '</ol>';
        return html;
    }

    function renderDesignCompare(jobId, tcId, status) {
        if (!status || !projectId) return '';
        const refUrl = '/api/projects/' + encodeURIComponent(projectId)
            + '/design-references/' + encodeURIComponent(tcId);
        const actualUrl = '/api/execute-runs/' + encodeURIComponent(jobId)
            + '/tcs/' + encodeURIComponent(tcId) + '/screenshots/design-actual.png?v=design-actual.png';
        return '<div class="design-compare-panel"><p class="hint"><strong>Design compare:</strong> '
            + renderDesignBadge(status) + '</p><div class="design-compare-shots">'
            + '<figure><figcaption>Reference</figcaption><img src="' + escapeHtml(refUrl)
            + '" alt="Reference" loading="lazy"/></figure>'
            + '<figure><figcaption>Actual</figcaption><img src="' + escapeHtml(actualUrl)
            + '" alt="Actual" loading="lazy"/></figure></div></div>';
    }

    function renderIrBlock(data) {
        const block = document.getElementById('ir-block');
        const dl = document.getElementById('ir-details');
        const rows = [];
        if (data.visualAssertStatus) {
            rows.push(['Visual check', data.visualAssertStatus]);
        }
        if (data.designCompareStatus) {
            rows.push(['Design compare', data.designCompareStatus]);
        }
        if (data.expectedResult) {
            rows.push(['Expected result', data.expectedResult]);
        }
        if (data.provenCount != null) {
            rows.push(['Proven steps', String(data.provenCount)]);
        }
        if (!rows.length) {
            block.hidden = true;
            return;
        }
        block.hidden = false;
        dl.innerHTML = rows.map(r => '<dt>' + escapeHtml(r[0]) + '</dt><dd>' + escapeHtml(r[1]) + '</dd>').join('');
    }

    function isSignedOutStep(step) {
        const label = String(step.label || '').toLowerCase();
        const reason = String(step.reason || step.reasonTechnical || '').toLowerCase();
        return label.includes('signedout') || label.includes('signed-out')
            || reason.includes('signed-out') || reason.includes('signedout');
    }

    function renderTabs(data, jobId, tcId) {
        const failure = document.getElementById('tab-failure');
        const capture = document.getElementById('tab-capture');
        const signedOut = document.getElementById('tab-signed-out');
        const timeline = document.getElementById('tab-timeline');
        let failureHtml = '';
        if (data.failureReasonUser || data.failureReason) {
            failureHtml += '<p class="hint"><strong>What went wrong:</strong> '
                + escapeHtml(data.failureReasonUser || data.failureReason) + '</p>';
        } else {
            failureHtml += '<p class="muted">No failure reason recorded for this case.</p>';
        }
        const failedSteps = (data.timeline || []).filter(s => s.state === 'failed');
        if (failedSteps.length) {
            failureHtml += renderTimeline(failedSteps);
        }
        failure.innerHTML = failureHtml;

        let captureHtml = renderDesignCompare(jobId, tcId, data.designCompareStatus);
        const shots = (data.timeline || []).filter(s => s.screenshotUrl);
        if (shots.length) {
            captureHtml += '<div class="design-compare-shots">';
            shots.forEach(s => {
                captureHtml += '<figure><figcaption>' + escapeHtml(s.label || s.state)
                    + '</figcaption><img src="' + escapeHtml(s.screenshotUrl)
                    + '" alt="Capture" loading="lazy"/></figure>';
            });
            captureHtml += '</div>';
        }
        if (!captureHtml) {
            captureHtml = '<p class="muted">No capture or compare files for this case.</p>';
        }
        capture.innerHTML = captureHtml;

        const signedSteps = (data.timeline || []).filter(isSignedOutStep);
        if (signedSteps.length) {
            signedOut.innerHTML = renderTimeline(signedSteps);
        } else {
            signedOut.innerHTML = '<p class="muted">No signed-out check steps recorded for this case.</p>';
        }

        timeline.innerHTML = renderTimeline(data.timeline || []);
    }

    function failuresApiBase(tcId) {
        return '/api/execute-runs/' + encodeURIComponent(jobId)
            + '/failures/' + encodeURIComponent(tcId);
    }

    function projectHealBase() {
        return '/api/projects/' + encodeURIComponent(projectId) + '/heal-proposals';
    }

    function projectFlakyBase() {
        return '/api/projects/' + encodeURIComponent(projectId) + '/flaky-tags';
    }

    function sortReplayEvents(merged) {
        merged.sort(function (a, b) {
            const ta = a.t != null ? a.t : (a.ts != null ? a.ts : (a.ms != null ? a.ms : (a.stepIndex != null ? a.stepIndex : 0)));
            const tb = b.t != null ? b.t : (b.ts != null ? b.ts : (b.ms != null ? b.ms : (b.stepIndex != null ? b.stepIndex : 0)));
            return Number(ta) - Number(tb);
        });
        return merged;
    }

    function normalizeReplayEvents(data) {
        if (!data) return [];
        if (Array.isArray(data)) return data;
        const direct = data.events || data.timeline || data.items;
        if (Array.isArray(direct)) return direct;
        const merged = [];
        function pushLane(arr, lane) {
            (arr || []).forEach(function (ev) {
                const copy = Object.assign({}, ev);
                if (!copy.lane) copy.lane = lane;
                merged.push(copy);
            });
        }
        if (data.screen || data.network || data.console) {
            pushLane(data.screen, 'screen');
            pushLane(data.network, 'network');
            pushLane(data.console, 'console');
        }
        return sortReplayEvents(merged);
    }

    function extractFailureSteps(data) {
        if (!data) return [];
        if (Array.isArray(data)) return data;
        const arr = data.failures || data.steps || data.items || data.results;
        if (Array.isArray(arr)) return arr;
        if (data.stepIndex != null || data.hasScreen != null || data.hasNetwork != null || data.hasConsole != null) {
            return [data];
        }
        return [];
    }

    function networkEntriesFromBlob(blob) {
        if (!blob) return [];
        if (Array.isArray(blob)) return blob;
        return blob.entries || blob.events || blob.items || blob.log || [];
    }

    function consoleEntriesFromBlob(blob) {
        if (!blob) return [];
        if (Array.isArray(blob)) return blob;
        return blob.messages || blob.events || blob.items || blob.log || [];
    }

    async function fetchJsonFlexible(url) {
        const res = await fetch(url, { credentials: 'same-origin', headers: API_HEADERS });
        if (res.status === 404 || res.status === 501) {
            return { stub: true, status: res.status };
        }
        if (!res.ok) {
            return { error: true, status: res.status };
        }
        const data = await res.json().catch(function () { return {}; });
        return { data: data, status: res.status };
    }

    async function buildReplayFromFailureSteps(tcId, steps) {
        const events = [];
        const base = failuresApiBase(tcId);
        for (let i = 0; i < steps.length; i++) {
            const step = steps[i] || {};
            const stepIndex = step.stepIndex != null ? step.stepIndex : (step.index != null ? step.index : i);
            const tLabel = step.failedAt || step.t || ('step ' + stepIndex);
            const reason = step.reason || step.message || step.failureReason || '';
            const hasScreen = step.hasScreen === true || !!step.screen || !!step.screenshotUrl || !!step.screenUrl;
            const hasNetwork = step.hasNetwork === true || !!step.network;
            const hasConsole = step.hasConsole === true || !!step.console;
            const screenPath = base + '/' + encodeURIComponent(stepIndex) + '/screen';
            const networkPath = base + '/' + encodeURIComponent(stepIndex) + '/network';
            const consolePath = base + '/' + encodeURIComponent(stepIndex) + '/console';

            if (hasScreen || step.hasScreen !== false) {
                // Prefer explicit hasScreen; if flags absent but meta-only, still try screen URL when Backend said hasScreen
                if (step.hasScreen === true || step.screenshotUrl || step.screenUrl || step.screen) {
                    events.push({
                        t: tLabel,
                        lane: 'screen',
                        label: reason || ('Failure screen @ step ' + stepIndex),
                        message: reason,
                        stepIndex: stepIndex,
                        screenshotUrl: step.screenshotUrl || step.screenUrl || screenPath
                    });
                } else if (step.hasScreen == null && step.hasNetwork == null && step.hasConsole == null
                    && !step.network && !step.console && !step.events) {
                    // Meta-only without flags: offer screen URL as best-effort (img onerror hides)
                    events.push({
                        t: tLabel,
                        lane: 'screen',
                        label: reason || ('Failure @ step ' + stepIndex),
                        message: reason,
                        stepIndex: stepIndex,
                        screenshotUrl: screenPath
                    });
                }
            }

            let netBlob = step.network;
            if (!netBlob && (hasNetwork || step.hasNetwork === true)) {
                const net = await fetchJsonFlexible(networkPath);
                if (net.stub) {
                    // leave empty; overall stub handled by caller if primary 404
                } else if (!net.error) {
                    netBlob = net.data;
                }
            }
            networkEntriesFromBlob(netBlob).forEach(function (entry, idx) {
                events.push({
                    t: entry.t || entry.startedDateTime || entry.timestamp || tLabel,
                    ts: entry.ts,
                    ms: entry.time != null ? entry.time : entry.ms,
                    lane: 'network',
                    method: entry.method || (entry.request && entry.request.method) || 'GET',
                    url: entry.url || (entry.request && entry.request.url) || entry.name || '',
                    status: entry.status != null ? entry.status
                        : (entry.response && entry.response.status),
                    message: entry.message || '',
                    stepIndex: stepIndex
                });
            });

            let conBlob = step.console;
            if (!conBlob && (hasConsole || step.hasConsole === true)) {
                const con = await fetchJsonFlexible(consolePath);
                if (!con.stub && !con.error) conBlob = con.data;
            }
            consoleEntriesFromBlob(conBlob).forEach(function (entry) {
                events.push({
                    t: entry.t || entry.timestamp || tLabel,
                    ts: entry.ts,
                    lane: 'console',
                    level: entry.level || entry.type || 'log',
                    message: entry.message || entry.text || entry.label || '',
                    label: entry.label || '',
                    stepIndex: stepIndex
                });
            });

            // Nested events already shaped
            if (Array.isArray(step.events)) {
                step.events.forEach(function (ev) {
                    const copy = Object.assign({ stepIndex: stepIndex }, ev);
                    if (!copy.lane) copy.lane = ev.type || 'screen';
                    if (copy.lane === 'screen' && !copy.screenshotUrl && !copy.screenUrl) {
                        copy.screenshotUrl = screenPath;
                    }
                    events.push(copy);
                });
            }
        }
        return sortReplayEvents(events);
    }

    function formatReplayTime(ev) {
        if (ev.t != null) return String(ev.t);
        if (ev.ts != null) return String(ev.ts);
        if (ev.ms != null) return String(ev.ms) + ' ms';
        if (ev.stepIndex != null) return 'step ' + ev.stepIndex;
        return '—';
    }

    function renderFailureReplayEvents(events) {
        const list = document.getElementById('failure-replay-list');
        const empty = document.getElementById('failure-replay-empty');
        const waiting = document.getElementById('failure-replay-waiting');
        const err = document.getElementById('failure-replay-err');
        if (waiting) waiting.hidden = true;
        if (err) err.hidden = true;
        if (!list) return;
        list.innerHTML = '';
        if (!events || !events.length) {
            if (empty) empty.hidden = false;
            return;
        }
        if (empty) empty.hidden = true;
        events.forEach(function (ev) {
            const lane = String(ev.lane || ev.type || 'screen').toLowerCase();
            const li = document.createElement('li');
            let body = '';
            if (lane === 'network') {
                body += '<div><span class="mono">' + escapeHtml(ev.method || 'GET') + '</span> '
                    + escapeHtml(ev.url || ev.label || '') + '</div>';
                const bits = [];
                if (ev.status != null) bits.push('status ' + ev.status);
                if (ev.ms != null) bits.push(ev.ms + ' ms');
                if (ev.message) bits.push(ev.message);
                if (bits.length) body += '<p class="muted">' + escapeHtml(bits.join(' · ')) + '</p>';
            } else if (lane === 'console') {
                body += '<div><strong>' + escapeHtml(ev.level || 'log') + '</strong> '
                    + escapeHtml(ev.message || ev.label || '') + '</div>';
            } else {
                body += '<div>' + escapeHtml(ev.label || ev.message || 'Screenshot') + '</div>';
                const screenSrc = ev.screenshotUrl || ev.screenUrl;
                if (screenSrc) {
                    body += '<div class="failure-replay-shot"><img src="' + escapeHtml(screenSrc)
                        + '" alt="Failure timeline screenshot" loading="lazy"/></div>';
                } else if (ev.message) {
                    body += '<p class="muted">' + escapeHtml(ev.message) + '</p>';
                }
            }
            li.innerHTML = '<span class="failure-replay-t">' + escapeHtml(formatReplayTime(ev)) + '</span>'
                + '<span class="failure-replay-lane lane-' + escapeHtml(lane) + '">' + escapeHtml(lane) + '</span>'
                + '<div class="failure-replay-body">' + body + '</div>';
            list.appendChild(li);
        });
    }

    function resetFailureReplayUi(mode) {
        const list = document.getElementById('failure-replay-list');
        const empty = document.getElementById('failure-replay-empty');
        const waiting = document.getElementById('failure-replay-waiting');
        const err = document.getElementById('failure-replay-err');
        if (list) list.innerHTML = '';
        if (empty) empty.hidden = true;
        if (waiting) waiting.hidden = mode !== 'waiting';
        if (err) {
            err.hidden = mode !== 'error';
            if (mode !== 'error') err.textContent = '';
        }
    }

    async function loadFailureReplay(tcId) {
        resetFailureReplayUi(null);
        if (!jobId || !tcId) {
            resetFailureReplayUi('waiting');
            return;
        }
        try {
            const primary = await fetchJsonFlexible(failuresApiBase(tcId));
            if (primary.stub) {
                resetFailureReplayUi('waiting');
                return;
            }
            if (primary.error) {
                const err = document.getElementById('failure-replay-err');
                if (err) {
                    err.hidden = false;
                    err.textContent = 'Could not load failure timeline (' + primary.status + ').';
                }
                return;
            }
            const data = primary.data || {};
            let events = normalizeReplayEvents(data);
            if (!events.length) {
                const steps = extractFailureSteps(data);
                if (steps.length) {
                    events = await buildReplayFromFailureSteps(tcId, steps);
                }
            } else {
                // Ensure screen lanes without URL get contract /screen path when stepIndex known
                events = events.map(function (ev) {
                    const copy = Object.assign({}, ev);
                    if ((String(copy.lane || '').toLowerCase() === 'screen' || copy.screenshotUrl || copy.screenUrl)
                        && !copy.screenshotUrl && !copy.screenUrl && copy.stepIndex != null) {
                        copy.screenshotUrl = failuresApiBase(tcId) + '/'
                            + encodeURIComponent(copy.stepIndex) + '/screen';
                        if (!copy.lane) copy.lane = 'screen';
                    }
                    return copy;
                });
            }
            renderFailureReplayEvents(events);
        } catch (e) {
            resetFailureReplayUi('waiting');
        }
    }

    function normalizeProposals(data) {
        if (!data) return [];
        if (Array.isArray(data)) return data;
        return data.proposals || data.items || data.results || [];
    }

    function proposalSummary(p) {
        return p.summary || p.reason || p.label
            || ((p.locatorBefore && p.locatorAfter)
                ? ((p.locatorBefore.strategy || '') + '=' + (p.locatorBefore.value || '')
                    + ' → ' + (p.locatorAfter.strategy || '') + '=' + (p.locatorAfter.value || ''))
                : (p.proposalId || p.id || 'Proposal'));
    }

    function proposalTarget(p) {
        if (p.target) return String(p.target);
        const parts = [];
        if (p.pageClass) parts.push(p.pageClass);
        if (p.method || p.targetMethod) parts.push(p.method || p.targetMethod);
        if (p.tier) parts.push('tier ' + p.tier);
        if (p.stepIndex != null) parts.push('step ' + p.stepIndex);
        if (p.status && p.status !== 'pending') parts.push(p.status);
        return parts.join(' · ');
    }

    function resetHealUi(mode, waitingText) {
        const list = document.getElementById('heal-proposals-list');
        const empty = document.getElementById('heal-proposals-empty');
        const waiting = document.getElementById('heal-proposals-waiting');
        const msg = document.getElementById('heal-proposals-msg');
        const err = document.getElementById('heal-proposals-err');
        if (list && mode !== 'keep-msg') list.innerHTML = '';
        if (empty) empty.hidden = true;
        if (waiting) {
            waiting.hidden = mode !== 'waiting';
            if (mode === 'waiting' && waitingText) waiting.textContent = waitingText;
            else if (mode === 'waiting') waiting.textContent = 'Waiting on failure timeline / fix-suggestion APIs.';
        }
        if (msg && mode !== 'keep-msg') msg.hidden = true;
        if (err) {
            err.hidden = mode !== 'error';
            if (mode !== 'error') err.textContent = '';
        }
    }

    function renderHealProposals(proposals) {
        const list = document.getElementById('heal-proposals-list');
        const empty = document.getElementById('heal-proposals-empty');
        const waiting = document.getElementById('heal-proposals-waiting');
        if (waiting) waiting.hidden = true;
        if (!list) return;
        list.innerHTML = '';
        if (!proposals || !proposals.length) {
            if (empty) empty.hidden = false;
            return;
        }
        if (empty) empty.hidden = true;
        proposals.forEach(function (p) {
            const id = p.proposalId || p.id;
            const row = document.createElement('div');
            row.className = 'heal-proposal-row';
            row.setAttribute('data-proposal-id', id || '');
            let detail = '';
            if (p.detail) detail += '<p class="muted">' + escapeHtml(p.detail) + '</p>';
            else if (p.reason && p.reason !== proposalSummary(p)) {
                detail += '<p class="muted">' + escapeHtml(p.reason) + '</p>';
            }
            const target = proposalTarget(p);
            if (target) detail += '<p class="muted mono">' + escapeHtml(target) + '</p>';
            if (p.confidence != null) {
                const conf = (typeof p.confidence === 'number' && p.confidence <= 1)
                    ? Math.round(p.confidence * 100) + '%'
                    : String(p.confidence);
                detail += '<p class="muted">Confidence: ' + escapeHtml(conf) + '</p>';
            }
            row.innerHTML = '<div class="heal-proposal-main"><p class="title">'
                + escapeHtml(proposalSummary(p)) + '</p>' + detail + '</div>'
                + '<div class="heal-proposal-actions">'
                + '<button type="button" class="button heal-accept-btn"'
                + (id ? '' : ' disabled') + '>Accept fix</button>'
                + '<button type="button" class="button ghost danger-ghost heal-reject-btn"'
                + (id ? '' : ' disabled') + '>Reject</button></div>';
            const acceptBtn = row.querySelector('.heal-accept-btn');
            const rejectBtn = row.querySelector('.heal-reject-btn');
            if (acceptBtn && id) {
                acceptBtn.addEventListener('click', function () { decideHeal(id, 'accept'); });
            }
            if (rejectBtn && id) {
                rejectBtn.addEventListener('click', function () { decideHeal(id, 'reject'); });
            }
            list.appendChild(row);
        });
    }

    async function loadHealProposals(tcId) {
        resetHealUi(null);
        if (!projectId) {
            resetHealUi('waiting', 'Select a project / open Evidence from a run with project');
            return;
        }
        if (!jobId || !tcId) {
            resetHealUi('waiting');
            return;
        }
        try {
            const res = await fetch(projectHealBase() + '?status=pending', {
                credentials: 'same-origin', headers: API_HEADERS
            });
            if (res.status === 404 || res.status === 501) {
                resetHealUi('waiting');
                return;
            }
            if (!res.ok) {
                const err = document.getElementById('heal-proposals-err');
                if (err) {
                    err.hidden = false;
                    err.textContent = 'Could not load fix suggestions (' + res.status + ').';
                }
                return;
            }
            const data = await res.json().catch(function () { return {}; });
            let proposals = normalizeProposals(data);
            proposals = proposals.filter(function (p) {
                const pJob = p.jobId != null ? String(p.jobId) : '';
                const pTc = p.tcId != null ? String(p.tcId) : '';
                if (pJob && pJob !== String(jobId)) return false;
                if (pTc && pTc !== String(tcId)) return false;
                const st = String(p.status || 'pending').toLowerCase();
                return !st || st === 'pending';
            });
            renderHealProposals(proposals);
        } catch (e) {
            resetHealUi('waiting');
        }
    }

    async function decideHeal(proposalId, action) {
        const msg = document.getElementById('heal-proposals-msg');
        const err = document.getElementById('heal-proposals-err');
        if (msg) msg.hidden = true;
        if (err) err.hidden = true;
        if (!projectId) {
            if (err) {
                err.hidden = false;
                err.textContent = 'Select a project / open Evidence from a run with project';
            }
            return;
        }
        if (!proposalId) return;
        const path = projectHealBase() + '/' + encodeURIComponent(proposalId) + '/' + action;
        try {
            const res = await fetch(path, {
                method: 'POST',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                body: action === 'reject' ? JSON.stringify({ reason: '' }) : JSON.stringify({})
            });
            const data = await res.json().catch(function () { return {}; });
            if (res.status === 404 || res.status === 501) {
                if (err) {
                    err.hidden = false;
                    err.textContent = 'Waiting on failure timeline / fix-suggestion APIs.';
                }
                return;
            }
            if (!res.ok) {
                if (err) {
                    err.hidden = false;
                    err.textContent = data.message || ('Fix suggestion ' + action + ' failed (' + res.status + ').');
                }
                return;
            }
            if (msg) {
                msg.hidden = false;
                msg.textContent = action === 'accept'
                    ? 'Accepted — applied to existing generated Java only (fix broken steps).'
                    : 'Rejected fix suggestion.';
            }
            await loadHealProposals(currentTcId);
        } catch (e) {
            if (err) {
                err.hidden = false;
                err.textContent = 'Waiting on failure timeline / fix-suggestion APIs.';
            }
        }
    }

    let currentFlakyTagId = null;

    function syncFlakyChip(flaky, tagId) {
        currentFlaky = !!flaky;
        currentFlakyTagId = tagId || null;
        const row = document.getElementById('tc-flaky-row');
        const btn = document.getElementById('flaky-toggle');
        const msg = document.getElementById('flaky-msg');
        if (row) row.hidden = !currentTcId;
        if (btn) {
            btn.setAttribute('aria-pressed', currentFlaky ? 'true' : 'false');
            btn.textContent = currentFlaky ? 'Sometimes fails' : 'Mark as sometimes fails';
            btn.disabled = !projectId;
        }
        if (msg) {
            if (!projectId) msg.textContent = 'Select a project / open Evidence from a run with project';
            else msg.textContent = currentFlaky ? 'Tagged as sometimes fails.' : '';
        }
    }

    function normalizeFlakyTags(data) {
        if (!data) return [];
        if (Array.isArray(data)) return data;
        return data.tags || data.items || data.results || data.flakyTags || [];
    }

    function isActiveFlakyTag(tag) {
        if (!tag) return false;
        if (tag.clearedAt) return false;
        const t = String(tag.tag || 'flaky').toLowerCase();
        return !t || t === 'flaky';
    }

    async function loadFlakyForTc(tcId) {
        if (!projectId || !tcId) {
            syncFlakyChip(false, null);
            return;
        }
        try {
            const res = await fetch(projectFlakyBase(), {
                credentials: 'same-origin', headers: API_HEADERS
            });
            if (res.status === 404 || res.status === 501) {
                const msg = document.getElementById('flaky-msg');
                syncFlakyChip(false, null);
                if (msg) msg.textContent = 'Waiting on Backend sometimes-fails tagging API.';
                return;
            }
            if (!res.ok) {
                syncFlakyChip(false, null);
                return;
            }
            const data = await res.json().catch(function () { return []; });
            const tags = normalizeFlakyTags(data).filter(function (tag) {
                return String(tag.tcId || '') === String(tcId) && isActiveFlakyTag(tag);
            });
            const hit = tags[0];
            syncFlakyChip(!!hit, hit ? (hit.tagId || hit.id) : null);
        } catch (e) {
            const msg = document.getElementById('flaky-msg');
            syncFlakyChip(false, null);
            if (msg) msg.textContent = 'Waiting on Backend sometimes-fails tagging API.';
        }
    }

    async function setFlaky(next) {
        const msg = document.getElementById('flaky-msg');
        if (!currentTcId) return;
        if (!projectId) {
            if (msg) msg.textContent = 'Select a project / open Evidence from a run with project';
            return;
        }
        try {
            if (next) {
                const res = await fetch(projectFlakyBase(), {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
                    body: JSON.stringify({
                        tcId: currentTcId,
                        sourceJobId: jobId || null,
                        signal: 'manual',
                        reason: 'Marked flaky from Evidence'
                    })
                });
                if (res.status === 404 || res.status === 501) {
                    if (msg) msg.textContent = 'Waiting on Backend sometimes-fails tagging API.';
                    return;
                }
                if (!res.ok) {
                    if (msg) msg.textContent = 'Could not update sometimes-fails tag (' + res.status + ').';
                    return;
                }
            } else {
                if (!currentFlakyTagId) {
                    await loadFlakyForTc(currentTcId);
                    if (!currentFlakyTagId) {
                        syncFlakyChip(false, null);
                        return;
                    }
                }
                const res = await fetch(projectFlakyBase() + '/' + encodeURIComponent(currentFlakyTagId), {
                    method: 'DELETE',
                    credentials: 'same-origin',
                    headers: API_HEADERS
                });
                if (res.status === 404 || res.status === 501) {
                    if (msg) msg.textContent = 'Waiting on Backend sometimes-fails tagging API.';
                    return;
                }
                if (!res.ok) {
                    if (msg) msg.textContent = 'Could not clear sometimes-fails tag (' + res.status + ').';
                    return;
                }
            }
            await loadFlakyForTc(currentTcId);
        } catch (e) {
            if (msg) msg.textContent = 'Waiting on Backend sometimes-fails tagging API.';
        }
    }

    async function loadTcDetail(tcId) {
        errEl.hidden = true;
        currentTcId = tcId || '';
        const res = await fetch('/api/execute-runs/' + encodeURIComponent(jobId)
            + '/tcs/' + encodeURIComponent(tcId), {
            credentials: 'same-origin', headers: API_HEADERS
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            errEl.hidden = false;
            errEl.textContent = data.message || 'Could not load case evidence';
            resetFailureReplayUi('waiting');
            resetHealUi('waiting');
            syncFlakyChip(false, null);
            return;
        }
        document.getElementById('tc-meta').textContent = (data.title || tcId)
            + ' · ' + (data.qaStatus || '—') + ' · run at ' + (data.runAtLabel || '—');
        renderIrBlock(data);
        renderTabs(data, jobId, tcId);
        loadFailureReplay(tcId);
        loadHealProposals(tcId);
        loadFlakyForTc(tcId);
        const url = new URL(window.location.href);
        url.searchParams.set('tcId', tcId);
        history.replaceState(null, '', url.pathname + url.search);
    }

    async function loadTcList() {
        const res = await fetch('/api/execute-runs/' + encodeURIComponent(jobId) + '/tcs', {
            credentials: 'same-origin', headers: API_HEADERS
        });
        const tcs = res.ok ? await res.json() : [];
        tcSelect.innerHTML = '';
        tcs.forEach(tc => {
            const opt = document.createElement('option');
            opt.value = tc.tcId;
            opt.textContent = tc.tcId + ' · ' + (tc.title || '') + ' · ' + (tc.qaStatus || '');
            tcSelect.appendChild(opt);
        });
        const pick = initialTcId && tcs.some(t => t.tcId === initialTcId)
            ? initialTcId
            : (tcs.find(t => t.qaStatus === 'FAIL') || tcs[0] || {}).tcId;
        if (pick) {
            tcSelect.value = pick;
            await loadTcDetail(pick);
        }
    }

    tcSelect.addEventListener('change', () => {
        if (tcSelect.value) loadTcDetail(tcSelect.value);
    });

    document.getElementById('flaky-toggle')?.addEventListener('click', () => {
        setFlaky(!currentFlaky);
    });

    document.querySelectorAll('.evidence-tab').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.evidence-tab').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const tab = btn.getAttribute('data-tab');
            document.querySelectorAll('.evidence-tab-panel').forEach(p => p.hidden = true);
            document.getElementById('tab-' + tab).hidden = false;
        });
    });

    let triageLoaded = false;
    async function loadTriage() {
        if (triageLoaded || !jobId) return;
        triageLoaded = true;
        try {
            const jobRes = await fetch('/api/jobs/' + encodeURIComponent(jobId), {
                credentials: 'same-origin', headers: API_HEADERS
            });
            const job = jobRes.ok ? await jobRes.json() : {};
            const classSel = document.getElementById('failure-class');
            if (classSel && job.failureClass) classSel.value = job.failureClass;
            const [attemptsRes, interRes] = await Promise.all([
                fetch('/api/jobs/' + encodeURIComponent(jobId) + '/attempts', {
                    credentials: 'same-origin', headers: API_HEADERS
                }),
                fetch('/api/jobs/' + encodeURIComponent(jobId) + '/intermittency', {
                    credentials: 'same-origin', headers: API_HEADERS
                })
            ]);
            const attempts = attemptsRes.ok ? await attemptsRes.json() : { attempts: [] };
            const inter = interRes.ok ? await interRes.json() : {};
            const list = document.getElementById('attempts-list');
            const rows = attempts.attempts || [];
            list.textContent = rows.length
                ? rows.map(r => r.jobId + ' · ' + r.status
                    + (r.parentJobId ? ' (rerun of ' + r.parentJobId + ')' : ' (original)')).join(' · ')
                : 'This is the only attempt.';
            const sel = document.getElementById('compare-other');
            sel.innerHTML = '';
            rows.filter(r => r.jobId !== jobId).forEach(r => {
                const opt = document.createElement('option');
                opt.value = r.jobId;
                opt.textContent = r.jobId + ' · ' + r.status;
                sel.appendChild(opt);
            });
            document.getElementById('intermittency-verdict').textContent =
                'Stability: ' + (inter.verdict || '—')
                + ' (sample ' + (inter.sampleSize || 0) + ', min ' + (inter.minSample || 3) + ')';
        } catch (e) {
            triageLoaded = false;
        }
    }

    document.getElementById('compare-btn')?.addEventListener('click', async () => {
        const sel = document.getElementById('compare-other');
        const out = document.getElementById('compare-result');
        if (!sel.value) {
            out.hidden = false;
            out.textContent = 'Select another attempt to compare.';
            return;
        }
        const res = await fetch('/api/jobs/' + encodeURIComponent(jobId) + '/compare?other='
            + encodeURIComponent(sel.value), { credentials: 'same-origin', headers: API_HEADERS });
        const data = await res.json().catch(() => ({}));
        out.hidden = false;
        if (!res.ok) {
            out.textContent = data.message || 'Compare failed';
            return;
        }
        const d = data.divergence;
        out.textContent = (data.pinsMatch ? 'Pinned inputs match. ' : 'Pinned inputs differ. ')
            + (d ? ('First divergence at step ' + d.index + ' (' + d.field + '): '
                + ((d.left && d.left.observed) || '—') + ' vs '
                + ((d.right && d.right.observed) || '—'))
                : 'No meaningful divergence in recorded steps.');
    });

    document.getElementById('rerun-btn')?.addEventListener('click', async () => {
        const err = document.getElementById('triage-err');
        const msg = document.getElementById('triage-msg');
        err.hidden = true;
        msg.hidden = true;
        const res = await fetch('/api/jobs/' + encodeURIComponent(jobId) + '/rerun', {
            method: 'POST', credentials: 'same-origin', headers: API_HEADERS
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            err.hidden = false;
            err.textContent = data.message || 'Rerun failed';
            return;
        }
        window.location.href = '/status?jobId=' + encodeURIComponent(data.jobId);
    });

    document.getElementById('classify-btn')?.addEventListener('click', async () => {
        const sel = document.getElementById('failure-class');
        const err = document.getElementById('triage-err');
        const msg = document.getElementById('triage-msg');
        err.hidden = true;
        msg.hidden = true;
        const res = await fetch('/api/jobs/' + encodeURIComponent(jobId) + '/classification', {
            method: 'POST', credentials: 'same-origin',
            headers: Object.assign({ 'Content-Type': 'application/json' }, API_HEADERS),
            body: JSON.stringify({ kind: sel ? sel.value : 'ASSERTION' })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            err.hidden = false;
            err.textContent = data.message || 'Classification failed';
            return;
        }
        msg.hidden = false;
        msg.textContent = 'Saved ' + data.failureClass
            + (data.failureClassUserCorrected ? ' (corrected from ' + data.failureClassSuggested + ')' : '');
    });

    const vdLink = document.getElementById('visual-diffs-exec-link');
    if (vdLink && projectId) {
        vdLink.href = '/execute?projectId=' + encodeURIComponent(projectId) + '#visual-diffs-panel';
    }

    loadTcList();
    loadTriage();
})();

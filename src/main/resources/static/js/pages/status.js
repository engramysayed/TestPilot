/* Extracted from templates/status.html. Server values arrive via window.KEEL_PAGE (set inline by the template). */
(function () {
    const root = document.getElementById('status-root');
    const jobId = (root && root.getAttribute('data-job-id'))
        || new URLSearchParams(window.location.search).get('jobId')
        || '';
    const jobKind = (root && root.getAttribute('data-job-kind')) || 'CONVERT';
    const isExecute = jobKind === 'EXECUTE';
    const isGenerateBatch = jobKind === 'GENERATE_BATCH';
    const isGenerateCompare = jobKind === 'GENERATE_COMPARE';
    const isHunt = jobKind === 'HUNT';
    const failEl = document.getElementById('fail-js');
    const bar = document.getElementById('progress-bar');
    const liveProgress = document.getElementById('live-progress');
    const track = document.getElementById('progress-track');
    const busySpinner = document.getElementById('job-busy-spinner');
    const elapsedEl = document.getElementById('job-elapsed');
    let elapsedTimer = null;
    const initialCreatedAt = root && root.getAttribute('data-created-at');
    let elapsedStart = parseCreatedAt(initialCreatedAt);
    if (Number.isNaN(elapsedStart)) {
        elapsedStart = Date.now();
    }

    function parseCreatedAt(iso) {
        if (!iso) return NaN;
        const t = Date.parse(iso);
        return Number.isNaN(t) ? NaN : t;
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function isActiveStatus(status) {
        return status === 'QUEUED' || status === 'RUNNING' || status === 'CANCELLING';
    }

    function formatElapsed(ms) {
        const totalSec = Math.max(0, Math.floor(ms / 1000));
        const m = Math.floor(totalSec / 60);
        const s = totalSec % 60;
        return m + ':' + String(s).padStart(2, '0');
    }

    function setBusy(active, createdAtIso) {
        const parsed = parseCreatedAt(createdAtIso);
        if (!Number.isNaN(parsed)) {
            elapsedStart = parsed;
        }
        if (liveProgress) {
            liveProgress.setAttribute('aria-busy', active ? 'true' : 'false');
        }
        if (busySpinner) {
            busySpinner.hidden = !active;
        }
        if (track) {
            track.classList.toggle('progress-track--indeterminate', active);
        }
        if (bar) {
            bar.classList.toggle('progress-fill--indeterminate', active);
        }
        if (elapsedEl) {
            elapsedEl.hidden = !active;
        }
        if (active) {
            if (elapsedEl) {
                elapsedEl.textContent = 'Working for ' + formatElapsed(Date.now() - elapsedStart);
            }
            if (!elapsedTimer) {
                elapsedTimer = setInterval(function () {
                    elapsedEl.textContent = 'Working for ' + formatElapsed(Date.now() - elapsedStart);
                }, 1000);
            }
        } else if (elapsedTimer) {
            clearInterval(elapsedTimer);
            elapsedTimer = null;
        }
    }

    setBusy(isActiveStatus(root && root.getAttribute('data-initial-status')), initialCreatedAt);
    const downloadWrap = document.getElementById('download-wrap');
    const executeWrap = document.getElementById('execute-wrap');
    const compareWrap = document.getElementById('compare-wrap');
    const compareResultsLink = document.getElementById('compare-results-link');
    const bugReportLink = document.getElementById('bug-report-link');
    const failurePackLink = document.getElementById('failure-pack-link');
    const executeResultsLink = document.getElementById('execute-results-link');
    const cancelWrap = document.getElementById('cancel-wrap');
    const cancelBtn = document.getElementById('cancel-btn');
    let cancelRequested = false;

    const evidenceLink = document.getElementById('evidence-link');
    if (isExecute && jobId) {
        if (executeResultsLink) {
            executeResultsLink.href = '/execute?jobId=' + encodeURIComponent(jobId);
        }
        if (evidenceLink) {
            evidenceLink.href = '/evidence?jobId=' + encodeURIComponent(jobId);
        }
        try {
            sessionStorage.setItem('keel.lastExecuteJobId', jobId);
        } catch (e) { /* ignore */ }
    }

    function showFail(text) {
        failEl.hidden = false;
        failEl.textContent = text;
    }

    function updateCancelUi(status) {
        if (!cancelWrap) return;
        const active = isActiveStatus(status);
        cancelWrap.hidden = !active;
        if (!cancelBtn) return;
        if (cancelRequested && active) {
            cancelBtn.disabled = true;
            cancelBtn.textContent = 'Stopping…';
        } else if (active) {
            cancelBtn.disabled = false;
            cancelBtn.textContent = 'Force stop';
        }
    }

    async function requestCancel() {
        if (!jobId || cancelRequested) return;
        cancelRequested = true;
        updateCancelUi('RUNNING');
        if (cancelBtn) {
            cancelBtn.disabled = true;
            cancelBtn.textContent = 'Stopping…';
        }
        try {
            const res = await fetch('/api/jobs/' + encodeURIComponent(jobId) + '/force-stop', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json', 'X-Keel-Requested-With': 'Keel' }
            });
            if (!res.ok) {
                cancelRequested = false;
                if (cancelBtn) {
                    cancelBtn.disabled = false;
                    cancelBtn.textContent = 'Force stop';
                }
                let data = {};
                try { data = await res.json(); } catch (e) { /* ignore */ }
                showFail(data.message || ('Force stop failed HTTP ' + res.status));
            }
        } catch (e) {
            cancelRequested = false;
            if (cancelBtn) {
                cancelBtn.disabled = false;
                cancelBtn.textContent = 'Force stop';
            }
            showFail('Network error while force-stopping');
        }
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', requestCancel);
    }

    let refundCheckedFor = null;
    function render(data) {
        if (data && data.status && data.status !== refundCheckedFor
                && (data.status === 'FAILED' || data.status === 'COMPLETED_WITH_BLOCK')) {
            refundCheckedFor = data.status;
            loadRefund(data.status);
        }
        document.getElementById('status').textContent = data.status || '—';
        setBusy(isActiveStatus(data.status || ''), data.createdAt);
        const cur = data.progressCurrent || 0;
        const tot = data.progressTotal || 0;
        document.getElementById('progress').textContent = cur + ' / ' + tot;
        document.getElementById('message').textContent = data.message || '—';
        const precisionBanner = document.getElementById('precision-fallback-banner');
        if (precisionBanner) {
            const msgText = String(data.message || '');
            precisionBanner.hidden = !msgText.includes('PRECISION_FALLBACK');
        }
        document.getElementById('passed').textContent = data.passedCount != null ? data.passedCount : 0;
        document.getElementById('todo').textContent = data.todoCount != null ? data.todoCount : 0;
        const proofEl = document.getElementById('proof-kind');
        if (proofEl) {
            const kind = data.proofKind || 'UNCHECKED';
            const labels = {
                FRESH: 'Fresh browser proof',
                REUSED: 'Reused prior proof',
                BLOCKED: 'Blocked / incomplete',
                UNCHECKED: 'Not executed',
                SIMULATED: 'Simulated (dry-run)',
                INTERRUPTED: 'Interrupted — review before replay'
            };
            proofEl.textContent = labels[kind] || kind;
        }
        const sourceEl = document.getElementById('proof-source');
        if (sourceEl) {
            const sources = {
                BROWSER_PROOF: 'Live browser proof',
                FRAMEWORK_REPLAY: 'Generated-framework replay (not live browser proof)',
                SIMULATED: 'Simulated dry-run',
                UNCHECKED: 'Not executed'
            };
            sourceEl.textContent = sources[data.proofSource] || data.proofSource || '—';
        }
        const revEl = document.getElementById('library-revision');
        if (revEl) {
            const rev = data.libraryRevisionId || '';
            if (rev && data.projectId) {
                const href = '/projects/' + encodeURIComponent(data.projectId)
                    + '?tab=tcs&revision=' + encodeURIComponent(rev) + '#lib-history';
                revEl.innerHTML = '<a class="mono" href="' + href + '">' + escapeHtml(rev) + '</a>';
            } else {
                revEl.textContent = rev || '—';
            }
        }
        const provEl = document.getElementById('provider-allowlist');
        if (provEl) provEl.textContent = data.providerAllowlist || '—';
        const usedEl = document.getElementById('providers-used');
        if (usedEl) usedEl.textContent = data.providersUsed || '—';
        const fbEl = document.getElementById('fallback-used');
        if (fbEl) {
            if (data.fallbackUsed) {
                fbEl.textContent = (data.fallbackFrom || 'precision') + ' → ' + (data.fallbackTo || 'keel')
                    + (data.fallbackReason ? ' (' + data.fallbackReason + ')' : '');
            } else {
                fbEl.textContent = 'None';
            }
        }
        const visionEl = document.getElementById('vision-chip');
        if (visionEl) {
            const v = data.vision;
            if (!v || !v.usedVision) {
                visionEl.textContent = 'DOM / no vision evidence';
            } else {
                visionEl.textContent = 'Vision used (' + (v.attemptCount || 0) + ' assert, '
                    + (v.successfulFallbackCount || 0) + ' ok)';
            }
        }
        const dlEl = document.getElementById('download-state');
        if (dlEl) {
            if (data.artifactExpired) dlEl.textContent = 'Expired / unavailable';
            else if (data.downloadAvailable) dlEl.textContent = 'Available for this job';
            else dlEl.textContent = 'Not downloadable';
        }
        const nextEl = document.getElementById('next-action');
        if (nextEl) nextEl.textContent = data.nextAction || '—';
        const active = isActiveStatus(data.status || '');
        const pct = tot > 0 ? Math.min(100, Math.round((cur / tot) * 100))
            : (data.status === 'COMPLETED' || data.status === 'COMPLETED_WITH_BLOCK' ? 100 : 0);
        if (bar) {
            if (active) {
                bar.style.width = pct + '%';
            } else if (data.status === 'COMPLETED' || data.status === 'COMPLETED_WITH_BLOCK') {
                bar.style.width = '100%';
            } else if (data.status !== 'FAILED' && data.status !== 'CANCELLED') {
                bar.style.width = pct + '%';
            }
        }
        updateCancelUi(data.status || '');
        const kind = data.jobKind || jobKind;
        const executeJob = kind === 'EXECUTE';
        const generateBatchJob = kind === 'GENERATE_BATCH';
        const generateCompareJob = kind === 'GENERATE_COMPARE';
        const huntJob = kind === 'HUNT';
        const blockBanner = document.getElementById('block-banner');
        if (blockBanner) {
            blockBanner.hidden = executeJob || generateBatchJob || generateCompareJob || huntJob
                || data.status !== 'COMPLETED_WITH_BLOCK';
        }
        if (generateCompareJob) {
            if (downloadWrap) downloadWrap.hidden = true;
            if (executeWrap) executeWrap.hidden = true;
            if (compareWrap) compareWrap.hidden = false;
            if (compareResultsLink && data.projectId) {
                const href = '/generate?projectId='
                    + encodeURIComponent(data.projectId)
                    + '&compareJobId='
                    + encodeURIComponent(jobId);
                compareResultsLink.href = href;
                compareResultsLink.hidden = data.status !== 'COMPLETED';
            }
            if (data.status === 'FAILED') {
                showFail(data.message || 'Model comparison failed');
                return true;
            }
            if (data.status === 'CANCELLED') {
                showFail(data.message || 'Model comparison cancelled');
                return true;
            }
            if (data.status === 'COMPLETED') {
                return true;
            }
            return false;
        }
        if (executeJob) {
            if (downloadWrap) downloadWrap.hidden = true;
            if (executeWrap) executeWrap.hidden = false;
            const terminal = data.status === 'COMPLETED'
                || data.status === 'COMPLETED_WITH_BLOCK'
                || data.status === 'FAILED'
                || data.status === 'CANCELLED';
            if (evidenceLink) evidenceLink.hidden = !terminal;
            if (bugReportLink) {
                bugReportLink.hidden = !terminal;
            }
            if (failurePackLink && data.projectId) {
                const failed = data.status === 'FAILED' || data.status === 'COMPLETED_WITH_BLOCK';
                failurePackLink.hidden = !failed;
                if (failed) {
                    failurePackLink.href = '/api/projects/'
                        + encodeURIComponent(data.projectId)
                        + '/jobs/' + encodeURIComponent(jobId)
                        + '/failure-pack';
                }
            }
            if (data.status === 'FAILED') {
                showFail(data.message || 'Execute run failed');
                return true;
            }
            if (data.status === 'CANCELLED') {
                showFail(data.message || 'Execute run cancelled');
                return true;
            }
            if (data.status === 'COMPLETED' || data.status === 'COMPLETED_WITH_BLOCK') {
                return true;
            }
            return false;
        }
        if (generateBatchJob) {
            if (data.status === 'FAILED') {
                const parts = [];
                if (data.error && String(data.error).trim()) {
                    parts.push(String(data.error).trim());
                }
                if (data.message && String(data.message).trim()) {
                    const msg = String(data.message).trim();
                    if (!parts.includes(msg)) {
                        parts.push(msg);
                    }
                }
                showFail(parts.length ? parts.join(' — ') : 'Generate batch failed');
                return true;
            }
            if (data.status === 'CANCELLED') {
                showFail(data.message || 'Generate batch cancelled');
                return true;
            }
        }
        if (data.status === 'COMPLETED' || data.status === 'COMPLETED_WITH_BLOCK') {
            if (downloadWrap) downloadWrap.hidden = executeJob;
            if (downloadWrap && generateBatchJob && data.status !== 'COMPLETED') {
                downloadWrap.hidden = true;
            }
            const downloadBtn = document.getElementById('download');
            if (downloadBtn && generateBatchJob) {
                downloadBtn.textContent = 'Download CSV';
            }
            if (downloadBtn && huntJob) {
                downloadBtn.textContent = 'Download hunter pack';
            }
            return true;
        }
        if (data.status === 'FAILED') {
            if (huntJob && downloadWrap && data.downloadable) {
                downloadWrap.hidden = false;
                const downloadBtn = document.getElementById('download');
                if (downloadBtn) downloadBtn.textContent = 'Download hunter pack';
            }
            showFail(data.message || 'Job failed');
            return true;
        }
        if (data.status === 'CANCELLED') {
            showFail(data.message || 'Job cancelled');
            return true;
        }
        return false;
    }

    let triageLoaded = false;
    async function loadTriage(data) {
        if (triageLoaded) return;
        triageLoaded = true;
        const classSel = document.getElementById('failure-class');
        if (classSel && data.failureClass) classSel.value = data.failureClass;
        try {
            const [attemptsRes, interRes] = await Promise.all([
                fetch('/api/jobs/' + encodeURIComponent(jobId) + '/attempts', {
                    credentials: 'same-origin', headers: { 'Accept': 'application/json' }
                }),
                fetch('/api/jobs/' + encodeURIComponent(jobId) + '/intermittency', {
                    credentials: 'same-origin', headers: { 'Accept': 'application/json' }
                })
            ]);
            const attempts = attemptsRes.ok ? await attemptsRes.json() : { attempts: [] };
            const inter = interRes.ok ? await interRes.json() : {};
            const list = document.getElementById('attempts-list');
            const rows = attempts.attempts || [];
            if (list) {
                list.textContent = rows.length
                    ? rows.map(r => r.jobId + ' · ' + r.status
                        + (r.parentJobId ? ' (rerun of ' + r.parentJobId + ')' : ' (original)')).join(' · ')
                    : 'This is the only attempt.';
            }
            const sel = document.getElementById('compare-other');
            if (sel) {
                sel.innerHTML = '';
                rows.filter(r => r.jobId !== jobId).forEach(r => {
                    const opt = document.createElement('option');
                    opt.value = r.jobId;
                    opt.textContent = r.jobId + ' · ' + r.status;
                    sel.appendChild(opt);
                });
            }
            const verdict = document.getElementById('intermittency-verdict');
            if (verdict) {
                verdict.textContent = 'Intermittency: ' + (inter.verdict || '—')
                    + ' (sample ' + (inter.sampleSize || 0) + ', min ' + (inter.minSample || 3) + ')';
            }
        } catch (e) {
            triageLoaded = false;
        }
    }

    const compareBtn = document.getElementById('compare-btn');
    if (compareBtn) {
        compareBtn.addEventListener('click', async () => {
            const sel = document.getElementById('compare-other');
            const out = document.getElementById('compare-result');
            if (!sel || !sel.value) {
                if (out) { out.hidden = false; out.textContent = 'Select another attempt to compare.'; }
                return;
            }
            const res = await fetch('/api/jobs/' + encodeURIComponent(jobId) + '/compare?other='
                + encodeURIComponent(sel.value), {
                credentials: 'same-origin', headers: { 'Accept': 'application/json' }
            });
            const data = await res.json().catch(() => ({}));
            if (out) {
                out.hidden = false;
                if (!res.ok) {
                    out.textContent = data.message || 'Compare failed';
                    return;
                }
                const d = data.divergence;
                out.textContent = (data.pinsMatch ? 'Pinned inputs match. ' : 'Pinned inputs differ. ')
                    + (d
                        ? ('First divergence at step ' + d.index + ' (' + d.field + '): '
                            + ((d.left && d.left.observed) || '—') + ' vs '
                            + ((d.right && d.right.observed) || '—'))
                        : 'No meaningful divergence in recorded steps.');
            }
        });
    }
    async function loadRefund(statusNow) {
        const wrap = document.getElementById('refund-wrap');
        if (!wrap || !jobId) return;
        if (statusNow !== 'FAILED' && statusNow !== 'COMPLETED_WITH_BLOCK') { wrap.hidden = true; return; }
        const stateEl = document.getElementById('refund-state');
        const form = document.getElementById('refund-form');
        const res = await fetch('/api/jobs/' + encodeURIComponent(jobId) + '/refund-request', {
            credentials: 'same-origin', headers: { 'Accept': 'application/json', 'X-Keel-Requested-With': 'Keel' }
        });
        if (res.ok) {
            const r = await res.json();
            wrap.hidden = false; form.hidden = true;
            stateEl.textContent = r.status === 'PENDING'
                ? 'Refund requested (' + r.chargedAmount + ' credits) — waiting for the account owner.'
                : r.status === 'APPROVED'
                    ? 'Refunded ' + r.refundedAmount + ' credits.' + (r.adminNote ? ' Note: ' + r.adminNote : '')
                    : 'Refund declined.' + (r.adminNote ? ' Reason: ' + r.adminNote : '');
            return;
        }
        if (typeof creditsEnabled !== 'undefined' && !creditsEnabled) return;
        wrap.hidden = false; form.hidden = false; stateEl.textContent = '';
    }
    const refundBtn = document.getElementById('refund-btn');
    if (refundBtn) {
        refundBtn.addEventListener('click', async () => {
            const note = document.getElementById('refund-note').value;
            const res = await fetch('/api/jobs/' + encodeURIComponent(jobId) + '/refund-request', {
                method: 'POST', credentials: 'same-origin',
                headers: { 'Accept': 'application/json', 'Content-Type': 'application/json', 'X-Keel-Requested-With': 'Keel' },
                body: JSON.stringify({ note: note })
            });
            const data = await res.json().catch(() => ({}));
            const stateEl = document.getElementById('refund-state');
            if (!res.ok) { stateEl.textContent = data.message || 'Could not request a refund.'; return; }
            document.getElementById('refund-form').hidden = true;
            stateEl.textContent = data.message;
        });
    }
    loadRefund(document.querySelector('[data-initial-status]')?.dataset.initialStatus);

    const rerunBtn = document.getElementById('rerun-btn');
    if (rerunBtn) {
        rerunBtn.addEventListener('click', async () => {
            const err = document.getElementById('triage-err');
            const msg = document.getElementById('triage-msg');
            if (err) err.hidden = true;
            if (msg) msg.hidden = true;
            const res = await fetch('/api/jobs/' + encodeURIComponent(jobId) + '/rerun', {
                method: 'POST',
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json', 'X-Keel-Requested-With': 'Keel' }
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                if (err) { err.hidden = false; err.textContent = data.message || 'Rerun failed'; }
                return;
            }
            window.location.href = '/status?jobId=' + encodeURIComponent(data.jobId);
        });
    }
    const classifyBtn = document.getElementById('classify-btn');
    if (classifyBtn) {
        classifyBtn.addEventListener('click', async () => {
            const sel = document.getElementById('failure-class');
            const err = document.getElementById('triage-err');
            const msg = document.getElementById('triage-msg');
            if (err) err.hidden = true;
            if (msg) msg.hidden = true;
            const res = await fetch('/api/jobs/' + encodeURIComponent(jobId) + '/classification', {
                method: 'POST',
                credentials: 'same-origin',
                headers: {
                    'Accept': 'application/json',
                    'Content-Type': 'application/json',
                    'X-Keel-Requested-With': 'Keel'
                },
                body: JSON.stringify({ kind: sel ? sel.value : 'ASSERTION' })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                if (err) { err.hidden = false; err.textContent = data.message || 'Classification failed'; }
                return;
            }
            if (msg) {
                msg.hidden = false;
                msg.textContent = 'Saved ' + data.failureClass
                    + (data.failureClassUserCorrected ? ' (corrected from ' + data.failureClassSuggested + ')' : '');
            }
        });
    }

    async function poll() {
        if (!jobId) {
            showFail('Missing jobId in status URL');
            return;
        }
        try {
            const res = await fetch('/api/jobs/' + encodeURIComponent(jobId), {
                credentials: 'same-origin',
                headers: { 'Accept': 'application/json' }
            });
            let data = {};
            try {
                data = await res.json();
            } catch (e) {
                showFail('Invalid job status response (HTTP ' + res.status + ')');
                setTimeout(poll, 1500);
                return;
            }
            if (!res.ok) {
                showFail(data.message || ('Job status error HTTP ' + res.status));
                if (res.status === 401 || res.status === 403) {
                    return;
                }
                setTimeout(poll, 1500);
                return;
            }
            failEl.hidden = true;
            const done = render(data);
            if (done && !isExecute) {
                loadTriage(data);
            }
            if (done && isExecute && evidenceLink && data.primaryTcId) {
                evidenceLink.href = '/evidence?jobId=' + encodeURIComponent(jobId)
                    + '&tcId=' + encodeURIComponent(data.primaryTcId);
            }
            if (!done) {
                setTimeout(poll, 600);
            }
        } catch (e) {
            showFail('Network error while polling job status');
            setTimeout(poll, 2000);
        }
    }

    poll();
})();

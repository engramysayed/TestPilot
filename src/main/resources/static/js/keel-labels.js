/* Plain-language labels for engine enums. Raw values stay in data-raw / option values. */
(function () {
    var STATUS = {
        QUEUED: 'Waiting to start', RUNNING: 'Running', CANCELLING: 'Stopping',
        COMPLETED: 'Done', COMPLETED_WITH_BLOCK: 'Needs attention',
        FAILED: 'Needs attention', CANCELLED: 'Stopped', BLOCKED: "Couldn't complete", TODO: "Couldn't complete",
        PASS: 'Passed', PASSED: 'Passed', FAIL: 'Needs attention', PARTIAL: 'Partly passed',
        HEALED: 'Fixed itself', FIXED: 'Fixed itself', FLAKY: 'Flaky',
        INFRA_ERROR: "Keel's side", CONFIG_ERROR: 'Setup problem', LEGACY_PASSED: 'Legacy', REUSED: 'Reused'
    };
    var PATH = {
        AUTOMATE: 'Automate & run', EXECUTE: 'Run only', VISION_ONLY: 'Screenshot check only',
        MANUAL: 'Manual (skipped)', '': 'Default'
    };
    var KIND = { EXECUTE: 'Test run', CONVERSION: 'Code build', AUTOMATE: 'Code build',
        GENERATE: 'Write test cases', GENERATE_BATCH: 'Write test cases (many)',
        GENERATE_COMPARE: 'Compare AI models', HUNT: 'Bug Hunter' };
    function look(map, v) {
        var k = (v == null ? '' : String(v)).trim().toUpperCase();
        return Object.prototype.hasOwnProperty.call(map, k) ? map[k] : v;
    }
    var api = {
        status: function (v) { return look(STATUS, v); },
        path: function (v) { return look(PATH, v); },
        kind: function (v) { return look(KIND, v); },
        chipClass: function (v) {
            var k = (v == null ? '' : String(v)).trim().toUpperCase();
            if (k === 'PASS' || k === 'PASSED' || k === 'COMPLETED' || k === 'LEGACY_PASSED') return 'chip--pass';
            if (k === 'HEALED' || k === 'FIXED' || k === 'REUSED') return 'chip--fix';
            if (k === 'FLAKY' || k === 'PARTIAL') return 'chip--flaky';
            return 'chip--bad';
        },
        /** Translate any element marked data-friendly="status|path|kind" whose text is a raw enum. */
        apply: function (root) {
            (root || document).querySelectorAll('[data-friendly]').forEach(function (el) {
                var map = el.getAttribute('data-friendly');
                var raw = (el.textContent || '').trim();
                if (!raw || raw === el.getAttribute('data-friendly-out')) return;
                var out = map === 'path' ? api.path(raw) : map === 'kind' ? api.kind(raw) : api.status(raw);
                if (out !== raw) {
                    el.setAttribute('data-raw', raw);
                    el.setAttribute('data-friendly-out', out);
                    el.textContent = out;
                }
            });
        }
    };
    window.KeelLabels = api;
    function start() {
        api.apply(document);
        new MutationObserver(function () { api.apply(document); })
            .observe(document.body, { childList: true, subtree: true, characterData: true });
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();

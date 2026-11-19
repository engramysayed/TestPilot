(function () {
    'use strict';

    function escapeHtml(s) {
        return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
            return ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'})[c];
        });
    }

    function elById(id) { return document.getElementById(id); }

    function render(data) {
        var status = elById('preflight-status');
        var list = elById('preflight-list');
        if (!status || !list) return;
        var allOk = !!data.allOk;
        status.textContent = allOk ? 'All systems go.' : 'Some checks failed.';
        status.className = 'preflight-modal__status ' + (allOk ? 'ok' : 'fail');
        list.innerHTML = '';
        (data.checks || []).forEach(function (c) {
            var li = document.createElement('li');
            li.className = 'preflight-item ' + (c.ok ? 'ok' : 'fail');
            li.innerHTML = '<span class="preflight-item__icon">' + (c.ok ? '&#10003;' : '&#10007;') + '</span>'
                + '<span class="preflight-item__name">' + escapeHtml(c.name) + '</span>'
                + '<span class="preflight-item__detail">' + escapeHtml(c.detail) + '</span>';
            list.appendChild(li);
        });
    }

    function runChecks() {
        var status = elById('preflight-status');
        var list = elById('preflight-list');
        if (status) { status.textContent = 'Running checks…'; status.className = 'preflight-modal__status'; }
        if (list) list.innerHTML = '';
        fetch('/api/preflight', { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
            .then(function (r) {
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.json();
            })
            .then(render)
            .catch(function (e) {
                if (status) {
                    status.textContent = 'Could not reach pre-flight endpoint: ' + (e && e.message ? e.message : e);
                    status.className = 'preflight-modal__status fail';
                }
            });
    }

    function open() {
        var m = elById('preflight-modal');
        if (!m) return;
        m.hidden = false;
        runChecks();
    }

    function close() {
        var m = elById('preflight-modal');
        if (m) m.hidden = true;
    }

    document.addEventListener('click', function (e) {
        var t = e.target;
        if (!t) return;
        if (t.closest && t.closest('[data-preflight-open]')) { open(); return; }
        if (t.closest && t.closest('[data-preflight-close]')) { close(); return; }
        if (t.closest && t.closest('[data-preflight-rerun]')) { runChecks(); return; }
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            var m = elById('preflight-modal');
            if (m && !m.hidden) close();
        }
    });
})();

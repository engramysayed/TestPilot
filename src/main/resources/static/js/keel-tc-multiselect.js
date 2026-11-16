/* Unified library case multi-select (Execute / Automate / GFK / Bug Hunter). */
(function (global) {
    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    /**
     * @param {object} cfg
     * @param {HTMLElement|string} cfg.root — .tc-ms element or its id
     * @param {string} [cfg.checkboxClass='lib-pick-check']
     * @param {function} [cfg.onChange]
     */
    function mount(cfg) {
        var root = typeof cfg.root === 'string' ? document.getElementById(cfg.root) : cfg.root;
        if (!root) return null;
        var panel = root.closest('.tc-multiselect') || root.parentElement;
        var list = root.querySelector('.tc-ms-list')
            || (cfg.listId && document.getElementById(cfg.listId))
            || document.getElementById('library-pick-list')
            || document.getElementById('gfk-library-pick-list');
        var trigger = root.querySelector('.tc-ms-trigger');
        var drop = root.querySelector('.tc-ms-dropdown');
        var search = root.querySelector('.tc-ms-search');
        var chips = root.querySelector('.tc-ms-chips');
        var summary = root.querySelector('.tc-ms-summary');
        var btnAll = root.querySelector('[data-tc-ms-all]')
            || (panel && panel.querySelector('[data-tc-ms-all]'))
            || (cfg.selectAllId && document.getElementById(cfg.selectAllId))
            || document.getElementById('lib-pick-all')
            || document.getElementById('gfk-lib-pick-all');
        var btnNone = root.querySelector('[data-tc-ms-none]')
            || (panel && panel.querySelector('[data-tc-ms-none]'))
            || (cfg.selectNoneId && document.getElementById(cfg.selectNoneId))
            || document.getElementById('lib-pick-none')
            || document.getElementById('gfk-lib-pick-none');
        var checkboxClass = cfg.checkboxClass || 'lib-pick-check';
        var onChange = cfg.onChange || function () {};

        function checkboxes() {
            return Array.prototype.slice.call(root.querySelectorAll('.' + checkboxClass));
        }

        function close() {
            if (drop) drop.hidden = true;
            if (trigger) trigger.setAttribute('aria-expanded', 'false');
            root.classList.remove('is-open');
        }

        function open() {
            if (!drop || !trigger) return;
            drop.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            root.classList.add('is-open');
            if (search) {
                search.value = '';
                filterList('');
                search.focus();
            }
        }

        function filterList(q) {
            var needle = String(q || '').trim().toLowerCase();
            root.querySelectorAll('.tc-ms-option').forEach(function (el) {
                var hay = el.dataset.search || '';
                el.hidden = needle !== '' && hay.indexOf(needle) < 0;
            });
        }

        function refreshSummary() {
            if (!summary) return;
            var selected = checkboxes().filter(function (cb) { return cb.checked; });
            var total = checkboxes().length;
            if (chips) chips.innerHTML = '';
            if (selected.length === 0) {
                summary.textContent = total ? 'All cases (' + total + ')' : 'All cases';
                summary.hidden = false;
                onChange(selected, total);
                return;
            }
            var show = selected.slice(0, 4);
            if (chips) {
                show.forEach(function (cb) {
                    var chip = document.createElement('span');
                    chip.className = 'tc-ms-chip';
                    chip.textContent = cb.value;
                    chips.appendChild(chip);
                });
                if (selected.length > 4) {
                    var more = document.createElement('span');
                    more.className = 'tc-ms-chip tc-ms-chip-more';
                    more.textContent = '+' + (selected.length - 4);
                    chips.appendChild(more);
                }
            }
            summary.textContent = selected.length + ' of ' + total;
            summary.hidden = false;
            onChange(selected, total);
        }

        function setCases(cases) {
            if (!list) return;
            list.innerHTML = '';
            close();
            (cases || []).forEach(function (tc) {
                var tcId = tc.tcId || tc.id;
                if (!tcId) return;
                var label = document.createElement('label');
                label.className = 'tc-ms-option generate-check';
                label.dataset.search = ((tcId || '') + ' ' + (tc.title || '')).toLowerCase();
                label.innerHTML = '<input type="checkbox" class="' + escapeHtml(checkboxClass) + '" value="'
                    + escapeHtml(tcId) + '"/> <span class="mono">' + escapeHtml(tcId) + '</span> '
                    + '<span class="tc-ms-option-title">' + escapeHtml(tc.title || '') + '</span>';
                var pick = label.querySelector('input');
                if (pick && tc.keelPath) pick.dataset.keelPath = tc.keelPath;
                pick.addEventListener('change', refreshSummary);
                list.appendChild(label);
            });
            refreshSummary();
        }

        function getSelectedIds() {
            return checkboxes().filter(function (cb) { return cb.checked; }).map(function (cb) { return cb.value; });
        }

        if (trigger) {
            trigger.addEventListener('click', function (e) {
                e.preventDefault();
                if (drop && drop.hidden) open();
                else close();
            });
        }
        if (search) search.addEventListener('input', function (e) { filterList(e.target.value); });
        if (btnAll) {
            btnAll.addEventListener('click', function () {
                checkboxes().forEach(function (cb) {
                    if (!cb.closest('.tc-ms-option') || !cb.closest('.tc-ms-option').hidden) cb.checked = true;
                });
                refreshSummary();
            });
        }
        if (btnNone) {
            btnNone.addEventListener('click', function () {
                checkboxes().forEach(function (cb) { cb.checked = false; });
                refreshSummary();
            });
        }
        document.addEventListener('click', function (e) {
            if (!root.contains(e.target) && !(panel && panel.contains(e.target))) close();
        });
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') close();
        });

        return {
            setCases: setCases,
            getSelectedIds: getSelectedIds,
            refreshSummary: refreshSummary,
            close: close
        };
    }

    async function fetchLibraryCases(projectId) {
        var res = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/generated-workbook/cases', {
            credentials: 'same-origin',
            headers: { 'X-Keel-Requested-With': 'Keel', 'Accept': 'application/json' }
        });
        if (!res.ok) return { ok: false, status: res.status, cases: [] };
        var data = await res.json();
        var cases = (data && Array.isArray(data.cases)) ? data.cases : (Array.isArray(data) ? data : []);
        return { ok: true, status: res.status, cases: cases };
    }

    global.KeelTcMultiselect = { mount: mount, fetchLibraryCases: fetchLibraryCases };
})(window);
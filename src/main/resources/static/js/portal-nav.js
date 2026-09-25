(function () {
    const input = document.getElementById('globalSearch');
    if (!input) return;

    const API_HEADERS = { 'Accept': 'application/json', 'X-Keel-Requested-With': 'Keel' };

    function showHint(message) {
        let el = document.getElementById('globalSearch-hint');
        if (!el) {
            el = document.createElement('p');
            el.id = 'globalSearch-hint';
            el.className = 'global-search-hint muted';
            el.setAttribute('role', 'status');
            input.closest('.topbar-search')?.appendChild(el);
        }
        el.textContent = message;
        el.hidden = !message;
    }

    async function navigate(query) {
        const q = query.trim();
        if (!q) return;
        try {
            const res = await fetch('/api/portal/search?q=' + encodeURIComponent(q), {
                credentials: 'same-origin',
                headers: API_HEADERS
            });
            if (res.ok) {
                const hits = await res.json();
                if (Array.isArray(hits) && hits.length > 0) {
                    window.location.href = hits[0].url;
                    return;
                }
            }
        } catch (e) {
            /* fall through */
        }
        showHint('No match — try Overview, Projects, Runs, Generate, or a project name.');
    }

    input.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            showHint('');
            navigate(input.value);
        }
        if (e.key === 'Escape') {
            input.value = '';
            showHint('');
            input.blur();
        }
    });
})();

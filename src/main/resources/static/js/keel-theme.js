(function () {
    var KEY = 'keel-theme';
    function current() {
        return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }
    function apply(theme, opts) {
        var t = theme === 'dark' ? 'dark' : 'light';
        var persist = !opts || opts.persist !== false;
        document.documentElement.setAttribute('data-theme', t);
        if (persist) {
            try { localStorage.setItem(KEY, t); } catch (e) { /* ignore */ }
        }
        var btn = document.getElementById('keel-theme-toggle');
        if (btn) {
            var dark = t === 'dark';
            /* Label is the action / destination theme */
            btn.textContent = dark ? 'Light theme' : 'Dark theme';
            btn.setAttribute('aria-pressed', dark ? 'true' : 'false');
        }
    }
    function isAuthShell() {
        var root = document.documentElement;
        if (root.classList.contains('auth-shell')) return true;
        var body = document.body;
        return !!(body && (body.classList.contains('login-body') || body.classList.contains('auth-body')));
    }
    function init() {
        if (isAuthShell()) {
            /* Keep login/request-access/invite on light tokens; leave stored app pref untouched */
            rootForceLight();
            return;
        }
        var stored = null;
        try { stored = localStorage.getItem(KEY); } catch (e) { /* ignore */ }
        if (stored === 'dark' || stored === 'light') apply(stored);
        else apply('dark'); /* dark-default: no stored preference */
        var btn = document.getElementById('keel-theme-toggle');
        if (btn && !btn._keelBound) {
            btn._keelBound = true;
            btn.addEventListener('click', function () {
                apply(current() === 'dark' ? 'light' : 'dark');
            });
        }
    }
    function rootForceLight() {
        document.documentElement.classList.add('auth-shell');
        apply('light', { persist: false });
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
    window.KeelTheme = { apply: apply, current: current };
})();

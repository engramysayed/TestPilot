(function () {
    var KEY = 'keel-theme';
    function current() {
        return document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    }
    function apply(theme) {
        var t = theme === 'dark' ? 'dark' : 'light';
        document.documentElement.setAttribute('data-theme', t);
        try { localStorage.setItem(KEY, t); } catch (e) { /* ignore */ }
        var btn = document.getElementById('keel-theme-toggle');
        if (btn) {
            var dark = t === 'dark';
            btn.textContent = dark ? 'Light theme' : 'Dark theme';
            btn.setAttribute('aria-pressed', dark ? 'true' : 'false');
        }
    }
    function init() {
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
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
    window.KeelTheme = { apply: apply, current: current };
})();

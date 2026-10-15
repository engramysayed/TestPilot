/**
 * Pack 11 — reusable Keel confirm / alert modal (Promise-based).
 * Matches .keel-modal-backdrop / .keel-modal chrome used on admin pages.
 */
(function (global) {
  'use strict';

  var STYLE_ID = 'keel-confirm-style';
  var ROOT_ID = 'keel-confirm-root';

  function ensureChrome() {
    if (!document.getElementById(STYLE_ID)) {
      var s = document.createElement('style');
      s.id = STYLE_ID;
      s.textContent =
        '#' + ROOT_ID + '.keel-modal-backdrop{position:fixed;inset:0;background:rgba(15,23,42,.45);'
        + 'display:flex;align-items:center;justify-content:center;z-index:90}'
        + '#' + ROOT_ID + '[hidden]{display:none!important}'
        + '#' + ROOT_ID + ' .keel-modal{width:min(420px,92vw);background:var(--surface,var(--calm-card,#fff));'
        + 'border-radius:12px;border:1px solid var(--calm-line,var(--border,#d0d7de));'
        + 'padding:1.1rem 1.25rem;box-shadow:0 12px 40px rgba(0,0,0,.18);color:var(--calm-ink,inherit)}'
        + '#' + ROOT_ID + ' .keel-modal h3{margin:0 0 .35rem;font-size:1.05rem}'
        + '#' + ROOT_ID + ' .keel-modal .lede{margin:0 0 .9rem;color:var(--muted,#656d76);font-size:.9rem;line-height:1.45}'
        + '#' + ROOT_ID + ' .keel-modal-actions{display:flex;gap:.5rem;justify-content:flex-end;margin-top:1rem}'
        + '#' + ROOT_ID + ' .keel-modal-actions .danger{background:var(--calm-bad,#b23b3b);border-color:var(--calm-bad,#b23b3b);color:#fff}';
      document.head.appendChild(s);
    }
    var root = document.getElementById(ROOT_ID);
    if (!root) {
      root = document.createElement('div');
      root.id = ROOT_ID;
      root.className = 'keel-modal-backdrop';
      root.hidden = true;
      root.setAttribute('role', 'presentation');
      root.innerHTML =
        '<div class="keel-modal" role="dialog" aria-modal="true" aria-labelledby="keel-confirm-title">'
        + '<h3 id="keel-confirm-title"></h3>'
        + '<p class="lede" id="keel-confirm-msg"></p>'
        + '<div class="keel-modal-actions">'
        + '<button type="button" class="button ghost" id="keel-confirm-cancel">Cancel</button>'
        + '<button type="button" class="button primary" id="keel-confirm-ok">OK</button>'
        + '</div></div>';
      document.body.appendChild(root);
    }
    return root;
  }

  function openDialog(opts) {
    opts = opts || {};
    var root = ensureChrome();
    var title = root.querySelector('#keel-confirm-title');
    var msg = root.querySelector('#keel-confirm-msg');
    var ok = root.querySelector('#keel-confirm-ok');
    var cancel = root.querySelector('#keel-confirm-cancel');
    title.textContent = opts.title || 'Confirm';
    msg.textContent = opts.message || '';
    ok.textContent = opts.confirmLabel || (opts.alertOnly ? 'OK' : 'Confirm');
    cancel.hidden = !!opts.alertOnly;
    ok.classList.toggle('danger', !!opts.danger);
    ok.classList.toggle('primary', !opts.danger);
    root.hidden = false;

    return new Promise(function (resolve) {
      function finish(val) {
        root.hidden = true;
        ok.removeEventListener('click', onOk);
        cancel.removeEventListener('click', onCancel);
        root.removeEventListener('click', onBackdrop);
        document.removeEventListener('keydown', onKey);
        resolve(val);
      }
      function onOk() { finish(true); }
      function onCancel() { finish(false); }
      function onBackdrop(e) { if (e.target === root) finish(false); }
      function onKey(e) {
        if (e.key === 'Escape') finish(false);
        if (e.key === 'Enter') finish(true);
      }
      ok.addEventListener('click', onOk);
      cancel.addEventListener('click', onCancel);
      root.addEventListener('click', onBackdrop);
      document.addEventListener('keydown', onKey);
      setTimeout(function () { ok.focus(); }, 0);
    });
  }

  global.keelConfirm = function (opts) {
    if (typeof opts === 'string') opts = { message: opts };
    return openDialog(opts || {});
  };

  global.keelAlert = function (opts) {
    if (typeof opts === 'string') opts = { message: opts };
    opts = opts || {};
    opts.alertOnly = true;
    opts.title = opts.title || 'Notice';
    opts.confirmLabel = opts.confirmLabel || 'OK';
    return openDialog(opts).then(function () { return true; });
  };
})(typeof window !== 'undefined' ? window : this);

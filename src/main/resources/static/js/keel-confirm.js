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
        + '<input type="text" id="keel-confirm-input" hidden style="width:100%;margin:.25rem 0 .5rem"/>'
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
    var input = root.querySelector('#keel-confirm-input');
    input.hidden = !opts.withInput;
    input.value = opts.defaultValue || '';
    input.placeholder = opts.placeholder || '';
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
      function onOk() { finish(opts.withInput ? input.value : true); }
      function onCancel() { finish(opts.withInput ? null : false); }
      function onBackdrop(e) { if (e.target === root) onCancel(); }
      function onKey(e) {
        if (e.key === 'Escape') onCancel();
        if (e.key === 'Enter') onOk();
      }
      ok.addEventListener('click', onOk);
      cancel.addEventListener('click', onCancel);
      root.addEventListener('click', onBackdrop);
      document.addEventListener('keydown', onKey);
      setTimeout(function () { (opts.withInput ? input : ok).focus(); }, 0);
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
  /** Text input dialog. Resolves to the string, or null when cancelled (native prompt semantics). */
  global.keelPrompt = function (opts) {
    if (typeof opts === 'string') opts = { message: opts };
    opts = opts || {};
    opts.withInput = true;
    opts.title = opts.title || 'Input';
    opts.confirmLabel = opts.confirmLabel || 'OK';
    return openDialog(opts);
  };

  /** Non-blocking notice in the corner (replaces alert() for info messages). */
  global.keelToast = function (message, kind) {
    var host = document.getElementById('keel-toast-host');
    if (!host) {
      host = document.createElement('div');
      host.id = 'keel-toast-host';
      host.setAttribute('role', 'status');
      host.setAttribute('aria-live', 'polite');
      host.style.cssText = 'position:fixed;inset-inline-end:1rem;inset-block-end:1rem;display:flex;flex-direction:column;gap:.5rem;z-index:95';
      document.body.appendChild(host);
    }
    var t = document.createElement('div');
    t.textContent = String(message == null ? '' : message);
    t.style.cssText = 'max-width:min(420px,90vw);padding:.6rem .9rem;border-radius:8px;box-shadow:0 6px 20px rgba(0,0,0,.18);'
      + 'background:' + (kind === 'error' ? 'var(--calm-bad,#b23b3b)' : 'var(--surface,#fff)') + ';'
      + 'color:' + (kind === 'error' ? '#fff' : 'var(--calm-ink,inherit)') + ';border:1px solid var(--calm-line,#d0d7de)';
    host.appendChild(t);
    setTimeout(function () { t.remove(); }, 6000);
  };
})(typeof window !== 'undefined' ? window : this);

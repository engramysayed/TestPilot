(function () {
  var topWindow = window;
  try { topWindow = window.top; } catch (e) { topWindow = window; }

  var rec = window.__keelRec || {
    gen: Math.random().toString(36).slice(2) + Date.now().toString(36),
    steps: [],
    check: false
  };
  var already = !!(window.__keelRec && window.__keelRec.installed);
  rec.installed = true;
  window.__keelRec = rec;
  if (already) {
    installBar();
    return 'ok';
  }

  function clean(text, max) {
    return String(text || '').replace(/\s+/g, ' ').trim().slice(0, max || 80);
  }

  function trustworthy(value) {
    var v = clean(value, 80);
    if (!v || v.length < 2) return false;
    if (/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}/i.test(v)) return false;
    if (/\d{6,}/.test(v)) return false;
    if (/^_+r_+|^:+r[0-9a-z]*:+$|^(ember|mui|radix|svelte|css|emotion|ng)-/i.test(v)) return false;
    var words = v.match(/[a-zA-Z]{3,}/g);
    return !!(words && words.length);
  }

  function cssEscape(value) {
    return String(value || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  }

  function locator(el) {
    if (!el || el.nodeType !== 1) return { s: '', v: '' };
    var testId = el.getAttribute('data-testid');
    if (trustworthy(testId)) return { s: 'data-testid', v: clean(testId, 80) };
    var dataTest = el.getAttribute('data-test');
    if (trustworthy(dataTest)) return { s: 'data-test', v: clean(dataTest, 80) };
    var dataQa = el.getAttribute('data-qa');
    if (trustworthy(dataQa)) return { s: 'data-qa', v: clean(dataQa, 80) };
    if (el.id && trustworthy(el.id)) return { s: 'id', v: clean(el.id, 80) };
    var name = el.getAttribute('name');
    if (trustworthy(name)) return { s: 'name', v: clean(name, 80) };
    var aria = el.getAttribute('aria-label');
    if (trustworthy(aria)) {
      return { s: 'css', v: el.tagName.toLowerCase() + '[aria-label="' + cssEscape(clean(aria, 80)) + '"]' };
    }
    var text = clean(el.innerText || el.textContent || '', 60);
    if (text && text.length <= 60 && el.closest) {
      var tag = el.tagName.toLowerCase();
      if (tag === 'a' || tag === 'button' || tag === 'summary' || el.getAttribute('role')) {
        return { s: 'xpath', v: '//' + tag + '[contains(normalize-space(.),"' + text.replace(/"/g, '') + '")]' };
      }
    }
    return { s: '', v: '' };
  }

  function label(el) {
    if (!el || el.nodeType !== 1) return '';
    var aria = el.getAttribute && el.getAttribute('aria-label');
    if (aria && clean(aria)) return clean(aria);
    var labelled = el.getAttribute && el.getAttribute('aria-labelledby');
    if (labelled) {
      var parts = [];
      labelled.split(/\s+/).forEach(function (id) {
        var node = document.getElementById(id);
        if (node) parts.push(node.innerText || node.textContent || '');
      });
      if (clean(parts.join(' '))) return clean(parts.join(' '));
    }
    if (el.id) {
      var labels = document.getElementsByTagName('label');
      for (var i = 0; i < labels.length; i++) {
        if (labels[i].htmlFor === el.id) {
          var via = clean(labels[i].innerText || labels[i].textContent || '');
          if (via) return via;
        }
      }
    }
    var wrap = el.closest ? el.closest('label') : null;
    if (wrap) {
      var wrapped = clean(wrap.innerText || wrap.textContent || '');
      if (wrapped) return wrapped;
    }
    var hinted = el.getAttribute && (el.getAttribute('placeholder') || el.getAttribute('name') || el.getAttribute('title'));
    if (hinted && clean(hinted)) return clean(hinted);
    var visible = clean(el.innerText || el.textContent || '');
    if (visible) return visible;
    return el.id ? clean(el.id) : '';
  }

  function store(step) {
    if (!step || !step.k) return;
    var loc = step.s ? { s: step.s, v: step.v || '' } : { s: '', v: '' };
    var safe = {
      k: clean(step.k, 20),
      label: clean(step.label, 120),
      value: clean(step.value, 200),
      secret: !!step.secret,
      s: clean(loc.s, 20),
      v: clean(loc.v, 300)
    };
    if (safe.secret) safe.value = '';
    var last = rec.steps[rec.steps.length - 1];
    if (safe.k === 'type' && last && last.k === 'type' && last.label === safe.label) {
      rec.steps[rec.steps.length - 1] = safe;
      return;
    }
    if (rec.steps.length >= 80) return;
    rec.steps.push(safe);
    paintCount();
  }

  function inChildFrame() {
    try {
      return window.parent !== window;
    } catch (e) {
      return true;
    }
  }

  function push(step) {
    if (inChildFrame()) {
      try {
        topWindow.postMessage({ source: 'keel-record', step: step }, location.origin);
      } catch (e) { /* cross-origin frame: the parent records the top page only */ }
      return;
    }
    store(step);
  }

  if (!inChildFrame()) {
    window.addEventListener('message', function (ev) {
      if (ev.origin !== location.origin) return;
      var data = ev.data;
      if (!data || data.source !== 'keel-record' || !data.step) return;
      store(data.step);
    });
  }

  function targetOf(raw) {
    if (!raw || !raw.closest) return raw;
    return raw.closest('a,button,summary,label,input,textarea,select,[role="button"],[role="link"],[role="tab"],[role="menuitem"]') || raw;
  }

  document.addEventListener('click', function (ev) {
    try {
      if (ev.target && ev.target.closest && ev.target.closest('#keel-rec-bar')) return;
      var el = targetOf(ev.target);
      if (!el) return;
      if (rec.check) {
        ev.preventDefault();
        ev.stopPropagation();
        var text = clean(el.innerText || el.textContent || label(el), 120);
        if (!text) return;
        var found = locator(el);
        push({ k: 'assert', label: text, value: '', secret: false, s: found.s, v: found.v });
        flash('Checked "' + text.slice(0, 42) + '"');
        return;
      }
      var clickable = el.closest ? el.closest('a,button,summary,[role="button"],[role="link"],[role="tab"],[role="menuitem"]') : null;
      if (!clickable) return;
      var foundClick = locator(clickable);
      push({ k: 'click', label: label(clickable), value: '', secret: false, s: foundClick.s, v: foundClick.v });
    } catch (e) {}
  }, true);

  function onEdit(el) {
    try {
      if (!el || !el.tagName || rec.check) return;
      var tag = el.tagName.toLowerCase();
      var found = locator(el);
      if (tag === 'select') {
        var opt = el.options && el.selectedIndex >= 0 ? el.options[el.selectedIndex] : null;
        var chosen = opt ? (opt.label || opt.text || opt.value || '') : '';
        push({ k: 'select', label: label(el), value: chosen, secret: false, s: found.s, v: found.v });
        return;
      }
      if (tag !== 'input' && tag !== 'textarea') return;
      var type = (el.getAttribute('type') || 'text').toLowerCase();
      if (type === 'hidden' || type === 'file' || type === 'submit' || type === 'button') return;
      if (type === 'password') {
        push({ k: 'type', label: label(el), value: '', secret: true, s: found.s, v: found.v });
        return;
      }
      if (type === 'checkbox' || type === 'radio') {
        push({ k: el.checked ? 'check' : 'uncheck', label: label(el), value: '', secret: false, s: found.s, v: found.v });
        return;
      }
      var val = el.value || '';
      if (!String(val).trim()) return;
      push({ k: 'type', label: label(el), value: val, secret: false, s: found.s, v: found.v });
    } catch (e) {}
  }

  document.addEventListener('change', function (ev) { onEdit(ev.target); }, true);
  var pending;
  document.addEventListener('input', function (ev) {
    var el = ev.target;
    window.clearTimeout(pending);
    pending = window.setTimeout(function () { onEdit(el); }, 400);
  }, true);
  document.addEventListener('keydown', function (ev) {
    if (ev.key !== 'Enter' || rec.check) return;
    var tag = ev.target && ev.target.tagName ? ev.target.tagName.toLowerCase() : '';
    if (tag !== 'input' && tag !== 'textarea') return;
    var found = locator(ev.target);
    push({ k: 'press', label: 'Enter', value: '', secret: false, s: found.s, v: found.v });
  }, true);

  if (!inChildFrame()) {
    var lastHref = location.href;
    function noteNav() {
      try {
        if (location.href === lastHref) return;
        lastHref = location.href;
        store({ k: 'open', label: location.href, value: '', secret: false, s: '', v: '' });
      } catch (e) {}
    }
    window.addEventListener('popstate', noteNav);
    var pushState = history.pushState;
    var replaceState = history.replaceState;
    if (typeof pushState === 'function') {
      history.pushState = function () {
        var result = pushState.apply(this, arguments);
        noteNav();
        return result;
      };
    }
    if (typeof replaceState === 'function') {
      history.replaceState = function () {
        var result = replaceState.apply(this, arguments);
        noteNav();
        return result;
      };
    }
    window.setInterval(noteNav, 500);
    installBar();
  }

  function paintCount() {
    var node = document.getElementById('keel-rec-count');
    if (!node) return;
    var n = rec.steps.filter(function (step) { return step.s && step.k !== 'open'; }).length;
    var checks = rec.steps.filter(function (step) { return step.k === 'assert' || step.k === 'assert-url'; }).length;
    node.textContent = n + (n === 1 ? ' locator' : ' locators') + (checks ? ' · ' + checks + ' check' + (checks === 1 ? '' : 's') : '');
  }

  function flash(text) {
    var node = document.getElementById('keel-rec-count');
    if (!node) return;
    node.textContent = text;
    window.setTimeout(paintCount, 1200);
  }

  function installBar() {
    if (inChildFrame()) return;
    if (!document.documentElement) {
      document.addEventListener('DOMContentLoaded', installBar, { once: true });
      return;
    }
    if (document.getElementById('keel-rec-bar')) return;
    var bar = document.createElement('div');
    bar.id = 'keel-rec-bar';
    bar.setAttribute('style', [
      'position:fixed', 'left:50%', 'bottom:18px', 'transform:translateX(-50%)',
      'z-index:2147483646', 'display:flex', 'gap:8px', 'align-items:center',
      'background:#143f38', 'color:#f4f7f6', 'border-radius:999px',
      'padding:8px 10px 8px 14px', 'font:13px/1.2 Segoe UI,sans-serif',
      'box-shadow:0 10px 30px rgba(0,0,0,.28)'
    ].join(';'));
    var brand = document.createElement('strong');
    brand.textContent = 'Keel';
    var count = document.createElement('span');
    count.id = 'keel-rec-count';
    count.setAttribute('style', 'opacity:.8;margin-right:6px');
    var stepsBtn = button('Steps', true);
    var checkBtn = button('Check this', false);
    var pageBtn = button('This page', false);
    stepsBtn.onclick = function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      rec.check = false;
      mark(stepsBtn, true);
      mark(checkBtn, false);
    };
    checkBtn.onclick = function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      rec.check = true;
      mark(stepsBtn, false);
      mark(checkBtn, true);
      flash('Click the text that must be on the page');
    };
    pageBtn.onclick = function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      push({ k: 'assert-url', label: location.href, value: location.href, secret: false, s: '', v: '' });
      flash('This page will be checked');
    };
    bar.appendChild(brand);
    bar.appendChild(count);
    bar.appendChild(stepsBtn);
    bar.appendChild(checkBtn);
    bar.appendChild(pageBtn);
    document.documentElement.appendChild(bar);
    paintCount();
  }

  function button(text, on) {
    var b = document.createElement('button');
    b.type = 'button';
    b.textContent = text;
    b.setAttribute('style', 'border:0;border-radius:999px;padding:6px 10px;cursor:pointer;font:inherit');
    mark(b, on);
    return b;
  }

  function mark(b, on) {
    b.style.background = on ? '#f4f7f6' : 'transparent';
    b.style.color = on ? '#143f38' : '#f4f7f6';
  }

  return 'ok';
})();

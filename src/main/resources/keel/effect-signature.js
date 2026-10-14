// A7 action-effect DOM signature. arguments[0] = target element or null, arguments[1] = mask selectors (array).
// Returns {url, focus, digest, nodes, target}. Text inside masked (volatile) subtrees is ignored.
var target = arguments[0], masks = arguments[1] || [];
function masked(el) {
  for (var i = 0; i < masks.length; i++) { try { if (el.closest(masks[i])) return true; } catch (e) {} }
  return false;
}
function h(s, acc) { for (var i = 0; i < s.length; i++) { acc = ((acc << 5) - acc + s.charCodeAt(i)) | 0; } return acc; }
var acc = 7, nodes = 0;
var walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT);
for (var n = walker.currentNode; n; n = walker.nextNode()) {
  if (n.nodeType === 1) {
    if (masks.length && masked(n)) continue;
    nodes++;
    acc = h(n.tagName, acc);
    var cs = null;
    if (n.hidden) acc = h('hidden', acc);
    if (n.value !== undefined && typeof n.value === 'string' && n.tagName !== 'BUTTON' && n.tagName !== 'OPTION') acc = h('v=' + n.value, acc);
    if (n.checked) acc = h('checked', acc);
    if (n.open) acc = h('open', acc);
    var st = n.getAttribute('style'); if (st) acc = h(st, acc);
    var cl = n.getAttribute('class'); if (cl) acc = h(cl, acc);
    ['aria-expanded','aria-pressed','aria-checked','aria-selected','aria-hidden','disabled'].forEach(function (a) {
      var v = n.getAttribute(a); if (v !== null) acc = h(a + '=' + v, acc);
    });
  } else {
    var p = n.parentElement;
    if (p && masks.length && masked(p)) continue;
    var t = n.nodeValue.replace(/\s+/g, ' ').trim(); if (t) acc = h(t, acc);
  }
}
var a = document.activeElement, focus = '';
if (a && target && (a === target || target.contains(a))) focus = '@target';
else if (a && a !== document.body) focus = a.tagName.toLowerCase() + '#' + (a.id || '') + '[' + (a.getAttribute('name') || a.getAttribute('aria-label') || '') + ']';
var ts = null;
if (target && target.isConnected) {
  ts = {};
  ['aria-expanded','aria-pressed','aria-checked','aria-selected','aria-disabled','disabled','class','open','data-state'].forEach(function (k) {
    var v = target.getAttribute(k); if (v !== null) ts[k] = v;
  });
  if (typeof target.value === 'string') ts.value = target.value;
  if (target.checked !== undefined) ts.checked = String(!!target.checked);
  ts.text = (target.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 200);
} else if (target) {
  ts = {detached: 'true'};
}
return {url: location.href, focus: focus, digest: String(acc), nodes: nodes, target: ts ? JSON.stringify(ts) : ''};

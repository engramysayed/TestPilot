// A9 bind-failure capture. Returns {html, url, title, boxes:[{x,y,w,h,kind}], texts:[{t,x,y,w,h}]} in device px.
// Input values are never serialized: value attributes and live values are replaced with a mask.
var dpr = window.devicePixelRatio || 1, MASK = '\u2022\u2022\u2022';
function box(el, kind) {
  var r = el.getBoundingClientRect();
  if (r.width <= 0 || r.height <= 0) return null;
  return {x: Math.floor(r.left * dpr) - 2, y: Math.floor(r.top * dpr) - 2, w: Math.ceil(r.width * dpr) + 4, h: Math.ceil(r.height * dpr) + 4, kind: kind};
}
var boxes = [];
document.querySelectorAll('input:not([type=hidden]):not([type=button]):not([type=submit]):not([type=reset]):not([type=checkbox]):not([type=radio]),textarea,select,[contenteditable=""],[contenteditable=true]')
  .forEach(function (el) { var b = box(el, 'input'); if (b) boxes.push(b); });
var texts = [];
var walker = document.createTreeWalker(document.body || document.documentElement, NodeFilter.SHOW_TEXT);
for (var n = walker.nextNode(); n && texts.length < 3000; n = walker.nextNode()) {
  var t = n.nodeValue.replace(/\s+/g, ' ').trim();
  if (!t || t.length > 400 || !n.parentElement) continue;
  var p = n.parentElement.tagName; if (p === 'SCRIPT' || p === 'STYLE' || p === 'NOSCRIPT') continue;
  var b = box(n.parentElement, 'text'); if (b) { b.t = t; texts.push(b); }
}
var clone = document.documentElement.cloneNode(true);
clone.querySelectorAll('script,noscript,template,iframe[srcdoc]').forEach(function (e) { e.textContent = ''; e.removeAttribute('srcdoc'); });
clone.querySelectorAll('style').forEach(function (e) { e.textContent = '/* style removed */'; });
clone.querySelectorAll('input,textarea,select,option').forEach(function (e) {
  if (e.hasAttribute('value')) e.setAttribute('value', MASK);
  if (e.tagName === 'TEXTAREA') e.textContent = MASK;
});
clone.querySelectorAll('[contenteditable]').forEach(function (e) { e.textContent = MASK; });
// live values (typed but not reflected to attributes) are exported as masked data attributes
var live = document.querySelectorAll('input,textarea,select'), cl = clone.querySelectorAll('input,textarea,select');
for (var i = 0; i < live.length && i < cl.length; i++) {
  var v = live[i].value; if (v) cl[i].setAttribute('data-keel-value', MASK + '(' + v.length + ')');
}
['src', 'href', 'action'].forEach(function (a) {
  clone.querySelectorAll('[' + a + ']').forEach(function (e) {
    var v = e.getAttribute(a); var q = v.indexOf('?'); if (q >= 0) e.setAttribute(a, v.substring(0, q) + '?[query]');
  });
});
return {html: '<!doctype html>\n' + clone.outerHTML, url: location.origin + location.pathname, title: document.title, boxes: boxes, texts: texts};

// A8 settle probe (shared with Codegen). arguments[0] = spinner selectors. Installs trackers once per document.
var w = window, sel = arguments[0] || [];
if (!w.__keelSettle) {
  var s = w.__keelSettle = {pending: 0, lastNet: Date.now(), lastMut: Date.now()};
  try {
    var of = w.fetch;
    if (of) w.fetch = function () {
      s.pending++; s.lastNet = Date.now();
      return of.apply(this, arguments).finally(function () { s.pending--; s.lastNet = Date.now(); });
    };
    var oo = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function () {
      s.pending++; s.lastNet = Date.now();
      this.addEventListener('loadend', function () { s.pending--; s.lastNet = Date.now(); });
      return oo.apply(this, arguments);
    };
  } catch (e) {}
  try {
    new MutationObserver(function () { s.lastMut = Date.now(); })
      .observe(document.documentElement, {subtree: true, childList: true, attributes: true, characterData: true});
  } catch (e) {}
}
var s = w.__keelSettle, now = Date.now();
var res = performance.getEntriesByType('resource');
var lastRes = 0;
for (var i = 0; i < res.length; i++) { var e = res[i].responseEnd || res[i].startTime; if (e > lastRes) lastRes = e; }
var resAgo = res.length ? Math.max(0, performance.now() - lastRes) : 1e9;
var anims = 0;
try {
  (document.getAnimations ? document.getAnimations() : []).forEach(function (a) {
    var t = a.effect && a.effect.getComputedTiming ? a.effect.getComputedTiming() : {};
    if (a.playState === 'running' && isFinite(t.endTime)) anims++;
  });
} catch (e) {}
var spin = 0;
sel.forEach(function (q) {
  try {
    document.querySelectorAll(q).forEach(function (el) {
      var cs = getComputedStyle(el);
      if (el.getClientRects().length && cs.visibility !== 'hidden' && cs.display !== 'none' && cs.opacity !== '0') spin++;
    });
  } catch (e) {}
});
return {url: location.href, ready: document.readyState, pending: s.pending, netIdleMs: Math.min(now - s.lastNet, resAgo),
        domQuietMs: now - s.lastMut, animations: anims, spinners: spin};

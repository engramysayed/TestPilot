// Keel page-scoped network tracker (r3; NOT part of the frozen settle-policy v1 bytes).
// arguments[0] = {ignore: [substring | "re:<regex>"], allow: [origin], scope: "page"|"all"}
// Counts only requests the page itself starts: fetch/XHR through the page's own (main-world) functions whose
// call stack is not an extension/devtools script, plus resource-timing entries of non-fetch/XHR kinds
// (img, script, css, iframe ...) from the top document and same-origin frames. Always ignored: non-http(s)
// schemes (chrome:, chrome-extension:, devtools:, data:, blob:, about: ...), other origins unless allowed,
// and the ignore list. Installs once per document; returns {pending, netIdleMs, counted, ignored}.
var w = window, cfg = arguments[0] || {};
var scopeAll = cfg.scope === 'all';
var ign = (cfg.ignore || []).map(function (p) {
  if (p.indexOf('re:') === 0) { try { var r = new RegExp(p.slice(3)); return function (u) { return r.test(u); }; } catch (e) { return function () { return false; }; } }
  return function (u) { return u.indexOf(p) >= 0; };
});
var allow = (cfg.allow || []).map(function (o) { return String(o).replace(/\/+$/, ''); });
function counts(url) {
  var u;
  try { u = new URL(url, location.href); } catch (e) { return false; }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') return false;
  for (var i = 0; i < ign.length; i++) if (ign[i](u.href)) return false;
  if (scopeAll) return true;
  return u.origin === location.origin || allow.indexOf(u.origin) >= 0;
}
var EXT = /(chrome|moz|safari-web)-extension:\/\/|devtools:\/\/|chrome:\/\//;
function fromExtension() {
  if (scopeAll) return false;
  try { return EXT.test(String(new Error().stack)); } catch (e) { return false; }
}
if (!w.__keelNet) {
  var n = w.__keelNet = {pending: 0, last: 0, counted: 0, ignored: 0};
  try {
    var of = w.fetch;
    if (of) w.fetch = function (input) {
      var url = typeof input === 'string' ? input : (input && input.url) || String(input);
      if (fromExtension() || !n.counts(url)) { n.ignored++; return of.apply(this, arguments); }
      n.pending++; n.counted++; n.last = Date.now();
      return of.apply(this, arguments).finally(function () { n.pending--; n.last = Date.now(); });
    };
    var oOpen = XMLHttpRequest.prototype.open, oSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (m, url) { this.__keelUrl = url; return oOpen.apply(this, arguments); };
    XMLHttpRequest.prototype.send = function () {
      if (fromExtension() || !n.counts(this.__keelUrl)) { n.ignored++; return oSend.apply(this, arguments); }
      n.pending++; n.counted++; n.last = Date.now();
      this.addEventListener('loadend', function () { n.pending--; n.last = Date.now(); });
      return oSend.apply(this, arguments);
    };
  } catch (e) {}
}
w.__keelNet.counts = counts; // refreshed every call: config may change between calls
var s = w.__keelNet, now = Date.now(), lastRes = -1, perfNow = performance.now();
function scan(win) {
  var res;
  try { res = win.performance.getEntriesByType('resource'); } catch (e) { return; }
  var offset = win === w ? 0 : (win.performance.timeOrigin - performance.timeOrigin);
  for (var i = 0; i < res.length; i++) {
    var e = res[i];
    if (e.initiatorType === 'fetch' || e.initiatorType === 'xmlhttprequest' || e.initiatorType === 'beacon') continue; // wrapper decides
    if (!counts(e.name)) continue;
    var end = (e.responseEnd || e.startTime) + offset;
    if (end > lastRes) lastRes = end;
  }
}
scan(w);
for (var f = 0; f < w.frames.length; f++) { try { if (w.frames[f].location.origin === location.origin) scan(w.frames[f]); } catch (e) {} }
var resAgo = lastRes < 0 ? 1e9 : Math.max(0, perfNow - lastRes);
var netAgo = s.last ? now - s.last : 1e9;
return {pending: s.pending, netIdleMs: Math.min(netAgo, resAgo), counted: s.counted, ignored: s.ignored};

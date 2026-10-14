// Keel contract v2 text matcher. SHARED FILE: Keel Codegen copies this verbatim (byte-identical).
// Usage (WebDriver executeScript): <this file> + "\nreturn keelTextMatch(arguments[0], arguments[1], arguments[2]);"
//   arguments[0] = shadow host element or null (null = current frame's document)
//   arguments[1] = "open" | "closed" | null
//   arguments[2] = decoded text literal
// Returns an Array of matching elements; the caller enforces exactly-one.
var KEEL_TEXT_MATCH_VERSION = 2;

/** Normalization: textContent -> NFC -> NBSP/figure/narrow spaces to U+0020 -> collapse \s+ -> trim. Case-sensitive. */
function keelNormalizeText(s) {
  if (s == null) return '';
  var t = String(s);
  if (t.normalize) t = t.normalize('NFC');
  t = t.replace(/[\u00A0\u2007\u202F\u200B\uFEFF]/g, function (c) { return (c === '\u200B' || c === '\uFEFF') ? '' : ' '; });
  return t.replace(/\s+/g, ' ').trim();
}

/** Interactive or nav element, rendered (has client rects; offscreen/clipped still counts), not visibility:hidden. */
function keelTextEligible(el) {
  if (!el || el.nodeType !== 1) return false;
  var tag = el.tagName.toLowerCase();
  var role = (el.getAttribute('role') || '').toLowerCase();
  var interactive = ['button','a','input','select','textarea','summary','option','label'].indexOf(tag) >= 0
    || ['button','link','checkbox','radio','switch','tab','menuitem','menuitemcheckbox','menuitemradio','option','treeitem','combobox'].indexOf(role) >= 0
    || el.hasAttribute('onclick')
    || (tag === 'li' && !el.querySelector('li') && !!el.closest('nav,aside,[role=navigation],[class*=sidebar],[class*=side-menu]'));
  if (!interactive) return false;
  if (el.getClientRects().length === 0) return false;
  var cs = el.ownerDocument.defaultView.getComputedStyle(el);
  return cs.visibility !== 'hidden' && cs.visibility !== 'collapse';
}

function keelTextOf(el) {
  var tag = el.tagName.toLowerCase();
  if (tag === 'input') {
    var type = (el.getAttribute('type') || '').toLowerCase();
    if (['button','submit','reset'].indexOf(type) >= 0) return keelNormalizeText(el.value);
    return '';
  }
  return keelNormalizeText(el.textContent);
}

function keelTextMatch(host, mode, literal) {
  var root = document;
  if (host) {
    root = mode === 'closed' ? host[Symbol.for('keel.shadowRoot')] : host.shadowRoot;
    if (!root) throw new Error('keel: shadow root not available for text= (' + mode + ')');
  }
  var want = keelNormalizeText(literal);
  var hits = [];
  var all = root.querySelectorAll('*');
  for (var i = 0; i < all.length; i++) {
    var el = all[i];
    if (keelTextEligible(el) && keelTextOf(el) === want) hits.push(el);
  }
  // Innermost wins: drop a hit that contains another hit (li > a "Orders" -> the a).
  return hits.filter(function (h) {
    for (var k = 0; k < hits.length; k++) if (hits[k] !== h && h.contains(hits[k])) return false;
    return true;
  });
}

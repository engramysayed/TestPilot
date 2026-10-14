// Keel page serializer. arguments[0] = options {maxDepth, keywords[], preferredAttrs[], maxCandidates}
// Returns {hook:boolean, frames:int, truncated:boolean, candidates:[...]}
const OPT = arguments[0] || {};
const MAX_DEPTH = Math.min(OPT.maxDepth == null ? 5 : OPT.maxDepth, 5);
const KEYWORDS = (OPT.keywords || []).map(k => String(k).toLowerCase().trim()).filter(k => k.length >= 2);
const PREF = OPT.preferredAttrs || [];
const MAX = OPT.maxCandidates || 4000;
const KEY = Symbol.for('keel.shadowRoot');
const MODE = Symbol.for('keel.shadowMode');
const STABLE = PREF.concat(['data-testid', 'data-test', 'data-qa', 'data-cy', 'name', 'aria-label']);
const ROLES = ['button','link','textbox','combobox','listbox','searchbox','checkbox','radio','switch',
  'spinbutton','slider','menuitem','menuitemcheckbox','menuitemradio','tab','treeitem','option'];
const NAV = 'nav,aside,[role=navigation],[class*=sidebar],[class*=side-menu]';
const out = []; let truncated = false; let frameCount = 0; let hookSeen = false;

function rootOf(el) { return el.getRootNode(); }
function esc(v) { return (window.CSS && CSS.escape) ? CSS.escape(v) : String(v).replace(/([^\w-])/g, '\\$1'); }
function attrSel(tag, a, v) { return tag + '[' + a + '="' + String(v).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"]'; }
function unique(root, sel, el) {
  try { const m = root.querySelectorAll(sel); return m.length === 1 && m[0] === el; } catch (e) { return false; }
}
/** CSS selector unique inside el's own root (document or shadow root). */
function selectorFor(el) {
  const root = rootOf(el); const tag = el.tagName.toLowerCase();
  if (el.id && unique(root, '#' + esc(el.id), el)) return '#' + esc(el.id);
  for (const a of STABLE) {
    const v = el.getAttribute(a);
    if (v && unique(root, attrSel(tag, a, v), el)) return attrSel(tag, a, v);
  }
  if (unique(root, tag, el)) return tag;
  for (const c of Array.from(el.classList).slice(0, 3)) {
    const sel = tag + '.' + esc(c); if (unique(root, sel, el)) return sel;
  }
  const parts = []; let cur = el;
  while (cur && cur.nodeType === 1) {
    const t = cur.tagName.toLowerCase();
    if (cur !== el && cur.id && unique(root, '#' + esc(cur.id), cur)) { parts.unshift('#' + esc(cur.id)); break; }
    let i = 1, s = cur;
    while ((s = s.previousElementSibling)) if (s.tagName === cur.tagName) i++;
    parts.unshift(t + ':nth-of-type(' + i + ')');
    const sel = parts.join(' > ');
    if (unique(root, sel, el)) return sel;
    cur = cur.parentElement;
  }
  return parts.join(' > ');
}
function hidden(el, win) {
  if (el.hidden || el.getAttribute('aria-hidden') === 'true') return true;
  const s = win.getComputedStyle(el);
  return s.display === 'none' || s.visibility === 'hidden' || s.visibility === 'collapse';
}
function accName(el, doc) {
  const al = el.getAttribute('aria-label'); if (al && al.trim()) return al.trim();
  const lb = el.getAttribute('aria-labelledby');
  if (lb) { const t = lb.split(/\s+/).map(id => { const n = doc.getElementById(id); return n ? n.textContent : ''; }).join(' ').trim(); if (t) return t; }
  // textContent, not innerText: offscreen / clipped items still have a name.
  const tx = (el.textContent || '').replace(/\s+/g, ' ').trim();
  if (tx) return tx.slice(0, 160);
  return (el.value || el.getAttribute('placeholder') || el.getAttribute('title') || el.getAttribute('alt') || '').toString().trim().slice(0, 160);
}
function ownText(el) {
  let t = ''; for (const n of el.childNodes) if (n.nodeType === 3) t += n.nodeValue; return t.replace(/\s+/g, ' ').trim();
}
function interactive(el) {
  const tag = el.tagName.toLowerCase();
  if (['button','a','input','select','textarea','summary','option','label'].includes(tag)) return true;
  if (ROLES.includes((el.getAttribute('role') || '').toLowerCase())) return true;
  if (el.getAttribute('contenteditable') === 'true' || el.hasAttribute('onclick') || typeof el.onclick === 'function') return true;
  if (el.hasAttribute('tabindex') && el.getAttribute('tabindex') !== '-1') return true;
  return tag === 'li' && !el.querySelector('li') && !!el.closest(NAV);
}
function keywordHit(el) {
  if (!KEYWORDS.length) return false;
  const t = (ownText(el) || el.getAttribute('aria-label') || el.getAttribute('placeholder') || el.getAttribute('title') || '').toLowerCase();
  return !!t && KEYWORDS.some(k => t.includes(k));
}
function scrollContainer(el, win) {
  for (let p = el.parentElement; p; p = p.parentElement) {
    const s = win.getComputedStyle(p);
    if (/(auto|scroll|overlay)/.test(s.overflowY + ' ' + s.overflowX) && (p.scrollHeight > p.clientHeight + 1 || p.scrollWidth > p.clientWidth + 1)) return p;
  }
  return null;
}
function shadowOf(el) {
  if (el.shadowRoot) return { root: el.shadowRoot, mode: 'open' };
  const r = el[KEY];
  if (r) { hookSeen = true; return { root: r, mode: el[MODE] || 'closed' }; }
  return null;
}
function emit(el, ctx, reasons) {
  if (out.length >= MAX) { truncated = true; return; }
  const win = ctx.win; const r = el.getBoundingClientRect();
  const vw = win.innerWidth, vh = win.innerHeight;
  const inViewport = r.width > 0 && r.height > 0 && r.bottom > 0 && r.right > 0 && r.top < vh && r.left < vw;
  const sc = scrollContainer(el, win);
  let clipped = false;
  if (sc) { const c = sc.getBoundingClientRect(); clipped = r.bottom <= c.top || r.top >= c.bottom || r.right <= c.left || r.left >= c.right; }
  const attrs = {};
  for (const a of ['id','name','type','role','href','aria-label','aria-expanded','placeholder','title','alt','class','for','value'].concat(STABLE)) {
    const v = el.getAttribute(a); if (v != null && v !== '' && a !== 'value') attrs[a] = String(v).slice(0, 200);
  }
  out.push({
    tag: el.tagName.toLowerCase(),
    role: (el.getAttribute('role') || '').toLowerCase(),
    name: accName(el, ctx.doc),
    attrs: attrs,
    css: selectorFor(el),
    framePath: ctx.frames.slice(),
    shadowPath: ctx.shadow.slice(),
    interactive: reasons.includes('interactive'),
    keyword: reasons.includes('keyword'),
    inViewport: inViewport,
    offscreen: !inViewport || clipped,
    scrollContainer: sc ? selectorFor(sc) : '',
    navItem: !!el.closest(NAV),
    rect: [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)]
  });
}
function walkRoot(root, ctx, depth) {
  // Iterative walk of one tree (document or shadow root); descends into shadow roots and frames.
  const stack = [root];
  while (stack.length) {
    const node = stack.pop();
    const kids = node.children ? Array.from(node.children) : [];
    const descend = [];
    for (let i = 0; i < kids.length; i++) {  // document order for emission
      const el = kids[i];
      const tag = el.tagName.toLowerCase();
      if (['script','style','noscript','template','meta','link'].includes(tag)) continue;
      if (hidden(el, ctx.win)) continue;  // offscreen is kept; display:none / visibility:hidden is not
      const reasons = [];
      if (interactive(el)) reasons.push('interactive');
      if (keywordHit(el)) reasons.push('keyword');
      if (reasons.length) emit(el, ctx, reasons);
      const sh = shadowOf(el);
      if (sh && ctx.shadow.length < 5) {
        walkRoot(sh.root, { win: ctx.win, doc: ctx.doc, frames: ctx.frames,
          shadow: ctx.shadow.concat([{ host: selectorFor(el), mode: sh.mode }]) }, depth);
      }
      if ((tag === 'iframe' || tag === 'frame') && depth < MAX_DEPTH) {
        frameCount++;
        let fdoc = null, fwin = null;
        try { fdoc = el.contentDocument; fwin = el.contentWindow; } catch (e) { fdoc = null; }
        const hop = { selector: selectorFor(el), index: frameIndex(el, ctx.win), shadowPath: ctx.shadow.slice(),
          name: el.getAttribute('name') || '', src: (el.getAttribute('src') || '').slice(0, 200),
          crossOrigin: !fdoc };
        if (fdoc && fdoc.documentElement) {
          walkRoot(fdoc.documentElement, { win: fwin, doc: fdoc, frames: ctx.frames.concat([hop]), shadow: [] }, depth + 1);
        } else {
          out.push({ tag: 'iframe', role: '', name: hop.name || hop.src, attrs: {}, css: hop.selector,
            framePath: ctx.frames.concat([hop]), shadowPath: [], interactive: false, keyword: false,
            inViewport: false, offscreen: false, scrollContainer: '', navItem: false, rect: [0,0,0,0], unreadable: true });
        }
      }
      descend.push(el);
    }
    for (let i = descend.length - 1; i >= 0; i--) stack.push(descend[i]);
  }
}
function frameIndex(el, win) {
  try { for (let i = 0; i < win.frames.length; i++) if (win.frames[i] === el.contentWindow) return i; } catch (e) {}
  return -1;
}
walkRoot(document.documentElement, { win: window, doc: document, frames: [], shadow: [] }, 0);
return { hook: !!window.__keelShadowHook, closedRootsSeen: hookSeen, frames: frameCount, truncated: truncated, candidates: out };

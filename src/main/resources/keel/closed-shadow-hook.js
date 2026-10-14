// Keel closed-shadow hook. MUST run before any page script (CDP Page.addScriptToEvaluateOnNewDocument).
// Keeps a reference to every shadow root (open or closed) on its host under a non-enumerable symbol.
(function () {
  try {
    var KEY = Symbol.for('keel.shadowRoot');
    var proto = Element.prototype;
    if (proto.__keelShadowHooked) return;
    var original = proto.attachShadow;
    if (typeof original !== 'function') return;
    Object.defineProperty(proto, '__keelShadowHooked', { value: true });
    proto.attachShadow = function (init) {
      var root = original.call(this, init);
      try {
        Object.defineProperty(this, KEY, { value: root, configurable: true });
        Object.defineProperty(this, Symbol.for('keel.shadowMode'),
            { value: (init && init.mode) || 'open', configurable: true });
      } catch (e) { /* frozen host: ignore */ }
      return root;
    };
    window.__keelShadowHook = true;
  } catch (e) { /* never break the page */ }
})();

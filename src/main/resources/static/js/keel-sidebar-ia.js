(function () {
  function $(id) { return document.getElementById(id); }
  async function getJson(url) {
    try {
      var res = await fetch(url, { credentials: 'same-origin', headers: { 'Accept': 'application/json', 'X-Keel-Requested-With': 'Keel' } });
      if (!res.ok) return null;
      return await res.json();
    } catch (e) { return null; }
  }
  function hasPerm(perms, code) {
    if (!Array.isArray(perms)) return false;
    if (perms.indexOf(code) >= 0) return true;
    if (code.indexOf('feature.') === 0) {
      return perms.indexOf('job.' + code.substring('feature.'.length)) >= 0;
    }
    return false;
  }
  async function refreshCredits() {
    var chip = $('keel-credits-chip');
    var bal = $('keel-credits-balance');
    if (!chip || !bal) return;
    var data = await getJson('/api/account/credits');
    if (!data || typeof data.balance !== 'number') { chip.hidden = true; return; }
    bal.textContent = String(data.balance);
    chip.hidden = false;
  }
  async function gateNav() {
    var banner = $('keel-pending-banner');
    var me = await getJson('/api/me/permissions');
    if (!me) return;
    var approved = me.approvalStatus === 'APPROVED';
    if (banner) banner.hidden = approved;
    var perms = approved ? (me.permissions || []) : [];
    var featureLinks = document.querySelectorAll('[data-feature-perm]');
    var anyVisible = false;
    featureLinks.forEach(function (a) {
      var code = a.getAttribute('data-feature-perm');
      var ok = approved && hasPerm(perms, code);
      a.hidden = !ok;
      if (ok) anyVisible = true;
    });
    var label = document.querySelector('[data-features-label]');
    if (label) label.hidden = !anyVisible;
    var path = location.pathname || '';
    var featurePaths = { '/generate': 'feature.generate', '/generate-from-knowledge': 'feature.generate', '/record': 'feature.generate', '/plain-cases': 'feature.execute', '/suites': 'feature.execute', '/execute': 'feature.execute', '/automate': 'feature.automate', '/bug-hunter': 'feature.hunt' };
    var need = featurePaths[path];
    if (need && (!approved || !hasPerm(perms, need))) {
      var main = document.querySelector('main.shell');
      if (main && !main.querySelector('.keel-feature-locked')) {
        var box = document.createElement('section');
        box.className = 'panel keel-feature-locked';
        var msg = approved
          ? 'Your role does not include this feature.'
          : 'Your account is pending approval. An admin must approve you and choose a role before you can use this page. Credits are not added automatically.';
        box.innerHTML = '<h2>Not available yet</h2><p class="panel-intro">' + msg + '</p>';
        main.insertBefore(box, main.firstChild);
        Array.prototype.forEach.call(main.children, function (el, i) { if (i > 0) el.hidden = true; });
      }
    }
  }
  document.addEventListener('DOMContentLoaded', function () {
    refreshCredits();
    gateNav();
  });
})();

(function () {
  var pop;
  var openBtn;

  function ensurePopover() {
    if (pop) return pop;
    pop = document.createElement('div');
    pop.className = 'keel-feature-help-popover';
    pop.setAttribute('role', 'tooltip');
    pop.hidden = true;
    document.body.appendChild(pop);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') closePopover();
    });
    document.addEventListener('click', function (e) {
      if (!openBtn || pop.hidden) return;
      if (e.target === openBtn || pop.contains(e.target)) return;
      closePopover();
    });
    return pop;
  }

  function closePopover() {
    if (!pop) return;
    pop.hidden = true;
    if (openBtn) openBtn.classList.remove('is-open');
    openBtn = null;
  }

  function positionNear(btn) {
    var r = btn.getBoundingClientRect();
    var margin = 8;
    pop.style.left = Math.min(r.left, window.innerWidth - pop.offsetWidth - margin) + 'px';
    var top = r.bottom + margin;
    if (top + pop.offsetHeight > window.innerHeight - margin) {
      top = Math.max(margin, r.top - pop.offsetHeight - margin);
    }
    pop.style.top = top + 'px';
  }

  function openPopover(btn) {
    var text = btn.getAttribute('data-help') || '';
    if (!text) return;
  ensurePopover();
    if (openBtn && openBtn !== btn) closePopover();
    openBtn = btn;
    btn.classList.add('is-open');
    var guide = btn.getAttribute('data-guide');
    var html = '<p>' + escapeHtml(text) + '</p>';
    if (guide) {
      html += '<a href="' + escapeAttr(guide) + '">More in Guide →</a>';
    }
    pop.innerHTML = html;
    pop.hidden = false;
    positionNear(btn);
    window.addEventListener('resize', onReposition, { passive: true });
    window.addEventListener('scroll', onReposition, { passive: true, capture: true });
  }

  function onReposition() {
    if (openBtn && !pop.hidden) positionNear(openBtn);
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function escapeAttr(s) {
    return String(s).replace(/"/g, '%22').replace(/'/g, '%27');
  }

  document.addEventListener('click', function (e) {
    var btn = e.target.closest('.keel-feature-help-btn');
    if (!btn) return;
    e.preventDefault();
    e.stopPropagation();
    if (openBtn === btn && pop && !pop.hidden) {
      closePopover();
      return;
    }
    openPopover(btn);
  });
})();
(function () {
  var pop;
  var openBtn;
  var POP_ID = 'keel-feature-help-popover';

  function ensurePopover() {
    if (pop) return pop;
    pop = document.createElement('div');
    pop.id = POP_ID;
    pop.className = 'keel-feature-help-popover';
    pop.setAttribute('role', 'dialog');
    pop.setAttribute('aria-modal', 'false');
    pop.setAttribute('aria-label', 'Feature help');
    pop.hidden = true;
    document.body.appendChild(pop);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') closePopover(true);
    });
    document.addEventListener('click', function (e) {
      if (!openBtn || pop.hidden) return;
      if (e.target === openBtn || pop.contains(e.target)) return;
      closePopover(false);
    });
    return pop;
  }

  function setExpanded(btn, open) {
    if (!btn) return;
    btn.setAttribute('aria-expanded', open ? 'true' : 'false');
  }

  function closePopover(restoreFocus) {
    if (!pop) return;
    pop.hidden = true;
    var prev = openBtn;
    if (openBtn) {
      openBtn.classList.remove('is-open');
      setExpanded(openBtn, false);
    }
    openBtn = null;
    window.removeEventListener('resize', onReposition);
    window.removeEventListener('scroll', onReposition, true);
    if (restoreFocus && prev) prev.focus();
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

  function onReposition() {
    if (openBtn && pop && !pop.hidden) positionNear(openBtn);
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

  function openPopover(btn) {
    var text = btn.getAttribute('data-help') || '';
    if (!text) return;
    ensurePopover();
    if (openBtn && openBtn !== btn) closePopover(false);
    openBtn = btn;
    btn.classList.add('is-open');
    setExpanded(btn, true);
    var guide = btn.getAttribute('data-guide');
    var html = '<p id="keel-feature-help-text">' + escapeHtml(text) + '</p>';
    if (guide) {
      html += '<a href="' + escapeAttr(guide) + '">More in Guide →</a>';
    }
    pop.innerHTML = html;
    pop.setAttribute('aria-describedby', 'keel-feature-help-text');
    pop.hidden = false;
    positionNear(btn);
    window.addEventListener('resize', onReposition, { passive: true });
    window.addEventListener('scroll', onReposition, { passive: true, capture: true });
    var link = pop.querySelector('a');
    if (link) {
      link.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
          e.preventDefault();
          closePopover(true);
        }
      });
    }
  }

  function togglePopover(btn) {
    if (openBtn === btn && pop && !pop.hidden) {
      closePopover(true);
      return;
    }
    openPopover(btn);
  }

  document.addEventListener('click', function (e) {
    var btn = e.target.closest('.keel-feature-help-btn');
    if (!btn) return;
    e.preventDefault();
    e.stopPropagation();
    togglePopover(btn);
  });

  document.addEventListener('keydown', function (e) {
    var btn = e.target.closest('.keel-feature-help-btn');
    if (!btn) return;
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      togglePopover(btn);
    }
  });

  document.querySelectorAll('.keel-feature-help-btn').forEach(function (btn) {
    if (!btn.getAttribute('aria-controls')) btn.setAttribute('aria-controls', POP_ID);
    if (!btn.hasAttribute('aria-expanded')) btn.setAttribute('aria-expanded', 'false');
  });
})();
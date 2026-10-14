/**
 * Keel Credits + account approval helpers (Final lock).
 * GET /api/account/credits; INSUFFICIENT_CREDITS / ACCOUNT_NOT_APPROVED / ACCOUNT_LOCKED.
 */
(function (global) {
  var API_HEADERS = { 'Accept': 'application/json', 'X-Keel-Requested-With': 'Keel' };

  function $(id) { return document.getElementById(id); }

  function formatInsufficient(body) {
    var required = body && body.required != null ? body.required : '?';
    var balance = body && body.balance != null ? body.balance : '?';
    var msg = (body && body.message) || 'Not enough credits to start this job.';
    return msg + ' You need ' + required + '; you have ' + balance + '. Ask your Keel admin to add credits after payment.';
  }

  function formatAccountBlocked(body) {
    var code = body && body.code;
    if (code === 'ACCOUNT_LOCKED') {
      return (body && body.message) || 'Your account is locked. Contact your Keel admin.';
    }
    return (body && body.message) || 'Your account is pending approval. You cannot start jobs until an admin approves you.';
  }

  function isCreditsError(body) {
    return body && body.code === 'INSUFFICIENT_CREDITS';
  }

  function isApprovalError(body) {
    return body && (body.code === 'ACCOUNT_NOT_APPROVED' || body.code === 'ACCOUNT_LOCKED');
  }

  /** Returns plain message if body is a credits/approval gate error; else null. */
  function gateMessage(body) {
    if (isCreditsError(body)) return formatInsufficient(body);
    if (isApprovalError(body)) return formatAccountBlocked(body);
    return null;
  }

  function showBanner(el, text) {
    if (!el) return;
    el.hidden = false;
    el.textContent = text;
  }

  async function refreshChromeBalance() {
    var chip = $('keel-credits-chip');
    var label = $('keel-credits-chip-label');
    if (!chip || !label) return null;
    try {
      var res = await fetch('/api/account/credits', { credentials: 'same-origin', headers: API_HEADERS });
      if (!res.ok) {
        chip.hidden = true;
        return null;
      }
      var data = await res.json();
      if (!data.enabled) {
        chip.hidden = true;
        return data;
      }
      chip.hidden = false;
      var bal = data.balance != null ? data.balance : 0;
      label.textContent = bal + ' credits';
      chip.classList.toggle('keel-credits-chip--low', bal < 10);
      chip.title = 'Keel Credits remaining';
      var pending = $('keel-approval-banner');
      if (!pending) {
        pending = document.createElement('div');
        pending.id = 'keel-approval-banner';
        pending.className = 'keel-approval-banner';
        pending.hidden = true;
        var main = document.querySelector('main.shell') || document.querySelector('main');
        if (main) main.insertBefore(pending, main.firstChild);
      }
      if (pending) {
        var st = data.approvalStatus || '';
        if (st && st !== 'APPROVED') {
          pending.hidden = false;
          pending.textContent = st === 'LOCKED'
            ? 'Your account is locked. Feature jobs are unavailable.'
            : st === 'REJECTED'
              ? 'Your account was not approved. Feature jobs are unavailable.'
              : 'Your account is pending approval. You can look around, but jobs stay locked until an admin approves you.';
        } else {
          pending.hidden = true;
        }
      }
      return data;
    } catch (e) {
      return null;
    }
  }

  function accountApprovedFromPage() {
    var meta = document.body && document.body.getAttribute('data-approval-status');
    return meta === 'APPROVED';
  }

  function blockIfNotApproved(errEl) {
    if (accountApprovedFromPage()) return false;
    var msg = 'Your account is pending approval. You cannot start this job yet.';
    var st = document.body.getAttribute('data-approval-status');
    if (st === 'LOCKED') msg = 'Your account is locked. You cannot start this job.';
    if (st === 'REJECTED') msg = 'Your account was not approved. You cannot start this job.';
    if (errEl) {
      errEl.hidden = false;
      errEl.textContent = msg;
    } else {
      alert(msg);
    }
    return true;
  }

  global.KeelCredits = {
    API_HEADERS: API_HEADERS,
    gateMessage: gateMessage,
    formatInsufficient: formatInsufficient,
    formatAccountBlocked: formatAccountBlocked,
    isCreditsError: isCreditsError,
    isApprovalError: isApprovalError,
    refreshChromeBalance: refreshChromeBalance,
    blockIfNotApproved: blockIfNotApproved,
    showBanner: showBanner
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', refreshChromeBalance);
  } else {
    refreshChromeBalance();
  }
})(window);

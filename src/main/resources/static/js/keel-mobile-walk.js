/**
 * Before an Android Automate or Execute start: say which phone and which app,
 * and refuse to start when that phone is busy or unplugged.
 */
(function (global) {
  'use strict';

  function headers() {
    return { 'X-Keel-Requested-With': 'Keel' };
  }

  global.keelMobileWalkPlan = async function (projectId, deviceId) {
    let url = '/api/projects/' + encodeURIComponent(projectId) + '/mobile/walk-plan';
    if (deviceId !== undefined && deviceId !== null) {
      url += '?deviceId=' + encodeURIComponent(deviceId);
    }
    const res = await fetch(url, {
      credentials: 'same-origin',
      headers: headers()
    });
    const data = await res.json().catch(function () { return {}; });
    if (!res.ok) {
      return {
        blocked: true,
        phoneState: 'OFFLINE',
        message: data.message || 'Could not check the phone.'
      };
    }
    return data;
  };

  /**
   * @param projectId
   * @param mode 'automate' allows continuing with no phone (text shells). 'execute' does not.
   * @returns the plan when the user confirmed, or null when they cancelled or the phone blocked the start.
   */
  global.keelConfirmMobileWalk = async function (projectId, mode, picked) {
    const explicit = picked && Object.prototype.hasOwnProperty.call(picked, 'deviceId');
    const plan = await global.keelMobileWalkPlan(projectId, explicit ? picked.deviceId : undefined);
    if (explicit && picked.phoneName && plan.phoneState === 'READY') {
      plan.phoneName = picked.phoneName;
    }
    const phone = plan.phoneName ? plan.phoneName : 'None connected';
    const app = plan.appLabel ? plan.appLabel : 'No app saved';
    const status = phoneStatus(plan);
    const lines = 'Phone: ' + phone + '\nStatus: ' + status + '\nApp: ' + app;
    if (plan.blocked || (mode === 'execute' && plan.phoneState !== 'READY')) {
      await global.keelAlert({
        title: 'This run cannot start',
        message: lines + (plan.message ? '\n' + plan.message : '')
      });
      return null;
    }
    const shells = mode === 'automate' && plan.phoneState !== 'READY';
    const ok = await global.keelConfirm({
      title: shells ? 'Build without a phone?' : 'Start on this phone?',
      message: lines + (shells ? '\nNo phone is connected, so locators will be the words from the Excel file.' : ''),
      confirmLabel: shells ? 'Build anyway' : 'Start'
    });
    return ok ? plan : null;
  };

  function phoneStatus(plan) {
    const msg = String(plan.message || '');
    if (/runner is not running/i.test(msg)) {
      return 'Phone is free, but the mobile runner is not running';
    }
    if (plan.phoneState === 'BUSY') return 'Busy — another run is using this phone';
    if (plan.phoneState === 'OFFLINE') return 'Not connected';
    if (plan.phoneState === 'NONE') return 'None connected';
    if (plan.phoneState === 'READY' && plan.blocked) return 'Not ready';
    if (plan.phoneState === 'READY') return 'Available';
    return 'Not connected';
  }

  /** Video when the phone is free. Stills if video cannot start. A sentence when it stops. */
  global.keelAttachPhoneLive = function (img, projectId, deviceId) {
    if (!img) return;
    const video = document.getElementById(img.id + '-video');
    const note = document.getElementById(img.id + '-note');
    if (img._keelLiveTimer) {
      clearInterval(img._keelLiveTimer);
      img._keelLiveTimer = null;
    }
    function showNote(text) {
      if (!note) return;
      note.hidden = !text;
      note.textContent = text || '';
    }
    if (!projectId || !deviceId) {
      img.hidden = true;
      img.removeAttribute('src');
      if (video) {
        video.hidden = true;
        video.removeAttribute('src');
      }
      showNote('');
      return;
    }
    const base = '/api/projects/' + encodeURIComponent(projectId)
      + '/mobile/devices/' + encodeURIComponent(deviceId);
    function stillUrl() {
      return base + '/live?t=' + Date.now();
    }
    async function explain() {
      try {
        const res = await fetch(base + '/live-note', { credentials: 'same-origin', headers: headers() });
        const data = await res.json().catch(function () { return {}; });
        showNote(data.message || 'The live picture stopped.');
      } catch (e) {
        showNote('The live picture stopped.');
      }
    }
    function startStills() {
      let tries = 0;
      img.hidden = false;
      img.alt = 'Live phone screen';
      img.src = stillUrl();
      img.onerror = function () {
        tries += 1;
        if (tries > 2) {
          img.hidden = true;
          explain();
          return;
        }
        setTimeout(function () { img.src = stillUrl(); }, 1500);
      };
    }
    showNote('');
    if (video) {
      video.hidden = false;
      img.hidden = true;
      video.src = base + '/live.mp4?t=' + Date.now();
      video.onerror = function () {
        video.hidden = true;
        video.removeAttribute('src');
        startStills();
      };
    } else {
      startStills();
    }
  };

  global.keelWatchRunner = function (el, projectId) {
    if (!el) return;
    if (el._keelRunnerTimer) clearInterval(el._keelRunnerTimer);
    if (!projectId) {
      el.hidden = true;
      return;
    }
    async function tick() {
      const plan = await global.keelMobileWalkPlan(projectId);
      el.hidden = false;
      el.textContent = plan.runnerOnline
        ? 'Mobile runner: running'
        : 'Mobile runner: not running. Start the mobile runner before you press Start.';
    }
    tick();
    el._keelRunnerTimer = setInterval(tick, 10000);
  };
})(window);

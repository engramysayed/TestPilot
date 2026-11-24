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

  /** Show the phone's screen in an img. The stream lasts a few minutes, then this reconnects. */
  global.keelAttachPhoneLive = function (img, projectId, deviceId) {
    if (!img) return;
    if (img._keelLiveTimer) {
      clearInterval(img._keelLiveTimer);
      img._keelLiveTimer = null;
    }
    if (!projectId || !deviceId) {
      img.hidden = true;
      img.removeAttribute('src');
      return;
    }
    let tries = 0;
    function url() {
      return '/api/projects/' + encodeURIComponent(projectId)
        + '/mobile/devices/' + encodeURIComponent(deviceId)
        + '/live?t=' + Date.now();
    }
    img.hidden = false;
    img.alt = 'Live phone screen';
    img.src = url();
    img.onerror = function () {
      tries += 1;
      if (tries > 3) {
        img.hidden = true;
        return;
      }
      setTimeout(function () { img.src = url(); }, 1500);
    };
    img._keelLiveTimer = setInterval(function () {
      tries = 0;
      img.src = url();
    }, 150000);
  };
})(window);

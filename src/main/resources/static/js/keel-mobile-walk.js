/**
 * Before an Android Automate or Execute start: say which phone and which app,
 * and refuse to start when that phone is busy or unplugged.
 */
(function (global) {
  'use strict';

  function headers() {
    return { 'X-Keel-Requested-With': 'Keel' };
  }

  global.keelMobileWalkPlan = async function (projectId) {
    const res = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/mobile/walk-plan', {
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
  global.keelConfirmMobileWalk = async function (projectId, mode) {
    const plan = await global.keelMobileWalkPlan(projectId);
    const phone = plan.phoneName ? plan.phoneName : 'None connected';
    const app = plan.appLabel ? plan.appLabel : 'No app saved';
    if (plan.blocked) {
      await global.keelAlert({
        title: 'This run cannot start',
        message: plan.message || ('Phone ' + phone + ' is not free.')
      });
      return null;
    }
    if (mode === 'execute' && plan.phoneState !== 'READY') {
      await global.keelAlert({
        title: 'This run cannot start',
        message: 'No phone is connected. Plug one in, then open Project Settings and press Find connected phones.'
      });
      return null;
    }
    const shells = mode === 'automate' && plan.phoneState !== 'READY';
    const ok = await global.keelConfirm({
      title: shells ? 'Build without a phone?' : 'Start on this phone?',
      message: 'Phone: ' + phone + '. App: ' + app + '.'
        + (shells ? ' No phone is connected, so locators will be the words from the Excel file.' : ''),
      confirmLabel: shells ? 'Build anyway' : 'Start'
    });
    return ok ? plan : null;
  };
})(window);

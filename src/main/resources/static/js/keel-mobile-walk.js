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
    if (/starting/i.test(msg)) {
      return 'Mobile runner is starting';
    }
    if (/runner is not running/i.test(msg) || /KEEL_RUNNER_TOKEN/i.test(msg)) {
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
        if (video._keelAbort) {
          try { video._keelAbort.abort(); } catch (e) { /* ignore */ }
        }
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
    if (video && window.MediaSource) {
      playStream(video, img, base + '/live.mp4?t=' + Date.now(), startStills);
    } else {
      startStills();
    }
  };

  function playStream(video, img, url, onFail) {
    if (video._keelAbort) {
      try { video._keelAbort.abort(); } catch (e) { /* ignore */ }
    }
    const ctrl = new AbortController();
    video._keelAbort = ctrl;
    const ms = new MediaSource();
    video.hidden = false;
    img.hidden = true;
    video.src = URL.createObjectURL(ms);
    let failed = false;
    function fail() {
      if (failed || ctrl.signal.aborted) return;
      failed = true;
      clearTimeout(timer);
      video.hidden = true;
      try { video.removeAttribute('src'); } catch (e) { /* ignore */ }
      onFail();
    }
    const timer = setTimeout(fail, 8000);
    ms.addEventListener('sourceopen', async function () {
      try {
        const res = await fetch(url, { credentials: 'same-origin', signal: ctrl.signal });
        if (!res.ok || !res.body) throw new Error('no video');
        const reader = res.body.getReader();
        const chunks = [];
        let sb = null;
        while (true) {
          const part = await reader.read();
          if (part.done) break;
          if (!sb) {
            chunks.push(part.value);
            const blob = concatBytes(chunks);
            const codec = codecFrom(blob);
            if (!codec) {
              if (blob.length > 200000) throw new Error('no init');
              continue;
            }
            clearTimeout(timer);
            sb = ms.addSourceBuffer('video/mp4; codecs="' + codec + '"');
            sb.mode = 'sequence';
            await appendBuffer(sb, blob);
            chunks.length = 0;
            video.play().catch(function () { /* autoplay is best-effort */ });
          } else {
            await appendBuffer(sb, part.value);
          }
        }
        if (!sb) {
          fail();
        } else if (!ctrl.signal.aborted) {
          playStream(video, img, url.replace(/t=\d+/, 't=' + Date.now()), onFail);
        }
      } catch (e) {
        fail();
      }
    });
  }

  function concatBytes(parts) {
    let n = 0;
    parts.forEach(function (p) { n += p.length; });
    const out = new Uint8Array(n);
    let at = 0;
    parts.forEach(function (p) {
      out.set(p, at);
      at += p.length;
    });
    return out;
  }

  function codecFrom(bytes) {
    for (let i = 0; i + 8 < bytes.length; i++) {
      if (bytes[i] === 0x61 && bytes[i + 1] === 0x76 && bytes[i + 2] === 0x63 && bytes[i + 3] === 0x43) {
        const hex = function (n) { return ('0' + (n & 255).toString(16)).slice(-2); };
        return 'avc1.' + hex(bytes[i + 5]) + hex(bytes[i + 6]) + hex(bytes[i + 7]);
      }
    }
    return '';
  }

  function appendBuffer(sb, data) {
    return new Promise(function (resolve, reject) {
      function go() {
        const done = function () {
          sb.removeEventListener('updateend', done);
          resolve();
        };
        sb.addEventListener('updateend', done);
        try {
          sb.appendBuffer(data);
        } catch (e) {
          sb.removeEventListener('updateend', done);
          reject(e);
        }
      }
      if (sb.updating) {
        const once = function () {
          sb.removeEventListener('updateend', once);
          go();
        };
        sb.addEventListener('updateend', once);
      } else {
        go();
      }
    });
  }

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
      el.textContent = plan.runnerMessage
        || (plan.runnerOnline ? 'Mobile runner is running.' : 'Mobile runner is not running.');
    }
    tick();
    el._keelRunnerTimer = setInterval(tick, 10000);
  };
})(window);

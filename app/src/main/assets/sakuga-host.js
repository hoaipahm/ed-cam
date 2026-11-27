/* Ed-Cam host wiring. The downloaded tabs.js is intentionally unchanged. */
(() => {
  'use strict';
  const native = !!window.AndroidTabs;
  const hosts = { primary: document.querySelector('#primary-host'), camera: document.querySelector('#camera-host') };
  const tabs = { primary: document.querySelector('#primary-tabs'), camera: document.querySelector('#camera-tabs') };
  let state = { mode: 'primary', selected: 0, epoch: 0 };

  function panel(value) {
    document.querySelectorAll('[data-panel]').forEach(element => { element.hidden = element.dataset.panel !== value; });
  }
  function reportBounds() {
    const element = tabs[state.mode];
    if (!element?.shadowRoot) return;
    const rect = element.shadowRoot.querySelector('[role="tablist"]').getBoundingClientRect();
    window.AndroidTabs?.bounds(state.epoch, rect.left, rect.top - 14, rect.right, rect.bottom + 8, innerWidth);
  }
  window.configureTabs = config => {
    if (!['primary', 'camera'].includes(config.mode)) return;
    state = config;
    for (const [mode, host] of Object.entries(hosts)) {
      host.classList.toggle('inactive', mode !== state.mode);
      host.style.visibility = mode === state.mode ? 'visible' : 'hidden';
    }
    const host = hosts[state.mode];
    if (native && config.rect) {
      const scale = innerWidth / config.viewport;
      const [x, y, width, height] = config.rect.map(value => value * scale);
      Object.assign(host.style, { left: `${x}px`, top: `${y}px`, width: `${width}px`, height: `${height}px` });
    }
    if (config.colors) {
      for (const [key, value] of Object.entries(config.colors)) document.documentElement.style.setProperty(`--sk-${key}`, value);
    }
    const control = tabs[state.mode];
    if (control.selected !== config.selected) control.selected = config.selected;
    host.elements.screen.value = control.value;
    requestAnimationFrame(reportBounds);
  };

  // This change listener opens the matching native panel, or the explicit browser test panel.
  for (const [mode, control] of Object.entries(tabs)) {
    hosts[mode].addEventListener('formdata', event => event.formData.set('screen', control.value));
    control.addEventListener('change', event => {
      if (state.mode !== mode) return;
      const { index, value, label } = event.detail;
      hosts[mode].elements.screen.value = value;
      if (native) window.AndroidTabs.change(mode, value, index);
      else panel(value);
      window.dispatchEvent(new CustomEvent('edcam:tab-change', { detail: { index, value, label } }));
    });
  }
  addEventListener('resize', reportBounds);
  if (native) window.AndroidTabs.ready();
  else {
    document.body.classList.add('demo');
    document.querySelector('#browser-preview').hidden = false;
    window.configureTabs(state);
    document.querySelector('#demo-mode').addEventListener('click', () => {
      const mode = state.mode === 'primary' ? 'camera' : 'primary';
      window.configureTabs({ mode, selected: mode === 'camera' ? 1 : 0, epoch: 0 });
      panel('camera');
    });
  }
})();

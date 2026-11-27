/* Ed-Cam host wiring. The downloaded tabs.js is intentionally unchanged. */
(() => {
  'use strict';
  const native = !!window.AndroidTabs;
  const hosts = { primary: document.querySelector('#primary-host'), camera: document.querySelector('#camera-host') };
  const tabs = { primary: document.querySelector('#primary-tabs'), camera: document.querySelector('#camera-tabs') };
  const controlNodes = new Map();
  const controlHost = document.querySelector('#controls-host');
  const iconText = { 'Flash bật': 'ϟ Bật', 'Flash tắt': 'ϟ Tắt', 'Hẹn giờ': '◷',
    'Lưới bật': '▦', 'Lưới tắt': '▦', 'Cài đặt': '⚙', 'Album ảnh': '▧',
    'Đổi camera trước hoặc sau': '⇄', 'Chụp ảnh ngay': '●', 'Quay lại': '‹', 'Chọn giao diện': '◐' };
  function configureControls(config) {
    if (!Array.isArray(config.controls)) return;
    const scale = innerWidth / config.viewport;
    const alive = new Set();
    for (const node of config.controls) {
      alive.add(node.id);
      let entry = controlNodes.get(node.id);
      if (!entry || entry.kind !== node.kind) {
        entry?.wrapper.remove();
        const wrapper = document.createElement('div');
        wrapper.className = `control-slot kind-${node.kind}`;
        const element = document.createElement(`sakuga-${node.kind}`);
        element.setAttribute('name', `control-${node.id}`);
        element.setAttribute('label', node.label || node.placeholder || 'Ed-Cam');
        if (node.kind === 'button') {
          element.textContent = iconText[node.label] || node.label;
          element.classList.toggle('shutter', node.label === 'Chụp ảnh ngay');
          if (node.label !== 'Chụp ảnh ngay' && !/Lưu|Nhập/.test(node.label)) element.setAttribute('variant', 'secondary');
        }
        if (node.kind === 'radio') {
          for (const option of node.options || []) {
            const item = document.createElement('option');
            item.value = option.value; item.textContent = option.label;
            element.append(item);
          }
        }
        if (node.kind === 'checkbox') {
          element.textContent = /yêu thích/i.test(node.label) ? '' : node.label;
        }
        if (node.kind === 'tooltip') element.setAttribute('text', node.label);
        if (node.kind === 'input') {
          element.setAttribute('placeholder', node.placeholder || '');
          element.setAttribute('type', /tìm/i.test(node.placeholder || '') ? 'search' : 'text');
        }
        if (node.kind === 'slider') {
          element.setAttribute('min', '0'); element.setAttribute('max', '100');
          element.setAttribute('step', '1'); element.setAttribute('unit', '%');
        }
        const event = node.kind === 'progress' ? 'sakuga:done' : node.kind === 'button' ? 'click' : ['slider','input'].includes(node.kind) ? 'input' : 'change';
        const relay = () => {
          const value = node.kind === 'progress' ? 'done' : ['checkbox','toggle'].includes(node.kind) ? String(element.checked)
            : node.kind === 'button' ? '' : String(element.value);
          window.AndroidTabs?.control(state.epoch, node.id, value);
          window.dispatchEvent(new CustomEvent('edcam:control', {detail: {id: node.id, kind: node.kind, value}}));
        };
        if (node.kind !== 'input') element.addEventListener(event, relay);
        wrapper.append(element); controlHost.append(wrapper);
        if (node.kind === 'input') element.shadowRoot.querySelector('input').addEventListener('input', relay);
        if (node.kind === 'checkbox') element.shadowRoot?.querySelector('input')?.setAttribute('aria-label', node.label);
        if (node.kind === 'input') element.shadowRoot?.querySelector('input')?.setAttribute('maxlength', /tìm/i.test(node.placeholder || '') ? '100' : '60');
        entry = {wrapper, element, kind: node.kind};
        controlNodes.set(node.id, entry);
      }
      const {wrapper, element} = entry;
      element.disabled = node.disabled;
      element.classList.toggle('selected', !!node.selected);
      if (node.kind === 'checkbox') element.shadowRoot?.querySelector('input')?.setAttribute('aria-label', node.label);
      element.setAttribute('label', node.label || node.placeholder || 'Ed-Cam');
      if (node.kind === 'button') {
        element.textContent = iconText[node.label] || node.label;
        element.classList.toggle('compact', !!iconText[node.label] && !element.classList.contains('shutter'));
        element.shadowRoot?.querySelector('button')?.setAttribute('aria-label', node.label);
      }
      if (['toggle','checkbox'].includes(node.kind)) {
        if (element.checked !== (node.value === 'true')) element.checked = node.value === 'true';
      } else if (node.kind === 'progress') {
        if (element.value !== Number(node.value)) element.set?.(Number(node.value));
      } else if (node.kind !== 'input' || !element.matches(':focus-within')) {
        if (String(element.value) !== String(node.value)) element.value = node.value;
      }
      const [x,y,w,h] = node.rect.map(v => v*scale);
      const [left,top,right,bottom] = node.clip.map(v => v*scale);
      Object.assign(wrapper.style, {left:`${x}px`, top:`${y}px`, width:`${w}px`, height:`${h}px`,
        clipPath: (top <= y+.5 && left <= x+.5 && right >= x+w-.5 && bottom >= y+h-.5) ? 'none' : `inset(${Math.max(0,top-y)}px ${Math.max(0,x+w-right)}px ${Math.max(0,y+h-bottom)}px ${Math.max(0,left-x)}px)`});
      wrapper.style.setProperty('--slot-width', `${Math.max(10,w-14)}px`);
      if (node.kind === 'tooltip') wrapper.style.setProperty('--tip-shift', `${Math.max(124,Math.min(innerWidth-124,x+w/2))-(x+w/2)}px`);
      // Only scale the housing of the sprite toggle; its painted frames remain untouched.
      if (node.kind === 'toggle') element.style.transform = 'scale(.64)';
    }
    for (const [id, entry] of controlNodes) if (!alive.has(id)) {
      entry.wrapper.remove(); controlNodes.delete(id);
    }
  }
  window.edcamDone = label => {
    for (const {element} of controlNodes.values())
      if (element.getAttribute('label') === label) element.done?.();
  };
  window.edcamToast = message => {
    const toast = document.querySelector('#app-toast');
    toast.show(message);
    SakugaFX.later(7000, () => window.AndroidTabs?.toastBounds(0,0,0,0,innerWidth));
    requestAnimationFrame(() => {
      const rect=toast.getBoundingClientRect();
      window.AndroidTabs?.toastBounds(rect.left,rect.top,rect.right,rect.bottom,innerWidth);
    });
  };
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
    if (!['primary', 'camera', 'none'].includes(config.mode)) return;
    state = config;
    for (const [mode, host] of Object.entries(hosts)) {
      host.classList.toggle('inactive', mode !== state.mode);
      host.style.visibility = mode === state.mode ? 'visible' : 'hidden';
    }
    configureControls(config);
    if (state.mode === 'none') return;
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

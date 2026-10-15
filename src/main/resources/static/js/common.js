// Shared helpers for all FlowSense screens
const FS = (() => {
  async function api(method, path) {
    const res = await fetch('/v1/api' + path, { method });
    let body = null;
    try { body = await res.json(); } catch (e) { /* empty body */ }
    if (!res.ok) throw new Error((body && body.message) || ('Request failed (' + res.status + ')'));
    return body;
  }

  function store(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value);
    } catch (e) { return null; }
  }

  let toastTimer;
  function toast(msg, isError) {
    let el = document.getElementById('toast');
    if (!el) { el = document.createElement('div'); el.id = 'toast'; el.className = 'toast'; document.body.appendChild(el); }
    el.textContent = msg;
    el.classList.toggle('err', !!isError);
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 3200);
  }

  // Load a script from the local vendor folder first (works offline), then the CDN
  function loadScript(localSrc, cdnSrc) {
    const tryLoad = src => new Promise((ok, fail) => {
      const s = document.createElement('script');
      s.src = src; s.onload = ok; s.onerror = fail;
      document.head.appendChild(s);
    });
    return tryLoad(localSrc).catch(() => tryLoad(cdnSrc));
  }

  function loadQrLib() {
    if (window.QRCode) return Promise.resolve();
    return loadScript('/js/vendor/qrcode.min.js', 'https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js');
  }

  // Draw a QR code into el; falls back to printing the text if the library is unavailable
  async function qr(el, text, size) {
    el.innerHTML = '';
    try {
      await loadQrLib();
      new QRCode(el, { text, width: size, height: size, colorDark: '#111111', colorLight: '#ffffff', correctLevel: QRCode.CorrectLevel.M });
    } catch (e) {
      el.innerHTML = '<div class="mono" style="padding:12px;border:1px dashed currentColor;border-radius:8px">' + escapeHtml(text) + '</div>';
    }
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  function mins(v) { return v == null ? '–' : Math.round(v); }

  function time(iso) {
    if (!iso) return '–';
    const d = new Date(iso);
    return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  function minutesSince(iso) { return (Date.now() - new Date(iso).getTime()) / 60000; }

  // Sparkline with area fill and an emphasized endpoint
  function spark(values, w = 120, h = 28) {
    const d = (values || []).slice(-120);
    if (d.length < 2) return '<span class="muted small">collecting…</span>';
    const max = Math.max(30, ...d);
    const pts = d.map((v, i) => (i / (d.length - 1) * w).toFixed(1) + ',' + (h - 3 - v / max * (h - 6)).toFixed(1)).join(' ');
    const last = d[d.length - 1], ly = (h - 3 - last / max * (h - 6)).toFixed(1);
    const lvl = last < 15 ? 'good' : (last < 35 ? 'warn' : 'crit');
    return `<svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" style="display:block;overflow:visible" aria-hidden="true">
      <polygon points="0,${h} ${pts} ${w},${h}" fill="var(--accent-soft)"/>
      <polyline points="${pts}" fill="none" stroke="var(--accent)" stroke-width="1.6"/>
      <circle cx="${w}" cy="${ly}" r="3" fill="var(--${lvl})"/></svg>`;
  }

  return { api, store, toast, qr, escapeHtml, mins, time, minutesSince, spark, loadScript };
})();

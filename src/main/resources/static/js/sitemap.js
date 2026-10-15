// FlowSense live site map: isometric Expo with architecture per pavilion.
// Crowd build-up is shown visually: a heat glow that grows and turns red, and people gathering at each door.
// Everything is built once and updated in place, so animations never restart on refresh.
const SiteMap = (() => {
  const NS = 'http://www.w3.org/2000/svg';
  const K = 5, OX = 550, OY = 70;
  const P = (mx, my, z = 0) => [OX + (mx - my) * K, OY + (mx + my) * K / 2 - z];
  const S = v => (+v).toFixed(1);
  const pts = a => a.map(q => { const p = P(...q); return S(p[0]) + ',' + S(p[1]); }).join(' ');
  const RX = r => r * K * Math.SQRT2, RY = r => r * K * Math.SQRT2 / 2;
  const LV = { GREEN: 'good', AMBER: 'warn', RED: 'crit' };
  const reduceMotion = window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;

  function el(tag, attrs, parent) {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(e);
    return e;
  }

  // ---------- materials (CSS tokens defined in ops.html, light + dark) ----------
  const M = name => ({ t: `var(--${name}-t)`, l: `var(--${name}-l)`, r: `var(--${name}-r)`, id: name });
  const MAT = { sand: M('sand'), mud: M('mud'), glass: M('glass'), white: M('white'), green: M('green'), steel: M('steel'), water: M('water'), coral: M('coral') };

  // ---------- primitives ----------
  function box(g, x1, y1, x2, y2, z0, z1, m) {
    el('polygon', { points: pts([[x1, y2, z1], [x2, y2, z1], [x2, y2, z0], [x1, y2, z0]]), fill: m.l }, g);
    el('polygon', { points: pts([[x2, y1, z1], [x2, y2, z1], [x2, y2, z0], [x2, y1, z0]]), fill: m.r }, g);
    return el('polygon', { points: pts([[x1, y1, z1], [x2, y1, z1], [x2, y2, z1], [x1, y2, z1]]), fill: m.t }, g);
  }
  function cyl(g, cx, cy, r, z0, z1, m) {
    const [px, pb] = P(cx, cy, z0), pt = P(cx, cy, z1)[1], rx = RX(r), ry = RY(r);
    el('path', { d: `M${S(px - rx)},${S(pt)} L${S(px - rx)},${S(pb)} A${S(rx)},${S(ry)} 0 0 0 ${S(px + rx)},${S(pb)} L${S(px + rx)},${S(pt)} Z`, fill: `url(#cg-${m.id})` }, g);
    return el('ellipse', { cx: S(px), cy: S(pt), rx: S(rx), ry: S(ry), fill: m.t }, g);
  }
  function dome(g, cx, cy, r, z0, h, m, lines) {
    const [px, pb] = P(cx, cy, z0), rx = RX(r), ry = RY(r);
    el('path', { d: `M${S(px - rx)},${S(pb)} A${S(rx)},${S(h)} 0 0 1 ${S(px + rx)},${S(pb)} A${S(rx)},${S(ry)} 0 0 1 ${S(px - rx)},${S(pb)} Z`, fill: `url(#dg-${m.id})` }, g);
    if (!lines) return;
    const st = { fill: 'none', stroke: 'var(--facet)', 'stroke-width': 0.9 };
    [0.3, 0.58, 0.82].forEach(t => {
      const w = rx * Math.sqrt(1 - t * t), wy = ry * Math.sqrt(1 - t * t), y = pb - h * t;
      el('path', Object.assign({ d: `M${S(px - w)},${S(y)} A${S(w)},${S(wy)} 0 0 0 ${S(px + w)},${S(y)}` }, st), g);
    });
    [0.15, 0.32, 0.5, 0.68, 0.85].forEach(f => {
      const a = Math.PI * f, bx = px - rx * Math.cos(a), by = pb + ry * Math.sin(a);
      el('path', Object.assign({ d: `M${S(bx)},${S(by)} Q${S(px - rx * Math.cos(a) * 0.75)},${S(pb - h * 0.95 + ry * Math.sin(a) * 0.35)} ${S(px)},${S(pb - h)}` }, st), g);
    });
  }
  // vertical lines on the two visible faces of a box (glass mullions)
  function mullions(g, x1, y1, x2, y2, z0, z1, n, color = 'var(--facet)') {
    for (let i = 1; i < n; i++) {
      const t = i / n;
      const a = P(x1 + (x2 - x1) * t, y2, z0), b = P(x1 + (x2 - x1) * t, y2, z1);
      el('line', { x1: S(a[0]), y1: S(a[1]), x2: S(b[0]), y2: S(b[1]), stroke: color, 'stroke-width': 0.8 }, g);
      const c = P(x2, y1 + (y2 - y1) * t, z0), d = P(x2, y1 + (y2 - y1) * t, z1);
      el('line', { x1: S(c[0]), y1: S(c[1]), x2: S(d[0]), y2: S(d[1]), stroke: color, 'stroke-width': 0.8 }, g);
    }
  }
  function floors(g, x1, y1, x2, y2, z0, z1, step, color = 'var(--facet)') {
    for (let z = z0 + step; z < z1 - 1; z += step) {
      el('polyline', { points: pts([[x1, y2, z], [x2, y2, z], [x2, y1, z]]), fill: 'none', stroke: color, 'stroke-width': 0.8 }, g);
    }
  }
  // Najdi crenellations: small triangles standing on the parapet of both visible faces
  function crenels(g, x1, y1, x2, y2, z, n, m, size = 6) {
    for (let i = 0; i < n; i++) {
      const a = i / n, b = (i + 0.6) / n, c = (a + b) / 2;
      el('polygon', { points: pts([[x1 + (x2 - x1) * a, y2, z], [x1 + (x2 - x1) * b, y2, z], [x1 + (x2 - x1) * c, y2, z + size]]), fill: m.l }, g);
      el('polygon', { points: pts([[x2, y1 + (y2 - y1) * a, z], [x2, y1 + (y2 - y1) * b, z], [x2, y1 + (y2 - y1) * c, z + size]]), fill: m.r }, g);
    }
  }
  // triangular Najdi windows on both faces
  function triWindows(g, x1, y1, x2, y2, z, n, size = 4) {
    for (let i = 0; i < n; i++) {
      const c = (i + 0.5) / n, hw = 0.18 / n;
      el('polygon', { points: pts([[x1 + (x2 - x1) * (c - hw), y2, z], [x1 + (x2 - x1) * (c + hw), y2, z], [x1 + (x2 - x1) * c, y2, z + size]]), fill: 'var(--opening)' }, g);
      el('polygon', { points: pts([[x2, y1 + (y2 - y1) * (c - hw), z], [x2, y1 + (y2 - y1) * (c + hw), z], [x2, y1 + (y2 - y1) * c, z + size]]), fill: 'var(--opening)' }, g);
    }
  }
  function palm(g, x, y, z, scale = 1) {
    const [px, py] = P(x, y, z);
    el('ellipse', { cx: S(px + 4 * scale), cy: S(py + 1), rx: S(7 * scale), ry: S(2.6 * scale), fill: 'var(--shadow)' }, g);
    el('path', { d: `M${S(px)},${S(py)} q${S(1.5 * scale)},${S(-8 * scale)} 0,${S(-16 * scale)}`, fill: 'none', stroke: 'var(--trunk)', 'stroke-width': S(1.8 * scale), 'stroke-linecap': 'round' }, g);
    const tx = px, ty = py - 16 * scale;
    [[-9, 3], [-6, -3], [0, -5], [6, -3], [9, 3], [3, 5], [-3, 5]].forEach(([dx, dy]) => {
      el('path', { d: `M${S(tx)},${S(ty)} q${S(dx * 0.5 * scale)},${S((dy - 4) * 0.5 * scale)} ${S(dx * scale)},${S(dy * scale)}`, fill: 'none', stroke: 'var(--frond)', 'stroke-width': S(2.2 * scale), 'stroke-linecap': 'round' }, g);
    });
  }

  // ---------- architecture per pavilion ----------
  // each returns { h: roof height in px, door: [mx, my] }
  const ARCH = {
    // Glass tower with a sandstone podium and a lit crown
    tower(g, x, y, f, z) {
      box(g, x - f * 1.2, y - f * 1.2, x + f * 1.2, y + f * 1.2, z, z + 10, MAT.sand);
      const t1 = f * 0.8;
      box(g, x - t1, y - t1, x + t1, y + t1, z + 10, z + 92, MAT.glass);
      mullions(g, x - t1, y - t1, x + t1, y + t1, z + 10, z + 92, 6);
      floors(g, x - t1, y - t1, x + t1, y + t1, z + 10, z + 92, 9);
      box(g, x - t1 * 0.7, y - t1 * 0.7, x + t1 * 0.7, y + t1 * 0.7, z + 92, z + 104, MAT.steel);
      el('polyline', { points: pts([[x - t1 * 0.7, y + t1 * 0.7, z + 100], [x + t1 * 0.7, y + t1 * 0.7, z + 100], [x + t1 * 0.7, y - t1 * 0.7, z + 100]]), fill: 'none', stroke: 'var(--accent)', 'stroke-width': 2 }, g);
      return { h: 104, door: [x + f * 1.2, y] };
    },
    // Streamlined mobility hall: long glass capsule on a podium with a sweeping roof blade
    mobility(g, x, y, f, z) {
      box(g, x - f * 1.5, y - f * 0.8, x + f * 1.5, y + f * 0.8, z, z + 8, MAT.sand);
      box(g, x - f * 1.3, y - f * 0.55, x + f * 1.3, y + f * 0.55, z + 8, z + 30, MAT.glass);
      mullions(g, x - f * 1.3, y - f * 0.55, x + f * 1.3, y + f * 0.55, z + 8, z + 30, 9);
      el('polygon', { points: pts([[x - f * 1.6, y - f * 0.75, z + 30], [x + f * 1.7, y - f * 0.75, z + 46], [x + f * 1.7, y + f * 0.75, z + 46], [x - f * 1.6, y + f * 0.75, z + 30]]), fill: 'var(--steel-t)', stroke: 'var(--steel-r)', 'stroke-width': 1 }, g);
      el('polygon', { points: pts([[x - f * 1.6, y + f * 0.75, z + 30], [x + f * 1.7, y + f * 0.75, z + 46], [x + f * 1.7, y + f * 0.75, z + 43], [x - f * 1.6, y + f * 0.75, z + 27]]), fill: 'var(--steel-l)' }, g);
      return { h: 46, door: [x + f * 1.5, y] };
    },
    // Geodesic dome on a sandstone drum
    geodome(g, x, y, f, z) {
      cyl(g, x, y, f * 1.15, z, z + 8, MAT.sand);
      dome(g, x, y, f * 1.05, z + 8, 54, MAT.glass, true);
      return { h: 62, door: [x + f * 1.15, y] };
    },
    // Ocean Lab: low aqua shell rising from a reflecting pool
    ocean(g, x, y, f, z) {
      const [px, py] = P(x, y, z + 0.5);
      el('ellipse', { cx: S(px), cy: S(py), rx: S(RX(f * 1.7)), ry: S(RY(f * 1.7)), fill: 'var(--water-t)', stroke: 'var(--water-r)', 'stroke-width': 1.5 }, g);
      [0.55, 0.8].forEach(k => el('ellipse', { cx: S(px), cy: S(py), rx: S(RX(f * 1.7 * k + 0.6)), ry: S(RY(f * 1.7 * k + 0.6)), fill: 'none', stroke: 'var(--water-l)', 'stroke-width': 0.8, opacity: .7 }, g));
      dome(g, x, y, f * 1.1, z + 1, 30, MAT.water, true);
      dome(g, x - f * 0.5, y + f * 0.35, f * 0.55, z + 1, 18, MAT.water, false);
      return { h: 31, door: [x + f * 1.2, y] };
    },
    // Green Riyadh: stepped terraces with planted roofs
    terraces(g, x, y, f, z) {
      const lv = [[1.15, 0, 14], [0.85, 14, 28], [0.55, 28, 42]];
      lv.forEach(([k, a, b]) => {
        box(g, x - f * k, y - f * k, x + f * k, y + f * k, z + a, z + b, { t: 'var(--green-t)', l: 'var(--sand-l)', r: 'var(--sand-r)' });
        el('polyline', { points: pts([[x - f * k, y + f * k, z + b], [x + f * k, y + f * k, z + b], [x + f * k, y - f * k, z + b]]), fill: 'none', stroke: 'var(--green-l)', 'stroke-width': 2.5 }, g);
      });
      [[0.9, -0.9], [-0.9, 0.9], [0.65, 0.65]].forEach(([a, b]) => {
        const [tx, ty] = P(x + a * f, y + b * f, z + 14);
        el('circle', { cx: S(tx), cy: S(ty - 3), r: 3.2, fill: 'var(--frond)' }, g);
      });
      return { h: 42, door: [x + f * 1.15, y] };
    },
    // Water Futures: glass drum with horizontal water rings
    drum(g, x, y, f, z) {
      cyl(g, x, y, f * 1.0, z, z + 66, MAT.glass);
      const [px] = P(x, y, z), rx = RX(f), ry = RY(f);
      for (let h = 10; h < 66; h += 11) {
        const yy = P(x, y, z + h)[1];
        el('path', { d: `M${S(px - rx)},${S(yy)} A${S(rx)},${S(ry)} 0 0 0 ${S(px + rx)},${S(yy)}`, fill: 'none', stroke: 'var(--water-r)', 'stroke-width': 1.6, opacity: .8 }, g);
      }
      const top = P(x, y, z + 66);
      el('ellipse', { cx: S(top[0]), cy: S(top[1]), rx: S(rx * 0.75), ry: S(ry * 0.75), fill: 'var(--water-t)' }, g);
      return { h: 66, door: [x + f, y] };
    },
    // Health Horizons: overlapping white shells
    shells(g, x, y, f, z) {
      box(g, x - f * 1.3, y - f * 1.1, x + f * 1.3, y + f * 1.1, z, z + 6, MAT.white);
      dome(g, x - f * 0.45, y - f * 0.35, f * 0.75, z + 6, 44, MAT.white, false);
      dome(g, x + f * 0.4, y + f * 0.1, f * 0.85, z + 6, 34, MAT.white, false);
      dome(g, x - f * 0.2, y + f * 0.6, f * 0.55, z + 6, 22, MAT.white, false);
      return { h: 50, door: [x + f * 1.3, y] };
    },
    // Youth Lab: cantilevered, stacked colourful volumes
    stack(g, x, y, f, z) {
      box(g, x - f * 0.9, y - f * 0.7, x + f * 0.5, y + f * 0.9, z, z + 16, MAT.white);
      box(g, x - f * 0.4, y - f * 1.0, x + f * 1.0, y + f * 0.5, z + 16, z + 31, MAT.coral);
      mullions(g, x - f * 0.4, y - f * 1.0, x + f * 1.0, y + f * 0.5, z + 16, z + 31, 5, 'var(--white-t)');
      box(g, x - f * 1.0, y - f * 0.4, x + f * 0.4, y + f * 1.0, z + 31, z + 45, MAT.glass);
      return { h: 45, door: [x + f * 0.5, y + f * 0.2] };
    },
    // Saudi Pavilion: Najdi mud-brick architecture (Diriyah) with corner towers and triangular openings
    najdi(g, x, y, f, z) {
      const H = 44;
      box(g, x - f, y - f, x + f, y + f, z, z + H, MAT.mud);
      triWindows(g, x - f, y - f, x + f, y + f, z + H * 0.62, 6, 5);
      triWindows(g, x - f, y - f, x + f, y + f, z + H * 0.3, 6, 4);
      el('polyline', { points: pts([[x - f, y + f, z + H - 4], [x + f, y + f, z + H - 4], [x + f, y - f, z + H - 4]]), fill: 'none', stroke: 'var(--opening)', 'stroke-width': 1.2, opacity: .5 }, g);
      crenels(g, x - f, y - f, x + f, y + f, z + H, 9, MAT.mud, 7);
      const t = f * 0.32;
      [[x + f, y - f], [x - f, y + f], [x + f, y + f]].forEach(([cx, cy]) => {
        box(g, cx - t, cy - t, cx + t, cy + t, z, z + H + 16, MAT.mud);
        box(g, cx - t * 0.8, cy - t * 0.8, cx + t * 0.8, cy + t * 0.8, z + H + 16, z + H + 21, MAT.mud);
        triWindows(g, cx - t, cy - t, cx + t, cy + t, z + H + 6, 1, 5);
        crenels(g, cx - t * 0.8, cy - t * 0.8, cx + t * 0.8, cy + t * 0.8, z + H + 21, 2, MAT.mud, 5);
      });
      // carved wooden door
      const a = P(x + f, y - f * 0.18, z), b = P(x + f, y + f * 0.18, z);
      el('path', { d: `M${S(a[0])},${S(a[1])} L${S(a[0])},${S(a[1] - 15)} Q${S((a[0] + b[0]) / 2)},${S((a[1] + b[1]) / 2 - 22)} ${S(b[0])},${S(b[1] - 15)} L${S(b[0])},${S(b[1])} Z`, fill: 'var(--wood)' }, g);
      return { h: H + 21, door: [x + f, y] };
    },
    // Heritage Gate: monumental portal between two towers
    gate(g, x, y, f, z) {
      box(g, x - f * 1.1, y - f * 1.2, x + f * 1.1, y - f * 0.3, z, z + 22, MAT.sand);
      box(g, x - f * 1.1, y - f * 0.25, x - f * 0.35, y + f * 0.45, z, z + 52, MAT.mud);
      box(g, x + f * 0.35, y - f * 0.25, x + f * 1.1, y + f * 0.45, z, z + 52, MAT.mud);
      box(g, x - f * 1.1, y - f * 0.25, x + f * 1.1, y + f * 0.45, z + 40, z + 52, MAT.mud);
      const a = P(x - f * 0.35, y + f * 0.45, z), b = P(x + f * 0.35, y + f * 0.45, z);
      el('path', { d: `M${S(a[0])},${S(a[1])} L${S(a[0])},${S(a[1] - 30)} Q${S((a[0] + b[0]) / 2)},${S((a[1] + b[1]) / 2 - 44)} ${S(b[0])},${S(b[1] - 30)} L${S(b[0])},${S(b[1])} Z`, fill: 'var(--opening)', opacity: .55 }, g);
      crenels(g, x - f * 1.1, y - f * 0.25, x + f * 1.1, y + f * 0.45, z + 52, 8, MAT.mud, 6);
      triWindows(g, x + f * 0.35, y - f * 0.25, x + f * 1.1, y + f * 0.45, z + 30, 2, 5);
      return { h: 58, door: [x + f * 1.1, y + f * 0.1] };
    },
    // Arts of the World: white cube wrapped in a mashrabiya lattice
    lattice(g, x, y, f, z) {
      box(g, x - f, y - f, x + f, y + f, z, z + 46, MAT.white);
      el('polygon', { points: pts([[x - f, y + f, z + 46], [x + f, y + f, z + 46], [x + f, y + f, z + 8], [x - f, y + f, z + 8]]), fill: 'url(#lattice)', opacity: .9 }, g);
      el('polygon', { points: pts([[x + f, y - f, z + 46], [x + f, y + f, z + 46], [x + f, y + f, z + 8], [x + f, y - f, z + 8]]), fill: 'url(#lattice)', opacity: .7 }, g);
      box(g, x - f * 0.5, y - f * 0.5, x + f * 0.5, y + f * 0.5, z + 46, z + 50, MAT.sand);
      return { h: 50, door: [x + f, y] };
    },
    // Nations Plaza: grid of shade umbrellas over an open plaza, lined with flags
    canopy(g, x, y, f, z) {
      el('polygon', { points: pts([[x - f * 1.5, y - f * 1.5, z + 0.5], [x + f * 1.5, y - f * 1.5, z + 0.5], [x + f * 1.5, y + f * 1.5, z + 0.5], [x - f * 1.5, y + f * 1.5, z + 0.5]]), fill: 'var(--sand-t)', stroke: 'var(--sand-l)' }, g);
      const flagCols = ['#0A7A6C', '#C0392B', '#2E6DB4', '#E0A526', '#5B4BA0', '#2C8550', '#D35400'];
      for (let i = 0; i < 7; i++) {
        const [fx, fy] = P(x - f * 1.5 + (i + 0.5) * (3 * f / 7), y + f * 1.55, z);
        el('line', { x1: S(fx), y1: S(fy), x2: S(fx), y2: S(fy - 30), stroke: 'var(--steel-r)', 'stroke-width': 1 }, g);
        el('rect', { x: S(fx), y: S(fy - 30), width: 7, height: 5, fill: flagCols[i] }, g);
      }
      const spots = [];
      [-0.75, 0.75].forEach(a => [-0.95, 0, 0.95].forEach(b => spots.push([x + a * f, y + b * f])));
      spots.sort((p, q) => (p[0] + p[1]) - (q[0] + q[1])).forEach(([ux, uy]) => {
        const base = P(ux, uy, z), top = P(ux, uy, z + 30), rx = RX(f * 0.62), ry = RY(f * 0.62);
        el('line', { x1: S(base[0]), y1: S(base[1]), x2: S(top[0]), y2: S(top[1]), stroke: 'var(--white-r)', 'stroke-width': 2 }, g);
        el('path', { d: `M${S(top[0] - rx)},${S(top[1] - 4)} L${S(top[0])},${S(top[1] + 6)} L${S(top[0] + rx)},${S(top[1] - 4)} Z`, fill: 'var(--white-l)' }, g);
        el('ellipse', { cx: S(top[0]), cy: S(top[1] - 4), rx: S(rx), ry: S(ry), fill: 'var(--white-t)', stroke: 'var(--white-r)', 'stroke-width': 0.8 }, g);
      });
      return { h: 36, door: [x + f * 1.5, y] };
    },
    // Innovation Exchange: elevated glass ring on columns
    ring(g, x, y, f, z) {
      [[-0.6, 0.6], [0.6, -0.6], [0.6, 0.6], [-0.6, -0.6]].forEach(([a, b]) => {
        const p1 = P(x + a * f, y + b * f, z), p2 = P(x + a * f, y + b * f, z + 20);
        el('line', { x1: S(p1[0]), y1: S(p1[1]), x2: S(p2[0]), y2: S(p2[1]), stroke: 'var(--steel-r)', 'stroke-width': 3 }, g);
      });
      cyl(g, x, y, f * 1.15, z + 20, z + 34, MAT.glass);
      const c = P(x, y, z + 34);
      el('ellipse', { cx: S(c[0]), cy: S(c[1]), rx: S(RX(f * 0.62)), ry: S(RY(f * 0.62)), fill: 'var(--glass-r)' }, g);
      el('ellipse', { cx: S(c[0]), cy: S(c[1] + 3), rx: S(RX(f * 0.55)), ry: S(RY(f * 0.55)), fill: 'var(--d-collab)' }, g);
      box(g, x - f * 0.3, y - f * 0.3, x + f * 0.3, y + f * 0.3, z, z + 20, MAT.glass);
      return { h: 34, door: [x + f * 0.3, y] };
    }
  };

  const STYLE_BY_NAME = {
    'AI & Humanity': 'tower', 'Future Mobility': 'mobility', 'Space Dome': 'geodome',
    'Ocean Lab': 'ocean', 'Green Riyadh': 'terraces', 'Water Futures': 'drum',
    'Health Horizons': 'shells', 'Youth Lab': 'stack',
    'Saudi Pavilion': 'najdi', 'Heritage Gate': 'gate', 'Arts of the World': 'lattice',
    'Nations Plaza': 'canopy', 'Innovation Exchange': 'ring'
  };
  const STYLE_BY_ZONE = { Technology: 'geodome', Planet: 'terraces', Humanity: 'shells', Culture: 'najdi', Collaboration: 'canopy' };

  // ---------- site layout ----------
  const DISTRICTS = [
    { key: 'Technology', x1: 6, y1: 9, x2: 40, y2: 44, fill: 'var(--d-tech)', ink: 'var(--di-tech)', sign: [17, 43] },
    { key: 'Planet', x1: 60, y1: 8, x2: 94, y2: 43, fill: 'var(--d-planet)', ink: 'var(--di-planet)', sign: [86, 12] },
    { key: 'Humanity', x1: 60, y1: 58, x2: 94, y2: 91, fill: 'var(--d-humanity)', ink: 'var(--di-humanity)', sign: [86, 87] },
    { key: 'Culture', x1: 6, y1: 57, x2: 40, y2: 92, fill: 'var(--d-culture)', ink: 'var(--di-culture)', sign: [14, 88] },
    { key: 'Collaboration', x1: 41, y1: 39, x2: 63, y2: 62, fill: 'var(--d-collab)', ink: 'var(--di-collab)', sign: [51, 37] }
  ];
  const CORRIDORS = [
    [[23, 27], [50, 24], [77, 26]], [[77, 26], [80, 51], [77, 74]], [[77, 74], [50, 78], [23, 75]], [[23, 75], [20, 51], [23, 27]],
    [[52, 51], [23, 27]], [[52, 51], [77, 26]], [[52, 51], [77, 74]], [[52, 51], [23, 75]],
    [[50, 1], [50, 24]], [[50, 99], [50, 78]], [[99, 51], [80, 51]], [[1, 51], [20, 51]]
  ];
  const FACILITIES = [
    { x: 50, y: 1.5, k: 'G', label: 'North Gate' }, { x: 50, y: 98.5, k: 'M', label: 'Metro station' },
    { x: 98.5, y: 51, k: 'G', label: 'East Gate · bus & taxi' }, { x: 1.5, y: 51, k: 'G', label: 'West Gate · parking' },
    { x: 44, y: 24, k: 'F' }, { x: 80, y: 46, k: 'F' }, { x: 56, y: 78, k: 'F' }, { x: 20, y: 56, k: 'F' },
    { x: 58, y: 26, k: 'A' }, { x: 44, y: 78, k: 'A' },
    { x: 34, y: 49, k: 'W' }, { x: 66, y: 51, k: 'W' }, { x: 50, y: 34, k: 'W' }, { x: 50, y: 68, k: 'W' }
  ];

  function create(svg, opts = {}) {
    const L = {}, PAV = {}, FLOW = {}, signRects = [];
    let data = [];

    // ---------- static scene ----------
    svg.innerHTML = '';
    const defs = el('defs', {}, svg);
    Object.values(MAT).forEach(m => {
      const lg = el('linearGradient', { id: 'cg-' + m.id, x1: 0, x2: 1, y1: 0, y2: 0 }, defs);
      el('stop', { offset: '0', 'stop-color': m.l }, lg); el('stop', { offset: '0.35', 'stop-color': m.t }, lg); el('stop', { offset: '1', 'stop-color': m.r }, lg);
      const dg = el('linearGradient', { id: 'dg-' + m.id, x1: 0.15, x2: 0.9, y1: 0.1, y2: 0.95 }, defs);
      el('stop', { offset: '0', 'stop-color': m.t }, dg); el('stop', { offset: '0.55', 'stop-color': m.l }, dg); el('stop', { offset: '1', 'stop-color': m.r }, dg);
    });
    ['good', 'warn', 'crit'].forEach(k => {
      const rg = el('radialGradient', { id: 'heat-' + k }, defs);
      el('stop', { offset: '0', 'stop-color': `var(--${k})`, 'stop-opacity': 0.75 }, rg);
      el('stop', { offset: '0.55', 'stop-color': `var(--${k})`, 'stop-opacity': 0.32 }, rg);
      el('stop', { offset: '1', 'stop-color': `var(--${k})`, 'stop-opacity': 0 }, rg);
    });
    const lat = el('pattern', { id: 'lattice', width: 8, height: 8, patternUnits: 'userSpaceOnUse' }, defs);
    el('path', { d: 'M4,0 L8,4 L4,8 L0,4 Z', fill: 'none', stroke: 'var(--sand-r)', 'stroke-width': 1 }, lat);
    const tex = el('pattern', { id: 'paving', width: 14, height: 7, patternUnits: 'userSpaceOnUse' }, defs);
    el('path', { d: 'M0,3.5 L7,0 L14,3.5 L7,7 Z', fill: 'none', stroke: 'var(--paving)', 'stroke-width': 0.6 }, tex);
    const mk = el('marker', { id: 'arrow', viewBox: '0 0 10 10', refX: 8, refY: 5, markerWidth: 6, markerHeight: 6, orient: 'auto-start-reverse' }, defs);
    el('path', { d: 'M0,0 L10,5 L0,10 z', fill: 'var(--accent)' }, mk);

    ['ground', 'corridors', 'plates', 'trees', 'facilities', 'buildings', 'signs', 'labels', 'flows', 'walkers'].forEach(n => L[n] = el('g', {}, svg));

    // site base
    el('polygon', { points: pts([[0, 100, 0], [100, 100, 0], [100, 100, -16], [0, 100, -16]]), fill: 'var(--ground-l)' }, L.ground);
    el('polygon', { points: pts([[100, 0, 0], [100, 100, 0], [100, 100, -16], [100, 0, -16]]), fill: 'var(--ground-r)' }, L.ground);
    el('polygon', { points: pts([[0, 0, 0], [100, 0, 0], [100, 100, 0], [0, 100, 0]]), fill: 'var(--ground)' }, L.ground);
    el('polygon', { points: pts([[0, 0, 0], [100, 0, 0], [100, 100, 0], [0, 100, 0]]), fill: 'url(#paving)' }, L.ground);

    CORRIDORS.forEach(c => {
      const d = c.map(([x, y], i) => (i ? 'L' : 'M') + P(x, y, 1).map(S).join(',')).join(' ');
      el('path', { d, fill: 'none', stroke: 'var(--corridor-edge)', 'stroke-width': 23, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }, L.corridors);
      el('path', { d, fill: 'none', stroke: 'var(--corridor)', 'stroke-width': 19, 'stroke-linejoin': 'round', 'stroke-linecap': 'round' }, L.corridors);
      el('path', { d, fill: 'none', stroke: 'var(--slat)', 'stroke-width': 15, 'stroke-dasharray': '1.6 4.4' }, L.corridors);
    });

    DISTRICTS.forEach(dz => {
      el('polygon', { points: pts([[dz.x1, dz.y2, 4], [dz.x2, dz.y2, 4], [dz.x2, dz.y2, 0], [dz.x1, dz.y2, 0]]), fill: 'var(--plate-edge)' }, L.plates);
      el('polygon', { points: pts([[dz.x2, dz.y1, 4], [dz.x2, dz.y2, 4], [dz.x2, dz.y2, 0], [dz.x2, dz.y1, 0]]), fill: 'var(--plate-edge2)' }, L.plates);
      el('polygon', { points: pts([[dz.x1, dz.y1, 4], [dz.x2, dz.y1, 4], [dz.x2, dz.y2, 4], [dz.x1, dz.y2, 4]]), fill: dz.fill }, L.plates);
    });
    // Expo Plaza landmark: circular shade ring on columns
    (() => {
      const cx = 52, cy = 51, r = 3.2, g = L.plates;
      const c = P(cx, cy, 4);
      el('ellipse', { cx: S(c[0]), cy: S(c[1]), rx: S(RX(r + 1)), ry: S(RY(r + 1)), fill: 'var(--sand-t)', stroke: 'var(--sand-l)' }, g);
      for (let i = 0; i < 10; i++) {
        const a = i / 10 * Math.PI * 2, px = cx + Math.cos(a) * r, py = cy + Math.sin(a) * r;
        const b = P(px, py, 4), t = P(px, py, 26);
        el('line', { x1: S(b[0]), y1: S(b[1]), x2: S(t[0]), y2: S(t[1]), stroke: 'var(--white-r)', 'stroke-width': 1.6 }, g);
      }
      const t = P(cx, cy, 26);
      el('ellipse', { cx: S(t[0]), cy: S(t[1]), rx: S(RX(r)), ry: S(RY(r)), fill: 'none', stroke: 'var(--white-t)', 'stroke-width': 6 }, g);
      el('ellipse', { cx: S(t[0]), cy: S(t[1]), rx: S(RX(r)), ry: S(RY(r)), fill: 'none', stroke: 'var(--white-r)', 'stroke-width': 1, transform: 'translate(0,3)' }, g);
    })();

    // palms along corridors
    CORRIDORS.slice(0, 8).forEach((c, ci) => {
      for (let s = 0; s < c.length - 1; s++) {
        const [x1, y1] = c[s], [x2, y2] = c[s + 1], len = Math.hypot(x2 - x1, y2 - y1);
        const nx = -(y2 - y1) / len, ny = (x2 - x1) / len;
        for (let t = 0.18; t < 0.86; t += 0.17) {
          [-1, 1].forEach(side => {
            if ((ci + s + Math.round(t * 10) + side) % 3 === 0) return;
            palm(L.trees, x1 + (x2 - x1) * t + nx * 3.5 * side, y1 + (y2 - y1) * t + ny * 3.5 * side, 1, 0.85);
          });
        }
      }
    });

    // district signage (outer corners)
    DISTRICTS.forEach(dz => {
      const [sx, sy] = P(dz.sign[0], dz.sign[1], 4);
      const label = dz.key.toUpperCase() + (dz.key === 'Collaboration' ? '' : ' DISTRICT');
      const w = label.length * 7.4 + 30, x0 = sx - w / 2;
      const y0 = sy - 10;
      signRects.push({ x1: x0, x2: x0 + w, y1: y0, y2: y0 + 20 });
      el('rect', { x: S(x0), y: S(y0), width: S(w), height: 20, rx: 10, fill: 'var(--label-bg)', stroke: dz.ink, 'stroke-width': 1.2 }, L.signs);
      el('circle', { cx: S(x0 + 11), cy: S(y0 + 10), r: 4, fill: dz.ink }, L.signs);
      el('text', { x: S(x0 + 20), y: S(y0 + 14), 'font-family': 'IBM Plex Mono, monospace', 'font-size': 10.5, 'font-weight': 600, 'letter-spacing': 1, fill: 'var(--ink)' }, L.signs).textContent = label;
    });

    FACILITIES.forEach(f => {
      const [fx, fy] = P(f.x, f.y, 6);
      el('circle', { cx: S(fx), cy: S(fy - 6), r: 8, fill: f.k === 'G' || f.k === 'M' ? 'var(--ink)' : 'var(--panel)', stroke: 'var(--line)' }, L.facilities);
      el('text', { x: S(fx), y: S(fy - 2.5), 'text-anchor': 'middle', 'font-family': 'IBM Plex Mono, monospace', 'font-size': 9.5, 'font-weight': 700, fill: f.k === 'G' || f.k === 'M' ? 'var(--bg)' : 'var(--ink)' }, L.facilities).textContent = f.k;
      if (f.label) el('text', { x: S(fx + 12), y: S(fy - 2.5), 'font-family': 'IBM Plex Sans, sans-serif', 'font-size': 11, 'font-weight': 500, fill: 'var(--muted)' }, L.facilities).textContent = f.label;
    });

    // ---------- pavilions ----------
    function build(p) {
      const f = 2.2 + p.designThroughput * 0.2, x = p.mapX, y = p.mapY, z = 4;
      const g = el('g', { class: 'pav-g' }, L.buildings);
      const title = el('title', {}, g);
      // crowd heat glow on the ground (grows and changes colour as the queue builds)
      const heat = el('ellipse', { cx: 0, cy: 0, rx: 0, ry: 0, fill: 'url(#heat-good)' }, g);
      const ring = el('ellipse', { fill: 'none', stroke: 'var(--crit)', 'stroke-width': 2.5, class: 'hot-ring', opacity: 0 }, g);
      const sh = P(x + 1.2, y + 1.2, z);
      el('ellipse', { cx: S(sh[0]), cy: S(sh[1]), rx: S(RX(f * 1.35)), ry: S(RY(f * 1.35)), fill: 'var(--shadow)' }, g);
      const body = el('g', {}, g);
      const style = STYLE_BY_NAME[p.name] || STYLE_BY_ZONE[p.zone] || 'geodome';
      const info = ARCH[style](body, x, y, f, z);
      const door = info.door;
      // people queuing at the door: a snake that grows outwards
      const crowd = el('g', {}, g), people = [];
      for (let i = 0; i < 48; i++) {
        const row = Math.floor(i / 6), col = i % 6, c = row % 2 ? 5 - col : col;
        const mx = door[0] + 1.0 + row * 1.0, my = door[1] - 2.4 + c * 0.95 + (row % 2) * 0.3;
        const [qx, qy] = P(mx, my, z);
        const person = el('g', { opacity: 0 }, crowd);
        el('line', { x1: S(qx), y1: S(qy), x2: S(qx), y2: S(qy - 3.4), stroke: 'var(--person)', 'stroke-width': 2, 'stroke-linecap': 'round' }, person);
        el('circle', { cx: S(qx), cy: S(qy - 5), r: 1.4, fill: 'var(--person)' }, person);
        people.push(person);
      }
      const hc = P(door[0] + 3, door[1], z);
      heat.setAttribute('cx', S(hc[0])); heat.setAttribute('cy', S(hc[1]));
      ring.setAttribute('cx', S(hc[0])); ring.setAttribute('cy', S(hc[1]));
      // overflow lane badge next to the door
      const lane = el('g', { opacity: 0 }, g);
      const [ox, oy] = P(door[0] + 0.4, door[1] + 2.4, z);
      el('rect', { x: S(ox - 16), y: S(oy - 22), width: 32, height: 15, rx: 4, fill: 'var(--accent)' }, lane);
      el('text', { x: S(ox), y: S(oy - 11.5), 'text-anchor': 'middle', 'font-family': 'IBM Plex Mono, monospace', 'font-size': 9.5, 'font-weight': 700, fill: 'var(--accent-ink)' }, lane).textContent = '+LANE';

      // label
      const lg = el('g', {}, L.labels);
      const [ax, ay] = P(x, y, z + info.h + 4);
      const lead = el('line', { stroke: 'var(--muted)', 'stroke-width': 1, opacity: .55 }, lg);
      const lbox = el('g', {}, lg);
      const nameW = p.name.length * 6.4 + 14, w = nameW + 92;
      el('rect', { class: 'lbl-bg', x: S(-w / 2), y: -11, width: S(w), height: 22, rx: 7, fill: 'var(--label-bg)', stroke: 'var(--line)' }, lbox);
      el('text', { x: S(-w / 2 + 8), y: 4, 'font-family': 'IBM Plex Sans, sans-serif', 'font-size': 11, 'font-weight': 600, fill: 'var(--ink)' }, lbox).textContent = p.name;
      el('rect', { x: S(w / 2 - 86), y: -2.5, width: 28, height: 5, rx: 2.5, fill: 'var(--line)' }, lbox);
      const gauge = el('rect', { x: S(w / 2 - 86), y: -2.5, width: 0, height: 5, rx: 2.5 }, lbox);
      const wait = el('text', { x: S(w / 2 - 8), y: 4, 'text-anchor': 'end', 'font-family': 'IBM Plex Mono, monospace', 'font-size': 10.5, 'font-weight': 700 }, lbox);

      g.addEventListener('click', () => opts.onSelect && opts.onSelect(p.pavilionId));
      PAV[p.pavilionId] = { g, body, title, heat, ring, people, lane, gauge, wait, f, x, y, z, door, label: { box: lbox, lead, ax, ay, w },
        cur: { n: 0, heat: 0 }, tgt: { n: 0, heat: 0, level: 'good' } };
    }

    function layoutLabels() {
      const placed = signRects.slice();
      const items = Object.values(PAV).sort((a, b) => a.label.ay - b.label.ay);
      const hit = r => placed.some(q => r.x1 < q.x2 + 4 && r.x2 > q.x1 - 4 && r.y1 < q.y2 + 3 && r.y2 > q.y1 - 3);
      items.forEach(v => {
        const { ax, ay, w } = v.label, cands = [];
        [-16, -38, -60, -82].forEach(dy => [0, w * 0.55, -w * 0.55, w * 1.05, -w * 1.05].forEach(dx => cands.push([dx, dy])));
        let pick = cands[0];
        for (const [dx, dy] of cands) {
          if (!hit({ x1: ax + dx - w / 2, x2: ax + dx + w / 2, y1: ay + dy - 11, y2: ay + dy + 11 })) { pick = [dx, dy]; break; }
        }
        const cx = ax + pick[0], cy = ay + pick[1];
        placed.push({ x1: cx - w / 2, x2: cx + w / 2, y1: cy - 11, y2: cy + 11 });
        v.label.box.setAttribute('transform', `translate(${S(cx)},${S(cy)})`);
        v.label.lead.setAttribute('x1', S(ax)); v.label.lead.setAttribute('y1', S(ay));
        v.label.lead.setAttribute('x2', S(cx)); v.label.lead.setAttribute('y2', S(cy + 11));
      });
    }

    function rebuild() {
      L.buildings.innerHTML = ''; L.labels.innerHTML = '';
      Object.keys(PAV).forEach(k => delete PAV[k]);
      [...data].sort((a, b) => (a.mapX + a.mapY) - (b.mapX + b.mapY)).forEach(build);
      layoutLabels();
    }

    // ---------- flows ----------
    function doorPoint(id) { const v = PAV[id]; return P(v.door[0] + 1, v.door[1], v.z); }
    function updateFlows(flows) {
      const seen = new Set();
      flows.forEach(fl => {
        if (!PAV[fl.fromPavilionId] || !PAV[fl.toPavilionId]) return;
        const key = fl.fromPavilionId + '>' + fl.toPavilionId;
        seen.add(key);
        let F = FLOW[key];
        if (!F) {
          const [x1, y1] = doorPoint(fl.fromPavilionId), [x2, y2] = doorPoint(fl.toPavilionId);
          const cx = (x1 + x2) / 2, cy = Math.min(y1, y2) - 50 - Math.hypot(x2 - x1, y2 - y1) * 0.15;
          const d = `M${S(x1)},${S(y1)} Q${S(cx)},${S(cy)} ${S(x2)},${S(y2)}`;
          const glow = el('path', { d, fill: 'none', stroke: 'var(--accent)', 'stroke-width': 8, opacity: .15, 'stroke-linecap': 'round' }, L.flows);
          const path = el('path', { d, fill: 'none', stroke: 'var(--accent)', 'stroke-width': 3, class: 'flow-line', 'marker-end': 'url(#arrow)' }, L.flows);
          F = FLOW[key] = { path, glow, len: path.getTotalLength(), dots: [], born: performance.now() };
        }
        F.count = fl.visitorsLast15Min;
        const want = Math.min(12, 3 + Math.round(fl.visitorsLast15Min / 3));
        while (F.dots.length < want) F.dots.push(el('circle', { r: 3.6, fill: 'var(--accent)', stroke: 'var(--panel)', 'stroke-width': 1.2 }, L.walkers));
        while (F.dots.length > want) F.dots.pop().remove();
        F.glow.setAttribute('stroke-width', S(6 + Math.min(12, fl.visitorsLast15Min / 2)));
      });
      Object.keys(FLOW).forEach(k => {
        if (!seen.has(k)) { const F = FLOW[k]; F.path.remove(); F.glow.remove(); F.dots.forEach(d => d.remove()); delete FLOW[k]; }
      });
      return Object.values(FLOW).reduce((s, F) => s + F.count, 0);
    }

    // ---------- live update ----------
    function update(list, flows) {
      const newIds = list.map(p => p.pavilionId).sort().join(',');
      const oldIds = data.map(p => p.pavilionId).sort().join(',');
      data = list;
      if (newIds !== oldIds) rebuild();
      list.forEach(p => {
        const v = PAV[p.pavilionId]; if (!v) return;
        const open = p.status === 'OPEN';
        v.tgt.n = open || p.status === 'PAUSED' ? Math.min(48, p.peopleInLine / 6) : 0;
        v.tgt.heat = open ? Math.min(1, p.waitMinutes / 45) : 0;
        v.tgt.level = open ? LV[p.level] : 'good';
        v.heat.setAttribute('fill', `url(#heat-${v.tgt.level})`);
        v.ring.setAttribute('opacity', open && p.level === 'RED' ? 1 : 0);
        v.lane.setAttribute('opacity', p.overflowLane === 'OPEN' ? 1 : 0);
        v.body.setAttribute('opacity', open ? 1 : 0.45);
        const col = open ? `var(--${LV[p.level]})` : 'var(--muted)';
        v.wait.textContent = open ? `${Math.round(p.waitMinutes)} min` : p.status.toLowerCase();
        v.wait.setAttribute('fill', col);
        v.gauge.setAttribute('fill', col);
        v.gauge.setAttribute('width', S(28 * (open ? Math.min(1, p.waitMinutes / 60) : 0)));
        v.title.textContent = `${p.name} · ${p.zone} district · Gate ${p.gate}\n` +
          `Wait ${Math.round(p.waitMinutes)} min · about ${Math.round(p.peopleInLine)} people in line · ${p.appWaiting} holding a place` +
          (p.overflowLane === 'OPEN' ? '\nOverflow lane open (+30% door capacity)' : '') + '\nClick to select for a surge';
      });
      return updateFlows(flows || []);
    }

    // ---------- animation: crowds and heat ease towards their targets, walkers move continuously ----------
    let last = performance.now();
    function frame(now) {
      const dt = Math.min(0.1, (now - last) / 1000); last = now;
      Object.values(PAV).forEach(v => {
        const k = reduceMotion ? 1 : Math.min(1, dt * 2.2);
        v.cur.n += (v.tgt.n - v.cur.n) * k;
        v.cur.heat += (v.tgt.heat - v.cur.heat) * k;
        const shown = Math.round(v.cur.n);
        if (shown !== v.shown) {
          v.people.forEach((pp, i) => pp.setAttribute('opacity', i < shown ? 0.85 : 0));
          v.shown = shown;
        }
        const r = v.f * (0.9 + v.cur.heat * 1.5);
        v.heat.setAttribute('rx', S(RX(r))); v.heat.setAttribute('ry', S(RY(r)));
        v.heat.setAttribute('opacity', S(0.25 + v.cur.heat * 0.75));
        v.ring.setAttribute('rx', S(RX(r * 0.8))); v.ring.setAttribute('ry', S(RY(r * 0.8)));
      });
      Object.values(FLOW).forEach(F => {
        const n = F.dots.length, grow = Math.min(1, (now - F.born) / 900);
        F.path.setAttribute('opacity', S(grow));
        F.glow.setAttribute('opacity', S(0.15 * grow));
        F.dots.forEach((dot, i) => {
          const t = reduceMotion ? (i + 0.5) / n : ((now / 3200) + i / n) % 1;
          const pt = F.path.getPointAtLength(t * F.len);
          dot.setAttribute('cx', S(pt.x)); dot.setAttribute('cy', S(pt.y));
          dot.setAttribute('opacity', S(grow * Math.min(1, Math.min(t, 1 - t) * 6)));
        });
      });
      requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);

    return { update };
  }

  return { create };
})();

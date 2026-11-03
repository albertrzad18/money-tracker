(function () {
  const NB = '\u00a0', MINUS = '\u2212';
  const NOW = new Date(2026, 10, 2, 14, 32);
  const DAY = 86400000, MONTH = 30.44 * DAY;
  const FX = { PLN: 1, USD: 3.6412 };
  const FX_DATE = '02.11.2026';

  const group = (s) => s.replace(/\B(?=(\d{3})+(?!\d))/g, NB);
  function num(v, minF, maxF) {
    let s = Math.abs(v).toFixed(maxF);
    let [i, d = ''] = s.split('.');
    d = d.replace(/0+$/, '');
    while (d.length < minF) d += '0';
    return group(i) + (d ? ',' + d : '');
  }
  function price(v) {
    const a = Math.abs(v);
    if (a >= 10000) return num(v, 0, 0);
    if (a >= 1) return num(v, 2, 2);
    if (a >= 0.01) return num(v, 2, 4);
    return num(v, 2, 8);
  }
  const SYM = { PLN: 'zł', USD: '$', EUR: '€', GBP: '£' };
  const money = (v, c) => price(v) + NB + (SYM[c] || c);
  const signedMoney = (v, c) => (Math.abs(v) < 0.005 ? '' : v > 0 ? '+' : MINUS) + money(Math.abs(v), c);
  const pct = (v) => v == null ? '—' : (Math.abs(v) < 0.05 ? '' : v > 0 ? '+' : MINUS) + num(v, 1, 1) + '%';
  const qty = (v) => num(v, 0, 8);
  const parse = (s) => { const n = parseFloat(String(s).replace(/[\s\u00a0]/g, '').replace(',', '.')); return isFinite(n) ? n : null; };
  const pad = (n) => String(n).padStart(2, '0');
  const toDate = (s) => { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); };
  const date = (s) => { const d = typeof s === 'string' ? toDate(s) : s; return pad(d.getDate()) + '.' + pad(d.getMonth() + 1) + '.' + d.getFullYear(); };
  const shortDate = (s) => { const d = typeof s === 'string' ? toDate(s) : s; return pad(d.getDate()) + '.' + pad(d.getMonth() + 1) + '.' + String(d.getFullYear()).slice(2); };
  const monthsAgo = (s) => -(NOW - toDate(s)) / MONTH;
  const tone = (v) => v == null || Math.abs(v) < 0.05 ? 'flat' : v > 0 ? 'up' : 'down';
  const C = { up: 'var(--up, #56633f)', down: 'var(--down, #a83a2c)', flat: 'var(--muted, #645c50)' };
  const BG = { up: 'var(--up-bg, #dfe9c9)', down: 'var(--down-bg, #f6d9cf)', flat: 'var(--inset, #f5ead8)' };

  const instruments = [
    { id: 1, sym: 'PKN.PL', name: 'Orlen SA', kind: 'Akcja', exch: 'GPW', cur: 'PLN', price: 68.42, day: 1.2, seed: 1,
      lots: [{ id: 11, date: '2026-03-14', price: 58.10, qty: 40 }, { id: 12, date: '2026-07-22', price: 71.30, qty: 25 }],
      sold: [{ id: 13, date: '2026-01-05', price: 52.40, qty: 30, sellDate: '2026-02-20', sellPrice: 61.00 }],
      keys: [[-24, 47], [-18, 55], [-13, 50.5], [-5, 64], [-2.5, 66.5], [-1.2, 64.8], [0, 68.42]] },
    { id: 2, sym: 'CSPX.UK', name: 'iShares Core S&P 500 UCITS ETF', kind: 'ETF', exch: 'LSE', cur: 'USD', price: 712.35, day: -0.4, seed: 2,
      lots: [{ id: 21, date: '2026-01-10', price: 598.20, qty: 3 }, { id: 22, date: '2026-06-05', price: 655.90, qty: 2 }, { id: 23, date: '2026-09-18', price: 694.10, qty: 1 }],
      sold: [], keys: [[-24, 470], [-16, 540], [-12, 572], [-8, 612], [-3.5, 668], [-1, 703], [0, 712.35]] },
    { id: 3, sym: 'BTC', name: 'Bitcoin', kind: 'Krypto', exch: 'CoinGecko', cur: 'USD', price: 104380, day: 2.3, seed: 3,
      lots: [{ id: 31, date: '2025-12-02', price: 96450, qty: 0.015 }, { id: 32, date: '2026-04-11', price: 83200, qty: 0.01 }],
      sold: [], keys: [[-12, 74000], [-9.5, 91000], [-7.5, 101500], [-5.5, 86000], [-2.5, 99000], [-1, 111000], [0, 104380]] },
    { id: 4, sym: 'AAPL.US', name: 'Apple Inc.', kind: 'Akcja', exch: 'NASDAQ', cur: 'USD', price: 227.80, day: -1.1, seed: 4,
      lots: [{ id: 41, date: '2026-02-03', price: 236.40, qty: 8 }],
      sold: [], keys: [[-24, 176], [-14, 229], [-10.5, 248], [-6, 206], [-3, 239], [0, 227.8]] },
    { id: 5, sym: 'CDR.PL', name: 'CD Projekt SA', kind: 'Akcja', exch: 'GPW', cur: 'PLN', price: 241.60, day: 0.6, seed: 5,
      lots: [{ id: 51, date: '2026-08-19', price: 268.00, qty: null }],
      sold: [], keys: [[-24, 151], [-12, 192], [-6, 233], [-1.5, 259], [0, 241.6]] },
  ];
  const closed = [
    { id: 6, sym: 'PZU.PL', name: 'PZU SA', kind: 'Akcja', cur: 'PLN', sold: [{ id: 61, date: '2025-11-12', price: 42.80, qty: 50, sellDate: '2026-06-03', sellPrice: 55.10 }] },
    { id: 7, sym: 'NVDA.US', name: 'NVIDIA Corp.', kind: 'Akcja', cur: 'USD', sold: [{ id: 71, date: '2026-01-20', price: 142.00, qty: 5, sellDate: '2026-04-07', sellPrice: 97.50 }] },
    { id: 8, sym: 'ETH', name: 'Ethereum', kind: 'Krypto', cur: 'USD', sold: [{ id: 81, date: '2026-05-15', price: 2540, qty: 0.4, sellDate: '2026-08-25', sellPrice: 4610 }] },
  ];

  instruments.forEach((inst) => {
    const k = inst.keys.slice();
    inst.lots.forEach((l) => k.push([monthsAgo(l.date), l.price]));
    inst.sold.forEach((l) => { k.push([monthsAgo(l.date), l.price]); k.push([monthsAgo(l.sellDate), l.sellPrice]); });
    k.sort((a, b) => a[0] - b[0]);
    inst._k = k;
  });
  function priceAt(inst, m) {
    const k = inst._k;
    let base;
    if (m <= k[0][0]) base = k[0][1];
    else if (m >= 0) base = inst.price;
    else {
      let i = 1; while (k[i][0] < m) i++;
      const [m0, p0] = k[i - 1], [m1, p1] = k[i];
      const t = (m - m0) / (m1 - m0 || 1);
      const s = t * t * (3 - 2 * t);
      base = p0 + (p1 - p0) * s;
    }
    const s = inst.seed;
    const n = Math.sin(m * 7.3 + s) * 0.55 + Math.sin(m * 17.9 + s * 2.1) * 0.3 + Math.sin(m * 61 + s * 3.7) * 0.15 + Math.sin(m * 190 + s) * 0.08;
    return base * (1 + n * 0.022 * Math.min(1, Math.abs(m) * 6));
  }

  function lotResult(inst, l) {
    const p = (inst.price / l.price - 1) * 100;
    const g = l.qty != null ? (inst.price - l.price) * l.qty : null;
    const t = tone(p);
    return {
      id: l.id, raw: l, dateStr: date(l.date), shortDate: shortDate(l.date),
      buyStr: money(l.price, inst.cur), buyPlain: price(l.price), qtyStr: l.qty != null ? qty(l.qty) : null,
      atStr: '@' + money(l.price, inst.cur) + (l.qty != null ? '  × ' + qty(l.qty) : ''),
      pct: p, pctStr: pct(p), gain: g, gainStr: g != null ? signedMoney(g, inst.cur) : '', c: C[t], bg: BG[t], tone: t,
      barW: Math.min(50, Math.abs(p) / 30 * 50) + '%', barL: p >= 0 ? '50%' : (50 - Math.min(50, Math.abs(p) / 30 * 50)) + '%',
    };
  }
  function realizedResult(inst, l) {
    const p = (l.sellPrice / l.price - 1) * 100;
    const g = l.qty != null ? (l.sellPrice - l.price) * l.qty : null;
    const t = tone(p);
    return {
      id: l.id, sym: inst.sym, name: inst.name, cur: inst.cur, instId: inst.id, raw: l, sellDate: l.sellDate,
      datesStr: date(l.date) + ' → ' + date(l.sellDate),
      pricesStr: '@' + price(l.price) + ' → ' + money(l.sellPrice, inst.cur) + (l.qty != null ? '  × ' + qty(l.qty) : ''),
      pct: p, pctStr: pct(p), gain: g, gainStr: g != null ? signedMoney(g, inst.cur) : '', c: C[t], bg: BG[t], tone: t,
    };
  }
  function position(inst) {
    const lots = inst.lots.map((l) => lotResult(inst, l));
    const allQty = inst.lots.every((l) => l.qty != null);
    let avg, cost = null, value = null, gain = null, q = null;
    if (allQty) {
      q = inst.lots.reduce((s, l) => s + l.qty, 0);
      cost = inst.lots.reduce((s, l) => s + l.qty * l.price, 0);
      avg = cost / q; value = q * inst.price; gain = value - cost;
    } else avg = inst.lots.reduce((s, l) => s + l.price, 0) / inst.lots.length;
    const p = (inst.price / avg - 1) * 100;
    const t = tone(p), dt = tone(inst.day);
    return {
      id: inst.id, inst, sym: inst.sym, name: inst.name, kind: inst.kind, exch: inst.exch, cur: inst.cur,
      priceStr: money(inst.price, inst.cur), dayStr: pct(inst.day) + ' dziś', dayPct: pct(inst.day), dayC: C[dt],
      avg, avgStr: money(avg, inst.cur), avgLabel: allQty ? 'Śr. cena' : 'Śr. cen zakupu',
      avgLabelLong: allQty ? 'Średnia cena (ważona)' : 'Średnia cen zakupu',
      pct: p, pctStr: pct(p), c: C[t], bg: BG[t], tone: t,
      qty: q, qtyStr: q != null ? qty(q) : null, cost, costStr: cost != null ? money(cost, inst.cur) : null,
      value, valueStr: value != null ? money(value, inst.cur) : null, gain, gainStr: gain != null ? signedMoney(gain, inst.cur) : null,
      gainC: gain != null ? C[tone(gain)] : C.flat, hasValue: value != null,
      lots, lotsCount: lots.length, lotsLabel: lots.length === 1 ? '1 zakup' : lots.length + ' zakupy',
      realized: inst.sold.map((l) => realizedResult(inst, l)),
      dots: lots.map((l) => ({ c: l.c })),
    };
  }
  const positions = instruments.map(position);
  const realized = instruments.concat(closed).flatMap((i) => i.sold.map((l) => realizedResult(i, l)))
    .sort((a, b) => (a.sellDate < b.sellDate ? 1 : -1));

  // Summary in PLN
  const PAL = ['#c67139', '#7a8a5e', '#d9a441', '#8c491a', '#a19786'];
  const valued = positions.filter((p) => p.hasValue);
  const valuePln = valued.reduce((s, p) => s + p.value * FX[p.cur], 0);
  const costPln = valued.reduce((s, p) => s + p.cost * FX[p.cur], 0);
  const gainPln = valuePln - costPln, pctPln = (valuePln / costPln - 1) * 100;
  const slices = (items) => {
    const sorted = items.sort((a, b) => b.v - a.v);
    let acc = 0; const R = 15.9155, CIRC = 100;
    return sorted.map((it, i) => {
      const share = it.v / valuePln * 100;
      const s = { label: it.label, share, shareStr: num(share, 1, 1) + '%', color: PAL[i % PAL.length], valueStr: '≈' + NB + money(it.v, 'PLN'),
        dash: Math.max(0, share - 1.2) + ' ' + (CIRC - Math.max(0, share - 1.2)), offset: String(25 - acc), w: share + '%' };
      acc += share; return s;
    });
  };
  const byInstrument = slices(valued.map((p) => ({ label: p.sym, v: p.value * FX[p.cur] })));
  const kinds = {}; valued.forEach((p) => { kinds[p.kind] = (kinds[p.kind] || 0) + p.value * FX[p.cur]; });
  const byKind = slices(Object.keys(kinds).map((k) => ({ label: k === 'Krypto' ? 'Kryptowaluty' : k === 'Akcja' ? 'Akcje' : 'ETF-y', v: kinds[k] })));
  const perCurrency = ['PLN', 'USD'].map((c) => {
    const ps = valued.filter((p) => p.cur === c);
    const v = ps.reduce((s, p) => s + p.value, 0), co = ps.reduce((s, p) => s + p.cost, 0), g = v - co, pp = (v / co - 1) * 100;
    return { cur: c, valueStr: money(v, c), gainStr: signedMoney(g, c), gainC: C[tone(g)], pctStr: pct(pp), c: C[tone(pp)] };
  });
  const realizedPer = ['PLN', 'USD'].map((c) => {
    const a = realized.filter((r) => r.cur === c).reduce((s, r) => s + r.gain, 0);
    return { cur: c, amount: a, str: signedMoney(a, c), c: C[tone(a)] };
  });
  const realizedPln = realizedPer.reduce((s, r) => s + r.amount * FX[r.cur], 0);
  const summary = {
    valueStr: '≈' + NB + money(valuePln, 'PLN'), valueShort: num(valuePln, 0, 0) + NB + 'zł', valuePlain: money(valuePln, 'PLN'),
    costStr: '≈' + NB + money(costPln, 'PLN'), gainStr: '≈' + NB + signedMoney(gainPln, 'PLN'), gainPlain: signedMoney(gainPln, 'PLN'),
    gainShort: (gainPln >= 0 ? '+' : MINUS) + num(gainPln, 0, 0) + NB + 'zł',
    pctStr: pct(pctPln), c: C[tone(pctPln)], bg: BG[tone(pctPln)], gainC: C[tone(gainPln)],
    byInstrument, byKind, perCurrency, realizedPer,
    realizedPlnStr: '≈' + NB + signedMoney(realizedPln, 'PLN'), realizedPlnC: C[tone(realizedPln)],
    fxNote: 'Przeliczono na PLN po kursach NBP z ' + FX_DATE + '. Liczone są tylko zakupy z podaną ilością.',
    withoutQty: positions.reduce((s, p) => s + p.inst.lots.filter((l) => l.qty == null).length, 0),
  };

  // Charts
  const RANGES = { '1T': 7 / 30.44, '1M': 1, '6M': 6, '1R': 12, 'MAX': 24 };
  function dateAt(m) { return new Date(NOW.getTime() + m * MONTH); }
  function series(inst, range) {
    let span = RANGES[range];
    if (inst.kind === 'Krypto') span = Math.min(span, 12);
    span = Math.min(span, -inst._k[0][0]);
    const n = range === '1T' ? 56 : range === '1M' ? 44 : 72;
    const pts = [];
    for (let i = 0; i <= n; i++) {
      const m = -span + span * i / n;
      const d = dateAt(m);
      pts.push({ v: priceAt(inst, m), label: range === '1T' ? shortDate(d) + ' ' + pad(d.getHours()) + ':00' : date(d) });
    }
    return pts;
  }
  function chart(points, refs, W, H, opts) {
    opts = opts || {};
    const vals = points.map((p) => p.v).concat(refs.map((r) => r.v));
    let lo = Math.min(...vals), hi = Math.max(...vals);
    const padV = (hi - lo) * 0.1 || hi * 0.05; lo -= padV; hi += padV;
    const X = (i) => (i / (points.length - 1)) * W, Y = (v) => H - ((v - lo) / (hi - lo)) * H;
    const xy = points.map((p, i) => [X(i), Y(p.v)]);
    const line = xy.map((q, i) => (i ? 'L' : 'M') + q[0].toFixed(1) + ' ' + q[1].toFixed(1)).join(' ');
    const area = line + ' L' + W + ' ' + H + ' L0 ' + H + ' Z';
    return {
      line, area, W, H,
      refs: refs.map((r) => ({ y: Y(r.v).toFixed(1), top: (Y(r.v) / H * 100).toFixed(2) + '%', c: r.c, label: r.label, dash: r.dash || '4 5' })),
      pts: points.map((p, i) => ({ x: xy[i][0], y: xy[i][1], left: (xy[i][0] / W * 100) + '%', top: (xy[i][1] / H * 100) + '%', v: p.v, label: p.label })),
      hiStr: opts.fmt ? opts.fmt(hi - padV) : price(hi - padV), loStr: opts.fmt ? opts.fmt(lo + padV) : price(lo + padV),
    };
  }
  function timeline(range) {
    const first = Math.min(...instruments.flatMap((i) => i.lots.concat(i.sold).map((l) => monthsAgo(l.date))));
    const span = range === '6M' ? 6 : range === '1R' ? 12 : -first + 0.3;
    const n = 72, val = [], inv = [];
    const all = instruments.flatMap((inst) => inst.lots.filter((l) => l.qty != null).map((l) => ({ inst, l, from: monthsAgo(l.date), to: 1 })).concat(
      inst.sold.map((l) => ({ inst, l, from: monthsAgo(l.date), to: monthsAgo(l.sellDate) }))));
    for (let i = 0; i <= n; i++) {
      const m = -span + span * i / n;
      let v = 0, c = 0;
      all.forEach(({ inst, l, from, to }) => { if (m >= from && m < to) { v += l.qty * priceAt(inst, m) * FX[inst.cur]; c += l.qty * l.price * FX[inst.cur]; } });
      val.push({ v, label: date(dateAt(m)) }); inv.push({ v: c, label: date(dateAt(m)) });
    }
    return { val, inv };
  }
  function multiChart(a, b, W, H) {
    const vals = a.concat(b).map((p) => p.v);
    let lo = Math.min(...vals), hi = Math.max(...vals); const pv = (hi - lo) * 0.08; lo = Math.max(0, lo - pv); hi += pv;
    const X = (i) => (i / (a.length - 1)) * W, Y = (v) => H - ((v - lo) / (hi - lo)) * H;
    const path = (s) => s.map((p, i) => (i ? 'L' : 'M') + X(i).toFixed(1) + ' ' + Y(p.v).toFixed(1)).join(' ');
    const la = path(a);
    return { line: la, area: la + ' L' + W + ' ' + H + ' L0 ' + H + ' Z', line2: path(b), hiStr: num(hi, 0, 0) + NB + 'zł', loStr: num(lo, 0, 0) + NB + 'zł',
      pts: a.map((p, i) => ({ left: (X(i) / W * 100) + '%', top: (Y(p.v) / H * 100) + '%', top2: (Y(b[i].v) / H * 100) + '%', label: p.label,
        v: '≈' + NB + money(p.v, 'PLN'), v2: money(b[i].v, 'PLN') })) };
  }
  function spark(inst, W, H) {
    const pts = series(inst, '1M');
    const c = chart(pts, [], W, H);
    return c.line;
  }

  const candidates = [
    { sym: 'PKN.PL', name: 'Orlen SA', kind: 'Akcja', exch: 'GPW', cur: 'PLN', price: 68.42, group: 'sec' },
    { sym: 'PKO.PL', name: 'PKO Bank Polski SA', kind: 'Akcja', exch: 'GPW', cur: 'PLN', price: 61.86, group: 'sec' },
    { sym: 'PZU.PL', name: 'PZU SA', kind: 'Akcja', exch: 'GPW', cur: 'PLN', price: 56.30, group: 'sec' },
    { sym: 'AAPL.US', name: 'Apple Inc.', kind: 'Akcja', exch: 'NASDAQ', cur: 'USD', price: 227.80, group: 'sec' },
    { sym: 'VWCE.DE', name: 'Vanguard FTSE All-World UCITS ETF', kind: 'ETF', exch: 'XETRA', cur: 'EUR', price: 141.12, group: 'sec' },
    { sym: 'CSPX.UK', name: 'iShares Core S&P 500 UCITS ETF', kind: 'ETF', exch: 'LSE', cur: 'USD', price: 712.35, group: 'sec' },
    { sym: 'BTC', name: 'Bitcoin', kind: 'Krypto', exch: null, cur: 'USD', price: 104380, group: 'crypto' },
    { sym: 'ETH', name: 'Ethereum', kind: 'Krypto', exch: null, cur: 'USD', price: 4280, group: 'crypto' },
    { sym: 'SOL', name: 'Solana', kind: 'Krypto', exch: null, cur: 'USD', price: 186.40, group: 'crypto' },
  ].map((c) => Object.assign(c, { meta: [c.kind, c.exch].filter(Boolean).join(' · '), priceStr: money(c.price, c.cur), sub: [c.name, c.exch].filter(Boolean).join(' · ') }));

  const THEMES = {
    light: { '--color-bg': '#f5ead8', '--color-surface': '#ebddc5', '--color-text': '#201e1d', '--color-accent': '#c67139',
      '--color-divider': 'rgba(32,30,29,.16)', '--muted': '#645c50', '--line': 'rgba(32,30,29,.10)', '--up': '#56633f', '--up-bg': '#dfe9c9',
      '--down': '#a83a2c', '--down-bg': '#f6d9cf', '--hero': '#56633f', '--hero-ink': '#f9f4ed', '--ind': '#ffe1d0', '--ind-ink': '#643312',
      '--inset': '#f5ead8', '--chart': '#c67139', '--chart-fill': 'rgba(198,113,57,.16)', '--chart-2': '#7a8a5e', '--on-accent': '#f9f4ed',
      '--w-bg': '#f9f4ed', '--knob': '#f9f4ed', '--track-off': '#dcd3c4', '--scrim': 'rgba(46,43,37,.42)', '--soft': '#e1eecc', '--soft-ink': '#3d472b', '--sec': '#8c491a', colorScheme: 'light' },
    dark: { '--color-bg': '#1f1d1a', '--color-surface': '#2d2a25', '--color-text': '#f5ead8', '--color-accent': '#d67f48',
      '--color-divider': 'rgba(245,234,216,.16)', '--muted': '#c0b6a5', '--line': 'rgba(245,234,216,.10)', '--up': '#b9cb98', '--up-bg': '#323a24',
      '--down': '#f19a87', '--down-bg': '#45261f', '--hero': '#3d472b', '--hero-ink': '#f0fae1', '--ind': '#643312', '--ind-ink': '#ffe1d0',
      '--inset': '#1f1d1a', '--chart': '#e08a52', '--chart-fill': 'rgba(224,138,82,.18)', '--chart-2': '#aebf92', '--on-accent': '#201e1d',
      '--w-bg': '#262420', '--knob': '#f5ead8', '--track-off': '#474238', '--scrim': 'rgba(0,0,0,.55)', '--soft': '#3d472b', '--soft-ink': '#e1eecc', '--sec': '#f6a06b', colorScheme: 'dark' },
  };

  const ICONS = {
    wallet: ['Wallet'], pie: ['ChartPie', 'PieChart'], history: ['History'], settings: ['Settings'], plus: ['Plus'],
    back: ['ArrowLeft'], more: ['EllipsisVertical', 'MoreVertical'], refresh: ['RefreshCw'], search: ['Search'], calendar: ['Calendar'],
    x: ['X'], check: ['Check'], up: ['ChevronUp'], down: ['ChevronDown'], trash: ['Trash2'], pencil: ['Pencil'], undo: ['Undo2'],
    signal: ['Signal'], wifi: ['Wifi'], battery: ['BatteryFull'], bell: ['Bell'], chart: ['ChartLine', 'LineChart'], right: ['ChevronRight'],
    sell: ['HandCoins', 'Coins'], alert: ['TriangleAlert', 'AlertTriangle'],
  };
  const iconCache = {};
  function icons(React, sw) {
    const L = window.lucide && window.lucide.icons;
    if (!L) return {};
    if (iconCache[sw]) return iconCache[sw];
    const map = {};
    Object.keys(ICONS).forEach((k) => {
      const name = ICONS[k].find((n) => L[n]);
      if (!name) return;
      let node = L[name];
      const kids = node[0] === 'svg' ? node[2] : node;
      map[k] = React.createElement('svg', { width: '100%', height: '100%', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor',
        strokeWidth: sw || 2.5, strokeLinecap: 'round', strokeLinejoin: 'round', style: { display: 'block' } },
        kids.map((c, i) => { const a = Object.assign({}, c[1]); delete a.key; return React.createElement(c[0], Object.assign({ key: i }, a)); }));
    });
    iconCache[sw] = map;
    return map;
  }
  function whenReady(cb) {
    let n = 0;
    const t = setInterval(() => { if (window.lucide || ++n > 60) { clearInterval(t); cb(); } }, 100);
    return t;
  }
  const clock = () => { const d = new Date(); return pad(d.getHours()) + ':' + pad(d.getMinutes()); };

  const FB = '"Figtree", system-ui, sans-serif';
  const FONTS = {
    bricolage: { label: 'Bricolage Grotesque', pair: 'Bricolage Grotesque + Figtree', h: '"Bricolage Grotesque", system-ui, sans-serif', b: FB, hw: '650', note: 'Grotesk z charakterem i lekko zaokrąglonymi zakończeniami. Ciepły, ale dużo spokojniejszy niż obecny.' },
    young: { label: 'Young Serif', pair: 'Young Serif + Figtree', h: '"Young Serif", Georgia, serif', b: FB, hw: '400', note: 'Miękki, książkowy szeryf. Kwoty wyglądają ciepło i poważnie zarazem.' },
    instrument: { label: 'Instrument Serif', pair: 'Instrument Serif + Instrument Sans', h: '"Instrument Serif", Georgia, serif', b: '"Instrument Sans", system-ui, sans-serif', hw: '400', note: 'Elegancki kontrast: smukły szeryf w nagłówkach, neutralny grotesk w treści.' },
    manrope: { label: 'Manrope', pair: 'Manrope', h: '"Manrope", system-ui, sans-serif', b: '"Manrope", system-ui, sans-serif', hw: '700', note: 'Jeden krój do wszystkiego. Najbardziej minimalistyczny i techniczny.' },
    nunito: { label: 'Nunito', pair: 'Nunito', h: '"Nunito", system-ui, sans-serif', b: '"Nunito", system-ui, sans-serif', hw: '800', note: 'Zaokrąglony sans. Pasuje do miękkich kształtów, przyjazny, bez ozdobników.' },
    caprasimo: { label: 'Caprasimo', pair: 'Caprasimo + Figtree', h: '"Caprasimo", system-ui, sans-serif', b: FB, hw: '400', note: 'Obecny krój z systemu Organic, dla porównania.' },
  };
  const FONT_ORDER = ['bricolage', 'young', 'instrument', 'manrope', 'nunito', 'caprasimo'];
  const DEFAULT_FONT = 'manrope';
  const FONT_Q = { bricolage: 'Bricolage+Grotesque:opsz,wght@12..96,400..800', young: 'Young+Serif', instrument: 'Instrument+Serif&family=Instrument+Sans:wght@400..700', manrope: 'Manrope:wght@400..800', nunito: 'Nunito:wght@400..800' };
  const loaded = {};
  function loadFont(k) {
    if (!FONT_Q[k] || loaded[k] || typeof document === 'undefined') return;
    loaded[k] = true;
    const l = document.createElement('link'); l.rel = 'stylesheet';
    l.href = 'https://fonts.googleapis.com/css2?family=' + FONT_Q[k] + '&display=swap';
    document.head.appendChild(l);
  }
  const fontVars = (k) => { loadFont(k || 'manrope'); const f = FONTS[k] || FONTS.manrope; return { '--font-heading': f.h, '--font-body': f.b, '--hw': f.hw }; };

  window.MT = { FONTS, FONT_ORDER, fontVars, loadFont, NOW, FX, fmt: { num, price, money, signedMoney, pct, qty, parse, date, shortDate, tone, C, BG }, positions, realized, summary,
    instruments, series, chart, timeline, multiChart, spark, candidates, THEMES, icons, whenReady, clock, updated: '14:32' };
})();

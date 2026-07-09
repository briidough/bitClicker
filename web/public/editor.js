(function () {
  'use strict';

  // ── Constants ─────────────────────────────────────────────────────────────
  const MAX_FRAMES = 6;
  const CELL_SIZE = { 16: 22, 32: 11 };         // px per cell — editor canvas
  // Draw-canvas background is always a plain white / light-grey checkerboard
  // (independent of the app theme; the preview keeps the theme colors).
  const CHECK_A = '#ffffff';
  const CHECK_B = '#d9d9d9';
  const GRID_COL = '#cccccc';

  // Palette presets: three base colors per theme. Lowlight/highlight are derived.
  const THEMES = {
    primary: ['#e23636', '#2ecc40', '#2b7fff'],
    neon:    ['#ff2d95', '#00fff0', '#39ff14'],
    sunset:  ['#ff6b35', '#f7548f', '#6a4c93'],
  };
  const FIXED_COL = ['#808080', '#000000', '#ffffff']; // grey / black / white

  // ── State ─────────────────────────────────────────────────────────────────
  const S = {
    gridSize: 16,
    uiTheme: 'purply',   // purply | sky | industrial — the app UI theme (separate from palette theme)
    frames: [],          // Color[][] where Color = null | '#rrggbb'
    current: 0,          // active frame index
    theme: 'primary',    // primary | neon | sunset | custom
    baseColors: THEMES.primary.slice(),
    activeColor: '#000000',
    erasing: false,
    ts: null,            // global TransformSettings
    history: [],         // auto-snapshots, newest first (max HIST_MAX)
    // Frame loop: marker sits in the gap between frames[gap] and frames[gap+1].
    // gap ∈ [0 .. frames.length-2]. parked = marker under ⟳ (loop off).
    loop: { shown: false, enabled: false, gap: null, parked: false },
  };

  // ── Auto-snapshot history ─────────────────────────────────────────────────
  const HIST_MAX = 5;
  const HIST_TICK_MS = 250;
  const HIST_THRESHOLD_MS = 5000;
  let histAccumMs = 0;
  let histPainting = false;
  let histDirty = false;
  let histInterval = null;

  // ── Color helpers ───────────────────────────────────────────────────────
  function hexToHsl(hex) {
    let r = parseInt(hex.slice(1, 3), 16) / 255;
    let g = parseInt(hex.slice(3, 5), 16) / 255;
    let b = parseInt(hex.slice(5, 7), 16) / 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    let h = 0, s = 0, l = (max + min) / 2;
    if (max !== min) {
      const d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      if (max === r) h = (g - b) / d + (g < b ? 6 : 0);
      else if (max === g) h = (b - r) / d + 2;
      else h = (r - g) / d + 4;
      h *= 60;
    }
    return { h, s: s * 100, l: l * 100 };
  }

  function hslToHex(h, s, l) {
    s /= 100; l /= 100;
    const c = (1 - Math.abs(2 * l - 1)) * s;
    const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
    const m = l - c / 2;
    let r = 0, g = 0, b = 0;
    if (h < 60) [r, g, b] = [c, x, 0];
    else if (h < 120) [r, g, b] = [x, c, 0];
    else if (h < 180) [r, g, b] = [0, c, x];
    else if (h < 240) [r, g, b] = [0, x, c];
    else if (h < 300) [r, g, b] = [x, 0, c];
    else [r, g, b] = [c, 0, x];
    const to = v => Math.round((v + m) * 255).toString(16).padStart(2, '0');
    return `#${to(r)}${to(g)}${to(b)}`;
  }

  function lowlight(hex) {
    const { h, s, l } = hexToHsl(hex);
    return hslToHex(h, s, l * 0.6);
  }

  function highlight(hex) {
    const { h, s, l } = hexToHsl(hex);
    return hslToHex(h, s, l + (100 - l) * 0.5);
  }

  // ── Default transform settings (mirrors TransformSettings.java defaults) ──
  function defaultTS() {
    return {
      effectType: 0,
      spread: 24, speedMs: 500, holdMs: 200, easing: 0,
      focalX: 50, focalY: 50,
      spin: 0, spinStrength: 100,
      explodeSpeedMs: 1000, explodeStrength: 100,
      unsplodeSpeedMs: 1000, unsplodeStrength: 95,
      gravityPush: 50, gravityPull: 50,
      gravityFocalX: 50, gravityFocalY: 100,
      popHoldMs: 0, extendMs: 500,
      wallDamping: 50, stayInCanvas: false, popStayAtFocus: false,
      twistFirstSpeedMs: 300, twistSecondSpeedMs: 300,
      twistFirstSmooth: 50, twistSecondSmooth: 50,
      twistDirection: 0, twistFullSpin: true, twistSpreadGap: false,
      morphSpeedMs: 600, morphHoldMs: 300, morphFadeDeaths: false,
      springStiffness: 30, springDamping: 30, springImpulse: 40,
      springSpeedMs: 1400, springHoldMs: 300,
    };
  }

  function newGrid(sz) {
    return Array.from({ length: sz }, () => Array(sz).fill(null));
  }

  function getActiveTS() { return S.ts; }

  // ── Canvas ────────────────────────────────────────────────────────────────
  const canvas = document.getElementById('grid');
  const ctx = canvas.getContext('2d');

  function setupCanvas() {
    const cs = CELL_SIZE[S.gridSize];
    const logical = S.gridSize * cs;
    const dpr = window.devicePixelRatio || 1;
    canvas.width = logical * dpr;
    canvas.height = logical * dpr;
    canvas.style.width = logical + 'px';
    canvas.style.height = logical + 'px';
    ctx.resetTransform();
    ctx.scale(dpr, dpr);
  }

  function renderCanvas() {
    setupCanvas();
    const cs = CELL_SIZE[S.gridSize];
    const frame = S.frames[S.current];
    for (let r = 0; r < S.gridSize; r++) {
      for (let c = 0; c < S.gridSize; c++) {
        ctx.fillStyle = frame[r][c] || ((r + c) % 2 === 0 ? CHECK_A : CHECK_B);
        ctx.fillRect(c * cs, r * cs, cs, cs);
      }
    }
    ctx.strokeStyle = GRID_COL;
    ctx.lineWidth = 0.5;
    ctx.beginPath();
    for (let i = 0; i <= S.gridSize; i++) {
      ctx.moveTo(i * cs, 0);
      ctx.lineTo(i * cs, S.gridSize * cs);
      ctx.moveTo(0, i * cs);
      ctx.lineTo(S.gridSize * cs, i * cs);
    }
    ctx.stroke();
  }

  // ── Preview canvas ────────────────────────────────────────────────────────
  const previewCanvas = document.getElementById('preview');
  let engine = null;

  function renderPreview(fi) {
    if (engine) engine.renderFrame(fi);
  }

  // ── Frame tabs ────────────────────────────────────────────────────────────
  function renderFrameTabs() {
    const bar = document.getElementById('frameTabs');
    bar.innerHTML = '';
    S.frames.forEach((_, fi) => {
      const tab = document.createElement('button');
      tab.className = 'frame-tab' + (fi === S.current ? ' active' : '');
      tab.textContent = `F${fi + 1}`;
      tab.addEventListener('click', () => switchFrame(fi));
      bar.appendChild(tab);
    });

    if (S.frames.length < MAX_FRAMES) {
      const add = document.createElement('button');
      add.className = 'add-frame-btn';
      add.title = 'Add frame';
      add.textContent = '+';
      add.addEventListener('click', addFrame);
      bar.appendChild(add);
    }

    if (S.frames.length > 1) {
      const del = document.createElement('button');
      del.className = 'del-frame-btn';
      del.title = 'Delete current frame';
      del.textContent = '✕';
      del.addEventListener('click', () => deleteFrame(S.current));
      bar.appendChild(del);
    }
  }

  function switchFrame(fi) {
    S.current = fi;
    renderCanvas();
    renderFrameTabs();
    syncSliders();
  }

  function addFrame() {
    if (S.frames.length >= MAX_FRAMES) return;
    S.frames.push(newGrid(S.gridSize));
    S.current = S.frames.length - 1;
    renderAll();
  }

  async function deleteFrame(fi) {
    if (S.frames.length <= 1) return;
    if (!(await confirmDeleteFrame(fi + 1))) return;
    S.frames.splice(fi, 1);
    if (S.current >= S.frames.length) S.current = S.frames.length - 1;
    if (engine) engine.stop();
    renderAll();
  }

  function confirmDeleteFrame(frameNumber) {
    return new Promise((resolve) => {
      const host = document.querySelector('.editor-col');
      const overlay = document.createElement('div');
      overlay.className = 'frame-del-overlay';

      const dialog = document.createElement('div');
      dialog.className = 'frame-del-dialog';

      const msg = document.createElement('div');
      msg.className = 'frame-del-msg';
      msg.textContent = `Delete Frame ${frameNumber}?`;

      const btnRow = document.createElement('div');
      btnRow.className = 'frame-del-btns';

      const yes = document.createElement('button');
      yes.className = 'frame-del-yes';
      yes.textContent = 'Yes.';

      const no = document.createElement('button');
      no.className = 'frame-del-no';
      no.textContent = 'No!';

      const close = (val) => { overlay.remove(); resolve(val); };
      yes.addEventListener('click', () => close(true));
      no.addEventListener('click', () => close(false));
      overlay.addEventListener('click', (e) => { if (e.target === overlay) close(false); });

      btnRow.appendChild(yes);
      btnRow.appendChild(no);
      dialog.appendChild(msg);
      dialog.appendChild(btnRow);
      overlay.appendChild(dialog);
      host.appendChild(overlay);
    });
  }

  // ── Frame loop row ──────────────────────────────────────────────────────
  function clampLoop() {
    const n = S.frames.length;
    if (n < 3) {
      S.loop.shown = false; S.loop.enabled = false;
      S.loop.parked = false; S.loop.gap = null;
      return;
    }
    if (S.loop.gap === null) S.loop.gap = n - 2;
    S.loop.gap = Math.max(0, Math.min(n - 2, S.loop.gap));
  }

  // Snap targets (x offsets relative to the loop row): one per interior frame
  // gap, plus a final "park" slot under the loop button.
  function loopSnaps() {
    const row = document.getElementById('loopRow');
    const tabsEl = document.getElementById('frameTabs');
    const rowLeft = row.getBoundingClientRect().left;
    const tabs = Array.from(tabsEl.querySelectorAll('.frame-tab'));
    const snaps = [];
    for (let g = 0; g < tabs.length - 1; g++) {
      const r1 = tabs[g].getBoundingClientRect();
      const r2 = tabs[g + 1].getBoundingClientRect();
      snaps.push({ x: (r1.right + r2.left) / 2 - rowLeft, gap: g });
    }
    const parkRef = tabsEl.querySelector('.add-frame-btn') || tabs[tabs.length - 1];
    if (parkRef) {
      const pr = parkRef.getBoundingClientRect();
      snaps.push({ x: (pr.left + pr.right) / 2 - rowLeft, park: true });
    }
    return snaps;
  }

  function nearestSnap(snaps, clientX) {
    const rowLeft = document.getElementById('loopRow').getBoundingClientRect().left;
    const x = clientX - rowLeft;
    let best = snaps[0], bestD = Infinity;
    for (const s of snaps) {
      const d = Math.abs(s.x - x);
      if (d < bestD) { bestD = d; best = s; }
    }
    return best;
  }

  function renderLoopRow() {
    const row = document.getElementById('loopRow');
    if (!row) return;
    clampLoop();
    row.innerHTML = '';
    const n = S.frames.length;

    const loopBtn = document.createElement('button');
    loopBtn.className = 'loop-btn';
    loopBtn.title = 'Frame loop';
    loopBtn.textContent = '⟳';
    loopBtn.disabled = n < 3;
    loopBtn.addEventListener('click', () => {
      if (n < 3) return;
      if (S.loop.shown) {
        S.loop.shown = false; S.loop.enabled = false; S.loop.parked = false;
      } else {
        S.loop.shown = true; S.loop.enabled = true;
        S.loop.parked = false; S.loop.gap = n - 2;
      }
      renderLoopRow();
    });
    row.appendChild(loopBtn);

    if (!S.loop.shown || n < 3) return;

    const snaps = loopSnaps();
    const parkSnap = snaps.find(s => s.park);
    const gapSnap = snaps.find(s => s.gap === S.loop.gap);

    const marker = document.createElement('button');
    marker.className = 'loopframe-marker '
      + (S.loop.parked ? 'parked' : (S.loop.enabled ? 'enabled' : 'disabled'));
    marker.title = 'Drag to set loop; click to disable; right-click to re-enable';
    marker.textContent = '↻';
    const target = S.loop.parked ? parkSnap : (gapSnap || parkSnap);
    marker.style.left = (target ? target.x : 0) + 'px';

    let dragging = false, moved = false, startX = 0, suppressClick = false;
    marker.addEventListener('mousedown', (e) => {
      if (e.button !== 0) return;
      e.preventDefault();
      dragging = true; moved = false; startX = e.clientX;
      const onMove = (ev) => {
        if (!dragging) return;
        if (Math.abs(ev.clientX - startX) > 3) moved = true;
        marker.style.left = nearestSnap(snaps, ev.clientX).x + 'px';
      };
      const onUp = (ev) => {
        dragging = false;
        window.removeEventListener('mousemove', onMove);
        window.removeEventListener('mouseup', onUp);
        if (!moved) return;
        suppressClick = true;
        const near = nearestSnap(snaps, ev.clientX);
        if (near.park) { S.loop.parked = true; S.loop.enabled = false; }
        else { S.loop.parked = false; S.loop.enabled = true; S.loop.gap = near.gap; }
        renderLoopRow();
      };
      window.addEventListener('mousemove', onMove);
      window.addEventListener('mouseup', onUp);
    });

    marker.addEventListener('click', () => {
      if (suppressClick) { suppressClick = false; return; }
      if (S.loop.parked) return;
      if (S.loop.enabled) { S.loop.enabled = false; renderLoopRow(); }
    });

    marker.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      if (!S.loop.parked && !S.loop.enabled) { S.loop.enabled = true; renderLoopRow(); }
    });

    row.appendChild(marker);
  }

  // ── Palette ───────────────────────────────────────────────────────────────
  function pickColor(color) {
    S.activeColor = color;
    S.erasing = false;
    renderPalette();
  }

  function makeSwatch(color, extraClass, title) {
    const sw = document.createElement('div');
    const isActive = !S.erasing && S.activeColor === color;
    sw.className = 'swatch' + (extraClass ? ' ' + extraClass : '') + (isActive ? ' active' : '');
    sw.style.background = color;
    sw.title = title || color;
    sw.addEventListener('click', () => pickColor(color));
    return sw;
  }

  function renderPalette() {
    const bar = document.getElementById('paletteBar');
    bar.innerHTML = '';
    const b = S.baseColors;

    // Row 0: base colors + grey
    b.forEach((color, i) => {
      const sw = makeSwatch(color, 'base', `Base ${i + 1} (right-click to edit)`);
      sw.addEventListener('contextmenu', (e) => {
        e.preventDefault();
        openBasePicker(i);
      });
      bar.appendChild(sw);
    });
    bar.appendChild(makeSwatch(FIXED_COL[0], null, 'Grey'));

    // Row 1: lowlights + black
    b.forEach(color => bar.appendChild(makeSwatch(lowlight(color), null, 'Lowlight')));
    bar.appendChild(makeSwatch(FIXED_COL[1], null, 'Black'));

    // Row 2: highlights + white
    b.forEach(color => bar.appendChild(makeSwatch(highlight(color), null, 'Highlight')));
    bar.appendChild(makeSwatch(FIXED_COL[2], null, 'White'));

    // Eraser (spans full width)
    const eraser = document.createElement('button');
    eraser.className = 'swatch-eraser' + (S.erasing ? ' active' : '');
    eraser.textContent = 'Eraser';
    eraser.title = 'Erase pixels';
    eraser.addEventListener('click', () => {
      S.erasing = true;
      renderPalette();
    });
    bar.appendChild(eraser);
  }

  // ── Canvas events ─────────────────────────────────────────────────────────
  let isPainting = false;

  function cellAt(e) {
    const cs = CELL_SIZE[S.gridSize];
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / (window.devicePixelRatio || 1) / rect.width;
    const scaleY = canvas.height / (window.devicePixelRatio || 1) / rect.height;
    const x = (e.clientX - rect.left) * scaleX;
    const y = (e.clientY - rect.top) * scaleY;
    const col = Math.floor(x / cs);
    const row = Math.floor(y / cs);
    if (row < 0 || row >= S.gridSize || col < 0 || col >= S.gridSize) return null;
    return { row, col };
  }

  function applyCell(cell) {
    if (!cell) return;
    const frame = S.frames[S.current];
    const color = S.erasing ? null : (S.activeColor || null);
    if (frame[cell.row][cell.col] === color) return;
    frame[cell.row][cell.col] = color;
    histDirty = true;
    // Fast single-cell redraw on editor canvas
    const cs = CELL_SIZE[S.gridSize];
    ctx.fillStyle = color || ((cell.row + cell.col) % 2 === 0 ? CHECK_A : CHECK_B);
    ctx.fillRect(cell.col * cs, cell.row * cs, cs, cs);
    ctx.strokeStyle = GRID_COL;
    ctx.lineWidth = 0.5;
    ctx.strokeRect(cell.col * cs + 0.25, cell.row * cs + 0.25, cs - 0.5, cs - 0.5);
  }

  function startHistoryTimer() {
    histPainting = true;
    if (histInterval !== null) return;
    histInterval = setInterval(() => {
      if (!histPainting) return;
      histAccumMs += HIST_TICK_MS;
      if (histAccumMs >= HIST_THRESHOLD_MS) {
        histAccumMs = 0;
        if (histDirty) {
          histDirty = false;
          captureHistory();
        }
      }
    }, HIST_TICK_MS);
  }

  canvas.addEventListener('mousedown', (e) => {
    if (e.button !== 0) return;   // left-click only; erase is a palette button now
    e.preventDefault();
    isPainting = true;
    startHistoryTimer();
    applyCell(cellAt(e));
  });

  canvas.addEventListener('mousemove', (e) => {
    if (!isPainting) return;
    applyCell(cellAt(e));
  });

  canvas.addEventListener('mouseup', () => { isPainting = false; histPainting = false; });
  canvas.addEventListener('mouseleave', () => { isPainting = false; histPainting = false; });
  canvas.addEventListener('contextmenu', (e) => e.preventDefault());

  // ── Transform panel ───────────────────────────────────────────────────────
  function buildTransformPanel() {
    const panel = document.getElementById('transformPanel');
    const scrollTop = panel.scrollTop;
    panel.innerHTML = '';

    const h = document.createElement('h3');
    h.textContent = 'Transform';
    panel.appendChild(h);

    const tabs = document.createElement('div');
    tabs.className = 'effect-tabs';
    ['Burst', 'Pop', 'Twist', 'Morph', 'Spring'].forEach((name, i) => {
      const btn = document.createElement('button');
      btn.className = 'btn' + (getActiveTS().effectType === i ? ' active' : '');
      btn.textContent = name;
      btn.addEventListener('click', () => {
        getActiveTS().effectType = i;
        buildTransformPanel();
      });
      tabs.appendChild(btn);
    });
    panel.appendChild(tabs);

    const et = getActiveTS().effectType;
    if (et === 0) buildBurstPanel(panel);
    else if (et === 1) buildPopPanel(panel);
    else if (et === 2) buildTwistPanel(panel);
    else if (et === 3) buildMorphPanel(panel);
    else buildSpringPanel(panel);

    panel.scrollTop = scrollTop;
  }

  function makeSlider(label, key, min, max, step) {
    step = step || 1;
    const ts = getActiveTS();
    const row = document.createElement('div');
    row.className = 'slider-row';

    const lbl = document.createElement('label');
    lbl.textContent = label;

    const sl = document.createElement('input');
    sl.type = 'range';
    sl.min = min; sl.max = max; sl.step = step;
    sl.value = ts[key];
    sl.dataset.key = key;
    sl.className = 'ts-slider';

    const val = document.createElement('span');
    val.className = 'slider-val';
    val.textContent = ts[key];
    val.dataset.key = key;
    val.className = 'slider-val ts-val';

    sl.addEventListener('input', () => {
      const v = parseInt(sl.value, 10);
      getActiveTS()[key] = v;
      val.textContent = v;
    });

    row.appendChild(lbl);
    row.appendChild(sl);
    row.appendChild(val);
    return row;
  }

  function makeToggle(label, key) {
    const ts = getActiveTS();
    const row = document.createElement('div');
    row.className = 'toggle-row';

    const lbl = document.createElement('label');
    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.checked = ts[key];
    cb.dataset.key = key;
    cb.className = 'ts-toggle';
    cb.addEventListener('change', () => { getActiveTS()[key] = cb.checked; });
    lbl.appendChild(cb);
    lbl.append(' ' + label);
    row.appendChild(lbl);
    return row;
  }

  function makeButtonGroup(label, key, options) {
    const ts = getActiveTS();
    const row = document.createElement('div');
    row.className = 'slider-row';

    const lbl = document.createElement('label');
    lbl.textContent = label;

    const grp = document.createElement('div');
    grp.className = 'btn-group';

    options.forEach(({ text, val }) => {
      const btn = document.createElement('button');
      btn.className = 'btn sm' + (ts[key] === val ? ' active' : '');
      btn.textContent = text;
      btn.dataset.group = key;
      btn.dataset.val = val;
      btn.addEventListener('click', () => {
        getActiveTS()[key] = val;
        grp.querySelectorAll('.btn').forEach(b =>
          b.classList.toggle('active', parseInt(b.dataset.val, 10) === val));
      });
      grp.appendChild(btn);
    });

    row.appendChild(lbl);
    row.appendChild(grp);
    return row;
  }

  function buildBurstPanel(parent) {
    const sectionLbl = document.createElement('div');
    sectionLbl.className = 'section-title';
    sectionLbl.textContent = 'Burst settings';
    parent.appendChild(sectionLbl);
    parent.appendChild(makeSlider('Spread', 'spread', 0, 100));
    parent.appendChild(makeSlider('Speed (ms)', 'speedMs', 50, 5000, 50));
    parent.appendChild(makeSlider('Hold (ms)', 'holdMs', 0, 3000, 50));
    parent.appendChild(makeButtonGroup('Easing', 'easing', [
      { text: 'Smooth', val: 0 }, { text: 'Sharp', val: 1 }, { text: 'Snappy', val: 2 },
    ]));
    parent.appendChild(makeSlider('Focal X', 'focalX', 0, 100));
    parent.appendChild(makeSlider('Focal Y', 'focalY', 0, 100));
    parent.appendChild(makeButtonGroup('Spin', 'spin', [
      { text: 'None', val: 0 }, { text: 'CW', val: 1 }, { text: 'CCW', val: 2 },
    ]));
    parent.appendChild(makeSlider('Spin Strength', 'spinStrength', 0, 200, 5));
  }

  function buildPopPanel(parent) {
    const sectionLbl = document.createElement('div');
    sectionLbl.className = 'section-title';
    sectionLbl.textContent = 'Pop settings';
    parent.appendChild(sectionLbl);
    parent.appendChild(makeSlider('Explode Speed (ms)', 'explodeSpeedMs', 100, 5000, 100));
    parent.appendChild(makeSlider('Explode Strength', 'explodeStrength', 0, 200, 5));
    parent.appendChild(makeSlider('Unsplode Speed (ms)', 'unsplodeSpeedMs', 100, 5000, 100));
    parent.appendChild(makeSlider('Unsplode Strength', 'unsplodeStrength', 0, 200, 5));
    parent.appendChild(makeSlider('Gravity Push', 'gravityPush', 0, 200, 5));
    parent.appendChild(makeSlider('Gravity Pull', 'gravityPull', 0, 200, 5));
    parent.appendChild(makeSlider('Gravity Focal X', 'gravityFocalX', 0, 100));
    parent.appendChild(makeSlider('Gravity Focal Y', 'gravityFocalY', 0, 100));
    parent.appendChild(makeSlider('Pop Hold (ms)', 'popHoldMs', 0, 3000, 50));
    parent.appendChild(makeSlider('Extend (ms)', 'extendMs', 0, 3000, 50));
    parent.appendChild(makeSlider('Wall Damping', 'wallDamping', 0, 100));
    parent.appendChild(makeToggle('Stay In Canvas', 'stayInCanvas'));
    parent.appendChild(makeToggle('Stay At Focus', 'popStayAtFocus'));
  }

  function buildTwistPanel(parent) {
    const sectionLbl = document.createElement('div');
    sectionLbl.className = 'section-title';
    sectionLbl.textContent = 'Twist settings';
    parent.appendChild(sectionLbl);
    parent.appendChild(makeSlider('First Speed (ms)', 'twistFirstSpeedMs', 50, 3000, 50));
    parent.appendChild(makeSlider('Second Speed (ms)', 'twistSecondSpeedMs', 50, 3000, 50));
    parent.appendChild(makeSlider('First Smooth', 'twistFirstSmooth', 0, 100));
    parent.appendChild(makeSlider('Second Smooth', 'twistSecondSmooth', 0, 100));
    parent.appendChild(makeButtonGroup('Direction', 'twistDirection', [
      { text: 'CW', val: 0 }, { text: 'CCW', val: 1 },
    ]));
    parent.appendChild(makeToggle('Full Spin', 'twistFullSpin'));
    parent.appendChild(makeToggle('Spread Gap', 'twistSpreadGap'));
  }

  function buildMorphPanel(parent) {
    const sectionLbl = document.createElement('div');
    sectionLbl.className = 'section-title';
    sectionLbl.textContent = 'Morph settings';
    parent.appendChild(sectionLbl);
    parent.appendChild(makeSlider('Speed (ms)', 'morphSpeedMs', 50, 3000, 50));
    parent.appendChild(makeSlider('Hold (ms)', 'morphHoldMs', 0, 3000, 50));
    parent.appendChild(makeToggle('Fade Deaths', 'morphFadeDeaths'));
  }

  function buildSpringPanel(parent) {
    const sectionLbl = document.createElement('div');
    sectionLbl.className = 'section-title';
    sectionLbl.textContent = 'Spring settings';
    parent.appendChild(sectionLbl);
    parent.appendChild(makeSlider('Stiffness', 'springStiffness', 1, 100));
    parent.appendChild(makeSlider('Damping (% critical)', 'springDamping', 0, 100));
    parent.appendChild(makeSlider('Impulse', 'springImpulse', 0, 100));
    parent.appendChild(makeSlider('Duration (ms)', 'springSpeedMs', 300, 3000, 50));
    parent.appendChild(makeSlider('Hold (ms)', 'springHoldMs', 0, 2000, 50));
  }

  function syncSliders() {
    const ts = getActiveTS();
    document.querySelectorAll('.ts-slider').forEach(sl => {
      const k = sl.dataset.key;
      if (k in ts) {
        sl.value = ts[k];
        const valEl = sl.nextElementSibling;
        if (valEl && valEl.classList.contains('ts-val')) valEl.textContent = ts[k];
      }
    });
    document.querySelectorAll('.ts-toggle').forEach(cb => {
      if (cb.dataset.key in ts) cb.checked = ts[cb.dataset.key];
    });
    document.querySelectorAll('[data-group]').forEach(btn => {
      const v = parseInt(btn.dataset.val, 10);
      btn.classList.toggle('active', ts[btn.dataset.group] === v);
    });
  }

  // ── SGA save ──────────────────────────────────────────────────────────────
  async function saveSga() {
    const zip = new JSZip();
    const gs = S.gridSize;
    const canvasPx = gs * 4;
    const savedIndices = [];   // original frame indices kept, in save order

    for (let fi = 0; fi < S.frames.length; fi++) {
      const frame = S.frames[fi];
      if (!frame.some(row => row.some(c => c !== null))) continue;

      let svg = `<?xml version="1.0" encoding="UTF-8"?>\n`;
      svg += `<svg xmlns="http://www.w3.org/2000/svg" width="${canvasPx}" height="${canvasPx}" viewBox="0 0 ${canvasPx} ${canvasPx}">\n`;
      for (let r = 0; r < gs; r++) {
        for (let c = 0; c < gs; c++) {
          if (frame[r][c]) {
            svg += `  <rect x="${c * 4}" y="${r * 4}" width="4" height="4" fill="${frame[r][c]}"/>\n`;
          }
        }
      }
      svg += `</svg>`;
      zip.file(`frame_${fi}.svg`, svg);
      savedIndices.push(fi);
    }

    if (savedIndices.length === 0) { alert('No non-empty frames to save.'); return; }

    // Frame-loop manifest. Endpoints are remapped onto the saved (compacted)
    // frame order; disabled if either loop frame was empty (and thus skipped).
    let manifest = { version: 1, loopEnabled: false };
    if (S.loop.shown && S.loop.enabled && !S.loop.parked && S.loop.gap !== null) {
      const posA = savedIndices.indexOf(S.loop.gap);
      const posB = savedIndices.indexOf(S.loop.gap + 1);
      if (posA >= 0 && posB === posA + 1) {
        manifest = { version: 1, loopEnabled: true, loopStart: posA, loopEnd: posB };
      }
    }
    zip.file('anim.json', JSON.stringify(manifest));

    const blob = await zip.generateAsync({ type: 'blob' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = 'sprite.sga'; a.click();
    URL.revokeObjectURL(url);
  }

  // ── SGA load ──────────────────────────────────────────────────────────────
  async function loadSga(file) {
    let zip;
    try { zip = await JSZip.loadAsync(file); }
    catch (e) { alert('Could not read .sga file: ' + e.message); return; }

    const svgMap = {};

    for (const [name, entry] of Object.entries(zip.files)) {
      if (name.endsWith('.svg')) svgMap[name] = await entry.async('text');
    }

    let manifest = null;
    const manifestEntry = zip.file('anim.json');
    if (manifestEntry) {
      try { manifest = JSON.parse(await manifestEntry.async('text')); } catch (e) { manifest = null; }
    }

    const names = Object.keys(svgMap).sort();
    if (names.length === 0) { alert('No SVG frames found in .sga file.'); return; }

    const frames = [];
    let targetSize = 16;
    for (const name of names) {
      const grid = parseSvg(svgMap[name]);
      if (grid) {
        frames.push(grid);
        if (grid.length > targetSize) targetSize = grid.length;
      }
    }
    if (frames.length === 0) { alert('No valid frames loaded.'); return; }

    // Web version only supports 16 and 32
    if (targetSize > 16) targetSize = 32;

    const resized = frames.map(f => {
      const g = newGrid(targetSize);
      for (let r = 0; r < Math.min(f.length, targetSize); r++)
        for (let c = 0; c < Math.min(f[r].length, targetSize); c++)
          g[r][c] = f[r][c];
      return g;
    });

    S.gridSize = targetSize;
    S.frames = resized.slice(0, MAX_FRAMES);
    S.current = 0;

    S.loop = { shown: false, enabled: false, gap: null, parked: false };
    if (manifest && manifest.loopEnabled && S.frames.length >= 3) {
      const a = manifest.loopStart, b = manifest.loopEnd;
      if (Number.isInteger(a) && b === a + 1 && a >= 0 && b <= S.frames.length - 1) {
        S.loop = { shown: true, enabled: true, gap: a, parked: false };
      }
    }

    updateSizeChecks();

    if (engine) engine.stop();
    renderAll();
  }

  // ── SVG parsing ───────────────────────────────────────────────────────────
  function parseSvg(text) {
    const doc = new DOMParser().parseFromString(text, 'image/svg+xml');
    const rects = doc.querySelectorAll('rect');
    if (!rects.length) return null;

    let minW = Infinity;
    rects.forEach(r => {
      const w = parseFloat(r.getAttribute('width') || '0');
      if (w > 0 && w < minW) minW = w;
    });
    if (!isFinite(minW)) return null;

    const cells = [];
    let maxCol = 0, maxRow = 0;
    rects.forEach(r => {
      const fill = r.getAttribute('fill');
      if (!fill || fill === 'none' || fill === 'transparent') return;
      const col = Math.round(parseFloat(r.getAttribute('x') || '0') / minW);
      const row = Math.round(parseFloat(r.getAttribute('y') || '0') / minW);
      const norm = normColor(fill);
      if (norm) { cells.push({ row, col, color: norm }); }
      if (col > maxCol) maxCol = col;
      if (row > maxRow) maxRow = row;
    });
    if (!cells.length) return null;

    let gridSize = 16;
    for (const s of [16, 32, 48, 64]) {
      if (maxCol < s && maxRow < s) { gridSize = s; break; }
    }
    const grid = newGrid(gridSize);
    cells.forEach(({ row, col, color }) => {
      if (row < gridSize && col < gridSize) grid[row][col] = color;
    });
    return grid;
  }

  function normColor(fill) {
    if (!fill) return null;
    fill = fill.trim();
    if (/^#[0-9a-fA-F]{6}$/.test(fill)) return fill.toLowerCase();
    if (/^#[0-9a-fA-F]{3}$/.test(fill)) {
      const [, r, g, b] = fill;
      return `#${r}${r}${g}${g}${b}${b}`.toLowerCase();
    }
    // Convert named or rgb() colors via canvas
    const tmp = document.createElement('canvas');
    const tc = tmp.getContext('2d');
    tc.fillStyle = fill;
    const c = tc.fillStyle;
    return /^#[0-9a-fA-F]{6}$/.test(c) ? c.toLowerCase() : null;
  }

  // ── Auto-snapshot history ─────────────────────────────────────────────────
  function captureHistory() {
    const framesCopy = S.frames.map(f => f.map(row => row.slice()));
    S.history.unshift({
      frames: framesCopy,
      frameIndex: Math.min(S.current, framesCopy.length - 1),
      gridSize: S.gridSize,
    });
    if (S.history.length > HIST_MAX) S.history.length = HIST_MAX;
    renderHistory();
  }

  function restoreSnapshot(snap) {
    if (!snap) return;
    S.gridSize = snap.gridSize;
    S.frames = snap.frames.map(f => f.map(row => row.slice()));
    S.current = Math.min(snap.frameIndex, S.frames.length - 1);
    updateSizeChecks();
    if (engine) engine.stop();
    renderAll();
  }

  function renderHistoryThumb(cv, snap) {
    const cells = snap.gridSize;
    const cs = Math.max(1, Math.floor(72 / cells));
    const px = cells * cs;
    const dpr = window.devicePixelRatio || 1;
    cv.width = px * dpr;
    cv.height = px * dpr;
    cv.style.width = px + 'px';
    cv.style.height = px + 'px';
    const c = cv.getContext('2d');
    c.resetTransform();
    c.scale(dpr, dpr);
    const frame = snap.frames[snap.frameIndex] || snap.frames[0];
    for (let r = 0; r < cells; r++) {
      for (let col = 0; col < cells; col++) {
        c.fillStyle = frame[r][col] || ((r + col) % 2 === 0 ? CHECK_A : CHECK_B);
        c.fillRect(col * cs, r * cs, cs, cs);
      }
    }
  }

  function renderHistory() {
    const host = document.getElementById('historyGrid');
    if (!host) return;
    host.innerHTML = '';
    S.history.forEach(snap => {
      const slot = document.createElement('button');
      slot.className = 'history-thumb';
      slot.title = 'Restore this state';
      const cv = document.createElement('canvas');
      renderHistoryThumb(cv, snap);
      slot.appendChild(cv);
      slot.addEventListener('click', () => {
        if (histDirty) captureHistory();
        histAccumMs = 0;
        histDirty = false;
        restoreSnapshot(snap);
      });
      host.appendChild(slot);
    });
  }

  // ── Full render ───────────────────────────────────────────────────────────
  function renderAll() {
    renderCanvas();
    renderPreview(0);
    renderFrameTabs();
    renderLoopRow();
    renderPalette();
    renderHistory();
    buildTransformPanel();
  }

  // ── Size options (in Whatnot menu) ────────────────────────────────────────
  function updateSizeChecks() {
    document.querySelectorAll('.size-opt').forEach(b =>
      b.classList.toggle('active', parseInt(b.dataset.size, 10) === S.gridSize));
  }

  // ── UI theme options (in Whatnot menu) ────────────────────────────────────
  function updateThemeChecks() {
    document.querySelectorAll('.theme-opt').forEach(b =>
      b.classList.toggle('active', b.dataset.theme === S.uiTheme));
  }
  function applyTheme(name) {
    S.uiTheme = name;
    if (name === 'purply') document.documentElement.removeAttribute('data-theme');
    else document.documentElement.setAttribute('data-theme', name);
    try { localStorage.setItem('bcw-ui-theme', name); } catch (e) {}
    updateThemeChecks();
    if (engine && engine.refreshTheme) engine.refreshTheme();
    renderAll();
  }

  // ── Base color picker (right-click a base swatch) ─────────────────────────
  let editingBase = -1;
  function openBasePicker(i) {
    editingBase = i;
    const picker = document.getElementById('baseColorPicker');
    picker.value = S.baseColors[i];
    picker.click();
  }

  // ── Resizable outer frame (drag the frame edge) ───────────────────────────
  function initFrameResize() {
    const root = document.documentElement;
    let dragging = false;
    document.body.addEventListener('mousedown', (e) => {
      if (e.target !== document.body) return; // only the frame/padding region
      dragging = true;
      e.preventDefault();
    });
    window.addEventListener('mousemove', (e) => {
      if (!dragging) return;
      // Frame thickness = cursor distance from the nearest viewport edge.
      const d = Math.min(e.clientX, e.clientY,
                         window.innerWidth - e.clientX,
                         window.innerHeight - e.clientY);
      root.style.setProperty('--frame-w', Math.max(0, Math.min(60, d)) + 'px');
    });
    window.addEventListener('mouseup', () => { dragging = false; });
  }

  // ── Resizable preview panel (grab either edge bar) ────────────────────────
  // Normal layout: drag resizes total preview width, clamped 200–320px (start 260).
  // Collapsed layout: drag nudges the 50/50 split ±50px via --collapsed-bias.
  function initPreviewResize() {
    const root = document.documentElement;
    const app = document.querySelector('.app');
    const BASE = 260, MIN = 200, MAX = 320;
    function drag(handleId, sign) {
      const handle = document.getElementById(handleId);
      handle.addEventListener('mousedown', (e) => {
        e.preventDefault();
        const startX = e.clientX;
        const collapsed = app.classList.contains('collapsed');
        const cssVar = collapsed ? '--collapsed-bias' : '--preview-w';
        const start = parseFloat(getComputedStyle(root).getPropertyValue(cssVar))
                      || (collapsed ? 0 : BASE);
        function onMove(ev) {
          const delta = (ev.clientX - startX) * sign;   // grow when dragging outward
          const next = collapsed
            ? Math.max(-50, Math.min(50, start + delta))
            : Math.max(MIN, Math.min(MAX, start + delta));
          root.style.setProperty(cssVar, next + 'px');
        }
        function onUp() {
          window.removeEventListener('mousemove', onMove);
          window.removeEventListener('mouseup', onUp);
        }
        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
      });
    }
    drag('previewResizeLeft', -1);  // drag left = wider
    drag('previewResizeRight', 1);  // drag right = wider
  }

  // ── Responsive collapse + Draw/Transform tabs (narrow screens) ────────────
  function initResponsive() {
    const app = document.querySelector('.app');
    const mq = window.matchMedia('(max-width: 640px)');
    function setMode(mode) {                 // 'draw' | 'transform'
      app.classList.toggle('mode-draw', mode === 'draw');
      app.classList.toggle('mode-transform', mode === 'transform');
      document.querySelectorAll('.mode-tab').forEach(b =>
        b.classList.toggle('active', b.dataset.mode === mode));
    }
    function apply() {
      app.classList.toggle('collapsed', mq.matches);
      if (mq.matches && !app.classList.contains('mode-draw')
                     && !app.classList.contains('mode-transform')) setMode('draw');
    }
    document.querySelectorAll('.mode-tab').forEach(btn =>
      btn.addEventListener('click', () => setMode(btn.dataset.mode)));
    mq.addEventListener('change', apply);
    apply();
  }

  // ── Init ──────────────────────────────────────────────────────────────────
  function init() {
    S.gridSize = 16;
    S.frames = [newGrid(16)];
    S.current = 0;
    S.theme = 'primary';
    S.baseColors = THEMES.primary.slice();
    S.activeColor = '#000000';
    S.erasing = false;
    S.ts = defaultTS();
    S.loop = { shown: false, enabled: false, gap: null, parked: false };
    window.addEventListener('resize', () => { if (S.loop.shown) renderLoopRow(); });

    // Grid size options (inside Whatnot menu)
    document.querySelectorAll('.size-opt').forEach(btn => {
      btn.addEventListener('click', () => {
        const newSize = parseInt(btn.dataset.size, 10);
        if (newSize === S.gridSize) return;
        if (!confirm(`Switch to ${newSize}×${newSize}? All frames will be reset.`)) return;
        S.gridSize = newSize;
        S.frames = [newGrid(newSize)];
        S.current = 0;
        S.loop = { shown: false, enabled: false, gap: null, parked: false };
        if (engine) engine.stop();
        updateSizeChecks();
        renderAll();
      });
    });
    updateSizeChecks();

    // UI theme options (inside Whatnot menu)
    document.querySelectorAll('.theme-opt').forEach(btn =>
      btn.addEventListener('click', () => applyTheme(btn.dataset.theme)));

    // File menu
    const menuBtn = document.getElementById('fileMenuBtn');
    const dropdown = document.getElementById('fileDropdown');
    menuBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      dropdown.classList.toggle('hidden');
    });
    document.addEventListener('click', () => dropdown.classList.add('hidden'));

    document.getElementById('cmdNew').addEventListener('click', () => {
      if (!confirm('New sprite? All frames will be reset.')) return;
      S.frames = [newGrid(S.gridSize)];
      S.current = 0;
      S.loop = { shown: false, enabled: false, gap: null, parked: false };
      S.history = [];
      histAccumMs = 0;
      histDirty = false;
      if (engine) engine.stop();
      renderAll();
    });

    const fileInput = document.getElementById('fileInput');
    document.getElementById('cmdLoad').addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', () => {
      if (fileInput.files[0]) loadSga(fileInput.files[0]);
      fileInput.value = '';
    });

    document.getElementById('cmdSave').addEventListener('click', saveSga);

    // Palette theme dropdown
    const themeSelect = document.getElementById('themeSelect');
    themeSelect.addEventListener('change', () => {
      S.theme = themeSelect.value;
      if (THEMES[S.theme]) S.baseColors = THEMES[S.theme].slice();
      renderPalette();
    });

    // Base color picker (custom): recompute lowlight/highlight from the new base
    const basePicker = document.getElementById('baseColorPicker');
    basePicker.addEventListener('input', () => {
      if (editingBase < 0) return;
      S.baseColors[editingBase] = basePicker.value;
      S.theme = 'custom';
      themeSelect.value = 'custom';
      renderPalette();
    });

    // Clear frame
    document.getElementById('clearAllBtn').addEventListener('click', () => {
      if (!confirm(`Clear frame ${S.current + 1}?`)) return;
      S.frames[S.current] = newGrid(S.gridSize);
      renderCanvas();
    });

    // Outer frame + preview resize handles
    initFrameResize();
    initPreviewResize();
    initResponsive();

    // Preview controls
    engine = new AnimEngine(() => S, previewCanvas);
    document.getElementById('previewStop').addEventListener('click', () => engine.stop());
    document.getElementById('previewStep').addEventListener('click', () => engine.step());
    document.getElementById('previewPlay').addEventListener('click', () => engine.play());

    applyTheme(localStorage.getItem('bcw-ui-theme') || 'purply');
    renderAll();
  }

  init();
})();

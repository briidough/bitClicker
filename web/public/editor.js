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
    // Mobile triangle brush: offset cursor so the paint point (tip) sits above
    // the finger instead of under it. enabled defaults by screen size at init.
    // mode: 'auto' = one finger on canvas, pressure-gated (light aims, firm
    // draws once + a 1s hold starts continuous draw). 'manual' = bimanual —
    // one finger aims on the canvas, a second finger taps/holds the grey
    // surface around the canvas to actually draw. Per-mode gesture state
    // (phase, active pointer ids, timers) lives in module-level vars below,
    // not here.
    brush: { enabled: false, mode: 'auto', tipCell: null },
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

  function cellAtPoint(clientX, clientY) {
    const cs = CELL_SIZE[S.gridSize];
    const rect = canvas.getBoundingClientRect();
    const scaleX = canvas.width / (window.devicePixelRatio || 1) / rect.width;
    const scaleY = canvas.height / (window.devicePixelRatio || 1) / rect.height;
    const x = (clientX - rect.left) * scaleX;
    const y = (clientY - rect.top) * scaleY;
    const col = Math.floor(x / cs);
    const row = Math.floor(y / cs);
    if (row < 0 || row >= S.gridSize || col < 0 || col >= S.gridSize) return null;
    return { row, col };
  }

  function cellAt(e) { return cellAtPoint(e.clientX, e.clientY); }

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
    if (S.brush.enabled) return;  // brush mode handles input via pointer events
    if (e.button !== 0) return;   // left-click only; erase is a palette button now
    e.preventDefault();
    isPainting = true;
    startHistoryTimer();
    applyCell(cellAt(e));
  });

  canvas.addEventListener('mousemove', (e) => {
    if (S.brush.enabled) return;
    if (!isPainting) return;
    applyCell(cellAt(e));
  });

  canvas.addEventListener('mouseup', () => { isPainting = false; histPainting = false; });
  canvas.addEventListener('mouseleave', () => { isPainting = false; histPainting = false; });
  canvas.addEventListener('contextmenu', (e) => e.preventDefault());

  // ── Mobile triangle brush ─────────────────────────────────────────────────
  // Offset cursor: the finger drags the base, the tip (a few cells above the
  // finger) marks the painted cell so it's never hidden under the fingertip.
  //
  // AUTO (one finger, on canvas): light touch aims only; a firm press draws
  // one dot and starts a hold timer; holding firm for HOLD_MS charges the
  // sprite, double-pulses, and switches to continuous drag-paint; releasing
  // leaves the sprite on screen and fades it out over FADE_OUT_MS (re-touching
  // before the fade finishes snaps it back to full opacity instantly).
  //
  // MANUAL (two fingers): one finger stays on the canvas purely to aim the
  // brush — it never draws. A second finger taps/holds the grey surface
  // surrounding the canvas (#brushTapSurface) to actually draw: a quick tap
  // draws one dot, a held tap (HOLD_MS) charges + double-pulses + starts
  // continuous draw that follows the aiming finger. Lifting the aiming finger
  // hides the surface and stops everything.
  //
  // All tunable timings/thresholds live in one place below so they're easy to
  // retune without hunting through the state machine.
  const BRUSH_CFG = {
    FIRM_PRESSURE_HI: 0.70,     // firm-press threshold (buffered touch pressure)
    FULL_PRESSURE_HI: 0.95,     // thumb-level pressure — skip the initial pulse/dot, just wait for the hold
    HOLD_MS: 1000,              // firm/tap hold duration -> charge + continuous draw
    CHARGE_MS: 600,             // charge (color-fill) animation duration
    PULSE_MS: 220,              // single pulse duration (matches the .pulse keyframe)
    PULSE_GAP_MS: 140,          // gap between the two pulses of a double-pulse
    FADE_OUT_MS: 2000,          // auto: sprite fade-out after release
    BRUSH_LIFT: 24,             // px the whole triangle floats above the finger
    BRUSH_REACH_MULT: 2.2,      // reach = mult * referenceCell
    OVERLAY_ALPHA: 0.42,        // grey tap-surface opacity (+20% from 0.35)
    DEBUG_TAP_COUNT: 5,         // taps on Manual to toggle debug mode
    DEBUG_TAP_WINDOW_MS: 600,   // max gap between taps counted toward the sequence
    VIBRATE_MS: 10,
  };

  let brushCursor = null;
  let brushDebugEl = null;
  let tapSurfaceEl = null;
  let pressureBuffer = null;   // latest pressure reading this gesture (null = cleared)
  let debugMode = false;       // in-memory only — never persisted, off on every load
  let manualTapCount = 0;      // Manual-button 5-tap debug toggle
  let manualTapLast = 0;

  // AUTO mode gesture state
  let autoPointerId = null;
  let autoPhase = 'idle';      // 'idle' | 'aim' | 'drawOnce' | 'charging' | 'continuous' | 'fading'
  let autoHoldTimer = null;
  let autoFadeTimer = null;

  // MANUAL mode gesture state
  let manualCanvasPointerId = null;   // aiming finger, on the canvas
  let manualTapPointerId = null;      // drawing finger, on #brushTapSurface
  let manualHoldTimer = null;
  let manualDrawing = false;          // continuous draw active (tap finger held past HOLD_MS)

  function brushDebug(e) {
    if (!debugMode) return;
    if (!brushDebugEl) brushDebugEl = document.getElementById('brushDebug');
    if (!brushDebugEl) return;
    if (!S.brush.enabled) { brushDebugEl.classList.add('hidden'); return; }
    const buf = pressureBuffer === null ? '-' : pressureBuffer.toFixed(3);
    const detail = S.brush.mode === 'manual'
      ? `canvasFinger:${manualCanvasPointerId !== null} tapFinger:${manualTapPointerId !== null} drawing:${manualDrawing}`
      : `phase:${autoPhase}`;
    brushDebugEl.classList.remove('hidden');
    brushDebugEl.textContent =
      `type:${e.pointerType}\n` +
      `press:${(e.pressure || 0).toFixed(3)} w:${(e.width || 0).toFixed(1)} h:${(e.height || 0).toFixed(1)}\n` +
      `buffer:${buf} hi:${BRUSH_CFG.FIRM_PRESSURE_HI}\n` +
      `mode:${S.brush.mode} ${detail}`;
  }

  function updateDebugUI() {
    document.body.classList.toggle('debug-on', debugMode);
    if (!debugMode) {
      if (!brushDebugEl) brushDebugEl = document.getElementById('brushDebug');
      if (brushDebugEl) brushDebugEl.classList.add('hidden');
    }
  }

  function brushReachPx() {
    // Size the brush off the 16-grid scale regardless of the actual grid size,
    // so the triangle stays the same on-screen size on 16×16 and 32×32.
    const rect = canvas.getBoundingClientRect();
    const referenceCell = rect.width / 16;
    return BRUSH_CFG.BRUSH_REACH_MULT * referenceCell;
  }

  function brushTipCell(clientX, clientY) {
    return cellAtPoint(clientX, clientY - brushReachPx() - BRUSH_CFG.BRUSH_LIFT);
  }

  function positionBrushCursor(clientX, clientY) {
    if (!brushCursor) brushCursor = document.getElementById('brushCursor');
    if (!brushCursor) return;
    const h = brushReachPx();
    brushCursor.style.height = h + 'px';
    brushCursor.style.width = (h * 100 / 120) + 'px';
    // Float the base BRUSH_LIFT above the finger (so the fingertip doesn't cover
    // the triangle); the tip then lands h+lift above the finger — exactly the
    // point brushTipCell() samples.
    brushCursor.style.transform =
      `translate(${clientX}px, ${clientY - BRUSH_CFG.BRUSH_LIFT}px) translate(-50%, -100%)`;
  }

  // Copy this event's pressure into the per-gesture buffer.
  function bufferPressure(e) {
    pressureBuffer = e.pressure || 0;
  }

  // Is the pointer currently "hard"? Mouse: any held button. Touch: read the
  // buffered pressure against the absolute high threshold. Reading from the
  // buffer (cleared on lift) keeps a stale pressure value from the previous
  // touch out of a fresh gesture.
  function isFirmNow(e) {
    if (e.pointerType === 'mouse') return (e.buttons & 1) === 1;
    if (pressureBuffer === null) return false;
    return pressureBuffer > BRUSH_CFG.FIRM_PRESSURE_HI;
  }

  function pulseBrush() {
    if (!brushCursor) brushCursor = document.getElementById('brushCursor');
    if (!brushCursor) return;
    brushCursor.classList.remove('pulse');
    void brushCursor.getBoundingClientRect();   // reflow so the animation restarts
    brushCursor.classList.add('pulse');
  }

  function doublePulse() {
    pulseBrush();
    setTimeout(pulseBrush, BRUSH_CFG.PULSE_MS + BRUSH_CFG.PULSE_GAP_MS);
  }

  // Fill color for the charge effect: whatever the user currently has selected
  // to draw with, so the sprite fills with the same color it's about to paint.
  function syncBrushFillColor() {
    const color = S.erasing ? '#808080' : (S.activeColor || '#000000');
    document.documentElement.style.setProperty('--brush-fill-color', color);
  }

  // Charge: fill the triangle from base to point with the selected color, as
  // if the color is welling up before it "comes out" once continuous auto-draw
  // starts. The fill persists (does not auto-clear) until the next gesture
  // resets it — see resetBrushFill().
  function startCharge() {
    if (!brushCursor) brushCursor = document.getElementById('brushCursor');
    if (!brushCursor) return;
    syncBrushFillColor();
    brushCursor.classList.remove('charging');
    void brushCursor.getBoundingClientRect();   // reflow so the fill restarts from empty
    brushCursor.classList.add('charging');
  }

  function resetBrushFill() {
    if (!brushCursor) brushCursor = document.getElementById('brushCursor');
    if (brushCursor) brushCursor.classList.remove('charging');
  }

  function showBrushCursor(show) {
    if (!brushCursor) brushCursor = document.getElementById('brushCursor');
    if (brushCursor) brushCursor.classList.toggle('hidden', !show);
  }

  // Cancel a fade-out in progress and snap the sprite back to full opacity
  // instantly (no transition) — used whenever a fresh auto gesture begins.
  function snapBrushOpacity() {
    if (!brushCursor) brushCursor = document.getElementById('brushCursor');
    if (!brushCursor) return;
    brushCursor.classList.remove('fading');
    brushCursor.classList.add('snap');
  }

  function resetAutoHoldTimer() {
    if (autoHoldTimer !== null) { clearTimeout(autoHoldTimer); autoHoldTimer = null; }
  }

  function cancelAutoFade() {
    if (autoFadeTimer !== null) { clearTimeout(autoFadeTimer); autoFadeTimer = null; }
  }

  // Auto mode: hold timer fired after a 1s firm press — charge, double-pulse,
  // then switch into continuous drag-paint.
  function autoHoldFire() {
    autoHoldTimer = null;
    autoPhase = 'charging';
    startCharge();
    setTimeout(() => {
      doublePulse();
      if (navigator.vibrate) navigator.vibrate(BRUSH_CFG.VIBRATE_MS);
      setTimeout(() => {
        if (autoPhase === 'charging') autoPhase = 'continuous';
      }, BRUSH_CFG.PULSE_MS * 2 + BRUSH_CFG.PULSE_GAP_MS);
    }, BRUSH_CFG.CHARGE_MS);
  }

  function startAutoGesture(e) {
    cancelAutoFade();
    resetAutoHoldTimer();
    autoPointerId = e.pointerId;
    snapBrushOpacity();
    resetBrushFill();
    if (e.pointerType === 'mouse') {
      // Mouse: click = immediate single dot, drag while held = continuous paint.
      autoPhase = 'continuous';
      pulseBrush();
      if (navigator.vibrate) navigator.vibrate(BRUSH_CFG.VIBRATE_MS);
      startHistoryTimer();
      applyCell(S.brush.tipCell);
      return;
    }
    autoPhase = 'aim';   // light touch: aim only, no draw yet
  }

  function handleAutoMove(e, cell, moved) {
    if (e.pointerType === 'mouse') {
      if (autoPhase === 'continuous' && moved) applyCell(cell);
      return;
    }
    if (autoPhase === 'aim') {
      if (isFirmNow(e)) {
        autoPhase = 'drawOnce';
        startHistoryTimer();
        // Thumb-level (full) pressure: skip the pulse + initial dot entirely —
        // just sit quietly until the hold fires, then charge/pulse as usual.
        const full = pressureBuffer !== null && pressureBuffer >= BRUSH_CFG.FULL_PRESSURE_HI;
        if (!full) {
          pulseBrush();
          if (navigator.vibrate) navigator.vibrate(BRUSH_CFG.VIBRATE_MS);
          applyCell(cell);
        }
        resetAutoHoldTimer();
        autoHoldTimer = setTimeout(autoHoldFire, BRUSH_CFG.HOLD_MS);
      }
    } else if (autoPhase === 'drawOnce') {
      if (!isFirmNow(e)) {
        // Pressure dropped before the hold fired — cancel the charge, back to aim.
        resetAutoHoldTimer();
        autoPhase = 'aim';
      }
      // Still just drawOnce/charging-pending: no continuous painting yet.
    } else if (autoPhase === 'continuous') {
      if (moved) applyCell(cell);
    }
    // 'charging': ignore moves until the charge sequence resolves to continuous.
  }

  function endAutoGesture(e) {
    resetAutoHoldTimer();
    autoPointerId = null;
    histPainting = false;
    pressureBuffer = null;
    if (e && e.pointerType === 'mouse') {
      autoPhase = 'idle';
      showBrushCursor(false);
      brushCursor && brushCursor.classList.remove('fading', 'charging');
      return;
    }
    autoPhase = 'fading';
    if (brushCursor) {
      // Leave 'charging' (the color fill) as-is so the filled sprite fades
      // out as a whole, rather than snapping back to empty before it fades.
      brushCursor.classList.remove('snap');
      void brushCursor.getBoundingClientRect();   // reflow so the fade transition restarts cleanly
      brushCursor.classList.add('fading');
    }
    cancelAutoFade();
    autoFadeTimer = setTimeout(() => {
      autoFadeTimer = null;
      autoPhase = 'idle';
      showBrushCursor(false);
      if (brushCursor) brushCursor.classList.remove('fading');
    }, BRUSH_CFG.FADE_OUT_MS);
  }

  // ── Manual mode: bimanual (aim finger on canvas, draw finger on the overlay) ──

  function positionTapSurface() {
    if (!tapSurfaceEl) tapSurfaceEl = document.getElementById('brushTapSurface');
    if (!tapSurfaceEl) return;
    const rect = canvas.getBoundingClientRect();
    const vw = window.innerWidth, vh = window.innerHeight;
    const top = tapSurfaceEl.querySelector('.strip-top');
    const bottom = tapSurfaceEl.querySelector('.strip-bottom');
    const left = tapSurfaceEl.querySelector('.strip-left');
    const right = tapSurfaceEl.querySelector('.strip-right');
    if (top) {
      top.style.left = '0px'; top.style.top = '0px';
      top.style.width = vw + 'px'; top.style.height = Math.max(0, rect.top) + 'px';
    }
    if (bottom) {
      bottom.style.left = '0px'; bottom.style.top = Math.max(0, rect.bottom) + 'px';
      bottom.style.width = vw + 'px'; bottom.style.height = Math.max(0, vh - rect.bottom) + 'px';
    }
    if (left) {
      left.style.left = '0px'; left.style.top = Math.max(0, rect.top) + 'px';
      left.style.width = Math.max(0, rect.left) + 'px'; left.style.height = Math.max(0, rect.bottom - rect.top) + 'px';
    }
    if (right) {
      right.style.left = Math.max(0, rect.right) + 'px'; right.style.top = Math.max(0, rect.top) + 'px';
      right.style.width = Math.max(0, vw - rect.right) + 'px'; right.style.height = Math.max(0, rect.bottom - rect.top) + 'px';
    }
  }

  function showTapSurface(show) {
    if (!tapSurfaceEl) tapSurfaceEl = document.getElementById('brushTapSurface');
    if (!tapSurfaceEl) return;
    if (show) positionTapSurface();
    tapSurfaceEl.classList.toggle('hidden', !show);
  }

  function stopManualDrawing() {
    if (manualHoldTimer !== null) { clearTimeout(manualHoldTimer); manualHoldTimer = null; }
    manualDrawing = false;
    histPainting = false;
  }

  function startManualAim(e) {
    manualCanvasPointerId = e.pointerId;
    resetBrushFill();
    showTapSurface(true);
  }

  function endManualAim() {
    manualCanvasPointerId = null;
    manualTapPointerId = null;
    stopManualDrawing();
    resetBrushFill();
    showTapSurface(false);
    showBrushCursor(false);
    hideTapPulse();
    pressureBuffer = null;
  }

  function manualHoldFire() {
    manualHoldTimer = null;
    startCharge();
    setTimeout(() => {
      doublePulse();
      if (navigator.vibrate) navigator.vibrate(BRUSH_CFG.VIBRATE_MS);
      setTimeout(() => {
        manualDrawing = true;
        startHistoryTimer();
        applyCell(S.brush.tipCell);
      }, BRUSH_CFG.PULSE_MS * 2 + BRUSH_CFG.PULSE_GAP_MS);
    }, BRUSH_CFG.CHARGE_MS);
  }

  let tapPulseEl = null;
  function showTapPulse(clientX, clientY) {
    if (!tapPulseEl) tapPulseEl = document.getElementById('tapPulseFx');
    if (!tapPulseEl) return;
    // Position via left/top (not transform) — the pulse keyframe animates
    // transform:scale() itself, which would otherwise clobber a translate().
    tapPulseEl.style.left = clientX + 'px';
    tapPulseEl.style.top = clientY + 'px';
    tapPulseEl.classList.remove('hidden', 'pulse');
    void tapPulseEl.getBoundingClientRect();   // reflow so the animation restarts
    tapPulseEl.classList.add('pulse');
  }

  // Force the ripple to its fully-cleared state — used whenever the manual
  // overlay/gesture tears down, so a mid-animation ripple can't linger after
  // the aiming finger lifts.
  function hideTapPulse() {
    if (!tapPulseEl) tapPulseEl = document.getElementById('tapPulseFx');
    if (!tapPulseEl) return;
    tapPulseEl.classList.remove('pulse');
    tapPulseEl.classList.add('hidden');
  }

  function handleTapPointerDown(e) {
    if (manualCanvasPointerId === null) return;   // aiming finger must be down first
    e.preventDefault();
    try { e.target.setPointerCapture(e.pointerId); } catch (_) {}
    manualTapPointerId = e.pointerId;
    showTapPulse(e.clientX, e.clientY);
    if (manualHoldTimer !== null) clearTimeout(manualHoldTimer);
    manualHoldTimer = setTimeout(manualHoldFire, BRUSH_CFG.HOLD_MS);
  }

  function handleTapPointerUp(e) {
    if (e.pointerId !== manualTapPointerId) return;
    if (manualHoldTimer !== null) {
      // Released before the hold fired: quick tap draws a single dot.
      clearTimeout(manualHoldTimer);
      manualHoldTimer = null;
      pulseBrush();
      if (navigator.vibrate) navigator.vibrate(BRUSH_CFG.VIBRATE_MS);
      startHistoryTimer();
      applyCell(S.brush.tipCell);
    }
    stopManualDrawing();
    manualTapPointerId = null;
  }

  function endBrush() {
    resetAutoHoldTimer();
    cancelAutoFade();
    autoPointerId = null;
    autoPhase = 'idle';
    manualCanvasPointerId = null;
    manualTapPointerId = null;
    stopManualDrawing();
    pressureBuffer = null;
    histPainting = false;
    S.brush.tipCell = null;
    showBrushCursor(false);
    showTapSurface(false);
    hideTapPulse();
    if (brushCursor) brushCursor.classList.remove('fading', 'charging', 'pulse');
  }

  canvas.addEventListener('pointerdown', (e) => {
    if (!S.brush.enabled) return;
    e.preventDefault();
    try { canvas.setPointerCapture(e.pointerId); } catch (_) {}
    positionBrushCursor(e.clientX, e.clientY);
    S.brush.tipCell = brushTipCell(e.clientX, e.clientY);
    showBrushCursor(true);
    if (S.brush.mode === 'manual') {
      startManualAim(e);
    } else {
      bufferPressure(e);
      startAutoGesture(e);
    }
    brushDebug(e);
  });

  canvas.addEventListener('pointermove', (e) => {
    if (!S.brush.enabled) return;
    positionBrushCursor(e.clientX, e.clientY);
    const isActivePointer = S.brush.mode === 'manual'
      ? e.pointerId === manualCanvasPointerId
      : e.pointerId === autoPointerId;
    if (S.brush.mode !== 'manual' && isActivePointer) bufferPressure(e);
    brushDebug(e);
    if (!isActivePointer) return;   // mouse hover with no button = pure aiming
    const cell = brushTipCell(e.clientX, e.clientY);
    const moved = !sameCell(cell, S.brush.tipCell);
    S.brush.tipCell = cell;
    if (S.brush.mode === 'manual') {
      if (manualDrawing && moved) applyCell(cell);
    } else {
      handleAutoMove(e, cell, moved);
    }
  });

  canvas.addEventListener('pointerup', (e) => {
    if (!S.brush.enabled) return;
    try { canvas.releasePointerCapture(e.pointerId); } catch (_) {}
    if (S.brush.mode === 'manual') {
      if (e.pointerId === manualCanvasPointerId) endManualAim();
    } else if (e.pointerId === autoPointerId) {
      endAutoGesture(e);
    }
  });
  canvas.addEventListener('pointercancel', (e) => {
    if (!S.brush.enabled) return;
    if (S.brush.mode === 'manual') {
      if (e.pointerId === manualCanvasPointerId) endManualAim();
    } else if (e.pointerId === autoPointerId) {
      endAutoGesture(e);
    }
  });
  canvas.addEventListener('pointerleave', (e) => {
    if (!S.brush.enabled) return;
    if (e.pointerType === 'mouse' && autoPointerId === null && manualCanvasPointerId === null) {
      showBrushCursor(false);
    }
  });

  const tapSurface = document.getElementById('brushTapSurface');
  if (tapSurface) {
    tapSurface.addEventListener('pointerdown', (e) => {
      if (!S.brush.enabled || S.brush.mode !== 'manual') return;
      handleTapPointerDown(e);
      brushDebug(e);
    });
    tapSurface.addEventListener('pointerup', (e) => {
      if (!S.brush.enabled || S.brush.mode !== 'manual') return;
      handleTapPointerUp(e);
    });
    tapSurface.addEventListener('pointercancel', (e) => {
      if (!S.brush.enabled || S.brush.mode !== 'manual') return;
      handleTapPointerUp(e);
    });
  }
  window.addEventListener('resize', () => {
    if (tapSurfaceEl && !tapSurfaceEl.classList.contains('hidden')) positionTapSurface();
  });
  window.addEventListener('orientationchange', () => {
    if (tapSurfaceEl && !tapSurfaceEl.classList.contains('hidden')) positionTapSurface();
  });

  // Manual mode requires two simultaneous touches by design (aim + draw
  // fingers), so block native pinch-zoom app-wide while the brush is on —
  // otherwise a two-finger gesture would resize the whole app.
  let pinchGuardInstalled = false;
  function pinchGuardHandler(e) {
    if (e.touches && e.touches.length > 1) e.preventDefault();
  }
  function installPinchGuard() {
    if (pinchGuardInstalled) return;
    document.addEventListener('touchmove', pinchGuardHandler, { passive: false });
    pinchGuardInstalled = true;
  }
  function removePinchGuard() {
    if (!pinchGuardInstalled) return;
    document.removeEventListener('touchmove', pinchGuardHandler, { passive: false });
    pinchGuardInstalled = false;
  }

  function sameCell(a, b) {
    if (a === b) return true;
    if (!a || !b) return false;
    return a.row === b.row && a.col === b.col;
  }

  function updateBrushCheck() {
    const btn = document.getElementById('brushToggle');
    if (btn) btn.classList.toggle('active', S.brush.enabled);
  }

  function updateBrushModeButtons() {
    const auto = document.getElementById('brushModeAuto');
    const manual = document.getElementById('brushModeManual');
    if (auto) auto.classList.toggle('active', S.brush.mode === 'auto');
    if (manual) manual.classList.toggle('active', S.brush.mode === 'manual');
  }

  function applyBrushMode() {
    canvas.style.touchAction = S.brush.enabled ? 'none' : '';
    canvas.style.cursor = S.brush.enabled ? 'none' : '';
    const flank = document.getElementById('paletteFlank');
    if (flank) flank.classList.toggle('modes-on', S.brush.enabled);
    if (S.brush.enabled) {
      document.documentElement.style.setProperty('--brush-charge-ms', BRUSH_CFG.CHARGE_MS + 'ms');
      document.documentElement.style.setProperty('--brush-fade-ms', BRUSH_CFG.FADE_OUT_MS + 'ms');
      document.documentElement.style.setProperty('--brush-overlay-alpha', String(BRUSH_CFG.OVERLAY_ALPHA));
      installPinchGuard();
    } else {
      endBrush();
      removePinchGuard();
    }
    updateBrushCheck();
    updateBrushModeButtons();
  }

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
  // Drag resizes total preview width, clamped 200–320px (start 260).
  // Desktop only — the handles are hidden on the collapsed mobile layout.
  function initPreviewResize() {
    const root = document.documentElement;
    const BASE = 260, MIN = 200, MAX = 320;
    function drag(handleId, sign) {
      const handle = document.getElementById(handleId);
      handle.addEventListener('mousedown', (e) => {
        e.preventDefault();
        const startX = e.clientX;
        const start = parseFloat(getComputedStyle(root).getPropertyValue('--preview-w')) || BASE;
        function onMove(ev) {
          const delta = (ev.clientX - startX) * sign;   // grow when dragging outward
          const next = Math.max(MIN, Math.min(MAX, start + delta));
          root.style.setProperty('--preview-w', next + 'px');
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

    // Edge-swipe to switch views (mobile only). Starting near a screen edge
    // keeps the canvas interior free for drawing/tapping.
    const EDGE = 30, DIST = 50;
    let sx = 0, sy = 0, fromEdge = null;   // 'left' | 'right' | null
    window.addEventListener('touchstart', (e) => {
      if (!app.classList.contains('collapsed')) { fromEdge = null; return; }
      // Don't let an edge-started brush stroke double as a view-switch swipe.
      if (S.brush.enabled && e.target === canvas) { fromEdge = null; return; }
      const t = e.touches[0]; sx = t.clientX; sy = t.clientY;
      fromEdge = sx <= EDGE ? 'left' : (sx >= window.innerWidth - EDGE ? 'right' : null);
    }, { passive: true });
    window.addEventListener('touchend', (e) => {
      if (!fromEdge || !app.classList.contains('collapsed')) return;
      const t = e.changedTouches[0];
      const dx = t.clientX - sx, dy = t.clientY - sy;
      if (Math.abs(dx) >= DIST && Math.abs(dx) > Math.abs(dy)) {
        const drawing = app.classList.contains('mode-draw');
        if (drawing && fromEdge === 'right' && dx < 0) setMode('transform');
        else if (!drawing && fromEdge === 'left' && dx > 0) setMode('draw');
      }
      fromEdge = null;
    }, { passive: true });
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

    // Mobile brush toggle (inside Whatnot menu). Default ON for small screens.
    S.brush.enabled = window.matchMedia('(max-width: 640px)').matches;
    applyBrushMode();
    const brushToggle = document.getElementById('brushToggle');
    if (brushToggle) brushToggle.addEventListener('click', (e) => {
      e.stopPropagation();
      S.brush.enabled = !S.brush.enabled;
      applyBrushMode();
    });
    const setBrushMode = (mode) => {
      if (S.brush.mode === mode) return;
      endBrush();
      S.brush.mode = mode;
      updateBrushModeButtons();
    };
    const bmAuto = document.getElementById('brushModeAuto');
    const bmManual = document.getElementById('brushModeManual');
    if (bmAuto) bmAuto.addEventListener('click', () => setBrushMode('auto'));
    if (bmManual) bmManual.addEventListener('click', () => {
      // Tapping Manual 5x within the window toggles debug mode (off by
      // default every load, never persisted). Normal mode selection still
      // happens on every tap.
      const now = Date.now();
      if (now - manualTapLast > BRUSH_CFG.DEBUG_TAP_WINDOW_MS) manualTapCount = 0;
      manualTapCount++;
      manualTapLast = now;
      if (manualTapCount >= BRUSH_CFG.DEBUG_TAP_COUNT) {
        manualTapCount = 0;
        debugMode = !debugMode;
        updateDebugUI();
      }
      setBrushMode('manual');
    });

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

    // Demos list (auto-populated from web/public/demo_sprites/ via /api/demos)
    const prettyDemoName = (f) =>
      f.replace(/\.sga$/i, '').replace(/[_-]+/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    fetch('api/demos').then(r => r.json()).then(files => {
      const list = document.getElementById('demoList');
      if (!files.length) { list.textContent = '(none)'; return; }
      files.forEach(f => {
        const b = document.createElement('button');
        b.textContent = prettyDemoName(f);
        b.addEventListener('click', async () => {
          dropdown.classList.add('hidden');
          try {
            const blob = await fetch('demo_sprites/' + encodeURIComponent(f)).then(r => r.blob());
            await loadSga(blob);
          } catch (e) { alert('Could not load demo: ' + e.message); }
        });
        list.appendChild(b);
      });
    }).catch(() => {});

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

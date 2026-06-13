(function () {
  'use strict';

  // ── Constants ─────────────────────────────────────────────────────────────
  const MAX_FRAMES = 6;
  const CELL_SIZE = { 16: 22, 32: 11 };         // px per cell — editor canvas
  const CHECK_A = '#1e1430';
  const CHECK_B = '#2a1e44';
  const GRID_COL = '#2a1e44';

  // ── State ─────────────────────────────────────────────────────────────────
  const S = {
    gridSize: 16,
    frames: [],          // Color[][] where Color = null | '#rrggbb'
    current: 0,          // active frame index
    palette: [],         // 5 slots: '#rrggbb' | null
    slot: 0,             // active palette slot
    ts: null,            // global TransformSettings
  };

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

      if (S.frames.length > 1) {
        const del = document.createElement('button');
        del.className = 'frame-tab-del';
        del.title = 'Delete frame';
        del.textContent = '×';
        del.addEventListener('click', (e) => {
          e.stopPropagation();
          deleteFrame(fi);
        });
        tab.appendChild(del);
      }
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
  }

  function switchFrame(fi) {
    S.current = fi;
    renderCanvas();
    renderFrameTabs();
    syncSliders();
    updatePaletteControls();
  }

  function addFrame() {
    if (S.frames.length >= MAX_FRAMES) return;
    S.frames.push(newGrid(S.gridSize));
    S.current = S.frames.length - 1;
    renderAll();
  }

  function deleteFrame(fi) {
    if (S.frames.length <= 1) return;
    if (!confirm(`Delete frame ${fi + 1}?`)) return;
    S.frames.splice(fi, 1);
    if (S.current >= S.frames.length) S.current = S.frames.length - 1;
    if (engine) engine.stop();
    renderAll();
  }

  // ── Palette ───────────────────────────────────────────────────────────────
  function renderPalette() {
    const bar = document.getElementById('paletteBar');
    bar.innerHTML = '';
    S.palette.forEach((color, i) => {
      const sw = document.createElement('div');
      sw.className = 'swatch' + (i === S.slot ? ' active' : '') + (color ? '' : ' empty');
      if (color) sw.style.background = color;
      sw.title = `Slot ${i}${i === 0 ? ' (black, fixed)' : color ? ': ' + color : ' (empty)'}`;
      sw.addEventListener('click', () => {
        S.slot = i;
        renderPalette();
        updatePaletteControls();
      });
      bar.appendChild(sw);
    });
    updatePaletteControls();
  }

  function updatePaletteControls() {
    const i = S.slot;
    const color = S.palette[i];
    const lbl = document.getElementById('slotLabel');
    const picker = document.getElementById('slotColorPicker');
    const clearBtn = document.getElementById('clearSlotBtn');

    if (i === 0) {
      lbl.textContent = 'Slot 0 (black)';
      picker.style.display = 'none';
      clearBtn.style.display = 'none';
    } else {
      lbl.textContent = `Slot ${i}`;
      picker.style.display = '';
      picker.value = color || '#ff0000';
      clearBtn.style.display = color ? '' : 'none';
    }
  }

  // ── Canvas events ─────────────────────────────────────────────────────────
  let isPainting = false;
  let eraseMode = false;

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

  function applyCell(cell, erase) {
    if (!cell) return;
    const frame = S.frames[S.current];
    const color = erase ? null : (S.palette[S.slot] || null);
    if (frame[cell.row][cell.col] === color) return;
    frame[cell.row][cell.col] = color;
    // Fast single-cell redraw on editor canvas
    const cs = CELL_SIZE[S.gridSize];
    ctx.fillStyle = color || ((cell.row + cell.col) % 2 === 0 ? CHECK_A : CHECK_B);
    ctx.fillRect(cell.col * cs, cell.row * cs, cs, cs);
    ctx.strokeStyle = GRID_COL;
    ctx.lineWidth = 0.5;
    ctx.strokeRect(cell.col * cs + 0.25, cell.row * cs + 0.25, cs - 0.5, cs - 0.5);
  }

  canvas.addEventListener('mousedown', (e) => {
    e.preventDefault();
    isPainting = true;
    eraseMode = e.button === 2;
    applyCell(cellAt(e), eraseMode);
  });

  canvas.addEventListener('mousemove', (e) => {
    if (!isPainting) return;
    applyCell(cellAt(e), eraseMode);
  });

  canvas.addEventListener('mouseup', () => { isPainting = false; });
  canvas.addEventListener('mouseleave', () => { isPainting = false; });
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
    ['Burst', 'Pop', 'Twist', 'Morph'].forEach((name, i) => {
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
    else buildMorphPanel(panel);

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
    let saved = 0;

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
      saved++;
    }

    if (saved === 0) { alert('No non-empty frames to save.'); return; }

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

    // Infer palette from first frame's unique colors
    const seen = new Set();
    S.frames[0].forEach(row => row.forEach(c => {
      if (c && c.toLowerCase() !== '#000000') seen.add(c.toLowerCase());
    }));
    const cols = [...seen].slice(0, 4);
    S.palette = ['#000000', ...cols, ...Array(4 - cols.length).fill(null)];
    S.slot = 0;

    // Update size buttons UI
    document.querySelectorAll('.size-btn').forEach(b =>
      b.classList.toggle('active', parseInt(b.dataset.size, 10) === S.gridSize));

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

  // ── Full render ───────────────────────────────────────────────────────────
  function renderAll() {
    renderCanvas();
    renderPreview(0);
    renderFrameTabs();
    renderPalette();
    buildTransformPanel();
  }

  // ── Init ──────────────────────────────────────────────────────────────────
  function init() {
    S.gridSize = 16;
    S.frames = [newGrid(16)];
    S.current = 0;
    S.palette = ['#000000', null, null, null, null];
    S.slot = 0;
    S.ts = defaultTS();

    // Grid size buttons
    document.querySelectorAll('.size-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const newSize = parseInt(btn.dataset.size, 10);
        if (newSize === S.gridSize) return;
        if (!confirm(`Switch to ${newSize}×${newSize}? All frames will be reset.`)) return;
        S.gridSize = newSize;
        S.frames = [newGrid(newSize)];
        S.current = 0;
        if (engine) engine.stop();
        document.querySelectorAll('.size-btn').forEach(b =>
          b.classList.toggle('active', parseInt(b.dataset.size, 10) === newSize));
        renderAll();
      });
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

    // Slot color picker
    const picker = document.getElementById('slotColorPicker');
    picker.addEventListener('input', () => {
      if (S.slot > 0) {
        S.palette[S.slot] = picker.value;
        renderPalette();
      }
    });

    // Clear slot
    document.getElementById('clearSlotBtn').addEventListener('click', () => {
      if (S.slot > 0) {
        S.palette[S.slot] = null;
        S.slot = 0;
        renderPalette();
      }
    });

    // Clear frame
    document.getElementById('clearAllBtn').addEventListener('click', () => {
      if (!confirm(`Clear frame ${S.current + 1}?`)) return;
      S.frames[S.current] = newGrid(S.gridSize);
      renderCanvas();
    });

    // Preview controls
    engine = new AnimEngine(() => S, previewCanvas);
    document.getElementById('previewStop').addEventListener('click', () => engine.stop());
    document.getElementById('previewStep').addEventListener('click', () => engine.step());
    document.getElementById('previewPlay').addEventListener('click', () => engine.play());

    renderAll();
  }

  init();
})();

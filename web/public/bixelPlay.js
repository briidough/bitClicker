'use strict';

/**
 * BixelPlay — standalone .bxl animation player for any <canvas>.
 * Drop this single file into any web project. No dependencies required.
 *
 * Usage:
 *   const data   = await fetch('sprite.bxl').then(r => r.json());
 *   const player = new BixelPlay(canvasEl, data, { pixelSize: 8 });
 *   player.setFrame(0);
 *
 *   // Forward:  frames 0 → 1 → 2
 *   player.play(0, 2, () => console.log('fully grown'));
 *
 *   // Backward: frames 2 → 1 → 0  (uses same transition settings in reverse)
 *   player.play(2, 0, () => console.log('returned'));
 *
 * Options:
 *   pixelSize  — px per grid cell  (default: auto-fit from canvas.width / gridSize)
 *   bg         — background CSS color  (default: null = transparent with checkerboard)
 *   checkA/B   — transparent-cell checkerboard colors
 */

const _BX_TICK_MS    = 16;   // ~60 fps
const _BX_TWIST_SPR  = 10;   // matches AnimConfig.TWIST_SPREAD_DEF in bitClicker

class BixelPlay {
  constructor(canvas, bxlData, opts = {}) {
    this.canvas = canvas;
    this.ctx    = canvas.getContext('2d');
    this.data   = bxlData;

    const gs = bxlData.gridSize;
    this.pixelSize = opts.pixelSize != null
      ? opts.pixelSize
      : Math.floor(canvas.width / gs);
    this.bg     = opts.bg     || null;
    this.checkA = opts.checkA || '#cccccc';
    this.checkB = opts.checkB || '#aaaaaa';

    this.currentFrame   = 0;
    this.animating      = false;
    this._active        = null;
    this._progress      = 0;
    this._holdElapsed   = 0;
    this._extendElapsed = 0;
    this._tickTimer     = null;
    this._holdTimer     = null;
    this._sequence      = [];
    this._seqStep       = 0;
    this._onDone        = null;
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  /** Render frame n immediately with no animation. */
  setFrame(n) {
    this._stopTimers();
    this.animating    = false;
    this._active      = null;
    this._sequence    = [];
    this._onDone      = null;
    this.currentFrame = Math.max(0, Math.min(n, this.data.frameCount - 1));
    this._renderFrame(this.currentFrame);
  }

  /**
   * Animate from fromFrame to toFrame.
   * fromFrame > toFrame → plays each transition in reverse using the same
   * effect settings but with from/to pixels swapped, so the motion runs
   * back toward the source frame.
   *
   * @param {number}    fromFrame
   * @param {number}    toFrame
   * @param {function} [onDone]  — called when the full animation completes
   */
  play(fromFrame, toFrame, onDone) {
    this._stopTimers();
    this.animating = false;

    this._sequence = [];
    if (fromFrame < toFrame) {
      for (let i = fromFrame; i < toFrame; i++)
        this._sequence.push({ from: i, to: i + 1 });
    } else if (fromFrame > toFrame) {
      for (let i = fromFrame; i > toFrame; i--)
        this._sequence.push({ from: i, to: i - 1 });
    }

    this._seqStep     = 0;
    this._onDone      = onDone || null;
    this.currentFrame = fromFrame;

    if (this._sequence.length === 0) {
      if (onDone) onDone();
      return;
    }
    this._startNextStep();
  }

  /** Stop animation, stay on current frame. */
  stop() {
    this._stopTimers();
    this.animating = false;
    this._active   = null;
    this._sequence = [];
    this._onDone   = null;
  }

  // ── Sequence ───────────────────────────────────────────────────────────────

  _startNextStep() {
    if (this._seqStep >= this._sequence.length) {
      if (this._onDone) this._onDone();
      return;
    }
    const step = this._sequence[this._seqStep];
    const td   = this._buildTD(step.from, step.to);

    if (!td) {
      // No transition defined for this pair — jump instantly
      this.currentFrame = step.to;
      this._renderFrame(step.to);
      this._seqStep++;
      this._startNextStep();
      return;
    }

    this._active        = td;
    this._progress      = 0;
    this._holdElapsed   = 0;
    this._extendElapsed = 0;
    this.animating      = true;
    this._tickTimer     = setInterval(() => this._tick(), _BX_TICK_MS);
  }

  _stepComplete() {
    const step = this._sequence[this._seqStep];
    const ts   = this._active ? this._active.ts : null;

    this.currentFrame = step.to;
    this._seqStep++;
    if (this._tickTimer) { clearInterval(this._tickTimer); this._tickTimer = null; }
    this.animating = false;
    this._active   = null;

    const holdMs = ts
      ? ((ts.effectType === 3 ? ts.morphHoldMs : ts.holdMs) || 0)
      : 0;

    if (this._seqStep < this._sequence.length && holdMs > 1) {
      this._holdTimer = setTimeout(() => this._startNextStep(), holdMs);
    } else {
      this._startNextStep();
    }
  }

  // ── Tick ──────────────────────────────────────────────────────────────────

  _tick() {
    const td = this._active;
    if (!td) return;
    const ts = td.ts;

    // Pop: pause at peak (extend phase) then hold
    if (td.effectType === 1 && this._progress >= 0.5) {
      if (this._extendElapsed < td.lockedExtendMs) {
        this._extendElapsed += _BX_TICK_MS;
        this._render(); return;
      }
      if (this._holdElapsed < ts.popHoldMs) {
        this._holdElapsed += _BX_TICK_MS;
        this._render(); return;
      }
    }

    let delta;
    if (td.effectType === 1) {
      const ms = this._progress < 0.5 ? ts.explodeSpeedMs : ts.unsplodeSpeedMs;
      delta = _BX_TICK_MS * 0.5 / Math.max(16, ms);
    } else if (td.effectType === 2) {
      const ms = this._progress < 0.5 ? ts.twistFirstSpeedMs : ts.twistSecondSpeedMs;
      delta = _BX_TICK_MS * 0.5 / Math.max(16, ms);
    } else if (td.effectType === 3) {
      delta = _BX_TICK_MS / Math.max(16, ts.morphSpeedMs);
    } else if (td.effectType === 4) {
      this._stepSpring(td, ts);
      delta = _BX_TICK_MS / Math.max(16, ts.springSpeedMs);
    } else {
      delta = _BX_TICK_MS / Math.max(16, ts.speedMs);
    }

    this._progress += delta;

    if (this._progress >= 1) {
      this._progress = 1;
      this._render();
      this._stepComplete();
      return;
    }

    this._render();
  }

  // ── Render helpers ────────────────────────────────────────────────────────

  _clearCanvas() {
    const gs = this.data.gridSize;
    const cs = this.pixelSize;
    if (this.bg) {
      this.ctx.fillStyle = this.bg;
      this.ctx.fillRect(0, 0, gs * cs, gs * cs);
    } else {
      this.ctx.clearRect(0, 0, gs * cs, gs * cs);
    }
  }

  _renderFrame(fi) {
    const gs    = this.data.gridSize;
    const cs    = this.pixelSize;
    const frame = this.data.frames[fi] || this.data.frames[0];
    const ctx   = this.ctx;

    this._clearCanvas();
    for (let r = 0; r < gs; r++) {
      for (let c = 0; c < gs; c++) {
        const col = frame[r][c];
        if (col != null) {
          ctx.fillStyle = col;
          ctx.fillRect(c * cs, r * cs, cs, cs);
        } else if (!this.bg) {
          ctx.fillStyle = (r + c) % 2 === 0 ? this.checkA : this.checkB;
          ctx.fillRect(c * cs, r * cs, cs, cs);
        }
      }
    }
  }

  _render() {
    if (!this._active) { this._renderFrame(this.currentFrame); return; }
    const td = this._active;
    this._clearCanvas();
    if      (td.effectType === 0) this._renderBurst(td);
    else if (td.effectType === 1) this._renderPop(td);
    else if (td.effectType === 2) this._renderTwist(td);
    else if (td.effectType === 4) this._renderSpring(td);
    else                          this._renderMorph(td);
  }

  _renderSpring(td) {
    const cs  = this.pixelSize;
    const ctx = this.ctx;
    for (let i = 0; i < td.springX.length; i++) {
      ctx.fillStyle = td.fromPixels[i].color;
      ctx.fillRect(Math.round(td.springX[i]), Math.round(td.springY[i]), cs, cs);
    }
  }

  // ── TD builder ────────────────────────────────────────────────────────────

  /**
   * Build transition data for fromIdx → toIdx.
   * Looks up the stored transition settings for the sequential pair regardless
   * of direction; swapping fromIdx/toIdx naturally reverses all effects.
   */
  _buildTD(fromIdx, toIdx) {
    const lo    = Math.min(fromIdx, toIdx);
    const hi    = Math.max(fromIdx, toIdx);
    const trans = (this.data.transitions || []).find(
      t => t.fromFrame === lo && t.toFrame === hi);
    if (!trans) return null;

    const ts = this._bxlToTS(trans);
    const cs = this.pixelSize;
    const gs = this.data.gridSize;
    const fp = this._buildPixels(this.data.frames[fromIdx], cs);
    const tp = this._buildPixels(this.data.frames[toIdx],   cs);

    const td = {
      effectType: ts.effectType,
      ts,
      lockedExtendMs: 0,
      fromPixels: fp, toPixels: tp,
      fromDirs:   this._buildDirs(fp, ts, cs, gs),
      toDirs:     this._buildDirs(tp, ts, cs, gs),
      fromSpeeds: null, toSpeeds: null,
      fromGravDirs: null, toGravDirs: null,
      explodedPositions: null, extendedPositions: null,
      morphStables: [], morphBirths: [], morphBirthOrigins: [],
      morphBirthWave: [], morphTotalWaves: 1,
      morphDeaths: [], morphRecolorOld: [], morphRecolorNew: [],
    };

    if (ts.effectType === 1)
      this._buildPopData(td, ts, cs, gs, fp, tp);
    if (ts.effectType === 3)
      this._buildMorphData(td, ts, cs, gs,
        this.data.frames[fromIdx], this.data.frames[toIdx]);
    if (ts.effectType === 4)
      this._buildSpringData(td, ts, cs, fp, tp);

    return td;
  }

  /** Convert a .bxl transition JSON object into a flat ts property bag. */
  _bxlToTS(trans) {
    const type = trans.effectType;
    const ts   = { effectType: type, focalX: 50, focalY: 50, spin: 0, spinStrength: 100, holdMs: 0 };

    if (type === 0) {
      const b = trans.burst;
      ts.spread = b.spread; ts.speedMs = b.speedMs; ts.holdMs = b.holdMs;
      ts.easing = b.easing; ts.focalX = b.focalX; ts.focalY = b.focalY;
      ts.spin = b.spin; ts.spinStrength = b.spinStrength;
    } else if (type === 1) {
      const p = trans.pop;
      ts.explodeSpeedMs = p.explodeSpeedMs; ts.explodeStrength = p.explodeStrength;
      ts.unsplodeSpeedMs = p.unsplodeSpeedMs; ts.unsplodeStrength = p.unsplodeStrength;
      ts.gravityPush = p.gravityPush; ts.gravityPull = p.gravityPull;
      ts.gravityFocalX = p.gravityFocalX; ts.gravityFocalY = p.gravityFocalY;
      ts.popHoldMs = p.popHoldMs; ts.extendMs = p.extendMs;
      ts.easing = p.easing; ts.spread = p.spread;
      ts.wallDamping = p.wallDamping; ts.stayInCanvas = p.stayInCanvas;
      ts.popStayAtFocus = p.stayAtFocus;
      ts.holdMs = p.popHoldMs || 0;
    } else if (type === 2) {
      const tw = trans.twist;
      ts.twistFirstSpeedMs = tw.firstSpeedMs; ts.twistSecondSpeedMs = tw.secondSpeedMs;
      ts.twistFirstSmooth = tw.firstSmooth; ts.twistSecondSmooth = tw.secondSmooth;
      ts.twistDirection = tw.direction; ts.twistFullSpin = tw.fullSpin;
      ts.twistSpreadGap = tw.spreadGap;
    } else if (type === 3) {
      const m = trans.morph;
      ts.morphSpeedMs = m.speedMs; ts.morphHoldMs = m.holdMs;
      ts.focalX = m.focalX; ts.focalY = m.focalY;
      ts.morphFadeDeaths = m.fadeDeaths;
      ts.holdMs = m.holdMs || 0;
    } else if (type === 4) {
      const sp = trans.spring;
      ts.springK         = sp.stiffness / 1000;
      ts.springC         = (sp.damping / 100) * 2 * Math.sqrt(ts.springK);
      ts.springImpulse01 = sp.impulse / 100;
      ts.springSpeedMs   = sp.speedMs;
      ts.holdMs          = sp.holdMs || 0;
    }
    return ts;
  }

  // ── Pixel builders ────────────────────────────────────────────────────────

  _buildPixels(frame, cs) {
    const result = [];
    for (let r = 0; r < frame.length; r++)
      for (let c = 0; c < frame[r].length; c++)
        if (frame[r][c] != null)
          result.push({ x: c * cs, y: r * cs, color: frame[r][c] });
    return result;
  }

  _buildDirs(pixels, ts, cs, gs) {
    const totalPx = gs * cs;
    const cx = totalPx * ts.focalX / 100;
    const cy = totalPx * ts.focalY / 100;
    const spin = ts.spin, t = ts.spinStrength / 100 + 0.5;
    return pixels.map(p => {
      const dx = p.x + cs / 2 - cx, dy = p.y + cs / 2 - cy;
      const len = Math.hypot(dx, dy) || 0.001;
      const nx = dx / len, ny = dy / len;
      let bx, by;
      if (spin === 1)      { bx = nx*(1-t) + ny*t;  by = ny*(1-t) - nx*t; }
      else if (spin === 2) { bx = nx*(1-t) - ny*t;  by = ny*(1-t) + nx*t; }
      else                 { bx = nx; by = ny; }
      const bl = Math.hypot(bx, by) || 0.001;
      return { x: bx/bl, y: by/bl };
    });
  }

  _buildSpeeds(count, variance) {
    return Array.from({ length: count }, () => 1 + (Math.random() - 0.5) * variance);
  }

  _buildSpringData(td, ts, cs, fp, tp) {
    const n = fp.length;
    td.springX = new Float32Array(n);
    td.springY = new Float32Array(n);
    td.springVX = new Float32Array(n);
    td.springVY = new Float32Array(n);
    td.springHome = (tp.length === n) ? tp : fp;
    const impulseMag = ts.springImpulse01 * cs * 1.3;
    for (let i = 0; i < n; i++) {
      td.springX[i]  = fp[i].x;
      td.springY[i]  = fp[i].y;
      td.springVX[i] = td.fromDirs[i].x * impulseMag;
      td.springVY[i] = td.fromDirs[i].y * impulseMag;
    }
  }

  _stepSpring(td, ts) {
    const home = td.springHome, k = ts.springK, c = ts.springC;
    for (let i = 0; i < td.springX.length; i++) {
      const ax = -k * (td.springX[i] - home[i].x) - c * td.springVX[i];
      const ay = -k * (td.springY[i] - home[i].y) - c * td.springVY[i];
      td.springVX[i] += ax; td.springVY[i] += ay;
      td.springX[i]  += td.springVX[i]; td.springY[i] += td.springVY[i];
    }
  }

  _buildGravDirs(pixels, ts, cs, gs) {
    const gx = gs * cs * ts.gravityFocalX / 100;
    const gy = gs * cs * ts.gravityFocalY / 100;
    return pixels.map(p => {
      const dx = gx - (p.x + cs / 2), dy = gy - (p.y + cs / 2);
      const len = Math.hypot(dx, dy) || 0.001;
      return { x: dx / len, y: dy / len };
    });
  }

  _buildPopData(td, ts, cs, gs, fp, tp) {
    td.fromSpeeds    = this._buildSpeeds(fp.length, ts.gravityPush / 100);
    td.toSpeeds      = this._buildSpeeds(tp.length, ts.gravityPush / 100);
    td.fromGravDirs  = this._buildGravDirs(fp, ts, cs, gs);
    td.toGravDirs    = this._buildGravDirs(tp, ts, cs, gs);

    const spread          = ts.spread;
    const gravStrength    = ts.gravityPull / 100 * spread * 6;
    const stay            = ts.stayInCanvas;
    const bound           = gs * cs - cs;
    const explodeStrength = ts.explodeStrength / 100;
    const extendMs        = ts.extendMs;
    const extendRatio     = ts.explodeSpeedMs > 0 ? extendMs / ts.explodeSpeedMs : 0;
    const gravAtPeak      = ts.popStayAtFocus ? gravStrength : 0;
    const tExtendEnd      = 1.0 + extendRatio;
    const gravAtExtend    = ts.popStayAtFocus ? gravStrength * tExtendEnd * tExtendEnd : 0;
    td.lockedExtendMs     = extendMs;

    const clamp = v => Math.max(0, Math.min(v, bound));

    td.explodedPositions = fp.map((p, i) => {
      const push = spread * td.fromSpeeds[i] * explodeStrength * 0.8;
      let ex = p.x + td.fromDirs[i].x * push + td.fromGravDirs[i].x * gravAtPeak;
      let ey = p.y + td.fromDirs[i].y * push + td.fromGravDirs[i].y * gravAtPeak;
      if (stay) { ex = clamp(ex); ey = clamp(ey); }
      return { x: ex, y: ey };
    });

    td.extendedPositions = fp.map((p, i) => {
      const push = spread * td.fromSpeeds[i] * explodeStrength * 0.8;
      let ex = p.x + td.fromDirs[i].x * push + td.fromGravDirs[i].x * gravAtExtend;
      let ey = p.y + td.fromDirs[i].y * push + td.fromGravDirs[i].y * gravAtExtend;
      if (stay) { ex = clamp(ex); ey = clamp(ey); }
      return { x: ex, y: ey };
    });
  }

  _buildMorphData(td, ts, cs, gs, from, to) {
    const focalCol = Math.max(0, Math.min(gs - 1, Math.floor(ts.focalX / 100 * gs)));
    const focalRow = Math.max(0, Math.min(gs - 1, Math.floor(ts.focalY / 100 * gs)));

    const visited = Array.from({ length: gs }, () => new Uint8Array(gs));
    const queue   = [[focalRow, focalCol]];
    visited[focalRow][focalCol] = 1;

    const stables = [], births = [], deaths = [], rcOld = [], rcNew = [];
    const DR = [-1, 1, 0, 0], DC = [0, 0, -1, 1];

    let qi = 0;
    while (qi < queue.length) {
      const [r, c] = queue[qi++];
      const px = c * cs, py = r * cs;
      const fc = from[r][c], tc = to[r][c];

      if      (fc == null && tc == null)              { /* skip */ }
      else if (fc != null && tc != null && fc === tc) stables.push({ x: px, y: py, color: fc });
      else if (fc == null)                            births.push({ x: px, y: py, color: tc });
      else if (tc == null)                            deaths.push({ x: px, y: py, color: fc });
      else { rcOld.push({ x: px, y: py, color: fc }); rcNew.push({ x: px, y: py, color: tc }); }

      for (let d = 0; d < 4; d++) {
        const nr = r + DR[d], nc = c + DC[d];
        if (nr >= 0 && nr < gs && nc >= 0 && nc < gs && !visited[nr][nc]) {
          visited[nr][nc] = 1;
          queue.push([nr, nc]);
        }
      }
    }

    td.morphStables = stables; td.morphDeaths = deaths;
    td.morphRecolorOld = rcOld; td.morphRecolorNew = rcNew;
    td.morphBirths = births;

    const birthGrid = Array.from({ length: gs }, () => new Int16Array(gs).fill(-1));
    births.forEach((b, i) => { birthGrid[b.y / cs | 0][b.x / cs | 0] = i; });

    const birthWave    = new Int32Array(births.length).fill(-1);
    const birthOrigins = births.map(b => ({ x: b.x, y: b.y }));

    const waveVis = Array.from({ length: gs }, () => new Uint8Array(gs));
    const waveQ   = [];
    for (let r = 0; r < gs; r++)
      for (let c = 0; c < gs; c++)
        if (from[r][c] != null) { waveVis[r][c] = 1; waveQ.push([r, c, -1]); }

    let maxWave = -1, wqi = 0;
    while (wqi < waveQ.length) {
      const [r, c, wave] = waveQ[wqi++];
      for (let d = 0; d < 4; d++) {
        const nr = r + DR[d], nc = c + DC[d];
        if (nr < 0 || nr >= gs || nc < 0 || nc >= gs || waveVis[nr][nc]) continue;
        const bi = birthGrid[nr][nc];
        if (bi >= 0) {
          waveVis[nr][nc] = 1;
          const nw = wave + 1;
          birthWave[bi]    = nw;
          birthOrigins[bi] = { x: c * cs, y: r * cs };
          if (nw > maxWave) maxWave = nw;
          waveQ.push([nr, nc, nw]);
        }
      }
    }

    const floatWave = maxWave < 0 ? 0 : maxWave + 1;
    birthWave.forEach((w, i) => { if (w < 0) birthWave[i] = floatWave; });
    td.morphBirthWave    = birthWave;
    td.morphBirthOrigins = birthOrigins;
    td.morphTotalWaves   = Math.max(1, maxWave + 1);
  }

  // ── Easing ────────────────────────────────────────────────────────────────

  _easingOut(t, style) {
    if (style === 1) return t;
    if (style === 2) return 1 - Math.pow(1 - t, 4);
    return 1 - (1 - t) * (1 - t);
  }

  _easingIn(t, style) {
    if (style === 1) return t;
    if (style === 2) return t * t * t * t;
    return t * t;
  }

  _sineEase(t, amount) {
    const sinT = 0.5 * (1 - Math.cos(Math.PI * t));
    return t * (1 - amount) + sinT * amount;
  }

  // ── Renderers ─────────────────────────────────────────────────────────────

  _renderBurst(td) {
    const ts  = td.ts;
    const cs  = this.pixelSize;
    const ctx = this.ctx;

    if (this._progress < 0.5) {
      const t      = this._progress / 0.5;
      const offset = ts.spread * this._easingOut(t, ts.easing);
      td.fromPixels.forEach((p, i) => {
        ctx.fillStyle = p.color;
        ctx.fillRect(Math.round(p.x + td.fromDirs[i].x * offset),
                     Math.round(p.y + td.fromDirs[i].y * offset), cs, cs);
      });
    } else {
      const t      = (this._progress - 0.5) / 0.5;
      const offset = ts.spread * (1 - this._easingIn(t, ts.easing));
      td.toPixels.forEach((p, i) => {
        ctx.fillStyle = p.color;
        ctx.fillRect(Math.round(p.x + td.toDirs[i].x * offset),
                     Math.round(p.y + td.toDirs[i].y * offset), cs, cs);
      });
    }
  }

  _renderPop(td) {
    const ts    = td.ts;
    const cs    = this.pixelSize;
    const gs    = this.data.gridSize;
    const ctx   = this.ctx;
    const bound = gs * cs - cs;

    const spread          = ts.spread;
    const gravStrength    = ts.gravityPull / 100 * spread * 6;
    const stay            = ts.stayInCanvas;
    const damping         = ts.wallDamping / 100;
    const explodeStrength = ts.explodeStrength / 100;
    const snapThresh      = ts.unsplodeStrength / 100;
    const snapWindow      = Math.max(0.001, 1 - snapThresh);
    const pullSpeed       = 1.0 + ts.gravityPull / 100;

    const bounce = (pos, b, d) => {
      for (let i = 0; i < 3; i++) {
        if (pos >= 0 && pos <= b) break;
        pos = pos < 0 ? -pos * d : b - (pos - b) * d;
      }
      return Math.max(0, Math.min(pos, b));
    };

    const applyFocus = (px, py) => {
      const gx   = gs * cs * ts.gravityFocalX / 100;
      const gy   = gs * cs * ts.gravityFocalY / 100;
      const dx   = gx - (px + cs * 0.5);
      const dy   = gy - (py + cs * 0.5);
      const dist = Math.max(1, Math.hypot(dx, dy));
      const prox = 1 - Math.min(1, dist / (gs * cs));
      const mag  = ts.gravityPull / 100 * spread * prox * prox * 4;
      return [px + dx / dist * mag, py + dy / dist * mag];
    };

    if (this._progress < 0.5) {
      const t        = this._progress / 0.5;
      const tEased   = this._easingOut(t, ts.easing);
      const diminish = 1 - t * 0.2;
      const gravOff  = ts.popStayAtFocus ? gravStrength * t * t : 0;
      td.fromPixels.forEach((p, i) => {
        const push = spread * td.fromSpeeds[i] * explodeStrength * tEased * diminish;
        let px = p.x + td.fromDirs[i].x * push + td.fromGravDirs[i].x * gravOff;
        let py = p.y + td.fromDirs[i].y * push + td.fromGravDirs[i].y * gravOff;
        if (ts.popStayAtFocus) [px, py] = applyFocus(px, py);
        if (stay) { px = bounce(px, bound, damping); py = bounce(py, bound, damping); }
        ctx.fillStyle = p.color;
        ctx.fillRect(Math.round(px), Math.round(py), cs, cs);
      });

    } else if (td.lockedExtendMs > 0 && this._extendElapsed < td.lockedExtendMs) {
      const extFrac = this._extendElapsed / Math.max(1, ts.explodeSpeedMs);
      const tRaw    = 1.0 + extFrac;
      const tEased  = this._easingOut(1.0, ts.easing);
      const gravOff = ts.popStayAtFocus ? gravStrength * tRaw * tRaw : 0;
      td.fromPixels.forEach((p, i) => {
        const push = spread * td.fromSpeeds[i] * explodeStrength * tEased * 0.8;
        let px = p.x + td.fromDirs[i].x * push + td.fromGravDirs[i].x * gravOff;
        let py = p.y + td.fromDirs[i].y * push + td.fromGravDirs[i].y * gravOff;
        if (ts.popStayAtFocus) [px, py] = applyFocus(px, py);
        if (stay) { px = bounce(px, bound, damping); py = bounce(py, bound, damping); }
        ctx.fillStyle = p.color;
        ctx.fillRect(Math.round(px), Math.round(py), cs, cs);
      });

    } else if (this._holdElapsed < ts.popHoldMs) {
      const holdPos = td.lockedExtendMs > 0 ? td.extendedPositions : td.explodedPositions;
      holdPos.forEach((pos, i) => {
        ctx.fillStyle = td.fromPixels[i].color;
        ctx.fillRect(Math.round(pos.x), Math.round(pos.y), cs, cs);
      });

    } else {
      const startPos = td.lockedExtendMs > 0 ? td.extendedPositions : td.explodedPositions;
      const t = (this._progress - 0.5) / 0.5;
      td.toPixels.forEach((p, i) => {
        const approach = Math.min(1, t * td.toSpeeds[i]);
        let remainFrac;
        if (approach < snapThresh) {
          remainFrac = 1 - approach;
        } else {
          const snapT = (approach - snapThresh) / snapWindow;
          remainFrac = snapWindow * (1 - Math.min(1, snapT * pullSpeed));
        }
        const si = i % startPos.length;
        let px = p.x + (startPos[si].x - p.x) * remainFrac;
        let py = p.y + (startPos[si].y - p.y) * remainFrac;
        if (ts.popStayAtFocus) [px, py] = applyFocus(px, py);
        if (stay) { px = bounce(px, bound, damping); py = bounce(py, bound, damping); }
        ctx.fillStyle = p.color;
        ctx.fillRect(Math.round(px), Math.round(py), cs, cs);
      });
    }
  }

  _renderTwist(td) {
    const ts     = td.ts;
    const cs     = this.pixelSize;
    const ctx    = this.ctx;
    const drawSz = cs * (ts.twistSpreadGap ? (1 - _BX_TWIST_SPR * 2 / 100) : 1);
    const half   = drawSz / 2;
    const ccw    = ts.twistDirection === 1;

    let angle, pixels;
    if (this._progress < 0.5) {
      angle  = this._sineEase(this._progress / 0.5, ts.twistFirstSmooth / 100) * 90;
      pixels = td.fromPixels;
    } else {
      const eased = this._sineEase((this._progress - 0.5) / 0.5, ts.twistSecondSmooth / 100);
      angle  = ts.twistFullSpin ? 90 + eased * 90 : 90 - eased * 90;
      pixels = td.toPixels.length > 0 ? td.toPixels : td.fromPixels;
    }

    const rad = (ccw ? -angle : angle) * Math.PI / 180;
    pixels.forEach(p => {
      ctx.save();
      ctx.translate(p.x + cs / 2, p.y + cs / 2);
      ctx.rotate(rad);
      ctx.fillStyle = p.color;
      ctx.fillRect(-half, -half, drawSz, drawSz);
      ctx.restore();
    });
  }

  _renderMorph(td) {
    const t   = this._progress;
    const cs  = this.pixelSize;
    const ctx = this.ctx;

    td.morphStables.forEach(p => {
      ctx.fillStyle = p.color;
      ctx.fillRect(p.x, p.y, cs, cs);
    });

    const waveSlice = 1 / td.morphTotalWaves;
    td.morphBirths.forEach((p, i) => {
      let localT = (t - td.morphBirthWave[i] * waveSlice) / waveSlice;
      if (localT <= 0) return;
      if (localT > 1) localT = 1;
      const ox = td.morphBirthOrigins[i].x, oy = td.morphBirthOrigins[i].y;
      ctx.fillStyle = p.color;
      ctx.fillRect(Math.round(ox + (p.x - ox) * localT),
                   Math.round(oy + (p.y - oy) * localT), cs, cs);
    });

    td.morphDeaths.forEach(p => {
      const drawSz = Math.round(cs * (1 - t));
      if (drawSz <= 0) return;
      const off = (cs - drawSz) / 2;
      if (td.ts.morphFadeDeaths) {
        ctx.globalAlpha = 1 - t;
        ctx.fillStyle = p.color;
        ctx.fillRect(p.x + off, p.y + off, drawSz, drawSz);
        ctx.globalAlpha = 1;
      } else {
        ctx.fillStyle = p.color;
        ctx.fillRect(p.x + off, p.y + off, drawSz, drawSz);
      }
    });

    td.morphRecolorOld.forEach((oldP, i) => {
      const newP = td.morphRecolorNew[i];
      ctx.fillStyle = newP.color;
      ctx.fillRect(newP.x, newP.y, cs, cs);
      const oldSz = Math.round(cs * (1 - t));
      if (oldSz > 0) {
        const off = (cs - oldSz) / 2;
        ctx.fillStyle = oldP.color;
        ctx.fillRect(oldP.x + off, oldP.y + off, oldSz, oldSz);
      }
    });
  }

  // ── Timer helpers ──────────────────────────────────────────────────────────

  _stopTimers() {
    if (this._tickTimer) { clearInterval(this._tickTimer); this._tickTimer = null; }
    if (this._holdTimer) { clearTimeout(this._holdTimer);  this._holdTimer = null; }
  }
}

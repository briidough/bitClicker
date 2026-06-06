package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.SpriteModel.Mode;
import com.atari.spritemaker.ui.RetroTheme;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class PreviewPanel extends JPanel implements ChangeListener {

    private static final int CELL = 4;

    private final SpriteModel model;
    private final Canvas canvas;
    private final JPanel animControls;
    private final JToggleButton btnZoom1x;
    private final JToggleButton btnZoom2x;
    private final JToggleButton btnZoom4x;

    private int zoomLevel = 1;

    // Animation state
    private boolean animating = false;
    private float animProgress = 0f;
    private int midHoldElapsedMs = 0;
    private boolean playing = false;
    private int currentFrameIdx = 0;
    private int nextFrameIdx = 0;
    private int lockedEffectType = 0;

    // Per-transition pixel data
    private int[][] fromPixels;
    private Color[] fromColors;
    private float[][] fromDirs;
    private int[][] toPixels;
    private Color[] toColors;
    private float[][] toDirs;

    // Pixel Pop extras
    private float[] fromSpeeds;
    private float[][] fromGravDirs;
    private float[] toSpeeds;
    private float[][] toGravDirs;
    private int[][] explodedPositions;  // pixel positions at peak of explode (t=1.0)
    private int[][] extendedPositions;  // pixel positions at end of extend phase
    private int extendElapsedMs = 0;
    private int lockedExtendMs = 0;     // extendMs captured at startBurst; must match extendedPositions

    // Pixel Morph extras
    private int[][] morphStables;       // {x, y, rgb}
    private int[][] morphBirths;        // {x, y, rgb} target position
    private int[][] morphBirthOrigins;  // {originX, originY} per birth — parent cell's canvas position
    private int[]   morphBirthWave;     // wave index per birth (0 = first to appear)
    private int     morphTotalWaves;    // total waves (each gets 1/N of animation time)
    private int[][] morphDeaths;        // {x, y, rgb} source; slides toward focal + shrinks
    private int[][] morphRecolorOld;    // {x, y, oldRgb}; stays until t=1
    private int[][] morphRecolorNew;    // {x, y, newRgb}; slides from focal to position
    private float[] morphFocalPx;       // {focalX_px, focalY_px} in canvas coords

    // Preview canvas background (user-controlled via BgColorSlider)
    private Color canvasBg = RetroTheme.active
        ? RetroTheme.BG_DARK
        : (UIManager.getColor("Panel.background") != null
            ? UIManager.getColor("Panel.background") : Color.LIGHT_GRAY);

    // Draw mode cache
    private BufferedImage drawCache;
    private boolean drawDirty = true;

    // Timers
    private final javax.swing.Timer burstTimer;
    private final javax.swing.Timer pauseTimer;

    public PreviewPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BorderLayout());

        canvas = new Canvas();
        canvas.setBackground(canvasBg);
        add(canvas, BorderLayout.CENTER);
        add(new BgColorSlider(), BorderLayout.WEST);

        // Zoom controls — always visible, enabled state depends on grid size
        btnZoom1x = new JToggleButton("1x");
        btnZoom2x = new JToggleButton("2x");
        btnZoom4x = new JToggleButton("4x");
        btnZoom1x.setSelected(true);
        ButtonGroup zoomGroup = new ButtonGroup();
        zoomGroup.add(btnZoom1x);
        zoomGroup.add(btnZoom2x);
        zoomGroup.add(btnZoom4x);

        btnZoom1x.addActionListener(e -> { zoomLevel = 1; drawDirty = true; canvas.repaint(); });
        btnZoom2x.addActionListener(e -> { zoomLevel = 2; drawDirty = true; canvas.repaint(); });
        btnZoom4x.addActionListener(e -> { zoomLevel = 4; drawDirty = true; canvas.repaint(); });

        JPanel zoomColumn = new JPanel();
        zoomColumn.setLayout(new BoxLayout(zoomColumn, BoxLayout.Y_AXIS));
        zoomColumn.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 4));
        zoomColumn.add(btnZoom1x);
        zoomColumn.add(Box.createVerticalStrut(2));
        zoomColumn.add(btnZoom2x);
        zoomColumn.add(Box.createVerticalStrut(2));
        zoomColumn.add(btnZoom4x);
        add(zoomColumn, BorderLayout.EAST);

        // Animation controls (TRANSFORM mode only) — Step, Play, Pause, Reset
        animControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        JButton btnPlay  = new JButton("Play");
        JButton btnPause = new JButton("Pause");
        JButton btnStep  = new JButton("Step");
        JButton btnReset = new JButton("Reset");
        animControls.add(btnStep);
        animControls.add(btnPlay);
        animControls.add(btnPause);
        animControls.add(btnReset);
        animControls.setVisible(false);
        add(animControls, BorderLayout.SOUTH);

        burstTimer = new javax.swing.Timer(16, e -> tickBurst());
        pauseTimer = new javax.swing.Timer(200, null);
        pauseTimer.setRepeats(false);
        pauseTimer.addActionListener(e -> { pauseTimer.stop(); startBurst(); });

        btnPlay.addActionListener(e -> {
            playing = true;
            if (!animating) startBurst();
        });
        btnPause.addActionListener(e -> {
            playing = false;
        });
        btnStep.addActionListener(e -> {
            playing = false;
            if (!animating) startBurst();
        });
        btnReset.addActionListener(e -> {
            playing = false;
            animating = false;
            currentFrameIdx = 0;
            burstTimer.stop();
            pauseTimer.stop();
            canvas.repaint();
        });
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        boolean isTransform = model.getMode() == Mode.TRANSFORM;
        animControls.setVisible(isTransform);
        if (!isTransform) {
            playing = false;
            animating = false;
            burstTimer.stop();
            pauseTimer.stop();
        } else {
            List<Color[][]> frames = model.getAnimationFrames();
            if (frames.isEmpty()) {
                playing = false;
                animating = false;
                burstTimer.stop();
                pauseTimer.stop();
            }
            if (currentFrameIdx >= frames.size()) currentFrameIdx = 0;
            if (!animating) canvas.repaint();
        }

        int gridSize = model.getGridSize();
        if (gridSize == 16) {
            btnZoom1x.setEnabled(true);
            btnZoom2x.setEnabled(true);
            btnZoom4x.setEnabled(true);
        } else if (gridSize == 32) {
            btnZoom1x.setEnabled(true);
            btnZoom2x.setEnabled(true);
            btnZoom4x.setEnabled(false);
            if (zoomLevel > 2) {
                zoomLevel = 2;
                btnZoom2x.setSelected(true);
            }
        } else {
            btnZoom1x.setEnabled(true);
            btnZoom2x.setEnabled(false);
            btnZoom4x.setEnabled(false);
            if (zoomLevel > 1) {
                zoomLevel = 1;
                btnZoom1x.setSelected(true);
            }
        }

        drawDirty = true;
        canvas.repaint();
    }

    // ── animation logic ──────────────────────────────────────────────────────

    private void startBurst() {
        burstTimer.stop();
        List<Color[][]> frames = model.getAnimationFrames();
        if (frames.isEmpty()) return;
        lockedEffectType = model.getAnimEffectType();
        nextFrameIdx = frames.size() < 2 ? currentFrameIdx : (currentFrameIdx + 1) % frames.size();
        int gridSize = frames.get(currentFrameIdx).length;
        fromPixels = buildPixels(frames.get(currentFrameIdx), gridSize);
        fromColors = buildColors(fromPixels);
        fromDirs   = buildDirs(fromPixels, gridSize);
        toPixels   = buildPixels(frames.get(nextFrameIdx), gridSize);
        toColors   = buildColors(toPixels);
        toDirs     = buildDirs(toPixels, gridSize);
        if (lockedEffectType == 1) {
            float variance = model.getAnimGravityPush() / 100f;
            int gs = frames.get(currentFrameIdx).length;
            fromSpeeds   = buildSpeeds(fromPixels.length, variance);
            fromGravDirs = buildGravDirs(fromPixels, gs);
            toSpeeds     = buildSpeeds(toPixels.length, variance);
            toGravDirs   = buildGravDirs(toPixels, gs);

            // Always pre-compute exploded and extended positions so all phases can lerp cleanly.
            int cellSize = CELL * zoomLevel;
            float spread = model.getAnimSpread();
            float gravStrength = model.getAnimGravityPull() / 100f * spread * 6f;
            boolean stay = model.isAnimStayInCanvas();
            int bound = gs * cellSize - cellSize;
            float explodeStrength = model.getAnimExplodeStrength() / 100f;
            lockedExtendMs = model.getAnimExtendMs();
            float extendRatio = model.getAnimExplodeSpeedMs() > 0
                ? (float) lockedExtendMs / model.getAnimExplodeSpeedMs() : 0f;
            explodedPositions = new int[fromPixels.length][2];
            extendedPositions = new int[fromPixels.length][2];
            for (int i = 0; i < fromPixels.length; i++) {
                // easingOut(1.0)==1.0 for all styles; diminish at t=1.0 is 0.8
                float peakPushOff = spread * fromSpeeds[i] * explodeStrength * 0.8f;
                int expX = (int)(fromPixels[i][0] + fromDirs[i][0] * peakPushOff + fromGravDirs[i][0] * gravStrength);
                int expY = (int)(fromPixels[i][1] + fromDirs[i][1] * peakPushOff + fromGravDirs[i][1] * gravStrength);
                if (stay) {
                    expX = Math.max(0, Math.min(expX, bound));
                    expY = Math.max(0, Math.min(expY, bound));
                }
                explodedPositions[i][0] = expX;
                explodedPositions[i][1] = expY;
                // Extended: same trajectory, distance scaled so extend runs at the same px/ms as the explode
                int extX = expX + (int)((expX - fromPixels[i][0]) * extendRatio);
                int extY = expY + (int)((expY - fromPixels[i][1]) * extendRatio);
                if (stay) {
                    extX = Math.max(0, Math.min(extX, bound));
                    extY = Math.max(0, Math.min(extY, bound));
                }
                extendedPositions[i][0] = extX;
                extendedPositions[i][1] = extY;
            }
        }
        if (lockedEffectType == 3) {
            buildMorphData(frames.get(currentFrameIdx), frames.get(nextFrameIdx),
                           frames.get(currentFrameIdx).length);
        }
        animProgress = 0f;
        midHoldElapsedMs = 0;
        extendElapsedMs = 0;
        animating = true;
        burstTimer.start();
    }

    private void tickBurst() {
        float delta;
        if (lockedEffectType == 1) {
            if (animProgress >= 0.5f) {
                // Extend phase: continue explode physics outward
                int extendMs = lockedExtendMs;
                if (extendElapsedMs < extendMs) {
                    animProgress = 0.5f;
                    extendElapsedMs += 16;
                    canvas.repaint();
                    return;
                }
                // Delay phase: hold at final extended position
                int peakHoldMs = model.getAnimPopHoldMs();
                if (midHoldElapsedMs < peakHoldMs) {
                    animProgress = 0.5f;
                    midHoldElapsedMs += 16;
                    canvas.repaint();
                    return;
                }
            }
            // Each phase covers 0.5 of animProgress, driven by its own speed
            int phaseSpeed = animProgress < 0.5f
                ? model.getAnimExplodeSpeedMs()
                : model.getAnimUnsplodeSpeedMs();
            delta = 16f * 0.5f / phaseSpeed;
        } else if (lockedEffectType == 2) {
            int phaseMs = animProgress < 0.5f
                ? Math.max(16, model.getAnimTwistFirstSpeedMs())
                : Math.max(16, model.getAnimTwistSecondSpeedMs());
            delta = 16f * 0.5f / phaseMs;
        } else if (lockedEffectType == 3) {
            delta = 16f / Math.max(16, model.getAnimMorphSpeedMs());
        } else {
            delta = 16f / model.getAnimSpeedMs();
        }
        animProgress += delta;
        if (animProgress >= 1f) {
            animProgress = 1f;
            animating = false;
            burstTimer.stop();
            currentFrameIdx = nextFrameIdx;
            if (playing) {
                int holdMs = lockedEffectType == 3
                    ? model.getAnimMorphHoldMs()
                    : model.getAnimHoldMs();
                pauseTimer.setDelay(Math.max(1, holdMs));
                pauseTimer.start();
            }
        }
        canvas.repaint();
    }

    private int[][] buildPixels(Color[][] frame, int size) {
        int cellSize = CELL * zoomLevel;
        int count = 0;
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (frame[r][c] != null) count++;
        int[][] pixels = new int[count][3]; // {x, y, rgb}
        int i = 0;
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (frame[r][c] != null) {
                    pixels[i][0] = c * cellSize;
                    pixels[i][1] = r * cellSize;
                    pixels[i][2] = frame[r][c].getRGB();
                    i++;
                }
        return pixels;
    }

    private float[][] buildDirs(int[][] pixels, int gridSize) {
        int cellSize = CELL * zoomLevel;
        float totalPx = gridSize * cellSize;
        float cx = totalPx * model.getAnimFocalX() / 100f;
        float cy = totalPx * model.getAnimFocalY() / 100f;
        int spin = model.getAnimSpin();
        float t = model.getAnimSpinStrength() / 100f + 0.5f;
        float[][] dirs = new float[pixels.length][2];
        for (int i = 0; i < pixels.length; i++) {
            float dx = pixels[i][0] + cellSize / 2f - cx;
            float dy = pixels[i][1] + cellSize / 2f - cy;
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.001f) len = 0.001f;
            float nx = dx / len, ny = dy / len;
            float bx, by;
            if (spin == 1) {
                // blend radial → CW tangential
                bx = nx * (1 - t) +  ny * t;
                by = ny * (1 - t) + -nx * t;
            } else if (spin == 2) {
                // blend radial → CCW tangential
                bx = nx * (1 - t) + -ny * t;
                by = ny * (1 - t) +  nx * t;
            } else {
                bx = nx;
                by = ny;
            }
            float blen = (float) Math.hypot(bx, by);
            if (blen < 0.001f) blen = 0.001f;
            dirs[i][0] = bx / blen;
            dirs[i][1] = by / blen;
        }
        return dirs;
    }

    private float[] buildSpeeds(int count, float variance) {
        float[] s = new float[count];
        java.util.Random rng = new java.util.Random();
        for (int i = 0; i < count; i++)
            s[i] = 1f + (rng.nextFloat() - 0.5f) * variance;
        return s;
    }

    private float[][] buildGravDirs(int[][] pixels, int gridSize) {
        int cellSize = CELL * zoomLevel;
        float totalPx = gridSize * cellSize;
        float gx = totalPx * model.getAnimGravityFocalX() / 100f;
        float gy = totalPx * model.getAnimGravityFocalY() / 100f;
        float[][] dirs = new float[pixels.length][2];
        for (int i = 0; i < pixels.length; i++) {
            float dx = gx - (pixels[i][0] + cellSize / 2f);
            float dy = gy - (pixels[i][1] + cellSize / 2f);
            float len = (float) Math.hypot(dx, dy);
            if (len < 0.001f) len = 0.001f;
            dirs[i][0] = dx / len;
            dirs[i][1] = dy / len;
        }
        return dirs;
    }

    private Color[] buildColors(int[][] pixels) {
        Color[] colors = new Color[pixels.length];
        for (int i = 0; i < pixels.length; i++)
            colors[i] = new Color(pixels[i][2]);
        return colors;
    }

    private void buildMorphData(Color[][] from, Color[][] to, int gridSize) {
        int cellSize = CELL * zoomLevel;
        float totalPx = gridSize * cellSize;
        float fx = totalPx * model.getAnimFocalX() / 100f;
        float fy = totalPx * model.getAnimFocalY() / 100f;
        morphFocalPx = new float[]{ fx, fy };

        int focalCol = Math.max(0, Math.min(gridSize - 1, (int)(model.getAnimFocalX() / 100f * gridSize)));
        int focalRow = Math.max(0, Math.min(gridSize - 1, (int)(model.getAnimFocalY() / 100f * gridSize)));

        boolean[][] visited = new boolean[gridSize][gridSize];
        java.util.Deque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{ focalRow, focalCol });
        visited[focalRow][focalCol] = true;

        java.util.List<int[]> stableList     = new java.util.ArrayList<>();
        java.util.List<int[]> birthList      = new java.util.ArrayList<>();
        java.util.List<int[]> deathList      = new java.util.ArrayList<>();
        java.util.List<int[]> recolorOldList = new java.util.ArrayList<>();
        java.util.List<int[]> recolorNewList = new java.util.ArrayList<>();

        int[] dRow = {-1, 1, 0,  0};
        int[] dCol = { 0, 0, -1, 1};

        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int r = cur[0], c = cur[1];
            int px = c * cellSize, py = r * cellSize;

            Color fColor = from[r][c];
            Color tColor = to[r][c];

            if      (fColor == null && tColor == null) { /* empty — no output */ }
            else if (fColor != null && tColor != null && fColor.getRGB() == tColor.getRGB())
                stableList.add(new int[]{ px, py, fColor.getRGB() });
            else if (fColor == null)
                birthList.add(new int[]{ px, py, tColor.getRGB() });
            else if (tColor == null)
                deathList.add(new int[]{ px, py, fColor.getRGB() });
            else {
                recolorOldList.add(new int[]{ px, py, fColor.getRGB() });
                recolorNewList.add(new int[]{ px, py, tColor.getRGB() });
            }

            for (int d = 0; d < 4; d++) {
                int nr = r + dRow[d], nc = c + dCol[d];
                if (nr >= 0 && nr < gridSize && nc >= 0 && nc < gridSize && !visited[nr][nc]) {
                    visited[nr][nc] = true;
                    queue.add(new int[]{ nr, nc });
                }
            }
        }

        morphStables    = stableList    .toArray(new int[0][]);
        morphBirths     = birthList     .toArray(new int[0][]);
        morphDeaths     = deathList     .toArray(new int[0][]);
        morphRecolorOld = recolorOldList.toArray(new int[0][]);
        morphRecolorNew = recolorNewList.toArray(new int[0][]);

        // Build grid lookup: birthGrid[row][col] = index into morphBirths, -1 if not a birth
        int[][] birthGrid = new int[gridSize][gridSize];
        for (int[] row : birthGrid) java.util.Arrays.fill(row, -1);
        for (int i = 0; i < morphBirths.length; i++)
            birthGrid[morphBirths[i][1] / cellSize][morphBirths[i][0] / cellSize] = i;

        // Wave BFS: seed from all from-occupied cells, propagate through birth cells only.
        // Wave 0 = births adjacent to existing (from) pixels.
        // Wave k+1 = births adjacent to wave-k births.
        // Each birth records the canvas position of its spawning cell as its origin.
        int[] birthWaveAssign = new int[morphBirths.length];
        java.util.Arrays.fill(birthWaveAssign, -1);
        int[][] birthOriginArr = new int[morphBirths.length][2];
        int maxAssignedWave = -1;

        java.util.Deque<int[]> waveQ = new java.util.ArrayDeque<>();
        boolean[][] waveVis = new boolean[gridSize][gridSize];
        for (int r = 0; r < gridSize; r++)
            for (int c = 0; c < gridSize; c++)
                if (from[r][c] != null && !waveVis[r][c]) {
                    waveVis[r][c] = true;
                    waveQ.add(new int[]{ r, c, -1 });
                }

        while (!waveQ.isEmpty()) {
            int[] cur = waveQ.poll();
            int r = cur[0], c = cur[1], wave = cur[2];
            for (int d = 0; d < 4; d++) {
                int nr = r + dRow[d], nc = c + dCol[d];
                if (nr < 0 || nr >= gridSize || nc < 0 || nc >= gridSize) continue;
                int bIdx = birthGrid[nr][nc];
                if (bIdx >= 0 && !waveVis[nr][nc]) {
                    waveVis[nr][nc] = true;
                    int nextWave = wave + 1;
                    birthWaveAssign[bIdx] = nextWave;
                    birthOriginArr[bIdx][0] = c * cellSize;  // spawning cell's canvas position
                    birthOriginArr[bIdx][1] = r * cellSize;
                    if (nextWave > maxAssignedWave) maxAssignedWave = nextWave;
                    waveQ.add(new int[]{ nr, nc, nextWave });
                }
            }
        }

        // Floating births (no reachable from-cell chain): last wave, appear in place
        int floatWave = maxAssignedWave < 0 ? 0 : maxAssignedWave + 1;
        for (int i = 0; i < birthWaveAssign.length; i++) {
            if (birthWaveAssign[i] < 0) {
                birthWaveAssign[i] = floatWave;
                birthOriginArr[i][0] = morphBirths[i][0];
                birthOriginArr[i][1] = morphBirths[i][1];
            }
        }

        morphBirthWave    = birthWaveAssign;
        morphBirthOrigins = birthOriginArr;
        int maxWave = -1;
        for (int w : morphBirthWave) if (w > maxWave) maxWave = w;
        morphTotalWaves = maxWave + 1;
        if (morphTotalWaves < 1) morphTotalWaves = 1;
    }

    private float easingOut(float t, int style) {
        switch (style) {
            case 1: return t;
            case 2: return 1f - (1f-t)*(1f-t)*(1f-t)*(1f-t);
            default: return 1f - (1f-t)*(1f-t);
        }
    }

    private float easingIn(float t, int style) {
        switch (style) {
            case 1: return t;
            case 2: return t*t*t*t;
            default: return t*t;
        }
    }

    private float sineEase(float t, float amount) {
        float sinT = 0.5f * (1f - (float) Math.cos(Math.PI * t));
        return t * (1f - amount) + sinT * amount;
    }

    private void paintPixelTwist(Graphics2D g) {
        int cellSize = CELL * zoomLevel;
        boolean fullSpin  = model.isAnimTwistFullSpin();
        boolean spreadGap = model.isAnimTwistSpreadGap();
        boolean ccw       = model.getAnimTwistDirection() == 1;
        float drawSize    = cellSize * (spreadGap ? (1f - AnimConfig.TWIST_SPREAD_DEF * 2 / 100f) : 1f);

        float angle;
        int[][] pixels;
        Color[] colors;

        if (animProgress < 0.5f) {
            float t = animProgress / 0.5f;
            float eased = sineEase(t, model.getAnimTwistFirstSmooth() / 100f);
            angle = eased * 90f;
            pixels = fromPixels;
            colors = fromColors;
        } else {
            float t = (animProgress - 0.5f) / 0.5f;
            float eased = sineEase(t, model.getAnimTwistSecondSmooth() / 100f);
            angle = fullSpin ? 90f + eased * 90f : 90f - eased * 90f;
            pixels = toPixels.length > 0 ? toPixels : fromPixels;
            colors = toColors.length > 0 ? toColors : fromColors;
        }

        double angleRad = Math.toRadians(ccw ? -angle : angle);
        float half = drawSize / 2f;
        java.awt.geom.AffineTransform base = g.getTransform();

        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < pixels.length; i++) {
            float cx = pixels[i][0] + cellSize / 2f;
            float cy = pixels[i][1] + cellSize / 2f;
            java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform(base);
            at.translate(cx, cy);
            at.rotate(angleRad);
            g.setTransform(at);
            g.setColor(colors[i]);
            g.fillRect(Math.round(-half), Math.round(-half), Math.round(drawSize), Math.round(drawSize));
        }
        g.setTransform(base);
    }

    private void paintPixelMorph(Graphics2D g) {
        int cellSize = CELL * zoomLevel;
        float t = animProgress;
        float focalX = morphFocalPx[0];
        float focalY = morphFocalPx[1];

        for (int[] p : morphStables) {
            g.setColor(new Color(p[2]));
            g.fillRect(p[0], p[1], cellSize, cellSize);
        }

        float waveSlice = morphTotalWaves > 0 ? 1f / morphTotalWaves : 1f;
        for (int i = 0; i < morphBirths.length; i++) {
            int[] p = morphBirths[i];
            float ox = morphBirthOrigins[i][0];
            float oy = morphBirthOrigins[i][1];
            float waveStart = morphBirthWave[i] * waveSlice;
            float localT = (t - waveStart) / waveSlice;
            if (localT <= 0f) continue;  // not yet born
            if (localT > 1f) localT = 1f;
            int drawX = Math.round(ox + (p[0] - ox) * localT);
            int drawY = Math.round(oy + (p[1] - oy) * localT);
            g.setColor(new Color(p[2]));
            g.fillRect(drawX, drawY, cellSize, cellSize);
        }

        for (int[] p : morphDeaths) {
            int drawSize = Math.round(cellSize * (1f - t));
            if (drawSize <= 0) continue;
            g.setColor(canvasBg);
            g.fillRect(p[0], p[1], cellSize, cellSize);
            int offset = (cellSize - drawSize) / 2;
            g.setColor(new Color(p[2]));
            g.fillRect(p[0] + offset, p[1] + offset, drawSize, drawSize);
        }

        for (int i = 0; i < morphRecolorOld.length; i++) {
            int[] oldP = morphRecolorOld[i];
            int[] newP = morphRecolorNew[i];
            // new color fills the full cell as background; old shrinks on top revealing it
            g.setColor(new Color(newP[2]));
            g.fillRect(newP[0], newP[1], cellSize, cellSize);
            int oldSize = Math.round(cellSize * (1f - t));
            if (oldSize > 0) {
                int off = (cellSize - oldSize) / 2;
                g.setColor(new Color(oldP[2]));
                g.fillRect(oldP[0] + off, oldP[1] + off, oldSize, oldSize);
            }
        }
    }

    private void paintBurstPixels(Graphics g, int[][] pixels, Color[] colors, float[][] dirs, float offset) {
        int cellSize = CELL * zoomLevel;
        for (int i = 0; i < pixels.length; i++) {
            g.setColor(colors[i]);
            g.fillRect(
                (int)(pixels[i][0] + dirs[i][0] * offset),
                (int)(pixels[i][1] + dirs[i][1] * offset),
                cellSize, cellSize);
        }
    }

    private void paintPixelPop(Graphics2D g) {
        int cellSize = CELL * zoomLevel;
        float spread = model.getAnimSpread();
        float gravStrength = model.getAnimGravityPull() / 100f * spread * 6f;
        float pullSpeed = 1.0f + model.getAnimGravityPull() / 100f;
        boolean stay = model.isAnimStayInCanvas();
        int bound = model.getGridSize() * cellSize - cellSize;

        float explodeStrength = model.getAnimExplodeStrength() / 100f;
        float snapThreshold   = model.getAnimUnsplodeStrength() / 100f;
        float snapWindow      = 1f - snapThreshold;

        int extendMs = lockedExtendMs;

        if (animProgress < 0.5f) {
            // Explode: per-pixel random speeds + gravity, energy dims as t grows
            float t = animProgress / 0.5f;
            float tEased = easingOut(t, model.getAnimEasing());
            float diminish = 1f - t * 0.2f;
            float gravOffset = gravStrength * t * t;
            for (int i = 0; i < fromPixels.length; i++) {
                float pushOff = spread * fromSpeeds[i] * explodeStrength * tEased * diminish;
                int px = (int)(fromPixels[i][0] + fromDirs[i][0] * pushOff + fromGravDirs[i][0] * gravOffset);
                int py = (int)(fromPixels[i][1] + fromDirs[i][1] * pushOff + fromGravDirs[i][1] * gravOffset);
                if (stay) {
                    px = Math.max(0, Math.min(px, bound));
                    py = Math.max(0, Math.min(py, bound));
                }
                g.setColor(fromColors[i]);
                g.fillRect(px, py, cellSize, cellSize);
            }
        } else if (extendMs > 0 && extendElapsedMs < extendMs) {
            // Extend: continue from exploded positions along same direction
            float extendT = (float) extendElapsedMs / extendMs;
            for (int i = 0; i < fromPixels.length; i++) {
                int px = (int)(explodedPositions[i][0] + (extendedPositions[i][0] - explodedPositions[i][0]) * extendT);
                int py = (int)(explodedPositions[i][1] + (extendedPositions[i][1] - explodedPositions[i][1]) * extendT);
                if (stay) {
                    px = Math.max(0, Math.min(px, bound));
                    py = Math.max(0, Math.min(py, bound));
                }
                g.setColor(fromColors[i]);
                g.fillRect(px, py, cellSize, cellSize);
            }
        } else if (midHoldElapsedMs < model.getAnimPopHoldMs()) {
            // Delay: hold at the furthest position (extended or exploded if no extend)
            int[][] holdPos = extendMs > 0 ? extendedPositions : explodedPositions;
            for (int i = 0; i < fromPixels.length; i++) {
                g.setColor(fromColors[i]);
                g.fillRect(holdPos[i][0], holdPos[i][1], cellSize, cellSize);
            }
        } else {
            // Unsplode: lerp from the furthest position back to home
            int[][] startPos = extendMs > 0 ? extendedPositions : explodedPositions;
            float t = (animProgress - 0.5f) / 0.5f;
            for (int i = 0; i < toPixels.length; i++) {
                float approach = Math.min(1f, t * toSpeeds[i]);
                float remainFrac;
                if (approach < snapThreshold) {
                    remainFrac = 1f - approach;
                } else {
                    float snapT = (approach - snapThreshold) / snapWindow;
                    remainFrac = snapWindow * (1f - Math.min(1f, snapT * pullSpeed));
                }
                int sIdx = startPos.length > 0 ? i % startPos.length : -1;
                int px = sIdx >= 0 ? (int)(toPixels[i][0] + (startPos[sIdx][0] - toPixels[i][0]) * remainFrac) : toPixels[i][0];
                int py = sIdx >= 0 ? (int)(toPixels[i][1] + (startPos[sIdx][1] - toPixels[i][1]) * remainFrac) : toPixels[i][1];
                if (stay) {
                    px = Math.max(0, Math.min(px, bound));
                    py = Math.max(0, Math.min(py, bound));
                }
                g.setColor(toColors[i]);
                g.fillRect(px, py, cellSize, cellSize);
            }
        }
    }

    // ── canvas ───────────────────────────────────────────────────────────────

    private class Canvas extends JPanel {

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int cellSize = CELL * zoomLevel;
            int size = model.getGridSize();
            int spriteW = size * cellSize;
            int spriteH = size * cellSize;
            int offsetX = Math.max(0, (getWidth() - spriteW) / 2);
            int offsetY = (int)(getHeight() * 0.05f);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(offsetX, offsetY);

            if (model.getMode() == Mode.TRANSFORM) {
                paintTransform(g2, cellSize);
            } else {
                paintDraw(g2, cellSize);
            }

            g2.dispose();
        }

        private void paintTransform(Graphics2D g, int cellSize) {
            List<Color[][]> frames = model.getAnimationFrames();
            if (frames.isEmpty()) {
                g.setColor(RetroTheme.noFramesTextColor());
                g.drawString("No frames loaded", 8, 20);
                return;
            }

            if (animating) {
                if (lockedEffectType == 1) {
                    paintPixelPop(g);
                } else if (lockedEffectType == 2) {
                    paintPixelTwist(g);
                } else if (lockedEffectType == 3) {
                    paintPixelMorph(g);
                } else {
                    float spread = model.getAnimSpread();
                    int easing = model.getAnimEasing();
                    if (animProgress < 0.5f) {
                        float t = animProgress / 0.5f;
                        paintBurstPixels(g, fromPixels, fromColors, fromDirs, spread * easingOut(t, easing));
                    } else {
                        float t = (animProgress - 0.5f) / 0.5f;
                        paintBurstPixels(g, toPixels, toColors, toDirs, spread * (1f - easingIn(t, easing)));
                    }
                }
            } else {
                Color[][] frame = frames.get(currentFrameIdx);
                int size = frame.length;
                boolean twistSpread = model.getAnimEffectType() == 2 && model.isAnimTwistSpreadGap();
                int drawSz = twistSpread
                    ? Math.round(cellSize * (1f - AnimConfig.TWIST_SPREAD_DEF * 2 / 100f))
                    : cellSize;
                int off = (cellSize - drawSz) / 2;
                for (int row = 0; row < size; row++)
                    for (int col = 0; col < size; col++) {
                        Color c = frame[row][col];
                        if (c != null) {
                            g.setColor(c);
                            g.fillRect(col * cellSize + off, row * cellSize + off, drawSz, drawSz);
                        }
                    }
            }

            if (model.getAnimEffectType() == 1) {
                int canvasPx = model.getGridSize() * cellSize;
                paintGravityReticle(g, canvasPx, canvasPx);
            }
        }

        private static final BasicStroke STROKE_RETICLE_SHADOW = new BasicStroke(4f);
        private static final BasicStroke STROKE_RETICLE        = new BasicStroke(2f);
        private static final Color RETICLE_COLOR  = new Color(255, 80, 200);
        private static final Color RETICLE_SHADOW = new Color(0, 0, 0, 140);

        private void paintGravityReticle(Graphics2D g, int canvasW, int canvasH) {
            int gx = model.getAnimGravityFocalX();
            int gy = model.getAnimGravityFocalY();
            boolean xAtEdge = (gx == 0 || gx == 100);
            boolean yAtEdge = (gy == 0 || gy == 100);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (xAtEdge || yAtEdge) {
                int wx = gx == 0 ? 0 : (gx == 100 ? canvasW - 1 : -1);
                int wy = gy == 0 ? 0 : (gy == 100 ? canvasH - 1 : -1);
                if (xAtEdge) {
                    g2.setStroke(STROKE_RETICLE_SHADOW);
                    g2.setColor(RETICLE_SHADOW);
                    g2.drawLine(wx, 0, wx, canvasH);
                    g2.setStroke(STROKE_RETICLE);
                    g2.setColor(RETICLE_COLOR);
                    g2.drawLine(wx, 0, wx, canvasH);
                }
                if (yAtEdge) {
                    g2.setStroke(STROKE_RETICLE_SHADOW);
                    g2.setColor(RETICLE_SHADOW);
                    g2.drawLine(0, wy, canvasW, wy);
                    g2.setStroke(STROKE_RETICLE);
                    g2.setColor(RETICLE_COLOR);
                    g2.drawLine(0, wy, canvasW, wy);
                }
            } else {
                int fx = Math.round(canvasW * gx / 100f);
                int fy = Math.round(canvasH * gy / 100f);
                int arm = 10, r = 5;
                g2.setStroke(STROKE_RETICLE_SHADOW);
                g2.setColor(RETICLE_SHADOW);
                g2.drawLine(fx - arm, fy + 1, fx + arm, fy + 1);
                g2.drawLine(fx + 1, fy - arm, fx + 1, fy + arm);
                g2.drawOval(fx - r + 1, fy - r + 1, r * 2, r * 2);
                g2.setStroke(STROKE_RETICLE);
                g2.setColor(RETICLE_COLOR);
                g2.drawLine(fx - arm, fy, fx + arm, fy);
                g2.drawLine(fx, fy - arm, fx, fy + arm);
                g2.drawOval(fx - r, fy - r, r * 2, r * 2);
            }
            g2.dispose();
        }

        private void paintDraw(Graphics2D g, int cellSize) {
            int size = model.getGridSize();
            int w = size * cellSize, h = size * cellSize;
            if (drawDirty || drawCache == null
                    || drawCache.getWidth() != w || drawCache.getHeight() != h) {
                drawCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D bg = drawCache.createGraphics();
                for (int row = 0; row < size; row++) {
                    for (int col = 0; col < size; col++) {
                        Color c = model.getCellColor(row, col);
                        int x = col * cellSize, y = row * cellSize;
                        if (c == null) drawCheckerboard(bg, x, y, cellSize);
                        else { bg.setColor(c); bg.fillRect(x, y, cellSize, cellSize); }
                    }
                }
                bg.dispose();
                drawDirty = false;
            }
            g.drawImage(drawCache, 0, 0, null);
        }

        private void drawCheckerboard(Graphics g, int x, int y, int cellSize) {
            int half = cellSize / 2;
            if (half < 1) half = 1;
            g.setColor(new Color(0xcccccc));
            g.fillRect(x, y, half, half);
            g.fillRect(x + half, y + half, half, half);
            g.setColor(Color.WHITE);
            g.fillRect(x + half, y, half, half);
            g.fillRect(x, y + half, half, half);
        }
    }

    // ── background color slider ───────────────────────────────────────────────

    private static Color bgSliderColor(int val) {
        // 0=black (bottom) → 100=white (top), ROYGBIV in between
        Color[] stops = {
            Color.BLACK,
            new Color(148,   0, 211),  // violet
            new Color( 75,   0, 130),  // indigo
            new Color(  0,   0, 255),  // blue
            new Color(  0, 255,   0),  // green
            new Color(255, 255,   0),  // yellow
            new Color(255, 127,   0),  // orange
            new Color(255,   0,   0),  // red
            Color.WHITE
        };
        float pos = val / 12.5f;
        int seg = Math.min((int) pos, stops.length - 2);
        return blend(stops[seg], stops[seg + 1], pos - seg);
    }

    private static int colorToSliderVal(Color c) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int v = 0; v <= 100; v++) {
            Color g = bgSliderColor(v);
            double d = Math.pow(g.getRed() - c.getRed(), 2)
                     + Math.pow(g.getGreen() - c.getGreen(), 2)
                     + Math.pow(g.getBlue() - c.getBlue(), 2);
            if (d < bestDist) { bestDist = d; best = v; }
        }
        return best;
    }

    private static Color blend(Color a, Color b, float t) {
        int r = Math.round(a.getRed()   + (b.getRed()   - a.getRed())   * t);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = Math.round(a.getBlue() + (b.getBlue()  - a.getBlue())  * t);
        return new Color(r, g, bl);
    }

    private class BgColorSlider extends JPanel {
        private static final int W = 14;
        private static final int THUMB_H = 5;
        private int val;

        BgColorSlider() {
            val = colorToSliderVal(canvasBg);
            setPreferredSize(new Dimension(W, 80));
            setMinimumSize(new Dimension(W, 40));
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e)  { update(e.getY()); }
                @Override public void mouseDragged(MouseEvent e)  { update(e.getY()); }
                private void update(int y) {
                    int trackH = getHeight() - THUMB_H;
                    if (trackH <= 0) return;
                    int raw = trackH - (y - THUMB_H / 2);
                    val = Math.max(0, Math.min(100, Math.round(raw * 100f / trackH)));
                    canvasBg = bgSliderColor(val);
                    canvas.setBackground(canvasBg);
                    canvas.repaint();
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int h = getHeight() - THUMB_H;
            // gradient track: white at top, black at bottom
            for (int y = 0; y < h; y++) {
                int v = 100 - Math.round(y * 100f / h);
                g.setColor(bgSliderColor(v));
                g.fillRect(0, y + THUMB_H / 2, W, 1);
            }
            // thumb
            int thumbY = Math.round((100 - val) * h / 100f);
            g.setColor(Color.WHITE);
            g.drawRect(0, thumbY, W - 1, THUMB_H);
        }
    }
}

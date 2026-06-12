package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.SpriteModel.Mode;
import com.atari.spritemaker.model.TransformSettings;
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
    private int extendElapsedMs = 0;
    private boolean playing = false;
    private int currentFrameIdx = 0;

    // Pre-computed transition data
    private TransitionData[] precomputedTransitions = null;
    private int currentTransitionIdx = 0;
    private TransitionData active = null;
    private boolean uftLoopMode = false;
    private int lastSelectedUFT = -1;
    private boolean lastUFTEnabled = false;

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
    private javax.swing.Timer settingChangeTimer;

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

        btnZoom1x.addActionListener(e -> { zoomLevel = 1; precomputedTransitions = null; drawDirty = true; canvas.repaint(); });
        btnZoom2x.addActionListener(e -> { zoomLevel = 2; precomputedTransitions = null; drawDirty = true; canvas.repaint(); });
        btnZoom4x.addActionListener(e -> { zoomLevel = 4; precomputedTransitions = null; drawDirty = true; canvas.repaint(); });

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
            int selUFT = model.getSelectedUFTIndex();
            uftLoopMode = selUFT >= 0 && model.isUFTEnabled(selUFT);
            playing = true;
            burstTimer.stop();
            pauseTimer.stop();
            animating = false;
            precomputedTransitions = null;
            currentTransitionIdx = uftLoopMode ? selUFT : 0;
            currentFrameIdx = currentTransitionIdx;
            startBurst();
        });
        btnPause.addActionListener(e -> {
            playing = false;
        });
        btnStep.addActionListener(e -> {
            uftLoopMode = false;
            playing = false;
            precomputedTransitions = null;
            if (!animating) startBurst();
        });
        btnReset.addActionListener(e -> {
            playing = false;
            animating = false;
            uftLoopMode = false;
            currentFrameIdx = 0;
            currentTransitionIdx = 0;
            precomputedTransitions = null;
            burstTimer.stop();
            pauseTimer.stop();
            canvas.repaint();
        });
    }

    public void onTransformSettingChanged() {
        precomputedTransitions = null;
        if (settingChangeTimer != null) settingChangeTimer.stop();
        settingChangeTimer = new javax.swing.Timer(300, e -> {
            settingChangeTimer = null;
            if (model.getMode() == Mode.TRANSFORM && model.getAnimationFrames().size() >= 2) {
                animating = false;
                burstTimer.stop();
                pauseTimer.stop();
                startBurst();
            }
        });
        settingChangeTimer.setRepeats(false);
        settingChangeTimer.start();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        // Always invalidate pre-computed data when model changes
        precomputedTransitions = null;

        boolean isTransform = model.getMode() == Mode.TRANSFORM;
        animControls.setVisible(isTransform);
        if (!isTransform) {
            playing = false;
            animating = false;
            uftLoopMode = false;
            lastSelectedUFT = -1;
            lastUFTEnabled = false;
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
            if (currentTransitionIdx >= frames.size()) currentTransitionIdx = 0;

            // Handle UFT selection/enable changes
            int selectedUFT = model.getSelectedUFTIndex();
            boolean uftIsEnabled = selectedUFT >= 0 && model.isUFTEnabled(selectedUFT);
            if (selectedUFT != lastSelectedUFT || (selectedUFT >= 0 && uftIsEnabled != lastUFTEnabled)) {
                lastSelectedUFT = selectedUFT;
                lastUFTEnabled  = uftIsEnabled;
                burstTimer.stop();
                pauseTimer.stop();
                animating = false;
                if (selectedUFT >= 0) {
                    currentTransitionIdx = selectedUFT;
                    currentFrameIdx = selectedUFT;
                    if (uftIsEnabled && frames.size() >= 2) {
                        uftLoopMode = true;
                        playing = true;
                        startBurst();
                        return;
                    } else {
                        uftLoopMode = false;
                        playing = false;
                    }
                } else {
                    uftLoopMode = false;
                    playing = false;
                }
            }

            // Any model change while full animation is playing: restart from frame 0
            if (playing && !uftLoopMode && frames.size() >= 2) {
                burstTimer.stop();
                pauseTimer.stop();
                animating = false;
                currentTransitionIdx = 0;
                currentFrameIdx = 0;
                startBurst();
                return;
            }

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

    private void precomputeAllTransitions() {
        List<Color[][]> frames = model.getAnimationFrames();
        int N = frames.size();
        if (N < 2) { precomputedTransitions = new TransitionData[0]; return; }
        model.syncSelectedUFT();
        TransformSettings saved = TransformSettings.capture(model);
        TransformSettings globalFallback = saved.copy();
        precomputedTransitions = new TransitionData[N];
        for (int i = 0; i < N; i++) {
            TransformSettings ts = model.getTransformForTransition(i);
            model.applySettingsSilently(ts != null ? ts : globalFallback);
            precomputedTransitions[i] = buildTransitionData(frames, i, (i + 1) % N);
        }
        model.applySettingsSilently(saved);
        canvas.repaint();
    }

    private TransitionData buildTransitionData(List<Color[][]> frames, int fromIdx, int toIdx) {
        TransitionData td = new TransitionData();
        td.lockedEffectType = model.getAnimEffectType();
        td.animSpeedMs  = model.getAnimSpeedMs();
        td.animHoldMs   = model.getAnimHoldMs();
        td.animEasing   = model.getAnimEasing();
        td.animSpread   = model.getAnimSpread();
        td.animExplodeSpeedMs   = model.getAnimExplodeSpeedMs();
        td.animUnsplodeSpeedMs  = model.getAnimUnsplodeSpeedMs();
        td.animPopHoldMs        = model.getAnimPopHoldMs();
        td.animTwistFirstSpeedMs  = model.getAnimTwistFirstSpeedMs();
        td.animTwistSecondSpeedMs = model.getAnimTwistSecondSpeedMs();
        td.animTwistFirstSmooth   = model.getAnimTwistFirstSmooth() / 100f;
        td.animTwistSecondSmooth  = model.getAnimTwistSecondSmooth() / 100f;
        td.animTwistFullSpin   = model.isAnimTwistFullSpin();
        td.animTwistSpreadGap  = model.isAnimTwistSpreadGap();
        td.animTwistDirection  = model.getAnimTwistDirection();
        td.animGravityPull     = model.getAnimGravityPull() / 100f;
        td.animGravityPush     = model.getAnimGravityPush() / 100f;
        td.animExplodeStrength = model.getAnimExplodeStrength() / 100f;
        td.animUnsplodeStrength = model.getAnimUnsplodeStrength() / 100f;
        td.animStayInCanvas    = model.isAnimStayInCanvas();
        td.animWallDamping     = model.getAnimWallDamping() / 100f;
        td.animPopStayAtFocus  = model.isAnimPopStayAtFocus();
        td.animGravityFocalX   = model.getAnimGravityFocalX();
        td.animGravityFocalY   = model.getAnimGravityFocalY();
        td.animMorphFadeDeaths = model.isAnimMorphFadeDeaths();
        td.animMorphSpeedMs    = model.getAnimMorphSpeedMs();
        td.animMorphHoldMs     = model.getAnimMorphHoldMs();

        int gridSize = frames.get(fromIdx).length;
        td.fromPixels = buildPixels(frames.get(fromIdx), gridSize);
        td.fromColors = buildColors(td.fromPixels);
        td.fromDirs   = buildDirs(td.fromPixels, gridSize);
        td.toPixels   = buildPixels(frames.get(toIdx), gridSize);
        td.toColors   = buildColors(td.toPixels);
        td.toDirs     = buildDirs(td.toPixels, gridSize);

        if (td.lockedEffectType == 1) {
            int gs = gridSize;
            td.fromSpeeds   = buildSpeeds(td.fromPixels.length, td.animGravityPush);
            td.fromGravDirs = buildGravDirs(td.fromPixels, gs);
            td.toSpeeds     = buildSpeeds(td.toPixels.length, td.animGravityPush);
            td.toGravDirs   = buildGravDirs(td.toPixels, gs);

            int cellSize = CELL * zoomLevel;
            float spread = td.animSpread;
            float gravStrength = td.animGravityPull * spread * 6f;
            boolean stay = td.animStayInCanvas;
            int bound = gs * cellSize - cellSize;
            float explodeStrength = td.animExplodeStrength;
            td.lockedExtendMs = model.getAnimExtendMs();
            float extendRatio = td.animExplodeSpeedMs > 0
                ? (float) td.lockedExtendMs / td.animExplodeSpeedMs : 0f;
            // Gravity only applies when Stay at Focus is enabled; otherwise pure radial scatter
            float tExtendEnd   = 1.0f + extendRatio;
            float gravAtPeak   = td.animPopStayAtFocus ? gravStrength : 0f;
            float gravAtExtend = td.animPopStayAtFocus ? gravStrength * tExtendEnd * tExtendEnd : 0f;
            td.explodedPositions = new int[td.fromPixels.length][2];
            td.extendedPositions = new int[td.fromPixels.length][2];
            for (int i = 0; i < td.fromPixels.length; i++) {
                float peakPushOff = spread * td.fromSpeeds[i] * explodeStrength * 0.8f;
                int expX = (int)(td.fromPixels[i][0] + td.fromDirs[i][0] * peakPushOff + td.fromGravDirs[i][0] * gravAtPeak);
                int expY = (int)(td.fromPixels[i][1] + td.fromDirs[i][1] * peakPushOff + td.fromGravDirs[i][1] * gravAtPeak);
                if (stay) {
                    expX = Math.max(0, Math.min(expX, bound));
                    expY = Math.max(0, Math.min(expY, bound));
                }
                td.explodedPositions[i][0] = expX;
                td.explodedPositions[i][1] = expY;
                int extX = (int)(td.fromPixels[i][0] + td.fromDirs[i][0] * peakPushOff + td.fromGravDirs[i][0] * gravAtExtend);
                int extY = (int)(td.fromPixels[i][1] + td.fromDirs[i][1] * peakPushOff + td.fromGravDirs[i][1] * gravAtExtend);
                if (stay) {
                    extX = Math.max(0, Math.min(extX, bound));
                    extY = Math.max(0, Math.min(extY, bound));
                }
                td.extendedPositions[i][0] = extX;
                td.extendedPositions[i][1] = extY;
            }
        }
        if (td.lockedEffectType == 3) {
            buildMorphDataForTransition(frames.get(fromIdx), frames.get(toIdx), gridSize, td);
        }
        return td;
    }

    private void startBurst() {
        burstTimer.stop();
        List<Color[][]> frames = model.getAnimationFrames();
        if (frames.size() < 2) return;
        int N = frames.size();

        if (precomputedTransitions == null) {
            model.syncSelectedUFT();
            TransformSettings saved = TransformSettings.capture(model);
            TransformSettings globalFallback = saved.copy();
            precomputedTransitions = new TransitionData[N];
            final TransitionData[] batch = precomputedTransitions;

            // Compute only the current transition synchronously so animation starts immediately
            TransformSettings ts = model.getTransformForTransition(currentTransitionIdx);
            model.applySettingsSilently(ts != null ? ts : globalFallback);
            batch[currentTransitionIdx] = buildTransitionData(frames, currentTransitionIdx, (currentTransitionIdx + 1) % N);
            model.applySettingsSilently(saved);

            // Compute remaining slots one-at-a-time via invokeLater while animation plays
            scheduleNextLazyTransition(batch, (currentTransitionIdx + 1) % N, frames, globalFallback, saved);
        }

        if (precomputedTransitions.length == 0) return;
        currentTransitionIdx = Math.min(currentTransitionIdx, precomputedTransitions.length - 1);

        // If this slot is still null (lazy chain hasn't reached it yet), compute now
        if (precomputedTransitions[currentTransitionIdx] == null) {
            model.syncSelectedUFT();
            TransformSettings saved = TransformSettings.capture(model);
            TransformSettings globalFallback = saved.copy();
            TransformSettings ts = model.getTransformForTransition(currentTransitionIdx);
            model.applySettingsSilently(ts != null ? ts : globalFallback);
            precomputedTransitions[currentTransitionIdx] = buildTransitionData(frames, currentTransitionIdx, (currentTransitionIdx + 1) % N);
            model.applySettingsSilently(saved);
        }

        active = precomputedTransitions[currentTransitionIdx];
        animProgress = 0f;
        midHoldElapsedMs = 0;
        extendElapsedMs = 0;
        animating = true;
        burstTimer.start();
    }

    private void scheduleNextLazyTransition(TransitionData[] batch, int startIdx, List<Color[][]> frames,
                                             TransformSettings globalFallback, TransformSettings saved) {
        int N = batch.length;
        for (int i = 0; i < N; i++) {
            int idx = (startIdx + i) % N;
            if (batch[idx] == null) {
                final int computeIdx = idx;
                final int nextStart  = (idx + 1) % N;
                SwingUtilities.invokeLater(() -> {
                    if (precomputedTransitions != batch) return; // batch was invalidated
                    TransformSettings ts = model.getTransformForTransition(computeIdx);
                    model.applySettingsSilently(ts != null ? ts : globalFallback);
                    batch[computeIdx] = buildTransitionData(frames, computeIdx, (computeIdx + 1) % N);
                    model.applySettingsSilently(saved);
                    scheduleNextLazyTransition(batch, nextStart, frames, globalFallback, saved);
                });
                return;
            }
        }
    }

    private void tickBurst() {
        if (active == null) { burstTimer.stop(); return; }
        float delta;
        if (active.lockedEffectType == 1) {
            if (animProgress >= 0.5f) {
                if (extendElapsedMs < active.lockedExtendMs) {
                    animProgress = 0.5f;
                    extendElapsedMs += 16;
                    canvas.repaint();
                    return;
                }
                if (midHoldElapsedMs < active.animPopHoldMs) {
                    animProgress = 0.5f;
                    midHoldElapsedMs += 16;
                    canvas.repaint();
                    return;
                }
            }
            int phaseSpeed = animProgress < 0.5f
                ? active.animExplodeSpeedMs
                : active.animUnsplodeSpeedMs;
            delta = 16f * 0.5f / phaseSpeed;
        } else if (active.lockedEffectType == 2) {
            int phaseMs = animProgress < 0.5f
                ? Math.max(16, active.animTwistFirstSpeedMs)
                : Math.max(16, active.animTwistSecondSpeedMs);
            delta = 16f * 0.5f / phaseMs;
        } else if (active.lockedEffectType == 3) {
            delta = 16f / Math.max(16, active.animMorphSpeedMs);
        } else {
            delta = 16f / Math.max(16, active.animSpeedMs);
        }
        animProgress += delta;
        if (animProgress >= 1f) {
            animProgress = 1f;
            animating = false;
            burstTimer.stop();
            int N = model.getAnimationFrames().size();
            if (uftLoopMode) {
                // Loop just this transition
                if (playing) {
                    int holdMs = active.lockedEffectType == 3 ? active.animMorphHoldMs : active.animHoldMs;
                    pauseTimer.setDelay(Math.max(1, holdMs));
                    pauseTimer.start();
                }
            } else {
                currentTransitionIdx = (currentTransitionIdx + 1) % N;
                currentFrameIdx = currentTransitionIdx;
                if (playing) {
                    int holdMs = active.lockedEffectType == 3 ? active.animMorphHoldMs : active.animHoldMs;
                    pauseTimer.setDelay(Math.max(1, holdMs));
                    pauseTimer.start();
                }
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

    private void buildMorphDataForTransition(Color[][] from, Color[][] to, int gridSize, TransitionData td) {
        int cellSize = CELL * zoomLevel;
        float totalPx = gridSize * cellSize;
        float fx = totalPx * model.getAnimFocalX() / 100f;
        float fy = totalPx * model.getAnimFocalY() / 100f;
        td.morphFocalPx = new float[]{ fx, fy };

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

        td.morphStables    = stableList    .toArray(new int[0][]);
        td.morphBirths     = birthList     .toArray(new int[0][]);
        td.morphDeaths     = deathList     .toArray(new int[0][]);
        td.morphRecolorOld = recolorOldList.toArray(new int[0][]);
        td.morphRecolorNew = recolorNewList.toArray(new int[0][]);

        int[][] birthGrid = new int[gridSize][gridSize];
        for (int[] row : birthGrid) java.util.Arrays.fill(row, -1);
        for (int i = 0; i < td.morphBirths.length; i++)
            birthGrid[td.morphBirths[i][1] / cellSize][td.morphBirths[i][0] / cellSize] = i;

        int[] birthWaveAssign = new int[td.morphBirths.length];
        java.util.Arrays.fill(birthWaveAssign, -1);
        int[][] birthOriginArr = new int[td.morphBirths.length][2];
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
                    birthOriginArr[bIdx][0] = c * cellSize;
                    birthOriginArr[bIdx][1] = r * cellSize;
                    if (nextWave > maxAssignedWave) maxAssignedWave = nextWave;
                    waveQ.add(new int[]{ nr, nc, nextWave });
                }
            }
        }

        int floatWave = maxAssignedWave < 0 ? 0 : maxAssignedWave + 1;
        for (int i = 0; i < birthWaveAssign.length; i++) {
            if (birthWaveAssign[i] < 0) {
                birthWaveAssign[i] = floatWave;
                birthOriginArr[i][0] = td.morphBirths[i][0];
                birthOriginArr[i][1] = td.morphBirths[i][1];
            }
        }

        td.morphBirthWave    = birthWaveAssign;
        td.morphBirthOrigins = birthOriginArr;
        int maxWave = -1;
        for (int w : td.morphBirthWave) if (w > maxWave) maxWave = w;
        td.morphTotalWaves = maxWave + 1;
        if (td.morphTotalWaves < 1) td.morphTotalWaves = 1;
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
        if (active == null) return;
        int cellSize = CELL * zoomLevel;
        boolean fullSpin  = active.animTwistFullSpin;
        boolean spreadGap = active.animTwistSpreadGap;
        boolean ccw       = active.animTwistDirection == 1;
        float drawSize    = cellSize * (spreadGap ? (1f - AnimConfig.TWIST_SPREAD_DEF * 2 / 100f) : 1f);

        float angle;
        int[][] pixels;
        Color[] colors;

        if (animProgress < 0.5f) {
            float t = animProgress / 0.5f;
            float eased = sineEase(t, active.animTwistFirstSmooth);
            angle = eased * 90f;
            pixels = active.fromPixels;
            colors = active.fromColors;
        } else {
            float t = (animProgress - 0.5f) / 0.5f;
            float eased = sineEase(t, active.animTwistSecondSmooth);
            angle = fullSpin ? 90f + eased * 90f : 90f - eased * 90f;
            pixels = active.toPixels.length > 0 ? active.toPixels : active.fromPixels;
            colors = active.toColors.length > 0 ? active.toColors : active.fromColors;
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
        if (active == null) return;
        int cellSize = CELL * zoomLevel;
        float t = animProgress;

        for (int[] p : active.morphStables) {
            g.setColor(new Color(p[2]));
            g.fillRect(p[0], p[1], cellSize, cellSize);
        }

        float waveSlice = active.morphTotalWaves > 0 ? 1f / active.morphTotalWaves : 1f;
        for (int i = 0; i < active.morphBirths.length; i++) {
            int[] p = active.morphBirths[i];
            float ox = active.morphBirthOrigins[i][0];
            float oy = active.morphBirthOrigins[i][1];
            float waveStart = active.morphBirthWave[i] * waveSlice;
            float localT = (t - waveStart) / waveSlice;
            if (localT <= 0f) continue;
            if (localT > 1f) localT = 1f;
            int drawX = Math.round(ox + (p[0] - ox) * localT);
            int drawY = Math.round(oy + (p[1] - oy) * localT);
            g.setColor(new Color(p[2]));
            g.fillRect(drawX, drawY, cellSize, cellSize);
        }

        for (int[] p : active.morphDeaths) {
            int drawSize = Math.round(cellSize * (1f - t));
            if (drawSize <= 0) continue;
            g.setColor(canvasBg);
            g.fillRect(p[0], p[1], cellSize, cellSize);
            int offset = (cellSize - drawSize) / 2;
            if (active.animMorphFadeDeaths) {
                java.awt.Composite old = g.getComposite();
                g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 1f - t));
                g.setColor(new Color(p[2]));
                g.fillRect(p[0] + offset, p[1] + offset, drawSize, drawSize);
                g.setComposite(old);
            } else {
                g.setColor(new Color(p[2]));
                g.fillRect(p[0] + offset, p[1] + offset, drawSize, drawSize);
            }
        }

        for (int i = 0; i < active.morphRecolorOld.length; i++) {
            int[] oldP = active.morphRecolorOld[i];
            int[] newP = active.morphRecolorNew[i];
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

    private int popBounce(int pos, int bound, float damping) {
        for (int b = 0; b < 3; b++) {
            if (pos >= 0 && pos <= bound) break;
            if (pos < 0)     pos = (int)(-pos * damping);
            else if (pos > bound) pos = (int)(bound - (pos - bound) * damping);
        }
        return Math.max(0, Math.min(pos, bound));
    }

    private void applyStayAtFocus(int[] result, int cellSize) {
        int gs = model.getGridSize();
        float gx = active.animGravityFocalX / 100f * gs * cellSize;
        float gy = active.animGravityFocalY / 100f * gs * cellSize;
        float dx = gx - (result[0] + cellSize * 0.5f);
        float dy = gy - (result[1] + cellSize * 0.5f);
        float dist = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
        float maxDist = gs * cellSize;
        float proximity = 1f - Math.min(1f, dist / maxDist);
        float pullMag = active.animGravityPull * active.animSpread * proximity * proximity * 4f;
        result[0] += (int)(dx / dist * pullMag);
        result[1] += (int)(dy / dist * pullMag);
    }

    private void paintPixelPop(Graphics2D g) {
        if (active == null) return;
        int cellSize = CELL * zoomLevel;
        float spread = active.animSpread;
        float gravStrength = active.animGravityPull * spread * 6f;
        float pullSpeed = 1.0f + active.animGravityPull;
        boolean stay = active.animStayInCanvas;
        float damping = active.animWallDamping;
        int bound = model.getGridSize() * cellSize - cellSize;

        float explodeStrength = active.animExplodeStrength;
        float snapThreshold   = active.animUnsplodeStrength;
        float snapWindow      = 1f - snapThreshold;

        int extendMs = active.lockedExtendMs;
        int[] pos = new int[2];

        if (animProgress < 0.5f) {
            float t = animProgress / 0.5f;
            float tEased = easingOut(t, active.animEasing);
            float diminish = 1f - t * 0.2f;
            float gravOffset = active.animPopStayAtFocus ? gravStrength * t * t : 0f;
            for (int i = 0; i < active.fromPixels.length; i++) {
                float pushOff = spread * active.fromSpeeds[i] * explodeStrength * tEased * diminish;
                pos[0] = (int)(active.fromPixels[i][0] + active.fromDirs[i][0] * pushOff + active.fromGravDirs[i][0] * gravOffset);
                pos[1] = (int)(active.fromPixels[i][1] + active.fromDirs[i][1] * pushOff + active.fromGravDirs[i][1] * gravOffset);
                if (active.animPopStayAtFocus) applyStayAtFocus(pos, cellSize);
                if (stay) { pos[0] = popBounce(pos[0], bound, damping); pos[1] = popBounce(pos[1], bound, damping); }
                g.setColor(active.fromColors[i]);
                g.fillRect(pos[0], pos[1], cellSize, cellSize);
            }
        } else if (extendMs > 0 && extendElapsedMs < extendMs) {
            float extendFrac = (float) extendElapsedMs / Math.max(1, active.animExplodeSpeedMs);
            float tRaw       = 1.0f + extendFrac;
            float tEased     = easingOut(1.0f, active.animEasing);
            float diminish   = 0.8f;
            float gravOffset = active.animPopStayAtFocus ? gravStrength * tRaw * tRaw : 0f;
            for (int i = 0; i < active.fromPixels.length; i++) {
                float pushOff = spread * active.fromSpeeds[i] * explodeStrength * tEased * diminish;
                pos[0] = (int)(active.fromPixels[i][0] + active.fromDirs[i][0] * pushOff + active.fromGravDirs[i][0] * gravOffset);
                pos[1] = (int)(active.fromPixels[i][1] + active.fromDirs[i][1] * pushOff + active.fromGravDirs[i][1] * gravOffset);
                if (active.animPopStayAtFocus) applyStayAtFocus(pos, cellSize);
                if (stay) { pos[0] = popBounce(pos[0], bound, damping); pos[1] = popBounce(pos[1], bound, damping); }
                g.setColor(active.fromColors[i]);
                g.fillRect(pos[0], pos[1], cellSize, cellSize);
            }
        } else if (midHoldElapsedMs < active.animPopHoldMs) {
            int[][] holdPos = extendMs > 0 ? active.extendedPositions : active.explodedPositions;
            for (int i = 0; i < active.fromPixels.length; i++) {
                g.setColor(active.fromColors[i]);
                g.fillRect(holdPos[i][0], holdPos[i][1], cellSize, cellSize);
            }
        } else {
            int[][] startPos = extendMs > 0 ? active.extendedPositions : active.explodedPositions;
            float t = (animProgress - 0.5f) / 0.5f;
            for (int i = 0; i < active.toPixels.length; i++) {
                float approach = Math.min(1f, t * active.toSpeeds[i]);
                float remainFrac;
                if (approach < snapThreshold) {
                    remainFrac = 1f - approach;
                } else {
                    float snapT = (approach - snapThreshold) / snapWindow;
                    remainFrac = snapWindow * (1f - Math.min(1f, snapT * pullSpeed));
                }
                int sIdx = startPos.length > 0 ? i % startPos.length : -1;
                pos[0] = sIdx >= 0 ? (int)(active.toPixels[i][0] + (startPos[sIdx][0] - active.toPixels[i][0]) * remainFrac) : active.toPixels[i][0];
                pos[1] = sIdx >= 0 ? (int)(active.toPixels[i][1] + (startPos[sIdx][1] - active.toPixels[i][1]) * remainFrac) : active.toPixels[i][1];
                if (active.animPopStayAtFocus) applyStayAtFocus(pos, cellSize);
                if (stay) { pos[0] = popBounce(pos[0], bound, damping); pos[1] = popBounce(pos[1], bound, damping); }
                g.setColor(active.toColors[i]);
                g.fillRect(pos[0], pos[1], cellSize, cellSize);
            }
        }
    }

    // ── pre-computed transition data ──────────────────────────────────────────

    private static class TransitionData {
        int lockedEffectType;
        int lockedExtendMs;
        int[][] fromPixels, toPixels;
        Color[] fromColors, toColors;
        float[][] fromDirs, toDirs;
        float[] fromSpeeds, toSpeeds;
        float[][] fromGravDirs, toGravDirs;
        int[][] explodedPositions, extendedPositions;
        int[][] morphStables, morphBirths, morphBirthOrigins;
        int[][] morphDeaths, morphRecolorOld, morphRecolorNew;
        int[] morphBirthWave;
        int morphTotalWaves;
        float[] morphFocalPx;
        // Timing snapshot — tick/paint reads these, not live model
        int animSpeedMs, animHoldMs, animEasing, animSpread;
        int animExplodeSpeedMs, animUnsplodeSpeedMs, animPopHoldMs;
        int animTwistFirstSpeedMs, animTwistSecondSpeedMs;
        float animTwistFirstSmooth, animTwistSecondSmooth;
        boolean animTwistFullSpin, animTwistSpreadGap;
        int animTwistDirection;
        float animGravityPull, animGravityPush;
        float animExplodeStrength, animUnsplodeStrength;
        boolean animStayInCanvas;
        float animWallDamping;
        boolean animPopStayAtFocus;
        int animGravityFocalX, animGravityFocalY;
        boolean animMorphFadeDeaths;
        int animMorphSpeedMs, animMorphHoldMs;
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

            if (animating && active != null) {
                if (active.lockedEffectType == 1) {
                    paintPixelPop(g);
                } else if (active.lockedEffectType == 2) {
                    paintPixelTwist(g);
                } else if (active.lockedEffectType == 3) {
                    paintPixelMorph(g);
                } else {
                    float spread = active.animSpread;
                    int easing = active.animEasing;
                    if (animProgress < 0.5f) {
                        float t = animProgress / 0.5f;
                        paintBurstPixels(g, active.fromPixels, active.fromColors, active.fromDirs, spread * easingOut(t, easing));
                    } else {
                        float t = (animProgress - 0.5f) / 0.5f;
                        paintBurstPixels(g, active.toPixels, active.toColors, active.toDirs, spread * (1f - easingIn(t, easing)));
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

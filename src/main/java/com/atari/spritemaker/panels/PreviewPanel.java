package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.SpriteModel.Mode;
import com.atari.spritemaker.ui.RetroTheme;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
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
    private int[][] explodedPositions; // final explode positions for single-frame unsplode

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
        add(canvas, BorderLayout.CENTER);

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

        JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        zoomPanel.add(btnZoom1x);
        zoomPanel.add(btnZoom2x);
        zoomPanel.add(btnZoom4x);

        // Animation controls (TRANSFORM mode only)
        animControls = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        JButton btnPlay  = new JButton("Play");
        JButton btnPause = new JButton("Pause");
        JButton btnStep  = new JButton("Step");
        JButton btnReset = new JButton("Reset");
        animControls.add(btnPlay);
        animControls.add(btnPause);
        animControls.add(btnStep);
        animControls.add(btnReset);
        animControls.setVisible(false);

        JPanel southPanel = new JPanel();
        southPanel.setLayout(new BoxLayout(southPanel, BoxLayout.Y_AXIS));
        southPanel.add(zoomPanel);
        southPanel.add(animControls);
        add(southPanel, BorderLayout.SOUTH);

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
            startBurst();
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
        List<Color[][]> frames = model.getAnimationFrames();
        if (frames.isEmpty()) return;
        nextFrameIdx = frames.size() < 2 ? currentFrameIdx : (currentFrameIdx + 1) % frames.size();
        int gridSize = frames.get(currentFrameIdx).length;
        fromPixels = buildPixels(frames.get(currentFrameIdx), gridSize);
        fromColors = buildColors(fromPixels);
        fromDirs   = buildDirs(fromPixels, gridSize);
        toPixels   = buildPixels(frames.get(nextFrameIdx), gridSize);
        toColors   = buildColors(toPixels);
        toDirs     = buildDirs(toPixels, gridSize);
        if (model.getAnimEffectType() == 1) {
            float variance = model.getAnimGravityPush() / 100f;
            int gs = frames.get(currentFrameIdx).length;
            fromSpeeds   = buildSpeeds(fromPixels.length, variance);
            fromGravDirs = buildGravDirs(fromPixels, gs);
            toSpeeds     = buildSpeeds(toPixels.length, variance);
            toGravDirs   = buildGravDirs(toPixels, gs);

            if (nextFrameIdx == currentFrameIdx) {
                // Single frame: pre-compute where pixels land at t_phase=1.0 of explode
                // so unsplode can pick up exactly from those positions.
                int cellSize = CELL * zoomLevel;
                float spread = model.getAnimSpread();
                float gravStrength = model.getAnimGravityPull() / 100f * spread * 6f;
                boolean stay = model.isAnimStayInCanvas();
                int bound = gs * cellSize - cellSize;
                explodedPositions = new int[fromPixels.length][2];
                float explodeStrength = model.getAnimExplodeStrength() / 100f;
                for (int i = 0; i < fromPixels.length; i++) {
                    // easingOut(1.0)==1.0 for all styles; diminish at t=1.0 is 0.8; gravOffset=gravStrength*1^2
                    int px = (int)(fromPixels[i][0] + fromDirs[i][0] * spread * fromSpeeds[i] * explodeStrength * 0.8f + fromGravDirs[i][0] * gravStrength);
                    int py = (int)(fromPixels[i][1] + fromDirs[i][1] * spread * fromSpeeds[i] * explodeStrength * 0.8f + fromGravDirs[i][1] * gravStrength);
                    if (stay) {
                        px = Math.max(0, Math.min(px, bound));
                        py = Math.max(0, Math.min(py, bound));
                    }
                    explodedPositions[i][0] = px;
                    explodedPositions[i][1] = py;
                }
            } else {
                explodedPositions = null;
            }
        }
        animProgress = 0f;
        midHoldElapsedMs = 0;
        animating = true;
        burstTimer.start();
    }

    private void tickBurst() {
        float delta;
        if (model.getAnimEffectType() == 1) {
            // Hold particles at peak (animProgress==0.5) for the configured duration
            int peakHoldMs = model.getAnimPopHoldMs();
            if (animProgress >= 0.5f && midHoldElapsedMs < peakHoldMs) {
                animProgress = 0.5f;
                midHoldElapsedMs += 16;
                canvas.repaint();
                return;
            }
            // Each phase covers 0.5 of animProgress, driven by its own speed
            int phaseSpeed = animProgress < 0.5f
                ? model.getAnimExplodeSpeedMs()
                : model.getAnimUnsplodeSpeedMs();
            delta = 16f * 0.5f / phaseSpeed;
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
                pauseTimer.setDelay(Math.max(1, model.getAnimHoldMs()));
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
        int bound = model.getGridSize() * cellSize - cellSize; // max top-left for a pixel

        float explodeStrength = model.getAnimExplodeStrength() / 100f;
        float snapThreshold   = model.getAnimUnsplodeStrength() / 100f;
        float snapWindow      = 1f - snapThreshold;

        if (animProgress < 0.5f) {
            // Explode out: per-pixel random speeds + gravity pull toward focal
            // Energy diminishes slightly as t increases (pixels lose steam)
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
        } else {
            // Unsplode in: 95% fast approach at per-pixel speed, final 5% snap
            float t = (animProgress - 0.5f) / 0.5f;
            for (int i = 0; i < toPixels.length; i++) {
                float approach = Math.min(1f, t * toSpeeds[i]);
                // remainFrac: 1.0 = at start position, 0.0 = at home
                float remainFrac;
                if (approach < snapThreshold) {
                    remainFrac = 1f - approach;
                } else {
                    float snapT = (approach - snapThreshold) / snapWindow;
                    remainFrac = snapWindow * (1f - Math.min(1f, snapT * pullSpeed));
                }
                int px, py;
                if (explodedPositions != null) {
                    // Lerp from actual exploded position back to home
                    px = (int)(toPixels[i][0] + (explodedPositions[i][0] - toPixels[i][0]) * remainFrac);
                    py = (int)(toPixels[i][1] + (explodedPositions[i][1] - toPixels[i][1]) * remainFrac);
                } else {
                    px = (int)(toPixels[i][0] + toDirs[i][0] * spread * remainFrac);
                    py = (int)(toPixels[i][1] + toDirs[i][1] * spread * remainFrac);
                }
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
                if (model.getAnimEffectType() == 1) {
                    paintPixelPop(g);
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
                for (int row = 0; row < size; row++)
                    for (int col = 0; col < size; col++) {
                        Color c = frame[row][col];
                        if (c != null) {
                            g.setColor(c);
                            g.fillRect(col * cellSize, row * cellSize, cellSize, cellSize);
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
}

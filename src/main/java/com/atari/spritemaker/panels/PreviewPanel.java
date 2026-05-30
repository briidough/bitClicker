package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.SpriteModel.Mode;
import com.atari.spritemaker.ui.RetroTheme;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
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
    private boolean playing = false;
    private int currentFrameIdx = 0;
    private int nextFrameIdx = 0;

    // Per-transition pixel data
    private int[][] fromPixels;
    private float[][] fromDirs;
    private int[][] toPixels;
    private float[][] toDirs;

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

        btnZoom1x.addActionListener(e -> { zoomLevel = 1; canvas.repaint(); });
        btnZoom2x.addActionListener(e -> { zoomLevel = 2; canvas.repaint(); });
        btnZoom4x.addActionListener(e -> { zoomLevel = 4; canvas.repaint(); });

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

        canvas.repaint();
    }

    // ── animation logic ──────────────────────────────────────────────────────

    private void startBurst() {
        List<Color[][]> frames = model.getAnimationFrames();
        if (frames.isEmpty()) return;
        nextFrameIdx = frames.size() < 2 ? currentFrameIdx : (currentFrameIdx + 1) % frames.size();
        int gridSize = frames.get(currentFrameIdx).length;
        fromPixels = buildPixels(frames.get(currentFrameIdx), gridSize);
        fromDirs   = buildDirs(fromPixels, gridSize);
        toPixels   = buildPixels(frames.get(nextFrameIdx), gridSize);
        toDirs     = buildDirs(toPixels, gridSize);
        animProgress = 0f;
        animating = true;
        burstTimer.start();
    }

    private void tickBurst() {
        animProgress += 16f / model.getAnimSpeedMs();
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

    private void paintBurstPixels(Graphics g, int[][] pixels, float[][] dirs, float offset) {
        int cellSize = CELL * zoomLevel;
        for (int i = 0; i < pixels.length; i++) {
            g.setColor(new Color(pixels[i][2]));
            g.fillRect(
                (int)(pixels[i][0] + dirs[i][0] * offset),
                (int)(pixels[i][1] + dirs[i][1] * offset),
                cellSize, cellSize);
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
                float spread = model.getAnimSpread();
                int easing = model.getAnimEasing();
                if (animProgress < 0.5f) {
                    float t = animProgress / 0.5f;
                    paintBurstPixels(g, fromPixels, fromDirs, spread * easingOut(t, easing));
                } else {
                    float t = (animProgress - 0.5f) / 0.5f;
                    paintBurstPixels(g, toPixels, toDirs, spread * (1f - easingIn(t, easing)));
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
        }

        private void paintDraw(Graphics2D g, int cellSize) {
            int size = model.getGridSize();
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    Color c = model.getCellColor(row, col);
                    int x = col * cellSize, y = row * cellSize;
                    if (c == null) {
                        drawCheckerboard(g, x, y, cellSize);
                    } else {
                        g.setColor(c);
                        g.fillRect(x, y, cellSize, cellSize);
                    }
                }
            }
        }

        private void drawCheckerboard(Graphics g, int x, int y, int cellSize) {
            int half = cellSize / 2;
            if (half < 1) half = 1;
            g.setColor(RetroTheme.checkA());
            g.fillRect(x, y, half, half);
            g.fillRect(x + half, y + half, half, half);
            g.setColor(RetroTheme.checkB());
            g.fillRect(x + half, y, half, half);
            g.fillRect(x, y + half, half, half);
        }
    }
}

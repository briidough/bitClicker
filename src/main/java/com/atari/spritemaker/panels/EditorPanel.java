package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.SpriteModel.DrawingTool;
import com.atari.spritemaker.model.SpriteModel.Mode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

public class EditorPanel extends JPanel implements ChangeListener {

    private static final int CELL = 10;
    private final SpriteModel model;
    private final GridCanvas gridCanvas;
    private final JToggleButton eraserBtn;
    private final JLabel modeLabel;
    private final FrameTabBar frameTabBar;
    private final LoopBar loopBar;
    private Color lastKnownActiveColor;

    private static final int HIST_TICK_MS = 250;
    private static final int HIST_THRESHOLD_MS = 5000;
    private final javax.swing.Timer historyTimer;
    private long historyAccumMs = 0;
    private boolean drawing = false;
    private boolean dirty = false;

    public void resetHistoryTimer() { historyAccumMs = 0; dirty = false; }
    public boolean isDirty() { return dirty; }

    public EditorPanel(SpriteModel model) {
        this.model = model;
        this.lastKnownActiveColor = model.getActiveColor();
        setLayout(new BorderLayout(0, 4));

        historyTimer = new javax.swing.Timer(HIST_TICK_MS, e -> {
            if (!drawing) return;
            historyAccumMs += HIST_TICK_MS;
            if (historyAccumMs >= HIST_THRESHOLD_MS) {
                historyAccumMs = 0;
                if (dirty) {
                    dirty = false;
                    model.captureHistory();
                }
            }
        });
        historyTimer.start();

        gridCanvas = new GridCanvas();
        add(new JScrollPane(gridCanvas), BorderLayout.CENTER);

        eraserBtn = new JToggleButton("Eraser");
        modeLabel = new JLabel("— Draw —");
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.BOLD, 11f));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        titleRow.add(modeLabel);
        titleRow.add(eraserBtn);

        frameTabBar = new FrameTabBar();
        loopBar = new LoopBar();
        JPanel northContainer = new JPanel();
        northContainer.setLayout(new BoxLayout(northContainer, BoxLayout.Y_AXIS));
        northContainer.add(frameTabBar);
        northContainer.add(loopBar);
        northContainer.add(titleRow);
        add(northContainer, BorderLayout.NORTH);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        boolean isTransform = model.getMode() == Mode.TRANSFORM;
        modeLabel.setText(isTransform ? "— Transform —" : "— Draw —");
        eraserBtn.setVisible(!isTransform);
        Color currentColor = model.getActiveColor();
        if (currentColor != null && !currentColor.equals(lastKnownActiveColor)) {
            eraserBtn.setSelected(false);
        }
        lastKnownActiveColor = currentColor;
        if (model.getDrawingTool() == DrawingTool.PENCIL) gridCanvas.cancelLine();
        if (model.getDrawingTool() != DrawingTool.DRAG)   gridCanvas.clearDrag();
        gridCanvas.updateSize();
        gridCanvas.repaint();
        frameTabBar.refresh();
        loopBar.refresh();
    }

    private class GridCanvas extends JPanel {
        private static final BasicStroke STROKE_CROSS = new BasicStroke(2f);
        private int lineStartRow = -1, lineStartCol = -1;
        private int dragStartRow = -1, dragStartCol = -1;
        private Color[][] dragSnapshot = null;

        GridCanvas() {
            updateSize();
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e)  { handlePress(e); }
                @Override public void mouseDragged(MouseEvent e)  { handleDrag(e); }
                @Override public void mouseReleased(MouseEvent e) { drawing = false; clearDrag(); }

                private void handlePress(MouseEvent e) {
                    if (model.getMode() == Mode.TRANSFORM) return;
                    int col = e.getX() / CELL, row = e.getY() / CELL;
                    int size = model.getGridSize();
                    if (row < 0 || row >= size || col < 0 || col >= size) return;
                    drawing = true;
                    dirty = true;
                    model.pushUndoSnapshot();
                    if (model.getDrawingTool() == DrawingTool.DRAG) {
                        if (model.getCellColor(row, col) != null) {
                            dragStartRow = row;
                            dragStartCol = col;
                            dragSnapshot = model.getGridCopy();
                        }
                        return;
                    }
                    if (model.getDrawingTool() == DrawingTool.LINE) {
                        if (lineStartRow < 0) {
                            lineStartRow = row;
                            lineStartCol = col;
                            repaint();
                        } else {
                            drawLine(lineStartRow, lineStartCol, row, col);
                            lineStartRow = lineStartCol = -1;
                        }
                    } else {
                        paintCell(row, col);
                    }
                }

                private void handleDrag(MouseEvent e) {
                    if (model.getMode() == Mode.TRANSFORM) return;
                    drawing = true;
                    dirty = true;
                    if (model.getDrawingTool() == DrawingTool.DRAG) {
                        if (dragSnapshot == null) return;
                        int col = e.getX() / CELL, row = e.getY() / CELL;
                        applyDrag(row - dragStartRow, col - dragStartCol);
                        return;
                    }
                    if (model.getDrawingTool() != DrawingTool.PENCIL) return;
                    int col = e.getX() / CELL, row = e.getY() / CELL;
                    int size = model.getGridSize();
                    if (row < 0 || row >= size || col < 0 || col >= size) return;
                    paintCell(row, col);
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void cancelLine() { lineStartRow = lineStartCol = -1; }
        void clearDrag()  { dragStartRow = dragStartCol = -1; dragSnapshot = null; }

        private void applyDrag(int dr, int dc) {
            int size = model.getGridSize();
            int minR = size, maxR = -1, minC = size, maxC = -1;
            for (int r = 0; r < size; r++)
                for (int c = 0; c < size; c++)
                    if (dragSnapshot[r][c] != null) {
                        if (r < minR) minR = r;
                        if (r > maxR) maxR = r;
                        if (c < minC) minC = c;
                        if (c > maxC) maxC = c;
                    }
            if (maxR < 0) return;
            dr = Math.max(-minR, Math.min(dr, size - 1 - maxR));
            dc = Math.max(-minC, Math.min(dc, size - 1 - maxC));
            Color[][] shifted = new Color[size][size];
            for (int r = 0; r < size; r++)
                for (int c = 0; c < size; c++)
                    if (dragSnapshot[r][c] != null)
                        shifted[r + dr][c + dc] = dragSnapshot[r][c];
            model.setGrid(shifted);
        }

        private void paintCell(int row, int col) {
            if (eraserBtn.isSelected()) {
                model.setCellColor(row, col, null);
            } else if (model.getActiveColor() != null) {
                model.setCellColor(row, col, model.getActiveColor());
            }
        }

        private void drawLine(int r1, int c1, int r2, int c2) {
            int dx = Math.abs(c2 - c1), dy = Math.abs(r2 - r1);
            int sc = c1 < c2 ? 1 : -1, sr = r1 < r2 ? 1 : -1;
            int err = dx - dy;
            int size = model.getGridSize();
            while (true) {
                if (r1 >= 0 && r1 < size && c1 >= 0 && c1 < size) paintCell(r1, c1);
                if (r1 == r2 && c1 == c2) break;
                int e2 = 2 * err;
                if (e2 > -dy) { err -= dy; c1 += sc; }
                if (e2 < dx)  { err += dx; r1 += sr; }
            }
        }

        void updateSize() {
            int px = model.getGridSize() * CELL;
            setPreferredSize(new Dimension(px, px));
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int size = model.getGridSize();
            int totalPx = size * CELL;

            java.awt.image.BufferedImage bg = (model.getMode() == Mode.DRAW && model.isShowBgImage())
                    ? model.getBgImage() : null;
            if (bg != null) {
                g2.drawImage(bg, 0, 0, totalPx, totalPx, null);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            }

            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    Color c = model.getCellColor(row, col);
                    int x = col * CELL, y = row * CELL;
                    if (c == null) {
                        drawCheckerboard(g2, x, y);
                    } else {
                        g2.setColor(c);
                        g2.fillRect(x, y, CELL, CELL);
                    }
                    g2.setColor(Color.LIGHT_GRAY);
                    g2.drawRect(x, y, CELL, CELL);
                }
            }

            if (bg != null) g2.setComposite(AlphaComposite.SrcOver);

            if (lineStartRow >= 0) {
                int x = lineStartCol * CELL, y = lineStartRow * CELL;
                g2.setColor(Color.WHITE);
                g2.drawRect(x, y, CELL - 1, CELL - 1);
                g2.drawRect(x + 1, y + 1, CELL - 3, CELL - 3);
            }

            if (model.getMode() == Mode.TRANSFORM) {
                drawFocalCross(g2, size);
            }
        }

        private void drawFocalCross(Graphics2D g, int gridSize) {
            int arm = model.isFocalActive() ? CELL * 2 : CELL;
            int cx = Math.round(gridSize * CELL * model.getAnimFocalX() / 100f);
            int cy = Math.round(gridSize * CELL * model.getAnimFocalY() / 100f);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(STROKE_CROSS);
            g2.setColor(Color.BLACK);
            g2.drawLine(cx - arm, cy + 1, cx + arm, cy + 1);
            g2.drawLine(cx + 1, cy - arm, cx + 1, cy + arm);
            g2.setColor(Color.WHITE);
            g2.drawLine(cx - arm, cy, cx + arm, cy);
            g2.drawLine(cx, cy - arm, cx, cy + arm);
            g2.dispose();
        }

        private void drawCheckerboard(Graphics g, int x, int y) {
            int half = CELL / 2;
            g.setColor(new Color(0xcccccc));
            g.fillRect(x, y, half, half);
            g.fillRect(x + half, y + half, half, half);
            g.setColor(Color.WHITE);
            g.fillRect(x + half, y, half, half);
            g.fillRect(x, y + half, half, half);
        }
    }

    private class UftTabButton extends JPanel {
        private static final Color ENABLED_COLOR  = new Color(60, 100, 180);
        private static final Color SELECTED_COLOR = new Color(210, 220, 255);

        private boolean selected = false;
        private boolean enabled  = false;
        private final int idx;

        UftTabButton(int idx) {
            this.idx = idx;
            setPreferredSize(new Dimension(28, 22));
            setOpaque(true);
            setBorder(BorderFactory.createLineBorder(Color.GRAY));
            setToolTipText("UFT " + (idx + 1) + "→" + (idx + 2));

            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    int halfH = getHeight() / 2;
                    if (e.getY() < halfH) {
                        int cur = model.getSelectedUFTIndex();
                        if (cur == idx) model.setSelectedUFT(-1);
                        else            model.setSelectedUFT(idx);
                    } else {
                        model.toggleUFT(idx);
                    }
                }
            });
        }

        void setUFTSelected(boolean sel) { selected = sel; repaint(); }
        void setUFTEnabled(boolean en)   { enabled  = en;  repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Color defBg = UIManager.getColor("Button.background");
            if (defBg == null) defBg = new Color(238, 238, 238);
            int w = getWidth(), h = getHeight(), half = h / 2;
            g.setColor(selected ? SELECTED_COLOR : defBg);
            g.fillRect(0, 0, w, half);
            g.setColor(enabled ? ENABLED_COLOR : defBg);
            g.fillRect(0, half, w, h - half);
            g.setColor(Color.GRAY);
            g.drawLine(0, half, w, half);
            g.setColor(Color.DARK_GRAY);
            FontMetrics fm = g.getFontMetrics();
            String txt = "→";
            int tx = (w - fm.stringWidth(txt)) / 2;
            int ty = half / 2 + fm.getAscent() / 2;
            g.drawString(txt, tx, ty);
        }
    }

    // Frame-loop authoring: toggle a two-frame ping-pong loop, move the pair with arrows.
    private class LoopBar extends JPanel {
        private final JToggleButton toggle;
        private final JButton prev;
        private final JButton next;
        private final JLabel label;

        LoopBar() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 4, 0));

            toggle = new JToggleButton("↻");
            toggle.setMargin(new Insets(1, 4, 1, 4));
            toggle.setToolTipText("Loop a two-frame pair (needs 3+ frames)");
            toggle.addActionListener(e -> {
                int n = model.getFrameCount();
                if (n < 3) { loopBar.refresh(); return; }
                if (toggle.isSelected()) {
                    int a = model.isLoopEnabled() ? model.getLoopStart() : n - 2;
                    if (a < 0 || a > n - 2) a = n - 2;
                    model.setLoop(true, a, a + 1);
                } else {
                    model.clearLoop();
                }
            });

            prev = new JButton("◀");
            prev.setMargin(new Insets(1, 4, 1, 4));
            prev.setToolTipText("Move loop pair left");
            prev.addActionListener(e -> shift(-1));

            next = new JButton("▶");
            next.setMargin(new Insets(1, 4, 1, 4));
            next.setToolTipText("Move loop pair right");
            next.addActionListener(e -> shift(1));

            label = new JLabel();
            label.setFont(label.getFont().deriveFont(11f));

            add(toggle);
            add(prev);
            add(next);
            add(label);
            refresh();
        }

        private void shift(int d) {
            if (!model.isLoopEnabled()) return;
            int n = model.getFrameCount();
            int a = model.getLoopStart() + d;
            a = Math.max(0, Math.min(a, n - 2));
            if (a != model.getLoopStart()) model.setLoop(true, a, a + 1);
        }

        void refresh() {
            int n = model.getFrameCount();
            boolean can = n >= 3;
            boolean on = model.isLoopEnabled();
            toggle.setEnabled(can);
            toggle.setSelected(on);
            int a = model.getLoopStart();
            prev.setEnabled(on && can && a > 0);
            next.setEnabled(on && can && a < n - 2);
            if (on && can) {
                label.setText("Loop F" + (a + 1) + "–F" + (a + 2));
            } else if (can) {
                label.setText("Loop off");
            } else {
                label.setText("Loop needs 3+ frames");
            }
            revalidate();
            repaint();
        }
    }

    private class FrameTabBar extends JPanel {
        private static final int MAX_FRAMES = 6;

        private final List<JToggleButton> tabBtns = new ArrayList<>();
        private final List<UftTabButton>  uftBtns = new ArrayList<>();
        private final ButtonGroup tabGroup = new ButtonGroup();
        private final JToggleButton allBtn;
        private final JButton addBtn;
        private final JButton delBtn;

        FrameTabBar() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 2, 2));

            allBtn = new JToggleButton("►");
            allBtn.setPreferredSize(new Dimension(32, 22));
            allBtn.setMargin(new Insets(1, 2, 1, 2));
            allBtn.setToolTipText("Full animation");
            allBtn.addActionListener(e -> { model.setSelectedUFT(-1); model.setFullAnimationMode(true); });
            tabGroup.add(allBtn);

            for (int i = 0; i < MAX_FRAMES; i++) {
                final int idx = i;
                JToggleButton btn = new JToggleButton(String.valueOf(i + 1));
                btn.setPreferredSize(new Dimension(32, 22));
                btn.setMargin(new Insets(1, 2, 1, 2));
                btn.addActionListener(e -> {
                    model.setSelectedUFT(-1);
                    model.setFullAnimationMode(false);
                    model.switchToFrame(idx);
                });
                tabGroup.add(btn);
                tabBtns.add(btn);

                uftBtns.add(new UftTabButton(i));
            }

            addBtn = new JButton("+");
            addBtn.setPreferredSize(new Dimension(28, 22));
            addBtn.setMargin(new Insets(1, 2, 1, 2));
            addBtn.addActionListener(e -> model.addFrame());

            delBtn = new JButton("✕");
            delBtn.setPreferredSize(new Dimension(28, 22));
            delBtn.setMargin(new Insets(1, 2, 1, 2));
            delBtn.setForeground(Color.RED);
            delBtn.setToolTipText("Delete current frame");
            delBtn.addActionListener(e -> {
                int idx = model.getCurrentFrameIndex();
                if (confirmDeleteFrame(idx + 1)) model.deleteFrame(idx);
            });

            refresh();
        }

        void refresh() {
            removeAll();
            boolean isTransform = model.getMode() == Mode.TRANSFORM;
            int N = model.getFrameCount();
            int selectedUFT = model.getSelectedUFTIndex();
            boolean fullAnim = model.isFullAnimationMode();
            int currentFrame = model.getCurrentFrameIndex();

            if (isTransform) add(allBtn);

            for (int i = 0; i < N; i++) {
                add(tabBtns.get(i));
                if (isTransform) {
                    UftTabButton uft = uftBtns.get(i);
                    uft.setUFTSelected(i == selectedUFT);
                    uft.setUFTEnabled(model.isUFTEnabled(i));
                    int next = (i + 1) % N;
                    uft.setToolTipText("UFT " + (i + 1) + "→" + (next + 1));
                    add(uft);
                }
            }

            if (!isTransform) {
                if (N < MAX_FRAMES) add(addBtn);
            }
            if (N > 1) add(delBtn);

            // Sync ButtonGroup selection
            if (isTransform && selectedUFT >= 0) {
                tabGroup.clearSelection();
            } else if (isTransform && fullAnim) {
                tabGroup.setSelected(allBtn.getModel(), true);
            } else {
                int sel = Math.min(currentFrame, N - 1);
                tabGroup.setSelected(tabBtns.get(sel).getModel(), true);
            }

            revalidate();
            repaint();
        }

        private boolean confirmDeleteFrame(int frameNumber) {
            Window owner = SwingUtilities.getWindowAncestor(this);
            JDialog dialog = new JDialog(owner, "Delete Frame?", Dialog.ModalityType.APPLICATION_MODAL);
            final boolean[] result = {false};

            JLabel msg = new JLabel("Delete Frame " + frameNumber + "?", SwingConstants.CENTER);
            msg.setBorder(BorderFactory.createEmptyBorder(18, 24, 12, 24));

            JButton yes = new JButton("Yes.");
            yes.setBackground(new Color(60, 160, 60));
            yes.setForeground(Color.WHITE);
            yes.setOpaque(true);
            yes.setBorderPainted(false);
            yes.addActionListener(e -> { result[0] = true; dialog.dispose(); });

            JButton no = new JButton("No!");
            no.setBackground(new Color(190, 50, 50));
            no.setForeground(Color.WHITE);
            no.setOpaque(true);
            no.setBorderPainted(false);
            no.addActionListener(e -> { result[0] = false; dialog.dispose(); });

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 12));
            buttons.add(yes);
            buttons.add(no);

            dialog.setLayout(new BorderLayout());
            dialog.add(msg, BorderLayout.CENTER);
            dialog.add(buttons, BorderLayout.SOUTH);
            dialog.pack();
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
            return result[0];
        }
    }
}

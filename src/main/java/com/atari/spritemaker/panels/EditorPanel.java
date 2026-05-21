package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.SpriteModel.DrawingTool;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

public class EditorPanel extends JPanel implements ChangeListener {

    private static final int CELL = 10;
    private final SpriteModel model;
    private final GridCanvas gridCanvas;
    private final PaletteBar paletteBar;
    private final JButton pickColorBtn;
    private final JToggleButton eraserBtn;

    public EditorPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BorderLayout(0, 4));

        gridCanvas = new GridCanvas();
        add(new JScrollPane(gridCanvas), BorderLayout.CENTER);

        paletteBar = new PaletteBar();
        pickColorBtn = new JButton("Pick Color");
        eraserBtn = new JToggleButton("Eraser");

        pickColorBtn.setEnabled(false);
        pickColorBtn.addActionListener(e -> {
            int slot = model.getSelectedPaletteSlot();
            if (slot < 0) return;
            Color init = model.getPalette()[slot];
            Color chosen = JColorChooser.showDialog(this, "Pick Color", init);
            if (chosen != null) { model.setPaletteSlotColor(slot, chosen); eraserBtn.setSelected(false); }
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.add(paletteBar);
        controls.add(pickColorBtn);
        controls.add(eraserBtn);
        add(controls, BorderLayout.NORTH);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (model.getDrawingTool() == DrawingTool.PENCIL) gridCanvas.cancelLine();
        pickColorBtn.setEnabled(model.getSelectedPaletteSlot() >= 0);
        paletteBar.repaint();
        gridCanvas.updateSize();
        gridCanvas.repaint();
    }

    private class GridCanvas extends JPanel {
        private int lineStartRow = -1, lineStartCol = -1;

        GridCanvas() {
            updateSize();
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { handlePress(e); }
                @Override public void mouseDragged(MouseEvent e) { handleDrag(e); }

                private void handlePress(MouseEvent e) {
                    int col = e.getX() / CELL, row = e.getY() / CELL;
                    int size = model.getGridSize();
                    if (row < 0 || row >= size || col < 0 || col >= size) return;
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

            java.awt.image.BufferedImage bg = model.isShowBgImage() ? model.getBgImage() : null;
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
        }

        private void drawCheckerboard(Graphics g, int x, int y) {
            int half = CELL / 2;
            g.setColor(Color.decode("#cccccc"));
            g.fillRect(x, y, half, half);
            g.fillRect(x + half, y + half, half, half);
            g.setColor(Color.WHITE);
            g.fillRect(x + half, y, half, half);
            g.fillRect(x, y + half, half, half);
        }
    }

    private class PaletteBar extends JPanel {
        private static final int SW = 56, SH = 32;

        PaletteBar() {
            setPreferredSize(new Dimension(SW * 5, SH));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int slot = e.getX() / SW;
                    if (slot >= 0 && slot < 5) {
                        model.selectPaletteSlot(slot);
                        if (model.getActiveColor() != null) eraserBtn.setSelected(false);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Color[] pal = model.getPalette();
            int selected = model.getSelectedPaletteSlot();
            for (int i = 0; i < 5; i++) {
                int x = i * SW;
                if (pal[i] == null) {
                    drawSlotCheckerboard(g, x);
                } else {
                    g.setColor(pal[i]);
                    g.fillRect(x, 0, SW, SH);
                }
                g.setColor(i == selected ? Color.WHITE : Color.DARK_GRAY);
                g.drawRect(x, 0, SW - 1, SH - 1);
                if (i == selected) g.drawRect(x + 1, 1, SW - 3, SH - 3);
            }
        }

        private void drawSlotCheckerboard(Graphics g, int x) {
            int sq = 8;
            for (int dy = 0; dy < SH; dy += sq) {
                for (int dx = 0; dx < SW; dx += sq) {
                    g.setColor(((dx / sq + dy / sq) % 2 == 0)
                        ? Color.decode("#cccccc") : Color.WHITE);
                    g.fillRect(x + dx, dy, Math.min(sq, SW - dx), Math.min(sq, SH - dy));
                }
            }
        }
    }
}

package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
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
            if (chosen != null) model.setPaletteSlotColor(slot, chosen);
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.add(paletteBar);
        controls.add(pickColorBtn);
        controls.add(eraserBtn);
        add(controls, BorderLayout.NORTH);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        pickColorBtn.setEnabled(model.getSelectedPaletteSlot() >= 0);
        paletteBar.repaint();
        gridCanvas.updateSize();
        gridCanvas.repaint();
    }

    private class GridCanvas extends JPanel {
        GridCanvas() {
            updateSize();
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { paint(e); }
                @Override public void mouseDragged(MouseEvent e) { paint(e); }

                private void paint(MouseEvent e) {
                    int col = e.getX() / CELL;
                    int row = e.getY() / CELL;
                    int size = model.getGridSize();
                    if (row < 0 || row >= size || col < 0 || col >= size) return;
                    if (eraserBtn.isSelected()) {
                        model.setCellColor(row, col, null);
                    } else if (model.getActiveColor() != null) {
                        model.setCellColor(row, col, model.getActiveColor());
                    }
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void updateSize() {
            int px = model.getGridSize() * CELL;
            setPreferredSize(new Dimension(px, px));
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int size = model.getGridSize();
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    Color c = model.getCellColor(row, col);
                    int x = col * CELL, y = row * CELL;
                    if (c == null) {
                        drawCheckerboard(g, x, y);
                    } else {
                        g.setColor(c);
                        g.fillRect(x, y, CELL, CELL);
                    }
                    g.setColor(Color.LIGHT_GRAY);
                    g.drawRect(x, y, CELL, CELL);
                }
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
            setPreferredSize(new Dimension(SW * 4, SH));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int slot = e.getX() / SW;
                    if (slot >= 0 && slot < 4) model.selectPaletteSlot(slot);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Color[] pal = model.getPalette();
            int selected = model.getSelectedPaletteSlot();
            for (int i = 0; i < 4; i++) {
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

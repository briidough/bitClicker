package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;

public class PreviewPanel extends JPanel implements ChangeListener {

    private static final int CELL = 4;
    private final SpriteModel model;
    private final Canvas canvas;

    public PreviewPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BorderLayout());
        canvas = new Canvas();
        add(new JScrollPane(canvas), BorderLayout.CENTER);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        int px = model.getGridSize() * CELL;
        canvas.setPreferredSize(new Dimension(px, px));
        canvas.revalidate();
        canvas.repaint();
    }

    private class Canvas extends JPanel {
        Canvas() {
            int px = model.getGridSize() * CELL;
            setPreferredSize(new Dimension(px, px));
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
}

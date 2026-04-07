package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

/**
 * Read-only panel that mirrors the 16×16 grid, displaying each cell's
 * background color alongside its 6-digit hex value.
 */
public class HexMirrorPanel extends JPanel implements ChangeListener {

    private static final int CELL_W = 62;
    private static final int CELL_H = 28;
    private static final int OFFSET_X = 8;
    private static final int OFFSET_Y = 24;

    private final SpriteModel model;

    public HexMirrorPanel(SpriteModel model) {
        this.model = model;
        setBorder(BorderFactory.createTitledBorder("Hex Values"));
        int w = OFFSET_X * 2 + CELL_W * SpriteModel.GRID_SIZE;
        int h = OFFSET_Y + CELL_H * SpriteModel.GRID_SIZE + 8;
        setPreferredSize(new Dimension(w, h));
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 9);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        for (int row = 0; row < SpriteModel.GRID_SIZE; row++) {
            for (int col = 0; col < SpriteModel.GRID_SIZE; col++) {
                int x = OFFSET_X + col * CELL_W;
                int y = OFFSET_Y + row * CELL_H;

                Color cell = model.getCellColor(row, col);

                // Background
                g2.setColor(cell);
                g2.fillRect(x, y, CELL_W - 1, CELL_H - 1);

                // Border
                g2.setColor(Color.GRAY);
                g2.drawRect(x, y, CELL_W - 1, CELL_H - 1);

                // Hex text — use contrasting colour for readability
                String hex = String.format("%06X", cell.getRGB() & 0xFFFFFF);
                double lum = 0.299 * cell.getRed()
                           + 0.587 * cell.getGreen()
                           + 0.114 * cell.getBlue();
                g2.setColor(lum > 128 ? Color.BLACK : Color.WHITE);

                int tx = x + (CELL_W - fm.stringWidth(hex)) / 2;
                int ty = y + (CELL_H + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(hex, tx, ty);
            }
        }
    }
}

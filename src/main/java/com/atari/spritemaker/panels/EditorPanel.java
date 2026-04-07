package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.AtariPalette;
import com.atari.spritemaker.model.SpriteModel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;

/**
 * Centre panel containing:
 *   - 16×16 painting grid
 *   - 3-slot current palette
 *   - Region dropdown (NTSC / PAL / SECAM)
 *   - Scrollable region colour picker
 */
public class EditorPanel extends JPanel implements ChangeListener {

    private static final int CELL_SIZE = 30;

    private final SpriteModel model;
    private final GridCanvas gridCanvas;
    private final PaletteBar paletteBar;
    private final ColorPickerPanel colorPicker;
    private final JComboBox<AtariPalette.Region> regionCombo;

    // Prevents the combo's ActionListener from bouncing a model change back
    private boolean updatingFromModel = false;

    public EditorPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder("Sprite Editor"));

        // --- 16×16 grid -------------------------------------------------
        gridCanvas = new GridCanvas();
        gridCanvas.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(gridCanvas);
        add(Box.createVerticalStrut(8));

        // --- Current palette (3 slots) ----------------------------------
        paletteBar = new PaletteBar();
        paletteBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(paletteBar);
        add(Box.createVerticalStrut(6));

        // --- Colour picker — created before the combo listener so the
        //     lambda can safely reference the final field
        colorPicker = new ColorPickerPanel();

        // --- Region dropdown --------------------------------------------
        JPanel regionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        regionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        regionRow.add(new JLabel("Region:"));
        regionCombo = new JComboBox<>(AtariPalette.Region.values());
        regionCombo.addActionListener(e -> {
            if (!updatingFromModel) {
                model.setRegion((AtariPalette.Region) regionCombo.getSelectedItem());
                colorPicker.refresh();
            }
        });
        regionRow.add(regionCombo);
        add(regionRow);
        add(Box.createVerticalStrut(4));

        // --- Colour picker scroll pane ----------------------------------
        colorPicker.setAlignmentX(Component.LEFT_ALIGNMENT);
        JScrollPane pickerScroll = new JScrollPane(colorPicker,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pickerScroll.setPreferredSize(new Dimension(CELL_SIZE * 16, 130));
        pickerScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(pickerScroll);
        add(Box.createVerticalStrut(4));
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        // Sync combo without triggering its listener
        updatingFromModel = true;
        regionCombo.setSelectedItem(model.getRegion());
        updatingFromModel = false;

        colorPicker.refresh();
        paletteBar.refresh();
        gridCanvas.repaint();
    }

    // =========================================================================
    // Inner: 16×16 painting canvas
    // =========================================================================
    private class GridCanvas extends JPanel {

        GridCanvas() {
            setPreferredSize(new Dimension(CELL_SIZE * 16, CELL_SIZE * 16));

            MouseAdapter painter = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e)  { paint(e); }
                @Override public void mouseDragged(MouseEvent e)  { paint(e); }

                private void paint(MouseEvent e) {
                    int col = e.getX() / CELL_SIZE;
                    int row = e.getY() / CELL_SIZE;
                    if (row < 0 || row >= 16 || col < 0 || col >= 16) return;
                    Color active = model.getActiveColor();
                    if (!active.equals(model.getCellColor(row, col))) {
                        model.setCellColor(row, col, active);
                    }
                }
            };
            addMouseListener(painter);
            addMouseMotionListener(painter);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            float[] dash = {3f, 3f};
            Stroke dashed = new BasicStroke(1f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 1f, dash, 0f);

            for (int row = 0; row < 16; row++) {
                for (int col = 0; col < 16; col++) {
                    int x = col * CELL_SIZE;
                    int y = row * CELL_SIZE;

                    // Fill
                    g2.setColor(model.getCellColor(row, col));
                    g2.fillRect(x, y, CELL_SIZE, CELL_SIZE);

                    // Dotted border
                    g2.setColor(new Color(90, 90, 90));
                    g2.setStroke(dashed);
                    g2.drawRect(x, y, CELL_SIZE - 1, CELL_SIZE - 1);
                    g2.setStroke(new BasicStroke());
                }
            }
        }
    }

    // =========================================================================
    // Inner: 3-slot current palette bar
    // =========================================================================
    private class PaletteBar extends JPanel {

        private static final int SLOT_W = 56;
        private static final int SLOT_H = 32;
        private final JPanel[] slots = new JPanel[3];

        PaletteBar() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
            add(new JLabel("Current Palette:"));

            for (int i = 0; i < 3; i++) {
                final int idx = i;
                JPanel slot = new JPanel();
                slot.setPreferredSize(new Dimension(SLOT_W, SLOT_H));
                slot.setBackground(Color.BLACK);
                slot.setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
                slot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                slot.setToolTipText("Palette slot " + (i + 1));
                slot.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        model.selectPaletteSlot(idx);
                    }
                });
                slots[i] = slot;
                add(slot);
            }
        }

        void refresh() {
            Color[] pal = model.getPalette();
            int sel = model.getSelectedPaletteSlot();
            for (int i = 0; i < 3; i++) {
                slots[i].setBackground(pal[i]);
                slots[i].setBorder(i == sel
                        ? BorderFactory.createLineBorder(Color.WHITE, 3)
                        : BorderFactory.createLineBorder(Color.GRAY, 2));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            refresh();
        }
    }

    // =========================================================================
    // Inner: scrollable region colour picker
    // =========================================================================
    private class ColorPickerPanel extends JPanel {

        private static final int SWATCH = 20;
        private static final int COLS   = 16;

        ColorPickerPanel() {
            refresh();
        }

        void refresh() {
            removeAll();
            Color[] colors = AtariPalette.getColors(model.getRegion());
            int rows = (colors.length + COLS - 1) / COLS;
            setLayout(new GridLayout(rows, COLS, 1, 1));
            setPreferredSize(new Dimension(COLS * (SWATCH + 1), rows * (SWATCH + 1)));

            for (Color color : colors) {
                JPanel swatch = new JPanel();
                swatch.setBackground(color);
                swatch.setPreferredSize(new Dimension(SWATCH, SWATCH));
                swatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
                swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                String hex = String.format("#%06X", color.getRGB() & 0xFFFFFF);
                swatch.setToolTipText(hex);

                swatch.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        int slot = model.getSelectedPaletteSlot();
                        if (slot >= 0) {
                            model.setPaletteSlotColor(slot, color);
                        }
                    }

                    @Override
                    public void mouseEntered(MouseEvent e) {
                        swatch.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        swatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
                    }
                });
                add(swatch);
            }
            revalidate();
            repaint();
        }
    }
}

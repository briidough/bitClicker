package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

public class ActionEditsPanel extends JPanel implements ChangeListener {

    private static final int FIXED_WIDTH = 210;

    // Palette presets [row 0=highlight, row 1=main, row 2=lowlight][col 0..3]
    private static final Color[][] DAY_PRESET = {
        { new Color(0xFFB3B3), new Color(0xFFFAB3), new Color(0xB3FFB3), new Color(0xB3D9FF) },
        { new Color(0xFF3333), new Color(0xFFD700), new Color(0x33CC33), new Color(0x3399FF) },
        { new Color(0x991F1F), new Color(0x997F00), new Color(0x1A7A1A), new Color(0x1A5C99) }
    };
    private static final Color[][] DAWN_PRESET = {
        { new Color(0xFFCCE0), new Color(0xFFD9B3), new Color(0xFFEFB3), new Color(0xC4E0FF) },
        { new Color(0xFF69B4), new Color(0xFF8C42), new Color(0xFFD166), new Color(0x87CEEB) },
        { new Color(0x993D6B), new Color(0x994F25), new Color(0x997A38), new Color(0x4A8FA8) }
    };
    private static final Color[][] DUSK_PRESET = {
        { new Color(0xE0B3FF), new Color(0xFFB3E0), new Color(0xB3CCFF), new Color(0xB3FFE8) },
        { new Color(0x9933FF), new Color(0xFF33AA), new Color(0x3366FF), new Color(0x33FFAA) },
        { new Color(0x5C1F99), new Color(0x991F65), new Color(0x1F3D99), new Color(0x1F9960) }
    };

    private final SpriteModel model;
    private final CardLayout cardLayout;
    private final JPanel cardPanel;

    // Draw card
    private final ColorSwatchPanel[][] swatches = new ColorSwatchPanel[3][4];
    private final JComboBox<String> paletteCombo;
    private final Color[][] customColors;

    // Transform card
    private final CardLayout transformCardLayout;
    private final JPanel transformCardPanel;

    private final PixelBurstPanel  burstPanel;
    private final PixelPopPanel    popPanel;
    private final PixelTwistPanel  twistPanel;
    private final PixelMorphPanel  morphPanel;
    private final PixelSpringPanel springPanel;

    public ActionEditsPanel(SpriteModel model,
                             PixelBurstPanel  burstPanel,
                             PixelPopPanel    popPanel,
                             PixelTwistPanel  twistPanel,
                             PixelMorphPanel  morphPanel,
                             PixelSpringPanel springPanel) {
        this.burstPanel  = burstPanel;
        this.popPanel    = popPanel;
        this.twistPanel  = twistPanel;
        this.morphPanel  = morphPanel;
        this.springPanel = springPanel;
        this.model = model;
        setLayout(new BorderLayout());
        Color sepColor = UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, sepColor != null ? sepColor : Color.GRAY));

        // Initialize custom colours (white/grey/black defaults)
        customColors = new Color[3][4];
        for (int c = 0; c < 4; c++) customColors[0][c] = Color.WHITE;
        for (int c = 0; c < 4; c++) customColors[1][c] = Color.GRAY;
        for (int c = 0; c < 4; c++) customColors[2][c] = Color.BLACK;

        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);

        // ── Draw card ────────────────────────────────────────────────────────
        JPanel drawCard = new JPanel();
        drawCard.setLayout(new BoxLayout(drawCard, BoxLayout.Y_AXIS));
        drawCard.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        String[] rowLabels = { "Highlight", "Main", "Lowlight" };
        for (int row = 0; row < 3; row++) {
            JLabel rowLabel = new JLabel(rowLabels[row]);
            rowLabel.setFont(rowLabel.getFont().deriveFont(Font.BOLD, 10f));
            rowLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            drawCard.add(rowLabel);
            drawCard.add(Box.createVerticalStrut(2));

            JPanel rowPanel = new JPanel(new GridLayout(1, 4, 3, 0));
            rowPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            for (int col = 0; col < 4; col++) {
                final int r = row, c = col;
                swatches[row][col] = new ColorSwatchPanel(DAY_PRESET[row][col]);
                swatches[row][col].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        String sel = (String) paletteCombo.getSelectedItem();
                        if ("Custom".equals(sel)) {
                            Color chosen = JColorChooser.showDialog(
                                ActionEditsPanel.this, "Pick Color", swatches[r][c].color);
                            if (chosen != null) {
                                customColors[r][c] = chosen;
                                swatches[r][c].setColor(chosen);
                            }
                        }
                        model.setActiveColor(swatches[r][c].color);
                    }
                });
                rowPanel.add(swatches[row][col]);
            }
            rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowPanel.getPreferredSize().height));
            drawCard.add(rowPanel);
            drawCard.add(Box.createVerticalStrut(8));
        }

        JLabel paletteLabel = new JLabel("Palette:");
        paletteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        drawCard.add(paletteLabel);
        drawCard.add(Box.createVerticalStrut(3));

        paletteCombo = new JComboBox<>(new String[]{ "Day", "Dawn", "Dusk", "Custom" });
        paletteCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        paletteCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, paletteCombo.getPreferredSize().height));
        paletteCombo.addActionListener(e -> loadPreset((String) paletteCombo.getSelectedItem()));
        drawCard.add(paletteCombo);

        cardPanel.add(drawCard, "draw");

        // ── Transform card ───────────────────────────────────────────────────
        transformCardLayout = new CardLayout();
        transformCardPanel  = new JPanel(transformCardLayout);
        transformCardPanel.add(new JScrollPane(burstPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "burst");
        transformCardPanel.add(new JScrollPane(popPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "pop");
        transformCardPanel.add(new JScrollPane(twistPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "twist");
        transformCardPanel.add(new JScrollPane(morphPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "morph");
        transformCardPanel.add(new JScrollPane(springPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "spring");

        cardPanel.add(transformCardPanel, "transform");
        add(cardPanel, BorderLayout.CENTER);

        loadPreset("Day");
    }

    private void loadPreset(String name) {
        Color[][] preset;
        switch (name) {
            case "Dawn":   preset = DAWN_PRESET;   break;
            case "Dusk":   preset = DUSK_PRESET;   break;
            case "Custom": preset = customColors;  break;
            default:       preset = DAY_PRESET;    break;
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 4; col++)
                swatches[row][col].setColor(preset[row][col]);
    }

    public void showDrawMode() {
        cardLayout.show(cardPanel, "draw");
        setVisible(true);
        revalidateParent();
    }

    public void showTransformMode(String key) {
        cardLayout.show(cardPanel, "transform");
        transformCardLayout.show(transformCardPanel, key);
        setVisible(true);
        revalidateParent();
    }

    public void syncTransformCard() {
        int t = model.getAnimEffectType();
        String key = t == 1 ? "pop" : t == 2 ? "twist" : t == 3 ? "morph" : t == 4 ? "spring" : "burst";
        transformCardLayout.show(transformCardPanel, key);
    }

    private void revalidateParent() {
        Container p = getParent();
        if (p != null) { p.revalidate(); p.repaint(); }
    }

    public void refreshTransformUI() {
        burstPanel.refresh();
        popPanel.refresh();
        twistPanel.refresh();
        morphPanel.refresh();
        springPanel.refresh();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (!isVisible()) return;
        for (ColorSwatchPanel[] row : swatches)
            for (ColorSwatchPanel swatch : row)
                swatch.repaint();
        refreshTransformUI();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(FIXED_WIDTH, super.getPreferredSize().height); }
    @Override public Dimension getMaximumSize()   { return new Dimension(FIXED_WIDTH, Integer.MAX_VALUE); }
    @Override public Dimension getMinimumSize()   { return new Dimension(FIXED_WIDTH, 0); }

    // ── Inner: single colour swatch ──────────────────────────────────────────

    private class ColorSwatchPanel extends JPanel {
        Color color;

        ColorSwatchPanel(Color c) {
            this.color = c;
            setPreferredSize(new Dimension(42, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        void setColor(Color c) { this.color = c; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(color);
            g.fillRect(0, 0, getWidth(), getHeight());
            boolean active = color != null && color.equals(model.getActiveColor());
            if (active) {
                g.setColor(Color.WHITE);
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                g.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
            } else {
                g.setColor(Color.DARK_GRAY);
                g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
            }
        }
    }
}

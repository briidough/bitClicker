package com.atari.spritemaker.ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;

public class RetroButtonUI extends BasicButtonUI {

    public static ComponentUI createUI(JComponent c) {
        return new RetroButtonUI();
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        AbstractButton b = (AbstractButton) c;
        b.setFont(RetroTheme.RETRO_FONT);
        b.setBackground(RetroTheme.BG_MID);
        b.setForeground(RetroTheme.FG_ACCENT);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setBorderPainted(true);
        b.setBorder(new RetroBorder());
        b.setOpaque(false);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton btn = (AbstractButton) c;
        boolean pressed = btn.getModel().isPressed();

        g.setColor(pressed ? RetroTheme.BG_DARKEST : btn.getBackground());
        g.fillRect(0, 0, c.getWidth(), c.getHeight());

        if (pressed) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(1, 1);
            super.paint(g2, c);
            g2.dispose();
        } else {
            super.paint(g, c);
        }
    }

    @Override
    protected void paintText(Graphics g, JComponent c, Rectangle textRect, String text) {
        AbstractButton b = (AbstractButton) c;
        FontMetrics fm = g.getFontMetrics(b.getFont());
        int ascent = fm.getAscent();

        // Drop shadow
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(text, textRect.x + 1, textRect.y + ascent + 1);

        // Main text
        Color fg = b.getModel().isEnabled() ? b.getForeground() : new Color(0x3a3a6a);
        g.setColor(fg);
        g.drawString(text, textRect.x, textRect.y + ascent);
    }

    static class RetroBorder implements Border, UIResource {
        private static final int T = 3;
        private static final Insets INSETS = new Insets(T + 2, T + 3, T + 2, T + 3);

        @Override
        public Insets getBorderInsets(Component c) { return INSETS; }

        @Override
        public boolean isBorderOpaque() { return true; }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            boolean pressed = false;
            boolean selected = false;
            if (c instanceof AbstractButton) {
                ButtonModel model = ((AbstractButton) c).getModel();
                pressed  = model.isPressed();
                selected = (c instanceof JToggleButton) && model.isSelected();
            }
            boolean depressed = pressed || selected;

            Color hi = depressed ? RetroTheme.BORDER_LO : RetroTheme.BORDER_HI;
            Color lo = depressed ? RetroTheme.BORDER_HI : RetroTheme.BORDER_LO;

            // Top then left (draw top full-width first so corners go to top)
            g.setColor(hi);
            g.fillRect(x,         y,         w,     T); // top
            g.fillRect(x,         y + T,     T, h - T); // left

            // Bottom then right (overwrites corners)
            g.setColor(lo);
            g.fillRect(x,         y + h - T, w,     T); // bottom
            g.fillRect(x + w - T, y,         T,     h); // right
        }
    }
}

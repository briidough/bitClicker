package com.atari.spritemaker.ui;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

public class RetroToggleButtonUI extends RetroButtonUI {

    private static final Color SELECTED_BG   = new Color(0x3d2400);
    private static final Color SELECTED_TEXT = RetroTheme.FG_SELECTED;

    public static ComponentUI createUI(JComponent c) {
        return new RetroToggleButtonUI();
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        c.setForeground(RetroTheme.FG_TEXT);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        AbstractButton btn = (AbstractButton) c;
        boolean selected = btn.getModel().isSelected();
        boolean pressed  = btn.getModel().isPressed();

        Color bg = pressed   ? RetroTheme.BG_DARKEST
                 : selected  ? SELECTED_BG
                             : btn.getBackground();
        g.setColor(bg);
        g.fillRect(0, 0, c.getWidth(), c.getHeight());

        if (pressed || selected) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (pressed) g2.translate(1, 1);
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

        Color fg;
        if (!b.getModel().isEnabled()) {
            fg = new Color(0x3a3a6a);
        } else if (b.getModel().isSelected()) {
            fg = SELECTED_TEXT;
        } else {
            fg = b.getForeground();
        }

        // Drop shadow
        g.setColor(new Color(0, 0, 0, 160));
        g.drawString(text, textRect.x + 1, textRect.y + ascent + 1);

        g.setColor(fg);
        g.drawString(text, textRect.x, textRect.y + ascent);
    }
}

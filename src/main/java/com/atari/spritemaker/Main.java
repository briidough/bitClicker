package com.atari.spritemaker;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Fall back to default Swing L&F
        }
        SwingUtilities.invokeLater(() -> new SpriteEditorFrame().setVisible(true));
    }
}

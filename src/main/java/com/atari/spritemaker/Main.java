package com.atari.spritemaker;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new SpriteEditorFrame().setVisible(true));
    }
}

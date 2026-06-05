package com.atari.spritemaker;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.panels.*;
import com.atari.spritemaker.ui.RetroTheme;
import javax.swing.*;
import java.awt.*;

public class SpriteEditorFrame extends JFrame {

    public SpriteEditorFrame() {
        super("Sprite Editor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        SpriteModel  model        = new SpriteModel();
        ActionPanel  actionPanel  = new ActionPanel(model);
        EditorPanel  editorPanel  = new EditorPanel(model);
        PreviewPanel previewPanel = new PreviewPanel(model);

        model.addChangeListener(actionPanel);
        model.addChangeListener(editorPanel);
        model.addChangeListener(previewPanel);

        // ── menu bar ──────────────────────────────────────────────────────────
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem miNew    = new JMenuItem("New Sprite");
        JMenuItem miLoad   = new JMenuItem("Load Sprite");
        JMenuItem miSave   = new JMenuItem("Save Sprite");
        JMenuItem miExport = new JMenuItem("Export SVG");
        JMenuItem miLoadSvg = new JMenuItem("Load SVG");

        miNew   .addActionListener(e -> actionPanel.newSprite());
        miLoad  .addActionListener(e -> actionPanel.loadSprite());
        miSave  .addActionListener(e -> actionPanel.saveSprite());
        miExport.addActionListener(e -> actionPanel.exportSvg());
        miLoadSvg.addActionListener(e -> actionPanel.loadSvg());

        miNew   .setAccelerator(KeyStroke.getKeyStroke("ctrl N"));
        miLoad  .setAccelerator(KeyStroke.getKeyStroke("ctrl O"));
        miSave  .setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        miExport.setAccelerator(KeyStroke.getKeyStroke("ctrl E"));
        miLoadSvg.setAccelerator(KeyStroke.getKeyStroke("ctrl L"));

        fileMenu.add(miNew);
        fileMenu.add(miLoad);
        fileMenu.add(miSave);
        fileMenu.addSeparator();
        fileMenu.add(miExport);
        fileMenu.add(miLoadSvg);

        menuBar.add(fileMenu);

        // ── theme toggle ──────────────────────────────────────────────────────
        JCheckBoxMenuItem miRetro = new JCheckBoxMenuItem("Retro Theme");
        miRetro.addActionListener(e -> {
            if (miRetro.isSelected()) RetroTheme.apply();
            else                      RetroTheme.reset();
            SwingUtilities.updateComponentTreeUI(this);
            repaint();
        });
        menuBar.add(miRetro);

        setJMenuBar(menuBar);

        // ── layout ────────────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            editorPanel, previewPanel);
        split.setResizeWeight(0.7);

        JScrollPane actionScroll = new JScrollPane(actionPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        actionScroll.setBorder(null);
        add(actionScroll, BorderLayout.WEST);
        add(split, BorderLayout.CENTER);

        pack();
        setSize((int)(getWidth() * 1.4), (int)(getHeight() * 1.4));
        setMinimumSize(new Dimension(600, 600));
        setLocationRelativeTo(null);
    }
}

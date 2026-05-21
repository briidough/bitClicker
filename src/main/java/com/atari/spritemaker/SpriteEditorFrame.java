package com.atari.spritemaker;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.panels.*;
import javax.swing.*;
import java.awt.*;

public class SpriteEditorFrame extends JFrame {

    public SpriteEditorFrame() {
        super("Sprite Editor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        SpriteModel model = new SpriteModel();
        ActionPanel  actionPanel  = new ActionPanel(model);
        EditorPanel  editorPanel  = new EditorPanel(model);
        PreviewPanel previewPanel = new PreviewPanel(model);

        model.addChangeListener(actionPanel);
        model.addChangeListener(editorPanel);
        model.addChangeListener(previewPanel);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            editorPanel, previewPanel);
        split.setResizeWeight(0.7);

        add(actionPanel, BorderLayout.WEST);
        add(split, BorderLayout.CENTER);

        pack();
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
    }
}

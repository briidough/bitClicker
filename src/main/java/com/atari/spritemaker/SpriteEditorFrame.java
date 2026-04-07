package com.atari.spritemaker;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.panels.ActionPanel;
import com.atari.spritemaker.panels.EditorPanel;
import com.atari.spritemaker.panels.HexMirrorPanel;

import javax.swing.*;
import java.awt.*;

public class SpriteEditorFrame extends JFrame {

    public SpriteEditorFrame() {
        super("Atari 2600 Sprite Editor");

        SpriteModel model = new SpriteModel();

        EditorPanel    editorPanel    = new EditorPanel(model);
        HexMirrorPanel hexMirrorPanel = new HexMirrorPanel(model);
        ActionPanel    actionPanel    = new ActionPanel(model);

        // Both panels listen for model changes and repaint themselves
        model.addChangeListener(editorPanel);
        model.addChangeListener(hexMirrorPanel);

        // Hex mirror is wide — put it in a scroll pane
        JScrollPane mirrorScroll = new JScrollPane(hexMirrorPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        mirrorScroll.setBorder(null);
        mirrorScroll.setPreferredSize(new Dimension(660, 500));

        // Split editor / hex mirror so the user can resize
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                editorPanel, mirrorScroll);
        splitPane.setResizeWeight(0.5);
        splitPane.setOneTouchExpandable(true);

        setLayout(new BorderLayout(6, 0));
        add(actionPanel, BorderLayout.WEST);
        add(splitPane,   BorderLayout.CENTER);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setResizable(true);
    }
}

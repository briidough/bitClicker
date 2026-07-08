package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.SpriteModel.HistorySnapshot;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class HistoryPanel extends JPanel implements ChangeListener {

    private static final int FIXED_WIDTH = 150;
    private static final int THUMB = 120;

    private final SpriteModel model;
    private final EditorPanel editorPanel;
    private final JPanel listPanel;

    public HistoryPanel(SpriteModel model, EditorPanel editorPanel) {
        this.model = model;
        this.editorPanel = editorPanel;
        setLayout(new BorderLayout());
        Color sepColor = UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, sepColor != null ? sepColor : Color.GRAY));

        JLabel title = new JLabel("History", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        title.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        add(title, BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(listPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        rebuild();
    }

    private void rebuild() {
        listPanel.removeAll();
        List<HistorySnapshot> history = model.getHistory();
        for (int i = 0; i < history.size(); i++) {
            final HistorySnapshot snap = history.get(i);
            JButton thumb = new JButton(scaledIcon(snap));
            thumb.setAlignmentX(Component.CENTER_ALIGNMENT);
            thumb.setMargin(new Insets(2, 2, 2, 2));
            thumb.setMaximumSize(new Dimension(THUMB + 8, THUMB + 8));
            thumb.setToolTipText("Restore this state");
            thumb.addActionListener(e -> {
                if (editorPanel.isDirty()) model.captureHistory();
                editorPanel.resetHistoryTimer();
                model.restoreSnapshot(snap);
            });
            listPanel.add(Box.createVerticalStrut(6));
            listPanel.add(thumb);
        }
        listPanel.add(Box.createVerticalStrut(6));
        listPanel.revalidate();
        listPanel.repaint();
    }

    private Icon scaledIcon(HistorySnapshot snap) {
        Image img = snap.thumbnail.getScaledInstance(THUMB, THUMB, Image.SCALE_FAST);
        return new ImageIcon(img);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (isVisible()) rebuild();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) rebuild();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(FIXED_WIDTH, super.getPreferredSize().height); }
    @Override public Dimension getMaximumSize()   { return new Dimension(FIXED_WIDTH, Integer.MAX_VALUE); }
    @Override public Dimension getMinimumSize()   { return new Dimension(FIXED_WIDTH, 0); }
}

package com.atari.spritemaker;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.panels.*;
import com.atari.spritemaker.ui.RetroTheme;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.Properties;

public class SpriteEditorFrame extends JFrame implements ChangeListener {

    private final SpriteModel  model;
    private final ActionPanel  actionPanel;
    private JSplitPane mainSplit;
    private JSplitPane editorPreviewSplit;
    private int lastGridSize;
    private EditorPanel  editorPanel;
    private PreviewPanel previewPanel;
    private boolean previewAbove = false;
    private boolean retroTheme   = false;

    public SpriteEditorFrame() {
        super("Sprite Editor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        model                             = new SpriteModel();
        PixelBurstPanel  burstPanel       = new PixelBurstPanel(model);
        PixelPopPanel    popPanel         = new PixelPopPanel(model);
        PixelTwistPanel  twistPanel       = new PixelTwistPanel(model);
        PixelMorphPanel  morphPanel       = new PixelMorphPanel(model);
        ActionEditsPanel actionEditsPanel = new ActionEditsPanel(model, burstPanel, popPanel, twistPanel, morphPanel);
        actionPanel                       = new ActionPanel(model);
        actionPanel.setActionEditsPanel(actionEditsPanel);
        editorPanel  = new EditorPanel(model);
        previewPanel = new PreviewPanel(model);

        model.addChangeListener(actionPanel);
        model.addChangeListener(editorPanel);
        model.addChangeListener(previewPanel);
        model.addChangeListener(actionEditsPanel);
        model.addChangeListener(this);
        model.addTransformListener(previewPanel::onTransformSettingChanged);

        // ── menu bar ──────────────────────────────────────────────────────────
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem miNew     = new JMenuItem("New Sprite");
        JMenuItem miLoad    = new JMenuItem("Load Sprite");
        JMenuItem miSave    = new JMenuItem("Save Sprite");
        JMenuItem miExport  = new JMenuItem("Export SVG");
        JMenuItem miLoadSvg = new JMenuItem("Load SVG");

        miNew    .addActionListener(e -> actionPanel.newSprite());
        miLoad   .addActionListener(e -> actionPanel.loadSprite());
        miSave   .addActionListener(e -> actionPanel.saveSprite());
        miExport .addActionListener(e -> actionPanel.exportSvg());
        miLoadSvg.addActionListener(e -> actionPanel.loadSvg());

        JMenuItem miSaveSga = new JMenuItem("Save Spriteamation");
        JMenuItem miLoadSga = new JMenuItem("Load Spriteamation");
        miSaveSga.addActionListener(e -> actionPanel.saveSpritamation());
        miLoadSga.addActionListener(e -> actionPanel.loadSpritamation());

        miNew    .setAccelerator(KeyStroke.getKeyStroke("ctrl N"));
        miLoad   .setAccelerator(KeyStroke.getKeyStroke("ctrl O"));
        miSave   .setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        miExport .setAccelerator(KeyStroke.getKeyStroke("ctrl E"));
        miLoadSvg.setAccelerator(KeyStroke.getKeyStroke("ctrl L"));
        JMenuItem miExportBxl = new JMenuItem("Export for Web (.bxl)");
        miExportBxl.addActionListener(e -> actionPanel.exportBxl());

        miSaveSga   .setAccelerator(KeyStroke.getKeyStroke("ctrl shift S"));
        miLoadSga   .setAccelerator(KeyStroke.getKeyStroke("ctrl shift O"));
        miExportBxl .setAccelerator(KeyStroke.getKeyStroke("ctrl shift E"));

        fileMenu.add(miNew);
        fileMenu.add(miLoad);
        fileMenu.add(miSave);
        fileMenu.addSeparator();
        fileMenu.add(miExport);
        fileMenu.add(miLoadSvg);
        fileMenu.addSeparator();
        fileMenu.add(miSaveSga);
        fileMenu.add(miLoadSga);
        fileMenu.addSeparator();
        fileMenu.add(miExportBxl);
        menuBar.add(fileMenu);

        // ── edit menu ─────────────────────────────────────────────────────────
        JMenu editMenu = new JMenu("Edit");
        JMenuItem miUndo = new JMenuItem("Undo");
        JMenuItem miRedo = new JMenuItem("Redo");
        miUndo.setAccelerator(KeyStroke.getKeyStroke("ctrl Z"));
        miRedo.setAccelerator(KeyStroke.getKeyStroke("ctrl Y"));
        miUndo.addActionListener(e -> model.undo());
        miRedo.addActionListener(e -> model.redo());
        editMenu.add(miUndo);
        editMenu.add(miRedo);
        menuBar.add(editMenu);

        // ── options menu (directly beside File) ───────────────────────────────
        JMenu optionsMenu = new JMenu("Options");

        JCheckBoxMenuItem miRetro = new JCheckBoxMenuItem("Retro Theme");
        miRetro.addActionListener(e -> {
            retroTheme = miRetro.isSelected();
            if (retroTheme) RetroTheme.apply(); else RetroTheme.reset();
            SwingUtilities.updateComponentTreeUI(this);
            repaint();
        });

        JCheckBoxMenuItem miPreviewTop = new JCheckBoxMenuItem("Preview above editor");
        miPreviewTop.addActionListener(e -> setPreviewAbove(miPreviewTop.isSelected()));

        optionsMenu.add(miRetro);
        optionsMenu.addSeparator();
        optionsMenu.add(miPreviewTop);
        menuBar.add(optionsMenu);

        setJMenuBar(menuBar);

        // ── layout ────────────────────────────────────────────────────────────
        editorPreviewSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            editorPanel, previewPanel);
        editorPreviewSplit.setResizeWeight(0.75);
        editorPreviewSplit.setContinuousLayout(true);

        JScrollPane actionScroll = new JScrollPane(actionPanel,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        actionScroll.setBorder(null);

        actionEditsPanel.setVisible(false);

        // Right side: BorderLayout gives editorPreviewSplit the full height of its
        // container (BoxLayout.X_AXIS constrains heights to preferred sizes, which
        // prevents the vertical split pane divider from being draggable).
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(actionEditsPanel, BorderLayout.WEST);
        rightPanel.add(editorPreviewSplit, BorderLayout.CENTER);

        // Horizontal split gives ActionPanel a drag handle vs the editor area
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, actionScroll, rightPanel);
        mainSplit.setResizeWeight(0.0);
        mainSplit.setContinuousLayout(true);
        mainSplit.setBorder(null);
        add(mainSplit, BorderLayout.CENTER);

        lastGridSize = model.getGridSize();

        pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = (int)(screen.width  * 0.92);
        int maxH = (int)(screen.height * 0.92);
        setSize(Math.min((int)(getWidth() * 1.4), maxW),
                Math.min((int)(getHeight() * 1.2), maxH));
        setMinimumSize(new Dimension(700, 700));
        validate();
        setLocationRelativeTo(null);

        // Default dividers — may be overridden by saved settings below
        SwingUtilities.invokeLater(() -> {
            validate();
            mainSplit.setDividerLocation(actionPanel.getPreferredSize().width + 4);
            int h = editorPreviewSplit.getHeight();
            int d = editorPreviewSplit.getDividerSize();
            editorPreviewSplit.setDividerLocation(previewAbove ? (int)((h-d)*0.25) : (int)((h-d)*0.75));
        });

        // ── restore saved settings ────────────────────────────────────────────
        Properties saved = loadSettingsFile();

        if ("true".equals(saved.getProperty("retroTheme"))) {
            retroTheme = true;
            miRetro.setSelected(true);
            RetroTheme.apply();
            SwingUtilities.updateComponentTreeUI(this);
        }

        if ("true".equals(saved.getProperty("previewAbove"))) {
            miPreviewTop.setSelected(true);
            setPreviewAbove(true);
        }

        try {
            int x = Integer.parseInt(saved.getProperty("windowX", "-1"));
            int y = Integer.parseInt(saved.getProperty("windowY", "-1"));
            int w = Integer.parseInt(saved.getProperty("windowWidth",  "-1"));
            int h = Integer.parseInt(saved.getProperty("windowHeight", "-1"));
            if (x >= 0 && y >= 0 && w > 0 && h > 0) setBounds(x, y, w, h);
        } catch (NumberFormatException ignored) {}

        // Saved split positions override defaults — queue after the default invokeLater
        String savedMs = saved.getProperty("mainSplitPos");
        String savedEs = saved.getProperty("editorSplitPos");
        SwingUtilities.invokeLater(() -> {
            try { if (savedMs != null) mainSplit.setDividerLocation(Integer.parseInt(savedMs)); }
            catch (NumberFormatException ignored) {}
            try { if (savedEs != null) editorPreviewSplit.setDividerLocation(Integer.parseInt(savedEs)); }
            catch (NumberFormatException ignored) {}
        });

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { saveSettings(); }
        });
    }

    // ── settings persistence ──────────────────────────────────────────────────

    private static final File SETTINGS_FILE =
        new File(System.getProperty("user.home"), ".sprite-editor.properties");

    private Properties loadSettingsFile() {
        Properties p = new Properties();
        if (SETTINGS_FILE.exists()) {
            try (FileReader r = new FileReader(SETTINGS_FILE)) { p.load(r); }
            catch (IOException ignored) {}
        }
        return p;
    }

    private void saveSettings() {
        Properties p = new Properties();
        p.setProperty("retroTheme",    String.valueOf(retroTheme));
        p.setProperty("previewAbove",  String.valueOf(previewAbove));
        p.setProperty("windowX",       String.valueOf(getX()));
        p.setProperty("windowY",       String.valueOf(getY()));
        p.setProperty("windowWidth",   String.valueOf(getWidth()));
        p.setProperty("windowHeight",  String.valueOf(getHeight()));
        p.setProperty("mainSplitPos",  String.valueOf(mainSplit.getDividerLocation()));
        p.setProperty("editorSplitPos",String.valueOf(editorPreviewSplit.getDividerLocation()));
        try (FileWriter w = new FileWriter(SETTINGS_FILE)) { p.store(w, null); }
        catch (IOException ignored) {}
    }

    // ── ChangeListener: window resize when grid size changes ──────────────────

    @Override
    public void stateChanged(ChangeEvent e) {
        int newSize = model.getGridSize();
        if (newSize != lastGridSize) {
            lastGridSize = newSize;
            SwingUtilities.invokeLater(this::resizeToFitCanvas);
        }
    }

    private void resizeToFitCanvas() {
        Dimension current = getSize();
        pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = (int)(screen.width  * 0.92);
        int maxH = (int)(screen.height * 0.92);
        int newW = Math.min(Math.max(getWidth(), current.width), maxW);
        int newH = Math.min(Math.max(getHeight(), current.height), maxH);
        setSize(newW, newH);
        validate();
        SwingUtilities.invokeLater(() -> {
            validate();
            mainSplit.setDividerLocation(actionPanel.getPreferredSize().width + 4);
            int h = editorPreviewSplit.getHeight();
            int dv = editorPreviewSplit.getDividerSize();
            editorPreviewSplit.setDividerLocation(previewAbove ? (int)((h-dv)*0.25) : (int)((h-dv)*0.75));
        });
    }

    // ── preview position toggle ───────────────────────────────────────────────

    private void setPreviewAbove(boolean above) {
        // Mirror current split: capture before swapping components
        int snapH    = editorPreviewSplit.getHeight();
        int snapDiv  = editorPreviewSplit.getDividerSize();
        int snapPos  = editorPreviewSplit.getDividerLocation();
        int usable   = snapH - snapDiv;
        // If laid out, mirror the ratio; otherwise fall back to a sensible default
        final int targetPos = (usable > 0 && snapPos > 0)
                ? usable - snapPos
                : (int)(usable * (above ? 0.25 : 0.75));

        previewAbove = above;
        if (above) {
            editorPreviewSplit.setTopComponent(previewPanel);
            editorPreviewSplit.setBottomComponent(editorPanel);
            editorPreviewSplit.setResizeWeight(0.25);
        } else {
            editorPreviewSplit.setTopComponent(editorPanel);
            editorPreviewSplit.setBottomComponent(previewPanel);
            editorPreviewSplit.setResizeWeight(0.75);
        }
        // setTopComponent triggers ui.resetToPreferredSizes() internally — force
        // validate() first so the split pane has its final height, then apply.
        SwingUtilities.invokeLater(() -> {
            validate();
            editorPreviewSplit.setDividerLocation(targetPos);
        });
    }
}

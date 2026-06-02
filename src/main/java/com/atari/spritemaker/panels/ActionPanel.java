package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.SpriteModel.DrawingTool;
import com.atari.spritemaker.model.SpriteModel.Mode;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ActionPanel extends JPanel implements ChangeListener {

    private final SpriteModel model;
    private final JToggleButton btnPencil, btnLine, btnDrag;
    private final JToggleButton btn16, btn32, btn48, btn64, btn80;
    private JButton btnFillFromImage;
    private JButton btnPasteImage;
    private JToggleButton btnShowBgImage;
    private JButton transformBtn;
    private JPanel drawModeControls;
    private JPanel transformModeControls;

    // Pixel Burst controls
    private JSlider sliderSpread, sliderSpeed, sliderHold, sliderFocalX, sliderFocalY, sliderSpinStrength;
    private JComboBox<String> comboEasing, comboSpin;

    // Pixel Pop controls
    private JButton btnBurstTab, btnPopTab;
    private JPanel tabContent;
    private JSlider sliderGravPush, sliderGravPull, sliderGravFocalX, sliderGravFocalY;
    private JSlider sliderExplodeSpeed, sliderUnsplodeSpeed;
    private JSlider sliderExplodeStrength, sliderUnsplodeStrength;
    private JSlider sliderPopHold;

    private java.io.File lastDir = null;

    private static final int DEF_SPREAD = 24, DEF_SPEED = 300, DEF_HOLD = 200;
    private static final int DEF_EASING = 0, DEF_FOCAL_X = 50, DEF_FOCAL_Y = 50, DEF_SPIN = 0, DEF_SPIN_STRENGTH = 100;
    private static final int DEF_GRAV_PUSH = 50, DEF_GRAV_PULL = 50, DEF_GRAV_FOCAL_X = 50, DEF_GRAV_FOCAL_Y = 100;
    private static final int DEF_EXPLODE_SPEED = 1000, DEF_UNSPLODE_SPEED = 1000;
    private static final int DEF_EXPLODE_STRENGTH = 100, DEF_UNSPLODE_STRENGTH = 95;
    private static final int DEF_POP_HOLD = 0;

    public ActionPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        transformBtn = makeBtn("Transform", e -> toggleMode());
        add(transformBtn);

        // ── draw-mode-only controls ──────────────────────────────────────────
        drawModeControls = new JPanel();
        drawModeControls.setLayout(new BoxLayout(drawModeControls, BoxLayout.Y_AXIS));
        drawModeControls.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnPasteImage = makeBtn("Paste Image", e -> pasteImage());
        drawModeControls.add(Box.createVerticalStrut(4));
        drawModeControls.add(btnPasteImage);

        btnFillFromImage = makeBtn("Fill from Image", e -> fillFromImage());
        btnFillFromImage.setEnabled(false);
        drawModeControls.add(Box.createVerticalStrut(4));
        drawModeControls.add(btnFillFromImage);

        drawModeControls.add(Box.createVerticalStrut(12));
        JLabel toolLbl = new JLabel("Drawing Tool:");
        toolLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        drawModeControls.add(toolLbl);
        drawModeControls.add(Box.createVerticalStrut(4));

        btnPencil = new JToggleButton("Pencil");
        btnLine   = new JToggleButton("Line");
        btnDrag   = new JToggleButton("Drag");
        btnPencil.setSelected(true);

        ButtonGroup toolGrp = new ButtonGroup();
        toolGrp.add(btnPencil); toolGrp.add(btnLine); toolGrp.add(btnDrag);

        btnPencil.addActionListener(e -> model.setDrawingTool(DrawingTool.PENCIL));
        btnLine  .addActionListener(e -> model.setDrawingTool(DrawingTool.LINE));
        btnDrag  .addActionListener(e -> model.setDrawingTool(DrawingTool.DRAG));

        JPanel toolRow = new JPanel(new GridLayout(1, 3, 2, 0));
        toolRow.add(btnPencil); toolRow.add(btnLine); toolRow.add(btnDrag);
        toolRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, toolRow.getPreferredSize().height));
        drawModeControls.add(toolRow);

        drawModeControls.add(Box.createVerticalStrut(12));
        btnShowBgImage = new JToggleButton("Show Pasted Image");
        btnShowBgImage.setSelected(true);
        btnShowBgImage.setEnabled(false);
        btnShowBgImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnShowBgImage.setMaximumSize(new Dimension(Integer.MAX_VALUE, btnShowBgImage.getPreferredSize().height));
        btnShowBgImage.addActionListener(e -> model.setShowBgImage(btnShowBgImage.isSelected()));
        drawModeControls.add(btnShowBgImage);

        add(drawModeControls);

        // ── transform-mode-only controls ─────────────────────────────────────
        transformModeControls = new JPanel();
        transformModeControls.setLayout(new BoxLayout(transformModeControls, BoxLayout.Y_AXIS));
        transformModeControls.setAlignmentX(Component.LEFT_ALIGNMENT);
        transformModeControls.setVisible(false);

        transformModeControls.add(Box.createVerticalStrut(4));
        transformModeControls.add(makeBtn("Load Animation Frames", e -> loadAnimationFrames()));

        // Tab buttons: Pixel Burst | Pixel Pop
        transformModeControls.add(Box.createVerticalStrut(8));
        btnBurstTab = new JButton("Pixel Burst");
        btnPopTab   = new JButton("Pixel Pop");
        btnBurstTab.setEnabled(false); // active by default → greyed
        JPanel tabRow = new JPanel(new GridLayout(1, 2, 2, 0));
        tabRow.add(btnBurstTab);
        tabRow.add(btnPopTab);
        tabRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, tabRow.getPreferredSize().height));
        transformModeControls.add(tabRow);

        transformModeControls.add(Box.createVerticalStrut(6));

        // ── Pixel Burst content panel ────────────────────────────────────────
        JPanel burstContentPanel = new JPanel();
        burstContentPanel.setLayout(new BoxLayout(burstContentPanel, BoxLayout.Y_AXIS));
        burstContentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        sliderSpread = new JSlider(5, 80, model.getAnimSpread());
        sliderSpread.addChangeListener(e -> model.setAnimSpread(sliderSpread.getValue()));
        burstContentPanel.add(wrapSlider("Spread", sliderSpread));

        burstContentPanel.add(Box.createVerticalStrut(6));
        sliderSpeed = new JSlider(100, 1500, model.getAnimSpeedMs());
        sliderSpeed.addChangeListener(e -> model.setAnimSpeedMs(sliderSpeed.getValue()));
        burstContentPanel.add(wrapSlider("Speed (ms)", sliderSpeed));

        burstContentPanel.add(Box.createVerticalStrut(6));
        sliderHold = new JSlider(0, 2000, model.getAnimHoldMs());
        sliderHold.addChangeListener(e -> model.setAnimHoldMs(sliderHold.getValue()));
        burstContentPanel.add(wrapSlider("Hold (ms)", sliderHold));

        burstContentPanel.add(Box.createVerticalStrut(8));
        JLabel easingLbl = new JLabel("Easing:");
        easingLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        burstContentPanel.add(easingLbl);
        burstContentPanel.add(Box.createVerticalStrut(2));
        comboEasing = new JComboBox<>(new String[]{"Smooth", "Sharp", "Snappy"});
        comboEasing.setSelectedIndex(model.getAnimEasing());
        comboEasing.setAlignmentX(Component.LEFT_ALIGNMENT);
        comboEasing.setMaximumSize(new Dimension(Integer.MAX_VALUE, comboEasing.getPreferredSize().height));
        comboEasing.addActionListener(e -> model.setAnimEasing(comboEasing.getSelectedIndex()));
        burstContentPanel.add(comboEasing);

        burstContentPanel.add(Box.createVerticalStrut(12));
        JLabel focalLbl = new JLabel("Focal Point:");
        focalLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        burstContentPanel.add(focalLbl);
        burstContentPanel.add(Box.createVerticalStrut(4));
        sliderFocalX = new JSlider(0, 100, model.getAnimFocalX());
        sliderFocalX.addChangeListener(e -> model.setAnimFocalX(sliderFocalX.getValue()));
        MouseAdapter focalDragTracker = new MouseAdapter() {
            public void mousePressed(MouseEvent e)  { model.setFocalActive(true); }
            public void mouseReleased(MouseEvent e) { model.setFocalActive(false); }
        };
        sliderFocalX.addMouseListener(focalDragTracker);
        burstContentPanel.add(wrapSlider("X %", sliderFocalX));
        burstContentPanel.add(Box.createVerticalStrut(4));
        sliderFocalY = new JSlider(0, 100, model.getAnimFocalY());
        sliderFocalY.addChangeListener(e -> model.setAnimFocalY(sliderFocalY.getValue()));
        sliderFocalY.addMouseListener(focalDragTracker);
        burstContentPanel.add(wrapSlider("Y %", sliderFocalY));

        burstContentPanel.add(Box.createVerticalStrut(8));
        JLabel spinLbl = new JLabel("Spin:");
        spinLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        burstContentPanel.add(spinLbl);
        burstContentPanel.add(Box.createVerticalStrut(2));
        comboSpin = new JComboBox<>(new String[]{"None", "Clockwise", "Counter-clockwise"});
        comboSpin.setSelectedIndex(model.getAnimSpin());
        comboSpin.setAlignmentX(Component.LEFT_ALIGNMENT);
        comboSpin.setMaximumSize(new Dimension(Integer.MAX_VALUE, comboSpin.getPreferredSize().height));
        burstContentPanel.add(comboSpin);

        sliderSpinStrength = new JSlider(0, 100, model.getAnimSpinStrength());
        sliderSpinStrength.addChangeListener(e -> model.setAnimSpinStrength(sliderSpinStrength.getValue()));
        JPanel spinStrengthSection = new JPanel();
        spinStrengthSection.setLayout(new BoxLayout(spinStrengthSection, BoxLayout.Y_AXIS));
        spinStrengthSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        spinStrengthSection.add(Box.createVerticalStrut(6));
        spinStrengthSection.add(wrapSlider("Spin Strength", sliderSpinStrength));
        spinStrengthSection.setVisible(model.getAnimSpin() != 0);
        burstContentPanel.add(spinStrengthSection);

        comboSpin.addActionListener(e -> {
            model.setAnimSpin(comboSpin.getSelectedIndex());
            spinStrengthSection.setVisible(comboSpin.getSelectedIndex() != 0);
            burstContentPanel.revalidate();
            burstContentPanel.repaint();
        });

        burstContentPanel.add(Box.createVerticalStrut(12));
        burstContentPanel.add(makeBtn("Reset Defaults", e -> resetBurstDefaults()));

        // ── Pixel Pop content panel ──────────────────────────────────────────
        JPanel popContentPanel = new JPanel();
        popContentPanel.setLayout(new BoxLayout(popContentPanel, BoxLayout.Y_AXIS));
        popContentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        sliderExplodeSpeed = new JSlider(500, 2000, model.getAnimExplodeSpeedMs());
        sliderExplodeSpeed.addChangeListener(e -> model.setAnimExplodeSpeedMs(sliderExplodeSpeed.getValue()));
        popContentPanel.add(wrapSlider("Explode Speed (ms)", sliderExplodeSpeed));

        popContentPanel.add(Box.createVerticalStrut(6));
        sliderExplodeStrength = new JSlider(50, 150, model.getAnimExplodeStrength());
        sliderExplodeStrength.addChangeListener(e -> model.setAnimExplodeStrength(sliderExplodeStrength.getValue()));
        popContentPanel.add(wrapSlider("Explode Strength", sliderExplodeStrength));

        popContentPanel.add(Box.createVerticalStrut(6));
        sliderUnsplodeSpeed = new JSlider(500, 2000, model.getAnimUnsplodeSpeedMs());
        sliderUnsplodeSpeed.addChangeListener(e -> model.setAnimUnsplodeSpeedMs(sliderUnsplodeSpeed.getValue()));
        popContentPanel.add(wrapSlider("Unsplode Speed (ms)", sliderUnsplodeSpeed));

        popContentPanel.add(Box.createVerticalStrut(6));
        sliderUnsplodeStrength = new JSlider(85, 99, model.getAnimUnsplodeStrength());
        sliderUnsplodeStrength.addChangeListener(e -> model.setAnimUnsplodeStrength(sliderUnsplodeStrength.getValue()));
        popContentPanel.add(wrapSlider("Unsplode Strength", sliderUnsplodeStrength));

        popContentPanel.add(Box.createVerticalStrut(6));
        sliderGravPush = new JSlider(0, 100, model.getAnimGravityPush());
        sliderGravPush.addChangeListener(e -> model.setAnimGravityPush(sliderGravPush.getValue()));
        popContentPanel.add(wrapSlider("Gravity Push", sliderGravPush));

        popContentPanel.add(Box.createVerticalStrut(6));
        sliderGravPull = new JSlider(0, 100, model.getAnimGravityPull());
        sliderGravPull.addChangeListener(e -> model.setAnimGravityPull(sliderGravPull.getValue()));
        popContentPanel.add(wrapSlider("Gravity Pull", sliderGravPull));

        popContentPanel.add(Box.createVerticalStrut(12));
        JLabel gravFocalLbl = new JLabel("Gravity Target:");
        gravFocalLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        popContentPanel.add(gravFocalLbl);
        popContentPanel.add(Box.createVerticalStrut(4));

        sliderGravFocalX = new JSlider(0, 100, model.getAnimGravityFocalX());
        sliderGravFocalX.addChangeListener(e -> model.setAnimGravityFocalX(sliderGravFocalX.getValue()));
        popContentPanel.add(wrapSlider("X %", sliderGravFocalX));
        popContentPanel.add(Box.createVerticalStrut(4));

        sliderGravFocalY = new JSlider(0, 100, model.getAnimGravityFocalY());
        sliderGravFocalY.addChangeListener(e -> model.setAnimGravityFocalY(sliderGravFocalY.getValue()));
        popContentPanel.add(wrapSlider("Y %", sliderGravFocalY));

        popContentPanel.add(Box.createVerticalStrut(10));
        JCheckBox chkStayInCanvas = new JCheckBox("Stay in Canvas");
        chkStayInCanvas.setSelected(model.isAnimStayInCanvas());
        chkStayInCanvas.setAlignmentX(Component.LEFT_ALIGNMENT);
        chkStayInCanvas.addActionListener(e -> model.setAnimStayInCanvas(chkStayInCanvas.isSelected()));
        popContentPanel.add(chkStayInCanvas);

        popContentPanel.add(Box.createVerticalStrut(6));
        sliderPopHold = new JSlider(0, 100, 0);
        sliderPopHold.addChangeListener(e -> model.setAnimPopHoldMs(sliderPopHold.getValue() * 20));
        popContentPanel.add(wrapSlider("Hang Time (ms)", sliderPopHold, 20));

        popContentPanel.add(Box.createVerticalStrut(12));
        popContentPanel.add(makeBtn("Reset Defaults", e -> resetPopDefaults()));

        // ── CardLayout to switch between burst and pop ───────────────────────
        tabContent = new JPanel(new CardLayout());
        tabContent.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabContent.add(burstContentPanel, "burst");
        tabContent.add(popContentPanel, "pop");
        transformModeControls.add(tabContent);

        btnBurstTab.addActionListener(e -> switchTab("burst"));
        btnPopTab  .addActionListener(e -> switchTab("pop"));

        add(transformModeControls);

        // ── grid size (always visible) ────────────────────────────────────────
        add(Box.createVerticalStrut(12));
        JLabel lbl = new JLabel("Grid Size:");
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lbl);
        add(Box.createVerticalStrut(4));

        btn16  = new JToggleButton("16");
        btn32  = new JToggleButton("32");
        btn48  = new JToggleButton("48");
        btn64  = new JToggleButton("64");
        btn80  = new JToggleButton("80");
        btn32.setSelected(true);

        ButtonGroup grp = new ButtonGroup();
        grp.add(btn16); grp.add(btn32); grp.add(btn48); grp.add(btn64); grp.add(btn80);

        btn16 .addActionListener(e -> confirmReset(16));
        btn32 .addActionListener(e -> confirmReset(32));
        btn48 .addActionListener(e -> confirmReset(48));
        btn64 .addActionListener(e -> confirmReset(64));
        btn80 .addActionListener(e -> confirmReset(80));

        JPanel sizeRow = new JPanel(new GridLayout(1, 5, 2, 0));
        sizeRow.add(btn16); sizeRow.add(btn32); sizeRow.add(btn48); sizeRow.add(btn64); sizeRow.add(btn80);
        sizeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, sizeRow.getPreferredSize().height));
        add(sizeRow);
    }

    private void switchTab(String key) {
        boolean burst = "burst".equals(key);
        btnBurstTab.setEnabled(!burst);
        btnPopTab  .setEnabled(burst);
        ((CardLayout) tabContent.getLayout()).show(tabContent, key);
        model.setAnimEffectType(burst ? 0 : 1);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        int size = model.getGridSize();
        btn16 .setSelected(size == 16);
        btn32 .setSelected(size == 32);
        btn48 .setSelected(size == 48);
        btn64 .setSelected(size == 64);
        btn80 .setSelected(size == 80);
        btnPencil.setSelected(model.getDrawingTool() == DrawingTool.PENCIL);
        btnLine  .setSelected(model.getDrawingTool() == DrawingTool.LINE);
        btnDrag  .setSelected(model.getDrawingTool() == DrawingTool.DRAG);
        boolean hasImage = model.getBgImage() != null;
        btnFillFromImage.setEnabled(hasImage);
        btnShowBgImage.setEnabled(hasImage);
        btnShowBgImage.setSelected(model.isShowBgImage());

        boolean isTransform = model.getMode() == Mode.TRANSFORM;
        transformBtn.setText(isTransform ? "Draw" : "Transform");
        drawModeControls.setVisible(!isTransform);
        transformModeControls.setVisible(isTransform);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private JPanel wrapSlider(String name, JSlider slider) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(name + ": " + slider.getValue());
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, slider.getPreferredSize().height));
        slider.addChangeListener(e -> lbl.setText(name + ": " + slider.getValue()));
        row.add(lbl);
        row.add(slider);
        return row;
    }

    private JPanel wrapSlider(String name, JSlider slider, int scale) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(name + ": " + (slider.getValue() * scale));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, slider.getPreferredSize().height));
        slider.addChangeListener(e -> lbl.setText(name + ": " + (slider.getValue() * scale)));
        row.add(lbl);
        row.add(slider);
        return row;
    }

    private void resetBurstDefaults() {
        model.setAnimSpread(DEF_SPREAD);         sliderSpread.setValue(DEF_SPREAD);
        model.setAnimSpeedMs(DEF_SPEED);         sliderSpeed.setValue(DEF_SPEED);
        model.setAnimHoldMs(DEF_HOLD);           sliderHold.setValue(DEF_HOLD);
        model.setAnimEasing(DEF_EASING);         comboEasing.setSelectedIndex(DEF_EASING);
        model.setAnimFocalX(DEF_FOCAL_X);        sliderFocalX.setValue(DEF_FOCAL_X);
        model.setAnimFocalY(DEF_FOCAL_Y);        sliderFocalY.setValue(DEF_FOCAL_Y);
        model.setAnimSpin(DEF_SPIN);             comboSpin.setSelectedIndex(DEF_SPIN);
        model.setAnimSpinStrength(DEF_SPIN_STRENGTH); sliderSpinStrength.setValue(DEF_SPIN_STRENGTH);
    }

    private void resetPopDefaults() {
        model.setAnimExplodeSpeedMs(DEF_EXPLODE_SPEED);       sliderExplodeSpeed.setValue(DEF_EXPLODE_SPEED);
        model.setAnimExplodeStrength(DEF_EXPLODE_STRENGTH);   sliderExplodeStrength.setValue(DEF_EXPLODE_STRENGTH);
        model.setAnimUnsplodeSpeedMs(DEF_UNSPLODE_SPEED);     sliderUnsplodeSpeed.setValue(DEF_UNSPLODE_SPEED);
        model.setAnimUnsplodeStrength(DEF_UNSPLODE_STRENGTH); sliderUnsplodeStrength.setValue(DEF_UNSPLODE_STRENGTH);
        model.setAnimGravityPush(DEF_GRAV_PUSH);          sliderGravPush.setValue(DEF_GRAV_PUSH);
        model.setAnimGravityPull(DEF_GRAV_PULL);      sliderGravPull.setValue(DEF_GRAV_PULL);
        model.setAnimGravityFocalX(DEF_GRAV_FOCAL_X); sliderGravFocalX.setValue(DEF_GRAV_FOCAL_X);
        model.setAnimGravityFocalY(DEF_GRAV_FOCAL_Y); sliderGravFocalY.setValue(DEF_GRAV_FOCAL_Y);
        model.setAnimPopHoldMs(DEF_POP_HOLD);          sliderPopHold.setValue(DEF_POP_HOLD);
    }

    private JButton makeBtn(String label, ActionListener al) {
        JButton b = new JButton(label);
        b.addActionListener(al);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
        return b;
    }

    private void toggleMode() {
        boolean toTransform = model.getMode() == Mode.DRAW;
        if (toTransform) {
            Color[][] copy = model.getGridCopy();
            boolean hasContent = false;
            outer:
            for (Color[] row : copy)
                for (Color c : row)
                    if (c != null) { hasContent = true; break outer; }
            if (hasContent) {
                java.util.List<Color[][]> frames = new java.util.ArrayList<>();
                frames.add(copy);
                model.setAnimationFrames(frames);
            }
        }
        model.setMode(toTransform ? Mode.TRANSFORM : Mode.DRAW);
    }

    private void confirmReset(int newSize) {
        int choice = JOptionPane.showConfirmDialog(this,
            "Reset grid? All pixels will be lost.", "Confirm Reset",
            JOptionPane.OK_CANCEL_OPTION);
        if (choice == JOptionPane.OK_OPTION) {
            model.resetGrid(newSize);
        } else {
            int cur = model.getGridSize();
            btn16 .setSelected(cur == 16);
            btn32 .setSelected(cur == 32);
            btn48 .setSelected(cur == 48);
            btn64 .setSelected(cur == 64);
            btn80 .setSelected(cur == 80);
        }
    }

    public void newSprite()  { confirmReset(model.getGridSize()); }

    // ── I/O ─────────────────────────────────────────────────────────────────

    public void loadSprite() {
        File file = chooseFile(true, "spr");
        if (file == null) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            int newSize = 32;
            Color[] newPalette = new Color[5];
            Color[][] newGrid = null;
            String section = null, line;
            int row = 0;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("SIZE=")) {
                    newSize = Integer.parseInt(line.substring(5).trim());
                    newGrid = new Color[newSize][newSize];
                } else if (line.startsWith("PALETTE=")) {
                    String[] parts = line.substring(8).split(",", -1);
                    for (int i = 0; i < 5 && i < parts.length; i++)
                        newPalette[i] = "null".equals(parts[i]) ? null : parseColor(parts[i].trim());
                } else if ("GRID=".equals(line)) {
                    section = "GRID";
                } else if ("GRID".equals(section) && newGrid != null && row < newSize) {
                    String[] tokens = line.split(",", -1);
                    for (int col = 0; col < newSize && col < tokens.length; col++)
                        newGrid[row][col] = "null".equals(tokens[col]) ? null : parseColor(tokens[col].trim());
                    row++;
                }
            }
            if (newGrid != null) {
                model.resetGrid(newSize);
                for (int i = 0; i < 5; i++)
                    if (newPalette[i] != null) model.setPaletteSlotColor(i, newPalette[i]);
                for (int r = 0; r < newSize; r++)
                    for (int c = 0; c < newSize; c++)
                        if (newGrid[r][c] != null) model.setCellColor(r, c, newGrid[r][c]);
                model.setFilePath(file.getAbsolutePath());
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Load failed: " + ex.getMessage());
        }
    }

    public void saveSprite() {
        String path = model.getFilePath();
        if (path == null) {
            File file = chooseFile(false, "spr");
            if (file == null) return;
            path = ensureExt(file.getAbsolutePath(), ".spr");
            model.setFilePath(path);
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("AUTHOR=" + model.getAuthor());
            pw.println("SIZE=" + model.getGridSize());
            Color[] pal = model.getPalette();
            StringBuilder palLine = new StringBuilder("PALETTE=");
            for (int i = 0; i < 5; i++) {
                if (i > 0) palLine.append(',');
                palLine.append(pal[i] == null ? "null" : hex(pal[i]));
            }
            pw.println(palLine);
            pw.println("GRID=");
            int size = model.getGridSize();
            for (int r = 0; r < size; r++) {
                StringBuilder rowLine = new StringBuilder();
                for (int c = 0; c < size; c++) {
                    if (c > 0) rowLine.append(',');
                    Color cell = model.getCellColor(r, c);
                    rowLine.append(cell == null ? "null" : hex(cell));
                }
                pw.println(rowLine);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
        }
    }

    public void exportSvg() {
        File file = chooseFile(false, "svg");
        if (file == null) return;
        String path = ensureExt(file.getAbsolutePath(), ".svg");
        int size = model.getGridSize();
        int canvasPx = size * 4;
        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pw.printf("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">%n",
                canvasPx, canvasPx, canvasPx, canvasPx);
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    Color cell = model.getCellColor(r, c);
                    if (cell != null) {
                        pw.printf("  <rect x=\"%d\" y=\"%d\" width=\"4\" height=\"4\" fill=\"#%s\"/>%n",
                            c * 4, r * 4, hex(cell));
                    }
                }
            }
            pw.println("</svg>");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage());
        }
    }

    public void loadSvg() {
        File file = chooseFile(true, "svg");
        if (file == null) return;
        Color[][] grid = parseSvgFile(file);
        if (grid == null) return;
        int gridSize = grid.length;
        model.resetGrid(gridSize);
        for (int r = 0; r < gridSize; r++)
            for (int c = 0; c < gridSize; c++)
                if (grid[r][c] != null) model.setCellColor(r, c, grid[r][c]);
    }

    private void loadAnimationFrames() {
        JFileChooser fc = new JFileChooser();
        if (lastDir != null) fc.setCurrentDirectory(lastDir);
        fc.setMultiSelectionEnabled(true);
        fc.setFileFilter(new FileNameExtensionFilter("SVG files", "svg"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File[] files = fc.getSelectedFiles();
        if (files.length == 0) return;
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        lastDir = files[0].getParentFile();

        java.util.List<Color[][]> frames = new java.util.ArrayList<>();
        for (File f : files) {
            Color[][] grid = parseSvgFile(f);
            if (grid != null) frames.add(grid);
        }
        if (frames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No valid SVG frames loaded.");
            return;
        }
        model.setAnimationFrames(frames);
        applyGridToModel(frames.get(0));
    }

    private void applyGridToModel(Color[][] frame) {
        int size = frame.length;
        model.resetGrid(size);
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                if (frame[r][c] != null) model.setCellColor(r, c, frame[r][c]);
    }

    private Color[][] parseSvgFile(File file) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(file);
            NodeList rects = doc.getElementsByTagNameNS("*", "rect");
            if (rects.getLength() == 0) {
                JOptionPane.showMessageDialog(this, "No rect elements found in SVG: " + file.getName());
                return null;
            }

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double totalW = 0;
            int wCount = 0;
            for (int i = 0; i < rects.getLength(); i++) {
                Element r = (Element) rects.item(i);
                double w = parseDouble(r.getAttribute("width"));
                if (w > 0) { totalW += w; wCount++; }
                double x = parseDouble(r.getAttribute("x"));
                double y = parseDouble(r.getAttribute("y"));
                if (x < minX) minX = x;
                if (y < minY) minY = y;
            }

            if (wCount == 0) {
                JOptionPane.showMessageDialog(this, "Could not determine cell size from SVG: " + file.getName());
                return null;
            }
            double cellSize = totalW / wCount;

            java.util.List<int[]> cells = new java.util.ArrayList<>();
            int maxCol = 0, maxRow = 0;
            for (int i = 0; i < rects.getLength(); i++) {
                Element r = (Element) rects.item(i);
                Color c = parseSvgColor(r);
                if (c == null) continue;
                int col = (int) Math.round((parseDouble(r.getAttribute("x")) - minX) / cellSize);
                int row = (int) Math.round((parseDouble(r.getAttribute("y")) - minY) / cellSize);
                cells.add(new int[]{ col, row, c.getRGB() });
                if (col > maxCol) maxCol = col;
                if (row > maxRow) maxRow = row;
            }

            if (cells.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No colored cells found in SVG: " + file.getName());
                return null;
            }

            int gridSize = -1;
            for (int s : new int[]{ 16, 32, 48, 64, 80 }) {
                if (maxCol < s && maxRow < s) { gridSize = s; break; }
            }
            if (gridSize == -1) {
                JOptionPane.showMessageDialog(this,
                    "SVG is too large (" + (maxCol + 1) + "×" + (maxRow + 1) + " cells). Max supported: 80×80. Min: 16×16.");
                return null;
            }

            Color[][] grid = new Color[gridSize][gridSize];
            for (int[] cell : cells)
                grid[cell[1]][cell[0]] = new Color(cell[2]);
            return grid;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Load SVG failed (" + file.getName() + "): " + ex.getMessage());
            return null;
        }
    }

    private Color parseSvgColor(Element rect) {
        String fill = rect.getAttribute("fill");
        if (fill != null && !fill.isEmpty() && !fill.equals("none")) {
            return parseHexColor(fill);
        }
        String style = rect.getAttribute("style");
        if (style != null && !style.isEmpty()) {
            for (String prop : style.split(";")) {
                prop = prop.trim();
                if (prop.startsWith("fill:")) {
                    String val = prop.substring(5).trim();
                    if (!val.equals("none")) return parseHexColor(val);
                }
            }
        }
        return null;
    }

    private Color parseHexColor(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try { return new Color(Integer.parseInt(s, 16)); }
        catch (NumberFormatException e) { return null; }
    }

    private double parseDouble(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0; }
    }

    private void fillFromImage() {
        BufferedImage bg = model.getBgImage();
        if (bg == null) return;
        int gridSize = model.getGridSize();
        int imgW = bg.getWidth(), imgH = bg.getHeight();
        int totalPx = gridSize * 10;
        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                int cx = col * 10 + 5;
                int cy = row * 10 + 5;
                int px = Math.min(cx * imgW / totalPx, imgW - 1);
                int py = Math.min(cy * imgH / totalPx, imgH - 1);
                int argb = bg.getRGB(px, py);
                if (((argb >>> 24) & 0xFF) > 0) {
                    model.setCellColor(row, col, new Color(argb & 0x00FFFFFF));
                }
            }
        }
    }

    private void pasteImage() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null) {
                JOptionPane.showMessageDialog(this, "Clipboard is empty.");
                return;
            }
            BufferedImage bi = null;
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                if (img instanceof BufferedImage) {
                    bi = (BufferedImage) img;
                } else {
                    bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g2 = bi.createGraphics();
                    g2.drawImage(img, 0, 0, null);
                    g2.dispose();
                }
            } else {
                for (DataFlavor f : t.getTransferDataFlavors()) {
                    if (f.getMimeType().startsWith("image/")
                            && InputStream.class.isAssignableFrom(f.getRepresentationClass())) {
                        try (InputStream is = (InputStream) t.getTransferData(f)) {
                            bi = ImageIO.read(is);
                            if (bi != null) break;
                        } catch (Exception ignored) {}
                    }
                }
                if (bi == null) {
                    DataFlavor uriList = new DataFlavor("text/uri-list;class=java.lang.String");
                    if (t.isDataFlavorSupported(uriList)) {
                        String uris = (String) t.getTransferData(uriList);
                        for (String line : uris.split("\\r?\\n")) {
                            line = line.trim();
                            if (line.isEmpty() || line.startsWith("#")) continue;
                            try {
                                bi = ImageIO.read(new File(new URI(line)));
                                if (bi != null) break;
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            if (bi == null) {
                JOptionPane.showMessageDialog(this, "No image found in clipboard.");
                return;
            }
            if (model.getBgImage() != null) model.setBgImage(null);
            model.setBgImage(bi);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Paste failed: " + ex.getMessage());
        }
    }

    // ── utilities ────────────────────────────────────────────────────────────

    private String hex(Color c) {
        return String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    private Color parseColor(String hex) {
        return new Color(Integer.parseInt(hex, 16));
    }

    private String ensureExt(String path, String ext) {
        return path.endsWith(ext) ? path : path + ext;
    }

    private File chooseFile(boolean open, String ext) {
        JFileChooser fc = new JFileChooser();
        if (lastDir != null) fc.setCurrentDirectory(lastDir);
        fc.setFileFilter(new FileNameExtensionFilter(ext.toUpperCase() + " files", ext));
        int result = open ? fc.showOpenDialog(this) : fc.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            lastDir = f.getParentFile();
            return f;
        }
        return null;
    }
}

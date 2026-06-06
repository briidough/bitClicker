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
import java.util.zip.*;

public class ActionPanel extends JPanel implements ChangeListener {

    private final SpriteModel model;

    // Mode selector
    private final JToggleButton btnDraw, btnTransform;

    // Draw tools
    private final JToggleButton btnPencil, btnLine, btnDrag;

    // Grid size
    private final JComboBox<String> gridSizeCombo;
    private boolean comboUpdating = false;

    // Draw mode extras
    private JButton btnFillFromImage;
    private JButton btnPasteImage;
    private JToggleButton btnShowBgImage;
    private JPanel drawModeControls;

    // Transform mode
    private JPanel transformModeControls;
    private JToggleButton btnBurst, btnPop, btnTwist, btnMorph;

    // ActionEdits panel reference (injected after construction)
    private ActionEditsPanel actionEditsPanel;

    private java.io.File lastDir = null;

    public ActionPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // ── mode selector ────────────────────────────────────────────────────
        btnDraw      = new JToggleButton("Draw");
        btnTransform = new JToggleButton("Transform");
        btnDraw.setSelected(true);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(btnDraw);
        modeGroup.add(btnTransform);

        btnDraw.addActionListener(e -> {
            model.setMode(Mode.DRAW);
            if (actionEditsPanel != null) { actionEditsPanel.setVisible(false); revalidateParent(); }
        });
        btnTransform.addActionListener(e -> {
            model.setMode(Mode.TRANSFORM);
            if (actionEditsPanel != null) { actionEditsPanel.setVisible(false); revalidateParent(); }
        });

        JPanel modeRow = new JPanel(new GridLayout(1, 2, 2, 0));
        modeRow.add(btnDraw);
        modeRow.add(btnTransform);
        modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, modeRow.getPreferredSize().height));
        add(modeRow);

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

        drawModeControls.add(Box.createVerticalStrut(4));
        drawModeControls.add(makeBtn("Load Animation Frames", e -> loadAnimationFrames()));

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

        btnPencil.addActionListener(e -> {
            model.setDrawingTool(DrawingTool.PENCIL);
            if (actionEditsPanel != null) actionEditsPanel.showDrawMode();
            revalidateParent();
        });
        btnLine.addActionListener(e -> {
            model.setDrawingTool(DrawingTool.LINE);
            if (actionEditsPanel != null) actionEditsPanel.showDrawMode();
            revalidateParent();
        });
        btnDrag.addActionListener(e -> {
            model.setDrawingTool(DrawingTool.DRAG);
            if (actionEditsPanel != null) { actionEditsPanel.setVisible(false); revalidateParent(); }
        });

        // Re-click detection: toggle ActionEdits when already-selected tool is clicked again
        btnPencil.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (model.getDrawingTool() == DrawingTool.PENCIL && actionEditsPanel != null) {
                    if (actionEditsPanel.isVisible()) actionEditsPanel.setVisible(false);
                    else actionEditsPanel.showDrawMode();
                    revalidateParent();
                }
            }
        });
        btnLine.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (model.getDrawingTool() == DrawingTool.LINE && actionEditsPanel != null) {
                    if (actionEditsPanel.isVisible()) actionEditsPanel.setVisible(false);
                    else actionEditsPanel.showDrawMode();
                    revalidateParent();
                }
            }
        });

        JPanel toolCol = new JPanel(new GridLayout(3, 1, 0, 2));
        toolCol.add(btnPencil); toolCol.add(btnLine); toolCol.add(btnDrag);
        toolCol.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolCol.setMaximumSize(new Dimension(Integer.MAX_VALUE, toolCol.getPreferredSize().height));
        drawModeControls.add(toolCol);

        drawModeControls.add(Box.createVerticalStrut(12));
        btnShowBgImage = new JToggleButton("Show Pasted Image");
        btnShowBgImage.setSelected(true);
        btnShowBgImage.setEnabled(false);
        btnShowBgImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnShowBgImage.setMaximumSize(new Dimension(Integer.MAX_VALUE, btnShowBgImage.getPreferredSize().height));
        btnShowBgImage.addActionListener(e -> model.setShowBgImage(btnShowBgImage.isSelected()));
        drawModeControls.add(btnShowBgImage);

        // Grid size dropdown (draw mode only)
        drawModeControls.add(Box.createVerticalStrut(12));
        JLabel gridLbl = new JLabel("Grid Size:");
        gridLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        drawModeControls.add(gridLbl);
        drawModeControls.add(Box.createVerticalStrut(4));

        gridSizeCombo = new JComboBox<>(new String[]{ "16", "32", "48", "64", "80" });
        gridSizeCombo.setSelectedItem("32");
        gridSizeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        gridSizeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, gridSizeCombo.getPreferredSize().height));
        gridSizeCombo.addActionListener(e -> {
            if (comboUpdating) return;
            int size = Integer.parseInt((String) gridSizeCombo.getSelectedItem());
            if (size != model.getGridSize()) confirmReset(size);
        });
        drawModeControls.add(gridSizeCombo);

        add(drawModeControls);

        // ── transform-mode-only controls ─────────────────────────────────────
        transformModeControls = new JPanel();
        transformModeControls.setLayout(new BoxLayout(transformModeControls, BoxLayout.Y_AXIS));
        transformModeControls.setAlignmentX(Component.LEFT_ALIGNMENT);
        transformModeControls.setVisible(false);

        transformModeControls.add(Box.createVerticalStrut(4));
        transformModeControls.add(makeBtn("Load Animation Frames", e -> loadAnimationFrames()));

        transformModeControls.add(Box.createVerticalStrut(8));

        btnBurst = new JToggleButton("Pixel Burst");
        btnPop   = new JToggleButton("Pixel Pop");
        btnTwist = new JToggleButton("Pixel Twist");
        btnMorph = new JToggleButton("Pixel Morph");
        btnBurst.setSelected(true);

        ButtonGroup effectGroup = new ButtonGroup();
        effectGroup.add(btnBurst); effectGroup.add(btnPop);
        effectGroup.add(btnTwist); effectGroup.add(btnMorph);

        btnBurst.addActionListener(e -> { switchTab("burst", 0); revalidateParent(); });
        btnPop  .addActionListener(e -> { switchTab("pop",   1); revalidateParent(); });
        btnTwist.addActionListener(e -> { switchTab("twist", 2); revalidateParent(); });
        btnMorph.addActionListener(e -> { switchTab("morph", 3); revalidateParent(); });

        btnBurst.addMouseListener(makeEffectReclick(0, "burst"));
        btnPop  .addMouseListener(makeEffectReclick(1, "pop"));
        btnTwist.addMouseListener(makeEffectReclick(2, "twist"));
        btnMorph.addMouseListener(makeEffectReclick(3, "morph"));

        JPanel tabCol = new JPanel(new GridLayout(4, 1, 0, 2));
        tabCol.add(btnBurst); tabCol.add(btnPop); tabCol.add(btnTwist); tabCol.add(btnMorph);
        tabCol.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabCol.setMaximumSize(new Dimension(Integer.MAX_VALUE, tabCol.getPreferredSize().height));
        transformModeControls.add(tabCol);

        add(transformModeControls);

        // ── responsive icons ─────────────────────────────────────────────────
        setupResponsiveIcons();
    }

    public void setActionEditsPanel(ActionEditsPanel p) {
        this.actionEditsPanel = p;
    }

    private MouseAdapter makeEffectReclick(int effectType, String key) {
        return new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (model.getAnimEffectType() == effectType && actionEditsPanel != null) {
                    if (actionEditsPanel.isVisible()) actionEditsPanel.setVisible(false);
                    else actionEditsPanel.showTransformMode(key);
                    revalidateParent();
                }
            }
        };
    }

    private void switchTab(String key, int type) {
        model.setAnimEffectType(type);
        if (actionEditsPanel != null) actionEditsPanel.showTransformMode(key);
    }

    private void revalidateParent() {
        // ActionPanel → JScrollPane viewport → JScrollPane → mainContent
        Container p = getParent();
        if (p != null) p = p.getParent();
        if (p != null) p = p.getParent();
        if (p != null) { p.revalidate(); p.repaint(); }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        comboUpdating = true;
        gridSizeCombo.setSelectedItem(String.valueOf(model.getGridSize()));
        comboUpdating = false;

        btnPencil.setSelected(model.getDrawingTool() == DrawingTool.PENCIL);
        btnLine  .setSelected(model.getDrawingTool() == DrawingTool.LINE);
        btnDrag  .setSelected(model.getDrawingTool() == DrawingTool.DRAG);

        boolean hasImage = model.getBgImage() != null;
        btnFillFromImage.setEnabled(hasImage);
        btnShowBgImage.setEnabled(hasImage);
        btnShowBgImage.setSelected(model.isShowBgImage());

        boolean isTransform = model.getMode() == Mode.TRANSFORM;
        btnDraw     .setSelected(!isTransform);
        btnTransform.setSelected(isTransform);
        drawModeControls    .setVisible(!isTransform);
        transformModeControls.setVisible(isTransform);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private JButton makeBtn(String label, ActionListener al) {
        JButton b = new JButton(label);
        b.addActionListener(al);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
        return b;
    }

    private void confirmReset(int newSize) {
        int choice = JOptionPane.showConfirmDialog(this,
            "Reset grid? All pixels will be lost.", "Confirm Reset",
            JOptionPane.OK_CANCEL_OPTION);
        if (choice == JOptionPane.OK_OPTION) {
            model.resetGrid(newSize);
        } else {
            comboUpdating = true;
            gridSizeCombo.setSelectedItem(String.valueOf(model.getGridSize()));
            comboUpdating = false;
        }
    }

    public void newSprite()  { confirmReset(model.getGridSize()); }

    // ── responsive icons ─────────────────────────────────────────────────────

    private void setupResponsiveIcons() {
        AbstractButton[] btns = {
            btnDraw, btnTransform,
            btnPencil, btnLine, btnDrag,
            btnBurst, btnPop, btnTwist, btnMorph
        };
        for (AbstractButton btn : btns) {
            btn.putClientProperty("fullText", btn.getText());
            btn.putClientProperty("icon",     makeIcon(btn.getText()));
        }
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                boolean collapsed = getWidth() < 80;
                for (AbstractButton btn : btns) {
                    if (collapsed) {
                        btn.setText("");
                        btn.setIcon((Icon) btn.getClientProperty("icon"));
                    } else {
                        btn.setText((String) btn.getClientProperty("fullText"));
                        btn.setIcon(null);
                    }
                }
            }
        });
    }

    private static Icon makeIcon(String label) {
        int sz = 16;
        BufferedImage img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fg = UIManager.getColor("Button.foreground");
        if (fg == null) fg = Color.DARK_GRAY;
        g.setColor(fg);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (label) {
            case "Pencil":
            case "Draw":
                g.drawLine(4, 12, 11, 5);
                g.fillPolygon(new int[]{11, 13, 9}, new int[]{5, 3, 7}, 3);
                g.drawLine(3, 13, 4, 12);
                break;
            case "Line":
                g.fillOval(1, 1, 4, 4);
                g.drawLine(3, 3, 13, 13);
                g.fillOval(11, 11, 4, 4);
                break;
            case "Drag":
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(8, 2, 8, 14);
                g.drawLine(2, 8, 14, 8);
                g.drawLine(8, 2, 6, 4);  g.drawLine(8, 2, 10, 4);
                g.drawLine(8, 14, 6, 12); g.drawLine(8, 14, 10, 12);
                g.drawLine(2, 8, 4, 6);  g.drawLine(2, 8, 4, 10);
                g.drawLine(14, 8, 12, 6); g.drawLine(14, 8, 12, 10);
                break;
            case "Transform":
                g.drawOval(5, 5, 6, 6);
                for (int a = 0; a < 360; a += 45) {
                    double rad = Math.toRadians(a);
                    g.drawLine(8 + (int)(4*Math.cos(rad)), 8 + (int)(4*Math.sin(rad)),
                               8 + (int)(7*Math.cos(rad)), 8 + (int)(7*Math.sin(rad)));
                }
                break;
            case "Pixel Burst":
                for (int a = 0; a < 360; a += 30) {
                    double rad = Math.toRadians(a);
                    g.drawLine(8, 8, 8 + (int)(6*Math.cos(rad)), 8 + (int)(6*Math.sin(rad)));
                }
                break;
            case "Pixel Pop":
                g.drawOval(5, 5, 6, 6);
                for (int a = 0; a < 360; a += 90) {
                    double rad = Math.toRadians(a);
                    g.fillOval(6 + (int)(6*Math.cos(rad)), 6 + (int)(6*Math.sin(rad)), 3, 3);
                }
                break;
            case "Pixel Twist":
                g.drawArc(3, 3, 10, 10, 30, 300);
                g.drawLine(12, 4, 14, 7);
                break;
            case "Pixel Morph":
                g.fillOval(2, 5, 8, 8);
                g.setColor(new Color(fg.getRed()/2+60, fg.getGreen()/2+60, fg.getBlue()/2+60));
                g.fillOval(6, 5, 8, 8);
                break;
            default:
                g.drawString("?", 5, 12);
        }
        g.dispose();
        return new ImageIcon(img);
    }

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

        int targetSize = 16;
        for (Color[][] f : frames)
            if (f.length > targetSize) targetSize = f.length;

        Color[][] aligned0 = alignFrame(frames.get(0), targetSize);
        model.resetGrid(targetSize);
        for (int r = 0; r < targetSize; r++)
            for (int c = 0; c < targetSize; c++)
                if (aligned0[r][c] != null) model.setCellColor(r, c, aligned0[r][c]);

        if (frames.size() > 1) {
            java.util.List<Color[][]> additional = new java.util.ArrayList<>();
            for (int i = 1; i < frames.size(); i++)
                additional.add(alignFrame(frames.get(i), targetSize));
            model.setAdditionalFrames(additional);
        }
    }

    private Color[][] alignFrame(Color[][] src, int canvasSize) {
        int srcSize = src.length;
        int minR = srcSize, maxR = -1, minC = srcSize, maxC = -1;
        for (int r = 0; r < srcSize; r++)
            for (int c = 0; c < srcSize; c++)
                if (src[r][c] != null) {
                    if (r < minR) minR = r;
                    if (r > maxR) maxR = r;
                    if (c < minC) minC = c;
                    if (c > maxC) maxC = c;
                }
        if (maxR < 0) return new Color[canvasSize][canvasSize];

        int contentWidth  = maxC - minC + 1;
        int contentHeight = maxR - minR + 1;

        int dc = canvasSize / 2 - minC - contentWidth / 2;
        int dr = contentWidth > contentHeight
            ? canvasSize / 2 - minR - contentHeight / 2
            : (canvasSize - 1) - maxR;

        Color[][] result = new Color[canvasSize][canvasSize];
        for (int r = 0; r < srcSize; r++)
            for (int c = 0; c < srcSize; c++)
                if (src[r][c] != null) {
                    int nr = r + dr;
                    int nc = c + dc;
                    if (nr >= 0 && nr < canvasSize && nc >= 0 && nc < canvasSize)
                        result[nr][nc] = src[r][c];
                }
        return result;
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
            double minCellW = Double.MAX_VALUE;
            for (int i = 0; i < rects.getLength(); i++) {
                Element r = (Element) rects.item(i);
                double w = parseDouble(r.getAttribute("width"));
                if (w > 0 && w < minCellW) minCellW = w;
                double x = parseDouble(r.getAttribute("x"));
                double y = parseDouble(r.getAttribute("y"));
                if (x < minX) minX = x;
                if (y < minY) minY = y;
            }

            if (minCellW == Double.MAX_VALUE) {
                JOptionPane.showMessageDialog(this, "Could not determine cell size from SVG: " + file.getName());
                return null;
            }
            double cellSize = minCellW;

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
                    "SVG is too large (" + (maxCol + 1) + "×" + (maxRow + 1) + " cells). Max supported: 80×80.");
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

    // ── spriteamation (.sga) ────────────────────────────────────────────────

    public void saveSpritamation() {
        File file = chooseFile(false, "sga");
        if (file == null) return;
        String path = ensureExt(file.getAbsolutePath(), ".sga");
        java.util.List<Color[][]> frames = model.getAnimationFrames();
        int gridSize = model.getGridSize();
        int canvasPx = gridSize * 4;
        int saved = 0;
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path))) {
            for (int fi = 0; fi < frames.size(); fi++) {
                Color[][] frame = frames.get(fi);
                boolean hasContent = false;
                outer:
                for (int r = 0; r < frame.length; r++)
                    for (int c = 0; c < frame[r].length; c++)
                        if (frame[r][c] != null) { hasContent = true; break outer; }
                if (!hasContent) continue;
                StringBuilder sb = new StringBuilder();
                sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                sb.append(String.format("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">%n",
                    canvasPx, canvasPx, canvasPx, canvasPx));
                for (int r = 0; r < frame.length; r++)
                    for (int c = 0; c < frame[r].length; c++)
                        if (frame[r][c] != null)
                            sb.append(String.format("  <rect x=\"%d\" y=\"%d\" width=\"4\" height=\"4\" fill=\"#%s\"/>%n",
                                c * 4, r * 4, hex(frame[r][c])));
                sb.append("</svg>");
                byte[] data = sb.toString().getBytes("UTF-8");
                zos.putNextEntry(new ZipEntry("frame_" + fi + ".svg"));
                zos.write(data);
                zos.closeEntry();
                saved++;
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Save Spriteamation failed: " + ex.getMessage());
            return;
        }
        if (saved == 0)
            JOptionPane.showMessageDialog(this, "No non-empty frames to save.");
    }

    public void loadSpritamation() {
        File file = chooseFile(true, "sga");
        if (file == null) return;
        java.util.TreeMap<String, byte[]> entries = new java.util.TreeMap<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".svg")) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zis.read(buf)) != -1) baos.write(buf, 0, n);
                    entries.put(entry.getName(), baos.toByteArray());
                }
                zis.closeEntry();
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Load Spriteamation failed: " + ex.getMessage());
            return;
        }
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No SVG frames found in spriteamation file.");
            return;
        }
        java.util.List<Color[][]> frames = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, byte[]> e : entries.entrySet()) {
            Color[][] grid = parseSvgBytes(e.getValue(), e.getKey());
            if (grid != null) frames.add(grid);
        }
        if (frames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No valid frames loaded from spriteamation.");
            return;
        }
        int targetSize = 16;
        for (Color[][] f : frames) if (f.length > targetSize) targetSize = f.length;
        Color[][] first = frames.get(0);
        model.resetGrid(targetSize);
        for (int r = 0; r < first.length; r++)
            for (int c = 0; c < first[r].length; c++)
                if (first[r][c] != null) model.setCellColor(r, c, first[r][c]);
        if (frames.size() > 1) {
            java.util.List<Color[][]> additional = new java.util.ArrayList<>();
            for (int i = 1; i < frames.size(); i++) additional.add(frames.get(i));
            model.setAdditionalFrames(additional);
        }
    }

    private Color[][] parseSvgBytes(byte[] data, String entryName) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(data));
            NodeList rects = doc.getElementsByTagNameNS("*", "rect");
            if (rects.getLength() == 0) {
                JOptionPane.showMessageDialog(this, "No rect elements found in: " + entryName);
                return null;
            }
            double minCellW = Double.MAX_VALUE;
            for (int i = 0; i < rects.getLength(); i++) {
                double w = parseDouble(((Element) rects.item(i)).getAttribute("width"));
                if (w > 0 && w < minCellW) minCellW = w;
            }
            if (minCellW == Double.MAX_VALUE) {
                JOptionPane.showMessageDialog(this, "Could not determine cell size from: " + entryName);
                return null;
            }
            double cellSize = minCellW;
            java.util.List<int[]> cells = new java.util.ArrayList<>();
            int maxCol = 0, maxRow = 0;
            for (int i = 0; i < rects.getLength(); i++) {
                Element r = (Element) rects.item(i);
                Color c = parseSvgColor(r);
                if (c == null) continue;
                int col = (int) Math.round(parseDouble(r.getAttribute("x")) / cellSize);
                int row = (int) Math.round(parseDouble(r.getAttribute("y")) / cellSize);
                cells.add(new int[]{ col, row, c.getRGB() });
                if (col > maxCol) maxCol = col;
                if (row > maxRow) maxRow = row;
            }
            if (cells.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No colored cells found in: " + entryName);
                return null;
            }
            int gridSize = -1;
            for (int s : new int[]{ 16, 32, 48, 64, 80 })
                if (maxCol < s && maxRow < s) { gridSize = s; break; }
            if (gridSize == -1) {
                JOptionPane.showMessageDialog(this, "Frame too large in: " + entryName);
                return null;
            }
            Color[][] grid = new Color[gridSize][gridSize];
            for (int[] cell : cells) grid[cell[1]][cell[0]] = new Color(cell[2]);
            return grid;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Parse failed (" + entryName + "): " + ex.getMessage());
            return null;
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

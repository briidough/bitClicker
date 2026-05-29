package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import com.atari.spritemaker.model.SpriteModel.DrawingTool;
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
    private final JToggleButton btnPencil, btnLine;
    private final JToggleButton btn32, btn48, btn64, btn80;
    private JButton btnFillFromImage;
    private JToggleButton btnShowBgImage;
    private java.io.File lastDir = null;

    public ActionPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(makeBtn("New Sprite",  e -> confirmReset(model.getGridSize())));
        add(Box.createVerticalStrut(4));
        add(makeBtn("Load Sprite", e -> loadSprite()));
        add(Box.createVerticalStrut(4));
        add(makeBtn("Save Sprite", e -> saveSprite()));
        add(Box.createVerticalStrut(4));
        add(makeBtn("Export SVG",  e -> exportSvg()));
        add(Box.createVerticalStrut(4));
        add(makeBtn("Load SVG",    e -> loadSvg()));
        add(Box.createVerticalStrut(4));
        add(makeBtn("Paste Image", e -> pasteImage()));
        add(Box.createVerticalStrut(4));
        btnFillFromImage = makeBtn("Fill from Image", e -> fillFromImage());
        btnFillFromImage.setEnabled(false);
        add(btnFillFromImage);

        add(Box.createVerticalStrut(12));
        JLabel toolLbl = new JLabel("Drawing Tool:");
        toolLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(toolLbl);
        add(Box.createVerticalStrut(4));

        btnPencil = new JToggleButton("Pencil");
        btnLine   = new JToggleButton("Line");
        btnPencil.setSelected(true);

        ButtonGroup toolGrp = new ButtonGroup();
        toolGrp.add(btnPencil); toolGrp.add(btnLine);

        btnPencil.addActionListener(e -> model.setDrawingTool(DrawingTool.PENCIL));
        btnLine  .addActionListener(e -> model.setDrawingTool(DrawingTool.LINE));

        JPanel toolRow = new JPanel(new GridLayout(1, 2, 2, 0));
        toolRow.add(btnPencil); toolRow.add(btnLine);
        toolRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, toolRow.getPreferredSize().height));
        add(toolRow);

        add(Box.createVerticalStrut(12));
        JLabel lbl = new JLabel("Grid Size:");
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lbl);
        add(Box.createVerticalStrut(4));

        btn32  = new JToggleButton("32");
        btn48  = new JToggleButton("48");
        btn64  = new JToggleButton("64");
        btn80 = new JToggleButton("80");
        btn32.setSelected(true);

        ButtonGroup grp = new ButtonGroup();
        grp.add(btn32); grp.add(btn48); grp.add(btn64); grp.add(btn80);

        btn32 .addActionListener(e -> confirmReset(32));
        btn48 .addActionListener(e -> confirmReset(48));
        btn64 .addActionListener(e -> confirmReset(64));
        btn80.addActionListener(e -> confirmReset(80));

        JPanel sizeRow = new JPanel(new GridLayout(1, 4, 2, 0));
        sizeRow.add(btn32); sizeRow.add(btn48); sizeRow.add(btn64); sizeRow.add(btn80);
        sizeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, sizeRow.getPreferredSize().height));
        add(sizeRow);

        add(Box.createVerticalStrut(12));
        btnShowBgImage = new JToggleButton("Show Pasted Image");
        btnShowBgImage.setSelected(true);
        btnShowBgImage.setEnabled(false);
        btnShowBgImage.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnShowBgImage.setMaximumSize(new Dimension(Integer.MAX_VALUE, btnShowBgImage.getPreferredSize().height));
        btnShowBgImage.addActionListener(e -> model.setShowBgImage(btnShowBgImage.isSelected()));
        add(btnShowBgImage);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        int size = model.getGridSize();
        btn32 .setSelected(size == 32);
        btn48 .setSelected(size == 48);
        btn64 .setSelected(size == 64);
        btn80.setSelected(size == 80);
        btnPencil.setSelected(model.getDrawingTool() == DrawingTool.PENCIL);
        btnLine  .setSelected(model.getDrawingTool() == DrawingTool.LINE);
        boolean hasImage = model.getBgImage() != null;
        btnFillFromImage.setEnabled(hasImage);
        btnShowBgImage.setEnabled(hasImage);
        btnShowBgImage.setSelected(model.isShowBgImage());
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
            int cur = model.getGridSize();
            btn32 .setSelected(cur == 32);
            btn48 .setSelected(cur == 48);
            btn64 .setSelected(cur == 64);
            btn80.setSelected(cur == 80);
        }
    }

    // ── I/O ─────────────────────────────────────────────────────────────────

    private void loadSprite() {
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

    private void saveSprite() {
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

    private void exportSvg() {
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

    private void loadSvg() {
        File file = chooseFile(true, "svg");
        if (file == null) return;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(file);
            NodeList rects = doc.getElementsByTagNameNS("*", "rect");
            if (rects.getLength() == 0) {
                JOptionPane.showMessageDialog(this, "No rect elements found in SVG.");
                return;
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
                JOptionPane.showMessageDialog(this, "Could not determine cell size from SVG.");
                return;
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
                JOptionPane.showMessageDialog(this, "No colored cells found in SVG.");
                return;
            }

            int gridSize = -1;
            for (int s : new int[]{ 32, 48, 64, 80 }) {
                if (maxCol < s && maxRow < s) { gridSize = s; break; }
            }
            if (gridSize == -1) {
                JOptionPane.showMessageDialog(this,
                    "SVG is too large (" + (maxCol + 1) + "×" + (maxRow + 1) + " cells). Max supported: 80×80.");
                return;
            }

            model.resetGrid(gridSize);
            for (int[] cell : cells)
                model.setCellColor(cell[1], cell[0], new Color(cell[2]));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Load SVG failed: " + ex.getMessage());
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
                // Try image/* stream flavors
                for (DataFlavor f : t.getTransferDataFlavors()) {
                    if (f.getMimeType().startsWith("image/")
                            && InputStream.class.isAssignableFrom(f.getRepresentationClass())) {
                        try (InputStream is = (InputStream) t.getTransferData(f)) {
                            bi = ImageIO.read(is);
                            if (bi != null) break;
                        } catch (Exception ignored) {}
                    }
                }
                // GTK image viewers copy a file URI (text/uri-list) rather than image bytes
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

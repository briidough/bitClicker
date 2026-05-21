package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.SpriteModel;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class ActionPanel extends JPanel implements ChangeListener {

    private final SpriteModel model;
    private final JToggleButton btn32, btn64, btn128;

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

        add(Box.createVerticalStrut(12));
        JLabel lbl = new JLabel("Grid Size:");
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(lbl);
        add(Box.createVerticalStrut(4));

        btn32  = new JToggleButton("32");
        btn64  = new JToggleButton("64");
        btn128 = new JToggleButton("128");
        btn32.setSelected(true);

        ButtonGroup grp = new ButtonGroup();
        grp.add(btn32); grp.add(btn64); grp.add(btn128);

        btn32 .addActionListener(e -> confirmReset(32));
        btn64 .addActionListener(e -> confirmReset(64));
        btn128.addActionListener(e -> confirmReset(128));

        JPanel sizeRow = new JPanel(new GridLayout(1, 3, 2, 0));
        sizeRow.add(btn32); sizeRow.add(btn64); sizeRow.add(btn128);
        sizeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, sizeRow.getPreferredSize().height));
        add(sizeRow);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        int size = model.getGridSize();
        btn32 .setSelected(size == 32);
        btn64 .setSelected(size == 64);
        btn128.setSelected(size == 128);
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
            btn64 .setSelected(cur == 64);
            btn128.setSelected(cur == 128);
        }
    }

    // ── I/O ─────────────────────────────────────────────────────────────────

    private void loadSprite() {
        File file = chooseFile(true, "spr");
        if (file == null) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            int newSize = 32;
            Color[] newPalette = new Color[4];
            Color[][] newGrid = null;
            String section = null, line;
            int row = 0;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("SIZE=")) {
                    newSize = Integer.parseInt(line.substring(5).trim());
                    newGrid = new Color[newSize][newSize];
                } else if (line.startsWith("PALETTE=")) {
                    String[] parts = line.substring(8).split(",", -1);
                    for (int i = 0; i < 4 && i < parts.length; i++)
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
                for (int i = 0; i < 4; i++)
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
            for (int i = 0; i < 4; i++) {
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
        fc.setFileFilter(new FileNameExtensionFilter(ext.toUpperCase() + " files", ext));
        int result = open ? fc.showOpenDialog(this) : fc.showSaveDialog(this);
        return result == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
    }
}

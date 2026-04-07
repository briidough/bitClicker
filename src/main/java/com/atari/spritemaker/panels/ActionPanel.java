package com.atari.spritemaker.panels;

import com.atari.spritemaker.model.AtariPalette;
import com.atari.spritemaker.model.SpriteModel;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

/**
 * Left panel containing the four action buttons.
 * File I/O is handled here; the model is updated accordingly and its
 * change listeners propagate the update to the other panels.
 */
public class ActionPanel extends JPanel {

    private final SpriteModel model;

    public ActionPanel(SpriteModel model) {
        this.model = model;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createTitledBorder("Actions"));
        setPreferredSize(new Dimension(150, 0));

        addButton("New Sprite",    this::newSprite);
        addButton("Load Sprite",   this::loadSprite);
        addButton("Save Sprite",   this::saveSprite);
        addButton("Export Sprite", this::exportSprite);
    }

    private void addButton(String label, Runnable action) {
        JButton btn = new JButton(label);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(130, 30));
        btn.addActionListener(e -> action.run());
        add(Box.createVerticalStrut(12));
        add(btn);
    }

    // -------------------------------------------------------------------------

    private void newSprite() {
        int choice = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                "Create a new sprite? Unsaved changes will be lost.",
                "New Sprite", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            model.resetGrid();
        }
    }

    private void loadSprite() {
        JFileChooser chooser = fileChooser(".spr", "Sprite files (*.spr)");
        if (chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this))
                == JFileChooser.APPROVE_OPTION) {
            try {
                loadFromFile(chooser.getSelectedFile());
            } catch (IOException | IllegalArgumentException ex) {
                showError("Error loading file:\n" + ex.getMessage());
            }
        }
    }

    private void saveSprite() {
        JFileChooser chooser = fileChooser(".spr", "Sprite files (*.spr)");
        if (model.getFilePath() != null) {
            chooser.setSelectedFile(new File(model.getFilePath()));
        }
        if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this))
                == JFileChooser.APPROVE_OPTION) {
            File file = ensureExtension(chooser.getSelectedFile(), ".spr");
            try {
                saveToFile(file);
                model.setFilePath(file.getAbsolutePath());
            } catch (IOException ex) {
                showError("Error saving file:\n" + ex.getMessage());
            }
        }
    }

    private void exportSprite() {
        JFileChooser chooser = fileChooser(".png", "PNG images (*.png)");
        if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this))
                == JFileChooser.APPROVE_OPTION) {
            File file = ensureExtension(chooser.getSelectedFile(), ".png");
            try {
                exportAsPng(file);
            } catch (IOException ex) {
                showError("Error exporting file:\n" + ex.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // File format helpers
    // -------------------------------------------------------------------------

    /**
     * Native save format (.spr):
     * <pre>
     * REGION=NTSC
     * PALETTE=FF0000,00FF00,0000FF
     * GRID=
     * 000000,000000,...   (16 values, 16 rows)
     * </pre>
     */
    private void saveToFile(File file) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("REGION=" + model.getRegion().name());

            Color[] pal = model.getPalette();
            w.printf("PALETTE=%s,%s,%s%n",
                    hex(pal[0]), hex(pal[1]), hex(pal[2]));

            w.println("GRID=");
            for (int row = 0; row < SpriteModel.GRID_SIZE; row++) {
                StringBuilder sb = new StringBuilder();
                for (int col = 0; col < SpriteModel.GRID_SIZE; col++) {
                    if (col > 0) sb.append(',');
                    sb.append(hex(model.getCellColor(row, col)));
                }
                w.println(sb);
            }
        }
    }

    private void loadFromFile(File file) throws IOException {
        model.resetGrid();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            int gridRow = 0;
            boolean inGrid = false;

            while ((line = r.readLine()) != null) {
                if (line.startsWith("REGION=")) {
                    model.setRegion(AtariPalette.Region.valueOf(line.substring(7).trim()));

                } else if (line.startsWith("PALETTE=")) {
                    String[] parts = line.substring(8).split(",");
                    for (int i = 0; i < Math.min(3, parts.length); i++) {
                        model.setPaletteSlotColor(i, parseColor(parts[i].trim()));
                    }

                } else if (line.equals("GRID=")) {
                    inGrid = true;

                } else if (inGrid && gridRow < SpriteModel.GRID_SIZE) {
                    String[] parts = line.split(",");
                    for (int col = 0; col < Math.min(parts.length, SpriteModel.GRID_SIZE); col++) {
                        model.setCellColor(gridRow, col, parseColor(parts[col].trim()));
                    }
                    gridRow++;
                }
            }
        }
        model.setFilePath(file.getAbsolutePath());
    }

    /** Exports the sprite as a 16×16 PNG (one pixel per cell). */
    private void exportAsPng(File file) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                img.setRGB(col, row, model.getCellColor(row, col).getRGB());
            }
        }
        ImageIO.write(img, "PNG", file);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static String hex(Color c) {
        return String.format("%06X", c.getRGB() & 0xFFFFFF);
    }

    private static Color parseColor(String hex) {
        return new Color(Integer.parseInt(hex, 16));
    }

    private static File ensureExtension(File file, String ext) {
        return file.getName().endsWith(ext) ? file : new File(file.getAbsolutePath() + ext);
    }

    private static JFileChooser fileChooser(String ext, String description) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(description, ext.replace(".", "")));
        return chooser;
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
                msg, "Error", JOptionPane.ERROR_MESSAGE);
    }
}

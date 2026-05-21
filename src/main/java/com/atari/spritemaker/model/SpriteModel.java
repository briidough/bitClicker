package com.atari.spritemaker.model;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SpriteModel {

    public enum DrawingTool { PENCIL, LINE }

    private int gridSize = 32;
    private final String author = "Briidough";
    private Color[][] grid = new Color[32][32];
    private Color activeColor = Color.BLACK;
    private final Color[] palette = { Color.BLACK, null, null, null, null };
    private int selectedPaletteSlot = 0;
    private DrawingTool drawingTool = DrawingTool.PENCIL;
    private BufferedImage bgImage = null;
    private boolean showBgImage = true;
    private String filePath = null;
    private final List<ChangeListener> listeners = new ArrayList<>();

    public int getGridSize() { return gridSize; }
    public String getAuthor() { return author; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String path) { filePath = path; }

    public Color getCellColor(int row, int col) { return grid[row][col]; }

    public void setCellColor(int row, int col, Color color) {
        Color current = grid[row][col];
        if (current == color) return;
        if (current != null && current.equals(color)) return;
        grid[row][col] = color;
        fireChange();
    }

    public DrawingTool getDrawingTool() { return drawingTool; }
    public void setDrawingTool(DrawingTool t) { drawingTool = t; fireChange(); }

    public BufferedImage getBgImage() { return bgImage; }
    public void setBgImage(BufferedImage img) { bgImage = img; showBgImage = true; fireChange(); }
    public boolean isShowBgImage() { return showBgImage; }
    public void setShowBgImage(boolean show) { showBgImage = show; fireChange(); }

    public Color getActiveColor() { return activeColor; }
    public Color[] getPalette() { return palette; }
    public int getSelectedPaletteSlot() { return selectedPaletteSlot; }

    public void selectPaletteSlot(int slot) {
        if (selectedPaletteSlot == slot) {
            selectedPaletteSlot = -1;
            activeColor = null;
        } else {
            selectedPaletteSlot = slot;
            activeColor = palette[slot];
        }
        fireChange();
    }

    public void setPaletteSlotColor(int slot, Color color) {
        palette[slot] = color;
        if (selectedPaletteSlot == slot) activeColor = color;
        fireChange();
    }

    public void resetGrid(int newSize) {
        gridSize = newSize;
        grid = new Color[newSize][newSize];
        for (int i = 0; i < 5; i++) palette[i] = null;
        palette[0] = Color.BLACK;
        selectedPaletteSlot = 0;
        activeColor = Color.BLACK;
        filePath = null;
        bgImage = null;
        showBgImage = true;
        fireChange();
    }

    public void addChangeListener(ChangeListener l) { listeners.add(l); }

    private void fireChange() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }
}

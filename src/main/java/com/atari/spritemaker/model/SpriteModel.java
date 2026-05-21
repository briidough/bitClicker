package com.atari.spritemaker.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SpriteModel {

    private int gridSize = 32;
    private final String author = "Briidough";
    private Color[][] grid = new Color[32][32];
    private Color activeColor = null;
    private final Color[] palette = new Color[4];
    private int selectedPaletteSlot = -1;
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
        for (int i = 0; i < 4; i++) palette[i] = null;
        selectedPaletteSlot = -1;
        activeColor = null;
        filePath = null;
        fireChange();
    }

    public void addChangeListener(ChangeListener l) { listeners.add(l); }

    private void fireChange() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }
}

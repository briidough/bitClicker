package com.atari.spritemaker.model;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class SpriteModel {

    public static final int GRID_SIZE = 16;

    private final Color[][] grid = new Color[GRID_SIZE][GRID_SIZE];
    private Color activeColor = Color.BLACK;
    private final Color[] palette = new Color[3];
    private int selectedPaletteSlot = -1;
    private AtariPalette.Region region = AtariPalette.Region.NTSC;
    private String filePath = null;

    private final List<ChangeListener> listeners = new ArrayList<>();

    public SpriteModel() {
        initGrid();
        palette[0] = Color.BLACK;
        palette[1] = Color.BLACK;
        palette[2] = Color.BLACK;
    }

    private void initGrid() {
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                grid[r][c] = Color.BLACK;
            }
        }
    }

    // --- Grid access ---------------------------------------------------------

    public Color getCellColor(int row, int col) {
        return grid[row][col];
    }

    public void setCellColor(int row, int col, Color color) {
        if (!grid[row][col].equals(color)) {
            grid[row][col] = color;
            fireChange();
        }
    }

    // --- Active color --------------------------------------------------------

    public Color getActiveColor() {
        return activeColor;
    }

    // --- Palette slots -------------------------------------------------------

    public Color[] getPalette() {
        return palette;
    }

    public int getSelectedPaletteSlot() {
        return selectedPaletteSlot;
    }

    /**
     * Clicking a slot makes it the active color source.
     * Clicking the already-selected slot deselects it and resets active to black.
     */
    public void selectPaletteSlot(int slot) {
        if (selectedPaletteSlot == slot) {
            selectedPaletteSlot = -1;
            activeColor = Color.BLACK;
        } else {
            selectedPaletteSlot = slot;
            activeColor = palette[slot];
        }
        fireChange();
    }

    /** Assigns a color to a palette slot (called when user picks from region picker). */
    public void setPaletteSlotColor(int slot, Color color) {
        palette[slot] = color;
        if (selectedPaletteSlot == slot) {
            activeColor = color;
        }
        fireChange();
    }

    // --- Region --------------------------------------------------------------

    public AtariPalette.Region getRegion() {
        return region;
    }

    public void setRegion(AtariPalette.Region region) {
        this.region = region;
        fireChange();
    }

    // --- File path -----------------------------------------------------------

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    // --- Reset ---------------------------------------------------------------

    public void resetGrid() {
        initGrid();
        palette[0] = Color.BLACK;
        palette[1] = Color.BLACK;
        palette[2] = Color.BLACK;
        selectedPaletteSlot = -1;
        activeColor = Color.BLACK;
        filePath = null;
        fireChange();
    }

    // --- Listeners -----------------------------------------------------------

    public void addChangeListener(ChangeListener l) {
        listeners.add(l);
    }

    private void fireChange() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener l : listeners) {
            l.stateChanged(event);
        }
    }
}

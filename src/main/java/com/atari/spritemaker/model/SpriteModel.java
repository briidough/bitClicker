package com.atari.spritemaker.model;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class SpriteModel {

    public enum DrawingTool { PENCIL, LINE, DRAG }
    public enum Mode { DRAW, TRANSFORM }

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
    private Mode mode = Mode.DRAW;
    private List<Color[][]> animationFrames = new ArrayList<>();
    private int animSpread  = 24;
    private int animSpeedMs = 500;
    private int animHoldMs  = 200;
    private int animEasing  = 0;   // 0=Smooth, 1=Sharp, 2=Snappy
    private int animFocalX  = 50;  // % of canvas width
    private int animFocalY  = 50;  // % of canvas height
    private int animSpin    = 0;   // 0=None, 1=CW, 2=CCW
    private int animSpinStrength = 100; // 0=radial, 100=tangential
    private boolean focalActive = false;
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
        animationFrames = new ArrayList<>();
        for (int i = 0; i < 5; i++) palette[i] = null;
        palette[0] = Color.BLACK;
        selectedPaletteSlot = 0;
        activeColor = Color.BLACK;
        filePath = null;
        bgImage = null;
        showBgImage = true;
        fireChange();
    }

    public Mode getMode() { return mode; }
    public void setMode(Mode m) { mode = m; fireChange(); }

    public void setGrid(Color[][] newGrid) {
        int size = newGrid.length;
        for (int r = 0; r < size; r++)
            grid[r] = java.util.Arrays.copyOf(newGrid[r], size);
        fireChange();
    }

    public Color[][] getGridCopy() {
        Color[][] copy = new Color[gridSize][gridSize];
        for (int r = 0; r < gridSize; r++)
            copy[r] = java.util.Arrays.copyOf(grid[r], gridSize);
        return copy;
    }

    public List<Color[][]> getAnimationFrames() { return animationFrames; }
    public void setAnimationFrames(List<Color[][]> frames) { animationFrames = frames; fireChange(); }

    public int getAnimSpread()   { return animSpread; }
    public void setAnimSpread(int v)  { animSpread = v; }
    public int getAnimSpeedMs()  { return animSpeedMs; }
    public void setAnimSpeedMs(int v) { animSpeedMs = v; }
    public int getAnimHoldMs()   { return animHoldMs; }
    public void setAnimHoldMs(int v)  { animHoldMs = v; }
    public int getAnimEasing()   { return animEasing; }
    public void setAnimEasing(int v)  { animEasing = v; }
    public int getAnimFocalX()   { return animFocalX; }
    public void setAnimFocalX(int v)  { animFocalX = v; fireChange(); }
    public int getAnimFocalY()   { return animFocalY; }
    public void setAnimFocalY(int v)  { animFocalY = v; fireChange(); }
    public int getAnimSpin()     { return animSpin; }
    public void setAnimSpin(int v)    { animSpin = v; }
    public int getAnimSpinStrength()  { return animSpinStrength; }
    public void setAnimSpinStrength(int v) { animSpinStrength = v; }
    public boolean isFocalActive()    { return focalActive; }
    public void setFocalActive(boolean v) { focalActive = v; fireChange(); }

    public void addChangeListener(ChangeListener l) { listeners.add(l); }

    private void fireChange() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }
}

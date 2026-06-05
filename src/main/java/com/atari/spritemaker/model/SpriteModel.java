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
    private int animEffectType    = 0;    // 0=PixelBurst, 1=PixelPop
    private int animGravityPush   = 50;
    private int animGravityPull   = 50;
    private int animGravityFocalX = 50;
    private int animGravityFocalY = 100;
    private int animExplodeSpeedMs    = 1000;
    private int animUnsplodeSpeedMs   = 1000;
    private int animExplodeStrength   = 100; // 50–150 (% multiplier on push energy)
    private int animUnsplodeStrength  = 95;  // 85–99 (snap threshold %)
    private boolean animStayInCanvas  = false;
    private int animPopHoldMs   = 0;
    private int animExtendMs    = 0;
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
    public void setAnimFocalX(int v)  { if (animFocalX == v) return; animFocalX = v; fireChange(); }
    public int getAnimFocalY()   { return animFocalY; }
    public void setAnimFocalY(int v)  { if (animFocalY == v) return; animFocalY = v; fireChange(); }
    public int getAnimSpin()     { return animSpin; }
    public void setAnimSpin(int v)    { animSpin = v; }
    public int getAnimSpinStrength()  { return animSpinStrength; }
    public void setAnimSpinStrength(int v) { animSpinStrength = v; }
    public int getAnimEffectType()        { return animEffectType; }
    public void setAnimEffectType(int v)  { animEffectType = v; }
    public int getAnimGravityPush()       { return animGravityPush; }
    public void setAnimGravityPush(int v) { animGravityPush = v; }
    public int getAnimGravityPull()       { return animGravityPull; }
    public void setAnimGravityPull(int v) { animGravityPull = v; }
    public int getAnimGravityFocalX()        { return animGravityFocalX; }
    public void setAnimGravityFocalX(int v)  { if (animGravityFocalX == v) return; animGravityFocalX = v; fireChange(); }
    public int getAnimGravityFocalY()        { return animGravityFocalY; }
    public void setAnimGravityFocalY(int v)  { if (animGravityFocalY == v) return; animGravityFocalY = v; fireChange(); }
    public int getAnimExplodeSpeedMs()           { return animExplodeSpeedMs; }
    public void setAnimExplodeSpeedMs(int v)     { animExplodeSpeedMs = v; }
    public int getAnimUnsplodeSpeedMs()          { return animUnsplodeSpeedMs; }
    public void setAnimUnsplodeSpeedMs(int v)    { animUnsplodeSpeedMs = v; }
    public int getAnimExplodeStrength()          { return animExplodeStrength; }
    public void setAnimExplodeStrength(int v)    { animExplodeStrength = v; }
    public int getAnimUnsplodeStrength()         { return animUnsplodeStrength; }
    public void setAnimUnsplodeStrength(int v)   { animUnsplodeStrength = v; }
    public boolean isAnimStayInCanvas()          { return animStayInCanvas; }
    public void setAnimStayInCanvas(boolean v)   { animStayInCanvas = v; }
    public int getAnimPopHoldMs()                { return animPopHoldMs; }
    public void setAnimPopHoldMs(int v)          { animPopHoldMs = v; }
    public int getAnimExtendMs()                 { return animExtendMs; }
    public void setAnimExtendMs(int v)           { animExtendMs = v; }
    public boolean isFocalActive()    { return focalActive; }
    public void setFocalActive(boolean v) { if (focalActive == v) return; focalActive = v; fireChange(); }

    public void addChangeListener(ChangeListener l) { listeners.add(l); }

    private void fireChange() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }
}

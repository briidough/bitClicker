package com.atari.spritemaker.model;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private int currentFrameIndex = 0;
    private List<BufferedImage> frameBackgrounds = new ArrayList<>();
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
    private int animExtendMs    = 500;
    private boolean focalActive = false;
    private int     animTwistFirstSpeedMs  = 300;
    private int     animTwistSecondSpeedMs = 300;
    private int     animTwistFirstSmooth   = 50;
    private int     animTwistSecondSmooth  = 50;
    private int     animTwistDirection     = 0;
    private boolean animTwistFullSpin      = true;
    private boolean animTwistSpreadGap     = false;
    private boolean animMorphFadeDeaths    = false;
    private boolean animPopStayAtFocus     = false;
    private int     animWallDamping        = 50;
    private int animMorphSpeedMs = 600;
    private int animMorphHoldMs  = 300;
    private int animSpringStiffness = 30;
    private int animSpringDamping   = 30;
    private int animSpringImpulse   = 40;
    private int animSpringSpeedMs   = 1400;
    private int animSpringHoldMs    = 300;
    private final List<ChangeListener> listeners = new ArrayList<>();
    private final List<Runnable> transformListeners = new ArrayList<>();

    private static final int MAX_HISTORY = 50;
    private final ArrayDeque<Color[][]> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Color[][]> redoStack = new ArrayDeque<>();

    // Unique Frame Transforms
    private final List<TransformSettings> uftSettings = new ArrayList<>();
    private final Set<Integer> uftEnabled = new HashSet<>();
    private int selectedUFTIndex = -1;
    private boolean fullAnimationMode = false;

    public SpriteModel() {
        animationFrames.add(grid);
        frameBackgrounds.add(null);
        ensureUFTCapacity();
    }

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
    public void setActiveColor(Color c) { activeColor = c; fireChange(); }
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
        animationFrames.add(grid);
        frameBackgrounds = new ArrayList<>();
        frameBackgrounds.add(null);
        currentFrameIndex = 0;
        for (int i = 0; i < 5; i++) palette[i] = null;
        palette[0] = Color.BLACK;
        selectedPaletteSlot = 0;
        activeColor = Color.BLACK;
        filePath = null;
        bgImage = null;
        showBgImage = true;
        uftSettings.clear();
        uftEnabled.clear();
        selectedUFTIndex = -1;
        fullAnimationMode = false;
        undoStack.clear();
        redoStack.clear();
        applySettingsSilently(new TransformSettings());
        ensureUFTCapacity();
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

    public void pushUndoSnapshot() {
        if (undoStack.size() >= MAX_HISTORY) undoStack.pollLast();
        undoStack.push(getGridCopy());
        redoStack.clear();
    }

    public void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.push(getGridCopy());
        setGrid(undoStack.pop());
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.push(getGridCopy());
        setGrid(redoStack.pop());
    }

    public List<Color[][]> getAnimationFrames() { return animationFrames; }
    public void setAnimationFrames(List<Color[][]> frames) { animationFrames = frames; fireChange(); }

    public int getCurrentFrameIndex() { return currentFrameIndex; }
    public int getFrameCount() { return animationFrames.size(); }

    public void switchToFrame(int index) {
        if (index < 0 || index >= animationFrames.size() || index == currentFrameIndex) return;
        frameBackgrounds.set(currentFrameIndex, bgImage);
        currentFrameIndex = index;
        grid = animationFrames.get(index);
        bgImage = frameBackgrounds.get(index);
        showBgImage = true;
        undoStack.clear();
        redoStack.clear();
        fireChange();
    }

    public void addFrame() {
        if (animationFrames.size() >= 6) return;
        frameBackgrounds.set(currentFrameIndex, bgImage);
        Color[][] blank = new Color[gridSize][gridSize];
        animationFrames.add(blank);
        frameBackgrounds.add(null);
        currentFrameIndex = animationFrames.size() - 1;
        grid = animationFrames.get(currentFrameIndex);
        bgImage = null;
        ensureUFTCapacity();
        fireChange();
    }

    public void setAdditionalFrames(List<Color[][]> frames) {
        frameBackgrounds.set(currentFrameIndex, bgImage);
        while (animationFrames.size() > 1) animationFrames.remove(animationFrames.size() - 1);
        while (frameBackgrounds.size() > 1) frameBackgrounds.remove(frameBackgrounds.size() - 1);
        int count = Math.min(frames.size(), 5);
        for (int i = 0; i < count; i++) {
            animationFrames.add(frames.get(i));
            frameBackgrounds.add(null);
        }
        if (currentFrameIndex >= animationFrames.size()) {
            currentFrameIndex = 0;
            grid = animationFrames.get(0);
            bgImage = frameBackgrounds.get(0);
        }
        ensureUFTCapacity();
        fireChange();
    }

    public int getAnimSpread()   { return animSpread; }
    public void setAnimSpread(int v)  { animSpread = v; fireTransformChange(); }
    public int getAnimSpeedMs()  { return animSpeedMs; }
    public void setAnimSpeedMs(int v) { animSpeedMs = v; fireTransformChange(); }
    public int getAnimHoldMs()   { return animHoldMs; }
    public void setAnimHoldMs(int v)  { animHoldMs = v; fireTransformChange(); }
    public int getAnimEasing()   { return animEasing; }
    public void setAnimEasing(int v)  { animEasing = v; fireTransformChange(); }
    public int getAnimFocalX()   { return animFocalX; }
    public void setAnimFocalX(int v)  { if (animFocalX == v) return; animFocalX = v; fireChange(); fireTransformChange(); }
    public int getAnimFocalY()   { return animFocalY; }
    public void setAnimFocalY(int v)  { if (animFocalY == v) return; animFocalY = v; fireChange(); fireTransformChange(); }
    public int getAnimSpin()     { return animSpin; }
    public void setAnimSpin(int v)    { animSpin = v; fireTransformChange(); }
    public int getAnimSpinStrength()  { return animSpinStrength; }
    public void setAnimSpinStrength(int v) { animSpinStrength = v; fireTransformChange(); }
    public int getAnimEffectType()        { return animEffectType; }
    public void setAnimEffectType(int v)  { if (animEffectType == v) return; animEffectType = v; fireChange(); fireTransformChange(); }
    public int getAnimGravityPush()       { return animGravityPush; }
    public void setAnimGravityPush(int v) { animGravityPush = v; fireTransformChange(); }
    public int getAnimGravityPull()       { return animGravityPull; }
    public void setAnimGravityPull(int v) { animGravityPull = v; fireTransformChange(); }
    public int getAnimGravityFocalX()        { return animGravityFocalX; }
    public void setAnimGravityFocalX(int v)  { if (animGravityFocalX == v) return; animGravityFocalX = v; fireChange(); fireTransformChange(); }
    public int getAnimGravityFocalY()        { return animGravityFocalY; }
    public void setAnimGravityFocalY(int v)  { if (animGravityFocalY == v) return; animGravityFocalY = v; fireChange(); fireTransformChange(); }
    public int getAnimExplodeSpeedMs()           { return animExplodeSpeedMs; }
    public void setAnimExplodeSpeedMs(int v)     { animExplodeSpeedMs = v; fireTransformChange(); }
    public int getAnimUnsplodeSpeedMs()          { return animUnsplodeSpeedMs; }
    public void setAnimUnsplodeSpeedMs(int v)    { animUnsplodeSpeedMs = v; fireTransformChange(); }
    public int getAnimExplodeStrength()          { return animExplodeStrength; }
    public void setAnimExplodeStrength(int v)    { animExplodeStrength = v; fireTransformChange(); }
    public int getAnimUnsplodeStrength()         { return animUnsplodeStrength; }
    public void setAnimUnsplodeStrength(int v)   { animUnsplodeStrength = v; fireTransformChange(); }
    public boolean isAnimStayInCanvas()          { return animStayInCanvas; }
    public void setAnimStayInCanvas(boolean v)   { animStayInCanvas = v; fireTransformChange(); }
    public int getAnimPopHoldMs()                { return animPopHoldMs; }
    public void setAnimPopHoldMs(int v)          { animPopHoldMs = v; fireTransformChange(); }
    public int getAnimExtendMs()                 { return animExtendMs; }
    public void setAnimExtendMs(int v)           { animExtendMs = v; fireTransformChange(); }
    public boolean isFocalActive()    { return focalActive; }
    public void setFocalActive(boolean v) { if (focalActive == v) return; focalActive = v; fireChange(); }
    public int  getAnimTwistFirstSpeedMs()          { return animTwistFirstSpeedMs; }
    public void setAnimTwistFirstSpeedMs(int v)     { animTwistFirstSpeedMs = v; fireTransformChange(); }
    public int  getAnimTwistSecondSpeedMs()         { return animTwistSecondSpeedMs; }
    public void setAnimTwistSecondSpeedMs(int v)    { animTwistSecondSpeedMs = v; fireTransformChange(); }
    public int  getAnimTwistFirstSmooth()           { return animTwistFirstSmooth; }
    public void setAnimTwistFirstSmooth(int v)      { animTwistFirstSmooth = v; fireTransformChange(); }
    public int  getAnimTwistSecondSmooth()          { return animTwistSecondSmooth; }
    public void setAnimTwistSecondSmooth(int v)     { animTwistSecondSmooth = v; fireTransformChange(); }
    public int  getAnimTwistDirection()             { return animTwistDirection; }
    public void setAnimTwistDirection(int v)        { animTwistDirection = v; fireTransformChange(); }
    public boolean isAnimTwistFullSpin()            { return animTwistFullSpin; }
    public void    setAnimTwistFullSpin(boolean v)  { animTwistFullSpin = v; fireTransformChange(); }
    public boolean isAnimTwistSpreadGap()           { return animTwistSpreadGap; }
    public void    setAnimTwistSpreadGap(boolean v) { animTwistSpreadGap = v; fireChange(); fireTransformChange(); }
    public boolean isAnimMorphFadeDeaths()          { return animMorphFadeDeaths; }
    public void    setAnimMorphFadeDeaths(boolean v) { animMorphFadeDeaths = v; fireTransformChange(); }
    public boolean isAnimPopStayAtFocus()           { return animPopStayAtFocus; }
    public void    setAnimPopStayAtFocus(boolean v) { animPopStayAtFocus = v; fireTransformChange(); }
    public int  getAnimWallDamping()                { return animWallDamping; }
    public void setAnimWallDamping(int v)           { animWallDamping = v; fireTransformChange(); }
    public int  getAnimMorphSpeedMs()      { return animMorphSpeedMs; }
    public void setAnimMorphSpeedMs(int v) { if (animMorphSpeedMs == v) return; animMorphSpeedMs = v; fireChange(); fireTransformChange(); }
    public int  getAnimMorphHoldMs()       { return animMorphHoldMs; }
    public void setAnimMorphHoldMs(int v)  { if (animMorphHoldMs == v) return; animMorphHoldMs = v; fireChange(); fireTransformChange(); }

    public int  getAnimSpringStiffness()      { return animSpringStiffness; }
    public void setAnimSpringStiffness(int v) { if (animSpringStiffness == v) return; animSpringStiffness = v; fireChange(); fireTransformChange(); }
    public int  getAnimSpringDamping()        { return animSpringDamping; }
    public void setAnimSpringDamping(int v)   { if (animSpringDamping == v) return; animSpringDamping = v; fireChange(); fireTransformChange(); }
    public int  getAnimSpringImpulse()        { return animSpringImpulse; }
    public void setAnimSpringImpulse(int v)   { if (animSpringImpulse == v) return; animSpringImpulse = v; fireChange(); fireTransformChange(); }
    public int  getAnimSpringSpeedMs()        { return animSpringSpeedMs; }
    public void setAnimSpringSpeedMs(int v)   { if (animSpringSpeedMs == v) return; animSpringSpeedMs = v; fireChange(); fireTransformChange(); }
    public int  getAnimSpringHoldMs()         { return animSpringHoldMs; }
    public void setAnimSpringHoldMs(int v)    { if (animSpringHoldMs == v) return; animSpringHoldMs = v; fireChange(); fireTransformChange(); }

    // ── UFT (Unique Frame Transform) API ─────────────────────────────────────

    public void ensureUFTCapacity() {
        int N = animationFrames.size();
        while (uftSettings.size() < N) uftSettings.add(new TransformSettings());
        while (uftSettings.size() > N) {
            int last = uftSettings.size() - 1;
            if (selectedUFTIndex == last) selectedUFTIndex = -1;
            uftEnabled.remove(last);
            uftSettings.remove(last);
        }
    }

    public void applySettingsSilently(TransformSettings s) {
        animEffectType       = s.animEffectType;
        animSpread           = s.animSpread;
        animSpeedMs          = s.animSpeedMs;
        animHoldMs           = s.animHoldMs;
        animEasing           = s.animEasing;
        animFocalX           = s.animFocalX;
        animFocalY           = s.animFocalY;
        animSpin             = s.animSpin;
        animSpinStrength     = s.animSpinStrength;
        animExplodeSpeedMs   = s.animExplodeSpeedMs;
        animExplodeStrength  = s.animExplodeStrength;
        animUnsplodeSpeedMs  = s.animUnsplodeSpeedMs;
        animUnsplodeStrength = s.animUnsplodeStrength;
        animGravityPush      = s.animGravityPush;
        animGravityPull      = s.animGravityPull;
        animGravityFocalX    = s.animGravityFocalX;
        animGravityFocalY    = s.animGravityFocalY;
        animPopHoldMs        = s.animPopHoldMs;
        animExtendMs         = s.animExtendMs;
        animTwistFirstSpeedMs  = s.animTwistFirstSpeedMs;
        animTwistSecondSpeedMs = s.animTwistSecondSpeedMs;
        animTwistFirstSmooth   = s.animTwistFirstSmooth;
        animTwistSecondSmooth  = s.animTwistSecondSmooth;
        animTwistDirection     = s.animTwistDirection;
        animMorphSpeedMs       = s.animMorphSpeedMs;
        animMorphHoldMs        = s.animMorphHoldMs;
        animStayInCanvas       = s.animStayInCanvas;
        animTwistFullSpin      = s.animTwistFullSpin;
        animTwistSpreadGap     = s.animTwistSpreadGap;
        animMorphFadeDeaths    = s.animMorphFadeDeaths;
        animPopStayAtFocus     = s.animPopStayAtFocus;
        animWallDamping        = s.animWallDamping;
        animSpringStiffness    = s.animSpringStiffness;
        animSpringDamping      = s.animSpringDamping;
        animSpringImpulse      = s.animSpringImpulse;
        animSpringSpeedMs      = s.animSpringSpeedMs;
        animSpringHoldMs       = s.animSpringHoldMs;
    }

    public void syncSelectedUFT() {
        if (selectedUFTIndex >= 0 && selectedUFTIndex < uftSettings.size())
            uftSettings.set(selectedUFTIndex, TransformSettings.capture(this));
    }

    public void setSelectedUFT(int i) {
        if (i == selectedUFTIndex) return;
        syncSelectedUFT();
        selectedUFTIndex = i;
        if (i >= 0) {
            // Always load this tab's stored settings (new tabs have field-initializer defaults)
            applySettingsSilently(uftSettings.get(i));
            uftEnabled.add(i);
        }
        fireChange();
    }

    public void toggleUFT(int i) {
        if (uftEnabled.contains(i)) {
            uftEnabled.remove(i);
        } else {
            uftEnabled.add(i);
        }
        fireChange();
    }

    public TransformSettings getTransformForTransition(int fromFrame) {
        int N = animationFrames.size();
        if (N <= 1) return null;
        for (int offset = 0; offset < N; offset++) {
            int slot = ((fromFrame - offset) % N + N) % N;
            if (uftEnabled.contains(slot)) return uftSettings.get(slot);
        }
        return null;
    }

    public boolean isUFTEnabled(int i)                   { return uftEnabled.contains(i); }
    public TransformSettings getUFTSettings(int i)       { return uftSettings.get(i); }
    public void setUFTSettings(int i, TransformSettings s) { uftSettings.set(i, s); }
    public void enableUFT(int i)                         { uftEnabled.add(i); }
    public void disableUFT(int i)                        { uftEnabled.remove(i); }
    public int  getSelectedUFTIndex()                    { return selectedUFTIndex; }
    public boolean isFullAnimationMode()                 { return fullAnimationMode; }
    public void setFullAnimationMode(boolean v)          { if (fullAnimationMode == v) return; fullAnimationMode = v; fireChange(); }

    public void addChangeListener(ChangeListener l) { listeners.add(l); }
    public void addTransformListener(Runnable r)    { transformListeners.add(r); }

    private void fireChange() {
        ChangeEvent e = new ChangeEvent(this);
        for (ChangeListener l : listeners) l.stateChanged(e);
    }

    private void fireTransformChange() {
        for (Runnable r : transformListeners) r.run();
    }
}

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Maven is **not installed** on this machine. Use `javac` and `jar` directly.

```bash
# Compile
find src -name "*.java" | sort | xargs $JAVA_HOME/bin/javac -d out

# Package
$JAVA_HOME/bin/jar cfe sprite-editor.jar com.atari.spritemaker.Main -C out .

# Run
$JAVA_HOME/bin/java -jar sprite-editor.jar
```

No external dependencies — pure Java 11+ with Swing only.

## Architecture

The app follows a simple MVC pattern with a shared model and event-driven panel updates.

**Model (`model/`):**
- `SpriteModel` — single source of truth. Holds the 16×16 `Color[][]` grid, a 3-slot palette (`Color[3]`), the active painting color, selected palette slot index, and current region. Fires `ChangeEvent` to all registered listeners on any mutation.
- `AtariPalette` — static color data only. `getColors(Region)` returns a `Color[]` for NTSC (128), PAL (104), or SECAM (8).

**Panels (`panels/`):**
All three panels implement `ChangeListener` and are registered on `SpriteModel`. They repaint/refresh themselves in response to model events — no panel talks directly to another.

- `ActionPanel` — owns all file I/O (new/load/save/export). Save format is a plain-text `.spr` file (REGION=, PALETTE=, GRID= sections). Export writes a 16×16 PNG via `javax.imageio.ImageIO`.
- `EditorPanel` — painting grid (click + drag), 3-slot `PaletteBar`, region `JComboBox`, and a scrollable `ColorPickerPanel`. Uses an `updatingFromModel` flag to prevent the combo's `ActionListener` from re-firing when `stateChanged` updates the combo programmatically. `ColorPickerPanel` **must** be instantiated before the combo listener lambda is defined (final field reference requirement).
- `HexMirrorPanel` — read-only custom-painted panel. Renders each cell with its background color and overlaid 6-digit hex string; text color is chosen for luminance contrast.

**Frame (`SpriteEditorFrame`):**
`ActionPanel` (WEST) + `JSplitPane` (CENTER) containing `EditorPanel` (left) and a `JScrollPane` wrapping `HexMirrorPanel` (right).

## Palette Interaction Flow

1. User selects a region → `SpriteModel.setRegion()` → `ColorPickerPanel.refresh()` repopulates swatches.
2. User clicks a palette slot → `SpriteModel.selectPaletteSlot(i)` — toggles selection; sets `activeColor` to that slot's color, or resets to black if already selected.
3. User clicks a color swatch → `SpriteModel.setPaletteSlotColor(selectedSlot, color)` — only works when a slot is selected.
4. User clicks/drags on the grid → `SpriteModel.setCellColor(row, col, activeColor)` — only paints if cell color differs from active color.

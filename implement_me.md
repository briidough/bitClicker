# implement_me.md — bixelPlay integration for welcometobrii

This file is for Claude to read at the start of a welcometobrii session.
It describes how to replace the sprout SVG animation with a .bxl + bixelPlay.js.

---

## What was done in bitClicker

- `bixelPlay.js` — standalone animation player class (no dependencies). Root of bitClicker repo.
- `.bxl` export format — JSON, exported from bitClicker via Ctrl+Shift+E.

---

## Step 1: Brendan exports the sprout from bitClicker

Before this session, Brendan must:

1. Open bitClicker
2. Design the sprout animation with 3 frames:
   - Frame 0 = resting state (small sprout)
   - Frame 1 = mid-grow
   - Frame 2 = fully grown / clicked state
3. Configure transitions between frames (Burst or Morph work well for a growing plant)
4. Press Ctrl+Shift+E → save as `sprout.bxl`

---

## Step 2: Files to copy into welcometobrii

From the bitClicker repo root, copy these two files:

```
bixelPlay.js     →  welcometobrii/[static assets folder]/bixelPlay.js
sprout.bxl       →  welcometobrii/[static assets folder]/sprout.bxl
```

---

## Step 3: Replace the SVG sprout with a canvas

In welcometobrii, find the sprout element (currently 3 SVG files in sequence).
Replace with a `<canvas>` element sized to match the CSS slot:

```html
<!-- OLD: 3 SVG animation sequence -->
<!-- <img class="sprout" src="sprout1.svg" ...> -->

<!-- NEW: single canvas, same CSS class/slot -->
<canvas id="sprout-canvas" class="sprout" width="128" height="128"></canvas>
```

The `width` and `height` attributes are the pixel resolution of the canvas.
CSS can still scale it with `width`/`height` style properties as before.

---

## Step 4: Load bixelPlay.js and initialize

```html
<script src="bixelPlay.js"></script>
<script>
  async function initSprout() {
    const data   = await fetch('sprout.bxl').then(r => r.json());
    const canvas = document.getElementById('sprout-canvas');
    const player = new BixelPlay(canvas, data, {
      // pixelSize auto-computed from canvas.width / data.gridSize
      bg: null,  // transparent — lets the page background show through
    });

    player.setFrame(0);  // show resting state immediately

    const lastFrame = data.frameCount - 1;

    // Preserve the existing click behavior: grow on press, return on release
    canvas.addEventListener('mousedown',  () => player.play(player.currentFrame, lastFrame));
    canvas.addEventListener('touchstart', () => player.play(player.currentFrame, lastFrame),
      { passive: true });

    canvas.addEventListener('mouseup',  () => player.play(player.currentFrame, 0));
    canvas.addEventListener('touchend', () => player.play(player.currentFrame, 0));

    // If the user moves off while pressed, also return
    canvas.addEventListener('mouseleave', () => {
      if (player.currentFrame !== 0) player.play(player.currentFrame, 0);
    });
  }

  initSprout();
</script>
```

---

## How play(fromFrame, toFrame) handles mid-animation clicks

`player.currentFrame` is updated live as each transition step completes.
Calling `play()` while animating immediately stops the current animation and
restarts from `player.currentFrame`. So if the user releases the mouse while
the grow animation is on frame 1, `play(1, 0)` fires — not `play(2, 0)`.
The reversal picks up from wherever the animation actually is.

---

## Preserving CSS grow/return-to-size behavior

If the existing sprout uses a CSS `transform: scale(...)` on click, keep it on
the wrapper element, not the canvas. The canvas renders the pixel animation;
the wrapper handles the DOM-level scale. They are independent.

Example:

```css
.sprout-wrapper {
  transition: transform 0.15s ease;
}
.sprout-wrapper:active {
  transform: scale(1.1);
}
```

```html
<div class="sprout-wrapper">
  <canvas id="sprout-canvas" class="sprout" width="128" height="128"></canvas>
</div>
```

---

## BixelPlay API reference

```js
const player = new BixelPlay(canvas, bxlData, opts);
```

**Options:**
- `pixelSize` — pixels per grid cell (default: `Math.floor(canvas.width / gridSize)`)
- `bg` — background CSS color (default: `null` = transparent)
- `checkA`, `checkB` — checkerboard colors for transparent cells

**Methods:**
- `player.setFrame(n)` — jump to frame n instantly, no animation
- `player.play(from, to, onDone?)` — animate frames from→to (works forward or backward)
- `player.stop()` — halt animation, stay on current frame

**Property:**
- `player.currentFrame` — the frame index currently shown (updated in real-time)

---

## Transition direction note (.sga format is unchanged)

`play(2, 0)` animates frames 2→1→0 using the existing 0→1 and 1→2 transition
settings, but with from/to pixels swapped. No new format is needed.

If a custom reverse transition is ever needed (e.g., a completely different
effect on the return path), open a new bitClicker session and ask Claude to add
`returnTransitions` to the BxlExporter and bixelPlay._buildTD.

---

## Keeping bixelPlay.js up to date

Whenever bitClicker is rebuilt with new animation features, re-copy bixelPlay.js:

```bash
cp bixelPlay.js web/public/bixelPlay.js
# then copy to welcometobrii as well
```

The CLAUDE.md in bitClicker has a reminder about this in the build section.

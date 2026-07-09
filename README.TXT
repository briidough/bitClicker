╔═══════════════════════════════════════════════════════════╗
║ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ║
║▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓║
║▓▓░░░▓▓▓▓░░░░▓▓▓▓▓▓░▓▓▓▓▓▓▓░░░▓▓▓▓▓▓▓▓░▓░▓▓▓▓▓▓░░░░▓▓░▓▓░▓▓║
║▓▓░▓▓░▓▓▓░▓▓▓▓▓▓▓▓░▓░▓▓▓▓▓▓░▓▓░▓▓▓▓▓▓░▓░▓░▓▓▓▓▓░▓▓▓▓▓░▓▓░▓▓║
║▓▓░░░▓▓▓▓░░░▓▓▓▓▓░▓▓▓░▓▓▓▓▓░▓▓▓░▓▓▓▓░▓▓░▓▓░▓▓▓▓░░░▓▓▓░▓▓░▓▓║
║▓▓░▓▓░▓▓▓░▓▓▓▓▓▓░▓░░░▓░▓▓▓▓░▓▓░▓▓▓▓▓░▓▓▓▓▓░▓▓▓▓░▓▓▓▓▓▓▓▓▓▓▓║
║▓▓░▓▓░▓▓▓░░░░▓▓▓░▓▓▓▓▓░▓▓▓▓░░░▓▓▓▓▓░▓▓▓▓▓▓▓░▓▓▓░░░░▓▓░▓▓░▓▓║
║▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓║
║ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ░ⒷⓇⒾⒾⒹⓄⓊⒼⒽ║
╚═══════════════════════════════════════════════════════════╝
Hello and welcome to bitclicker

bitClicker is a pixel sprite and animation editor.
Canvas 16x16 and 32x32 on web, 16 up to 80 on desktop
Preset color paletes.
For web, when custom, 
Build multi-frame animations with pixel transforms like Pop, Morph, Burst, and Spring
Runs anywhere with Java 11+ — no extra dependencies, just Swing.

History: while you draw, bitClicker auto-saves snapshots of your whole animation
every 5 seconds of active drawing. On desktop, click "History" to open the panel
(keeps 10). On web, a strip below the palette keeps the last 5. Click any snapshot
to jump back to that state (your current work is saved to history first).

Frame loop: with 3 or more frames you can loop a two-frame pair. The animation plays
the intro once, then ping-pongs that pair forever.
  Desktop: in the row just below the frame tabs, click the loop button (round arrow)
  to turn it on, then use the left/right arrows to choose which adjacent pair loops.
  Click the loop button again to turn it off. Play the preview to see it bounce; Step
  walks straight through the frames and breaks out of the loop.
  Web: in the draw panel, click the loop button at the right of the row under the
  frame tabs. A marker drops between two frames — drag it to pick the pair. Drag it
  under the loop button (or left-click it) to disable; right-click a disabled marker
  to re-enable.
The loop is saved inside your .sga file and travels with it. When a looped .bxl
export plays via bixelPlay, clicking the canvas breaks out of the loop and finishes
the animation.


Requires Java 11 or newer. Verify with java -version.

	Windows
		java -jar sprite-editor.jar
		(or double-click sprite-editor.jar if .jar is associated with Java)

	macOS

		java -jar sprite-editor.jar

	Linux
		java -jar sprite-editor.jar
		
I'll write more in time...

If you read this I did not waste my time. Wow.



hiimbrendan icopyrightnothing 202X
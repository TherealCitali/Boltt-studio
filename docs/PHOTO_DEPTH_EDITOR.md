# 0.3.1 — selected-photo depth test and visible clock controls

## Why the old test ignored edits

The original Depth Lab deliberately generated a separate solid background and fixed seven-segment clock. Editing the main photo/clock did not alter that diagnostic. Its controls were separate from the main editor, which was also a long scrolling page. This release connects a new default Depth mode to the actual editor instead of silently uploading that unrelated face.

## Find and use the controls

- Check **Boltt Studio · 0.3.1** in the top bar after updating.
- The pinned **Photo / Clock / Brush / Watch / Depth** navigation stays visible while scrolling.
- **Clock** jumps to always-expanded size, independent hour/minute, movement, spacing, fonts, colors and outline controls.
- **Depth** now defaults to **Use selected photo & clock**. Select a photo there or in Photo; both use the same image/crop state. Font/color/outline changes in Clock are shared too.
- Drag on the Depth preview to move the whole clock, or the selected hour/minute pair when independent editing is on. Clock size/position controls are repeated directly beneath that preview. The font/color link jumps straight to styling.
- The preview and export rebuild automatically. During changes, send/export waits for the prepared bytes to match the current design; it never silently sends the previous photo/layout.
- Toggle **Show test stripe** off for the selected-photo, live-clock control. When on, the extra stripe image follows the time element, and its vertical position can be adjusted independently.
- **Send current photo depth test…** / **Export this depth test .bin** use exactly this Depth variant. Ordinary **Send current face** remains the editor face without the diagnostic stripe.
- To repeat the original fixed test, turn **Use selected photo & clock** off. It deliberately ignores editor changes and uses the same original diagnostic bytes. Turn the switch back on to edit again.

## What is shared, and what isn't

The depth face retains the editor's background pixels, all live glyph tables, all four positions/set selections and the dash. Only the preview thumbnail and optional foreground stripe are replaced. This preserves custom fonts, hour/minute colors and dimensions, spacing and per-position transparency masks. All ten numerals remain present; preview time is not baked into the background.

Clock brush masks still use fixed screen coordinates. They do not automatically track a recropped photo subject. Repaint/recheck after changing crop; use **Brush** to get to the mask editor.

The stripe remains a **diagnostic image**, not an AI subject cutout. The supplied watch photo confirms that a stripe appeared in one state, not that alpha/draw order works through minute updates and sleep/wake. Continue those tests before declaring foreground-photo depth supported. A finished segmentation/foreground-subject editor and live date/battery fields are not claimed by this release.

## Validation

New JVM tests check preservation of background pixels, every numeral in four variable-size tables, positions and set indices; optional stripe removal; preview-time independence; and stripe bounds. Existing codec/protocol/mask tests and the independent Python depth fixture remain part of CI. No physical phone UI or watch redraw test is performed by the assistant.

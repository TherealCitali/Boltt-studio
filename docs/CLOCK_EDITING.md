# 0.3.0 — clock shapes and manual transparency

## Resize and move

In **Clock**, turn **Independent hours / minutes** on to select either pair. Size, digit width, digit height, digit spacing, X/Y sliders and preview dragging then affect only that pair. With the toggle off, sizing applies to both pairs and dragging preserves their relative offset. Turning independent editing off copies the hours' dimensions/spacing to the minutes, but preserves placement where bounds allow.

- Overall size scales proportionally until the screen-safe size limit.
- Width and height stretch separately, supporting tall, wide and condensed shapes.
- Presets preserve the selected/imported font. These are inspired shape controls, not licensed Apple or ColorOS fonts.
- **Stack to fit** / **Row to fit** arrange both pairs and reduce dimensions if necessary. Reset restores42×66 cells at the original stacked positions.
- Each pair and every glyph is bounded to240×296. Overlapping hour/minute groups produce a warning. Linked position ranges account for BOTH groups.
- Raster sources are cached at126×198 after higher-resolution drawing rather than enlarging only the old42×66 bitmap.

Variable-size glyph descriptors are already representable in the codec, but newly chosen sizes/configurations still need watch testing. Large stretched digits and four mask tables can increase the encoded file size; the send confirmation displays actual bytes. The2MiB application guard is not a measured watch limit.

## Depth lab: clock transparency brush

This tool edits the **current editor face**, not the separate magenta-stripe firmware probe.

1. Choose/crop your photo and arrange the clock first.
2. In **Depth lab · clock transparency brush**, enable **Paint mode**. Paint on that panel's preview, not the main drag-to-position preview.
3. **Erase** makes portions of the clock transparent so the background shows through. **Restore** reveals the clock again. Taps work; quick drag strokes are interpolated rather than leaving gaps.
4. Adjust brush diameter and feathering. **Tint mask** is a diagnostic overlay only; it is never exported.
5. Undo retains the latest20 stroke/clear snapshots. Clear restores the whole mask. Turning **Apply painted transparency** off keeps the strokes but exports an unmasked clock.
6. Use the full-day preview slider or10:09/12:34/23:58/08:08 presets to inspect different numerals. A warning appears when at least45% of any numeral's original opacity is removed.
7. Use the ordinary **Export watchface** / **Send current face** actions. The preview time is only a simulation/thumbnail; the installed clock remains live.

The mask uses fixed240×296 **screen coordinates**. Moving/resizing the clock re-evaluates each glyph against that same mask. Recropping/replacing the photo does NOT transform the mask with the subject: repaint or clear it after changing the photo crop. This is explicit manual clock masking, not automatic subject registration. Mask/design state survives rotation, not process death.

## Why four glyph tables?

The same numeral at the left and right positions may overlap different painted areas. Reusing one masked hour table would repeat the hole in both hour digits. With masking active, export therefore uses four position-specific tables (sets0/1/2/3), each containing all ten numerals with its own screen-aligned alpha mask. Unmasked faces use the usual two hour/minute tables. Background pixels are not modified; no clock screenshot is baked into them.

The four live `TimeNum` positions are retained. Whether this firmware handles every new size, four-table configuration, partial alpha and redraw/wake behavior correctly remains a **device test**; parsing/building tests do not prove it. Upload confirmation warns about modified glyph configurations.

## First device checks

Test resized/unmasked first, then independent placement, then a small masked region using otherwise default sizes. Inspect at multiple minute changes and screen sleep/wake. Restore an unmasked default-size face as a control if needed; there is no guaranteed recovery claim. Do not assume foreground-image layering is supported because manual glyph-alpha masking works: those are different rendering paths.

The original depth probe remains available unchanged. AI person extraction, subject-mask brushes/registration, automatic subject cutouts and production foreground-image depth remain separate pending work.

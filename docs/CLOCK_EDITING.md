# Digital clock editor · 0.5.0

Use the pinned **Clock** button to reach all controls.

- Toggle independent hours/minutes and select the pair to edit. Size/spacing/position and preview dragging affect that pair. With the toggle off, sizing applies to both and dragging preserves their relative offset.
- Overall size scales proportionally; width/height stretch separately. Tall, wide and condensed presets preserve the chosen font rather than supplying vendor fonts.
- Stack/row-to-fit arrangements reduce dimensions where necessary; reset restores42×66 cells and the original stacked positions.
- Bounds keep all glyphs on240×296; overlap produces a warning.
- System families and bounded TTF/OTF import, independent hour/minute RGB colors and outline remain available.
- Preview-time inspection now lives in Clock. It affects the simulation/thumbnail, never freezes the live watch time.

Normal export uses two digit tables: hour positions reference0, minute positions reference1. Depth settings, masks, brushes, four-position masking output and extra foreground images are removed. The generic codec preserves original digital fixture behavior; it is not an analogue encoder.

Size variants and watch-specific behavior still require physical testing. The previous missing-minute report is not declared solved merely by removing depth features. Sessions survive rotation, not process death.

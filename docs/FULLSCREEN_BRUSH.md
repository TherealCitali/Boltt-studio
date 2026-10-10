# 0.4.0 — full-screen brush studio

Open **Brush → Open full-screen brush studio**. This is a dedicated canvas workspace inspired by painting-app ergonomics, not a claim of feature parity with ibisPaint or Procreate.

## Canvas and tools

- Large, clipped canvas in a high-contrast dark workspace; app navigation is hidden behind the workspace.
- One-finger **Erase** / **Restore** with native-pixel brush diameter, soft/hard edges and an on-canvas brush outline.
- Two fingers pan/pinch from1× fit to12× fit. If a second finger joins a painting gesture, that gesture's provisional paint is rolled back before navigation starts; it does not leave an accidental dot or undo entry.
- Explicit **Pan / zoom** mode also allows one-finger panning without drawing. **Fit** recenters and resets zoom.
- **Undo / Redo** for up to20 draft operations. A new paint operation after undo clears redo. Clear is confirmed and undoable.
- **Mask tint** shows erased areas but is never exported. **Before** shows the draft's starting stored mask and disables painting/apply until switched off.
- Preview times10:09,12:34,23:58,08:08 help inspect other numerals. The watch clock remains live.
- Toolbars scroll horizontally on narrow screens. Safe-area insets keep controls out of display cutouts/navigation bars.

Zoom/pan change only the editing viewport. Every stroke is inverse-mapped to the original240×296 mask, so changing zoom cannot resize or shift exported holes. Resize/rotation keeps the same native canvas center; Fit is available if you lose your place.

## Draft semantics

Opening copies the stored mask into a separate draft and captures an **unmasked** copy of the current background/font/clock geometry, so Restore can recover previously erased glyph pixels. The editor itself is not changed while painting.

- **Done** copies the draft back as one editor undo step and enables painted transparency. If the mask was disabled before opening, opening still edits its stored strokes; Done enables it.
- **Cancel**, system Back and dismissal ask before discarding a changed draft. An unchanged draft closes directly.
- A cancelled/interrupted stroke rolls back instead of leaving an unrecorded partial edit.
- Draft, tools, mask history and viewport are retained by the editor ViewModel across rotation. They are **not saved across process death**; this is not project-file persistence.
- The full-screen entry is disabled during an active watch upload. No upload is triggered by Done.

The main preview, photo-depth test and ordinary exports use the applied mask as before: each clock position has all ten masked live numerals. This is clock-alpha masking, not AI subject segmentation, a photo-layer brush or a frozen clock screenshot. Foreground-image redraw support and date/battery encoding remain separate investigations.

## Validation scope

JVM tests cover inverse viewport mapping, focal-point zoom/pan, zoom/pan bounds, viewport resizing, copied draft history, undo/redo/clear, history limits and rollback semantics. Existing codec tests continue checking live glyph tables. Multitouch/stylus behavior, physical phone rendering, rotation and Back/Done interactions need device testing; no pressure sensitivity, automatic palm rejection or canvas rotation is advertised.

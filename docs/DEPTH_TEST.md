# Photo depth: firmware gate before segmentation

## Status

**Unverified. Version 0.2.1 adds a diagnostic probe, not a finished photo-depth editor.** The user has reported successful captured and custom digital-face uploads with0.2.0; that does not prove extra-image layering, blending, redraw order or a general file-size limit.

No frozen clock screenshot is exported as a fallback. The test face uses four genuine live `TimeNum` positions. Fixed10:09 appears only in the app simulation and list thumbnail.

## Run the first test

1. Keep the known-working0.2.0 APK/face available. This is not a guaranteed recovery method. Stop Da Fit, charge above30%, keep the app foreground and use only the documented target watch.
2. Open **Depth lab · firmware probe** beneath the Bluetooth controls. Keep **Include foreground stripe** enabled, then **Prepare test face**. The editor photo/font/clock settings are untouched.
3. Inspect the simulation and the **actual encoded byte count**. Connect explicitly, tap **Send depth probe…**, and read/accept the experiment warning. Alternatively export the `.bin` for inspection.
4. On the watch, expect a navy background, large white live digits and a horizontal magenta stripe crossing all four digit cells. Its center is opaque; edge bands have alpha64/128; a four-pixel clear window crosses the second digit's left segment. Transparent pixels should reveal the digits/background, not black boxes.
5. Observe at least two minute changes. Do newly drawn strokes cover the stripe or erase its clear window? Check that the stripe stays in front, not merely on the first frame.
6. Let the screen sleep and wake it several times. Check for reordered layers, opaque rectangles, flickering and missing digits. If this watch exposes distinct dim/always-on modes, record them separately rather than inferring their behavior from the normal screen.
7. Verify all four time positions are live. Minute changes alone do not demonstrate that hours update; observe an hour rollover or a normal, documented time change. Do not claim a pass just because the simulated10:09 thumbnail looks right.
8. Record firmware/phone Android versions, the initial appearance, minute-change behavior, sleep/wake behavior, alpha-edge quality and any errors. A short photo/video sequence is useful; remove personal details before sharing.

The optional **no-overlay control** uses identical live glyphs/coordinates/background but omits the extra image. Prepare it separately with the switch off. If BOTH control and overlay fail, the result does not isolate a layering problem: stop and investigate. Do not repeatedly retry failed writes or reset the watch blindly.

## Acceptance

Photo depth is supported on a tested firmware only when all four digits stay live, occlusion survives redraws/wake, alpha blending looks acceptable, and upload/display are reliable. A completed BLE transfer is not a pass. If layering or redraw behavior fails, genuine image-layer depth is unsupported on that tested configuration. No static-clock substitute is acceptable.

## Encoding under test

Element list (the existing dash descriptor remains):

1. Dash `0x23` (unused transparent1×1).
2. Background `Image 0x00`,240×296 at(0,0), solid color only.
3. `TimeNum 0x02`, four live positions, ten-glyph table,42×66 cells.
4. Foreground `Image 0x00`,216×18 at(12,139), cropped to its nonzero-alpha bounding box.
5. Two-byte element-list terminator.

The34-byte time element has12 reserved bytes. The terminator must follow the added image, not precede it. Background and glyph bitmaps do NOT contain a flattened stripe. Foreground image descriptors use the existing position/offset/width/height encoding; whether firmware honors multiple descriptors and draw order remains the experiment.

Legacy no-foreground output remains byte-identical to the independently encoded fixtures. Bounds, unique foreground, element ordering and alpha roundtrips are tested. `depth_probe_test.py` supplies a separate Python encoding/descriptor walk; the legacy Python face parser is **not** a multi-image parser (it overwrites its background field on repeated images), so do not use that parser to round-trip the probe.

## Size and memory

The deterministic overlay probe is16,396bytes, below the supplied120,956byte captured file. This is not a watch size limit or a compatibility guarantee. The codec's2MiB guard is an application safety bound, not a discovered firmware capacity.

A240×296 image has213,120bytes of alpha+RGB565 pixel data before row headers/RLE. That is neither final file size nor established firmware RAM consumption. Foregrounds should be bounding-box cropped with the matching coordinate offset; the test does this even though it starts with a full-canvas mask.

## Next phase only after a hardware pass

- On-device **person** segmentation with a suitably licensed model; do not advertise arbitrary-object extraction without a suitable model.
- Manual erase/restore, smoothing/feathering and optional inversion.
- One shared photo crop/zoom/pan transform for background and subject mask, avoiding registration drift.
- Exact clock-coordinate entry, selectable digit size/spacing/fonts/colors, and overlap/readability warnings.
- Export background-only decorations, live time and cropped subject foreground separately, plus thumbnail and measured file size.

This is static photographic occlusion, not3D motion or parallax. Segmentation, mask editing, flexible digit sizing/spacing and production photo-depth export are **not implemented in0.2.1**. No model downloads or new network permissions are introduced by the probe.

## 0.3.0 clarification

Variable-size clock controls and a manual **clock-alpha** brush are now available separately; see [CLOCK_EDITING.md](CLOCK_EDITING.md). They do not implement AI subject segmentation or certify the foreground-image experiment described above.

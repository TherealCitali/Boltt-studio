# Hardware reports

## Native BLE baseline · 0.2.0 / 5511d01

The user reported that both the original captured Da Fit face and a custom-generated face uploaded and displayed correctly on their device ("worked on device flawlessly", then confirmed "both"). This is a user-reported success on that setup, not an assistant-run hardware test or universal compatibility claim.

Exact Android/watch firmware identifiers, battery conditions and repeated soak-test results were not separately recorded with this report. Brillia MOY-7QI2-2.0.1 remains the intended protocol target; shared service UUIDs do not verify identity.

## Photo-depth image layering · 0.2.1

**Awaiting device results.** The baseline above does not establish support for two image elements, alpha compositing of foreground images, occlusion across minute redraws, or sleep/wake layering. Follow [DEPTH_TEST.md](DEPTH_TEST.md); do not infer a pass from CI or the app simulation.

## Custom fonts

The user also reported that imported TTF clock fonts worked successfully. This does not verify arbitrary font files or the new resizing/masking configurations.

## Resized / independently positioned / manually masked clock · 0.3.0

Awaiting device results. Four per-position masked glyph tables, variable dimensions and alpha-mask redraw behavior require testing separately from foreground-image layering.

# Brillia Face Maker (Fire-Boltt Brillia / MOYOUNG-V2, firmware MOY-7QI2-2.0.1)

Use your own background image, clock position and font, then upload the face over Bluetooth from Chrome on Android.

## Files
- `web/index.html` + `web/moyface.js` – the phone app (Web Bluetooth). Must be served over **https** (or localhost).
- `moyface.py` – Python encoder/decoder for the same format. `make_face.py` – command-line face maker.
- `dafit_captured_face.bin` – the face Da Fit uploaded in your capture (known-good test file).
- `demo_face.bin` – a custom face built by the encoder (moved clock, different font).

## What was reverse-engineered from your btsnoop capture
Format: API 0x23, 240x296 screen, 140x163 preview. Elements: dash (0x23), background image (0x00),
time digits (0x02: digit set per position + x,y for H1 H2 M1 M2). Images are row-RLE, 3 bytes/pixel
(alpha + RGB565 big-endian). Re-encoding the captured face gives pixel-identical images.

Upload (control char FEE2, data FEE6, notifications FEE3):
1. `FE EA 20 06 BA 01`
2. `FE EA 20 09 74 <size u32 big-endian>`
3. Watch sends `74 <block u16>`; the phone sends that 10240-byte block in 244-byte writes. Repeat.
4. Watch sends `74 FF FF <2-byte check>` when it's done.
5. `FE EA 20 09 74 00 00 00 00`, `FE EA 20 0A B4 11 B5 11 00 00`, `FE EA 20 06 19 0B`

Not yet known: the 2-byte check algorithm (the watch calculates it; we don't have to), and the `B4 11 B5 11` values.

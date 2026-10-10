# Native port status · 0.2.0

## Implemented

- Compose editor with the existing icon and warm Material 3 visual direction.
- Sampled photo selection, EXIF orientation, crop zoom/pan/reset, 240×296 preview.
- Clock drag placement and optional 4-pixel snap, bounded position sliders, center action, stacked/line layouts.
- Three system font families and bounded TTF/OTF import (4 MiB); imported fonts use their own style. Digit cells remain 42×66. Glyphs are cached during dragging. Font rendering is not claimed to match browser output.
- Independent hour/minute RGB sliders, six-digit hex input, presets and outline. Output colors quantize to RGB565.
- SAF export; bytes are snapshotted before opening the picker and retained in the editor ViewModel across rotation.
- Strict Kotlin API 0x23 RLE codec, independent Python checksum/pixel regression fixtures.
- Foreground-only experimental native Bluetooth upload and the original captured Da Fit face as a separate, confirmed first-test action. The captured asset is sent verbatim, not parsed and rebuilt.
- Independent signing and commit prereleases, latest-five prerelease retention, manual stable workflow.

## Native BLE implementation

Target: Fire-Boltt Brillia, MOYOUNG-V2, firmware MOY-7QI2-2.0.1. Shared UUIDs do **not** verify model or firmware.

- Android ≤11: manifest BLUETOOTH/BLUETOOTH_ADMIN plus runtime fine location and enabled system Location for scanning. Android 12+: runtime BLUETOOTH_SCAN/CONNECT; scan data is not used for location. No Internet or broad storage permission.
- Explicit device selection after a bounded 20-second scan; candidate filter and show-all option. No auto-connect or bonding shortcut.
- FEEA service; FEE2 control, FEE3 notifications, FEE6 data. Validate writable/notify properties and subscribe through CCCD 2902 before transfer.
- One serialized GATT operation, callback waiter installed before each request. Connection 20s, discovery 15s, ordinary operations 10s. MTU request 247 with 4s wait; default MTU23 means payload20. Negotiated payload is capped at244, never assumed.
- Writes prefer no-response where supported, otherwise response writes. A local Android callback plus 8ms pacing serializes no-response writes; that callback is **not** a watch acknowledgement.
- Incremental framed-notification decoder, bounded notification queue active before the handshake. BA acknowledgement is optional after4s, matching the captured sequence. Watch 74 requests drive 10,240-byte blocks. A next-block request is the protocol's implicit acknowledgement; there is no invented per-packet watch ACK.
- At most three requests per block;20s next-request timeout;10min total transfer deadline. Reject bad indices/truncated requests/early completion. Retransmissions do not inflate unique-byte progress.
- Only an explicit GATT invalid-length rejection permits a20-byte fallback at the **same offset**. Ambiguous write failures, timeouts and disconnects abort instead of blindly retrying.
- FFFF completion requires every block served and a two-byte check value. The check algorithm remains unknown. Final commands74 four zero bytes, B4 `11 B5 11 00 00`,19 `0B` are preserved. UI reports watch-reported completion and local final-command submission, **not verified installation**.
- Cancellation/disconnect cleanup, screen kept awake while sending, active operations cancelled when the activity leaves foreground (rotation exempt). Diagnostic log export; packet payloads, photo contents and font files are not logged. Device names/addresses are shown in the selector; inspect exported system error text before sharing.

## First hardware test

1. Read [BLE_TESTING.md](BLE_TESTING.md). Confirm the documented Brillia firmware manually; do not test another model just because it advertises FEEA.
2. Charge above30%, stop Da Fit, and keep the phone/watch close.
3. Grant permissions, enable Bluetooth and (Android10) system Location. Scan and explicitly select your watch. Use show-all if its advertisement/name is not recognized.
4. Send **captured Da Fit face** first. Confirm the risk dialog, keep the app visible, then inspect the watch. Only after a successful captured-face test try a generated face.
5. Export diagnostics if anything fails. There is no guaranteed rollback or recovery claim.

## Still pending / not verified

Saved projects/process-death restoration, editable `.bin` import, variable digit sizes, background-service uploads, device/firmware probing, completion-check verification, and multi-watch support. Session edits/fonts survive rotation but not process death. A confirmation dialog being prepared may be dismissed by rotation; prepare it again.

No physical watch transfer has been performed by the assistant. JVM tests cover the pure protocol and codec; they do not emulate Android Bluetooth controllers or establish watch compatibility. UI, real Android10 permission behavior and hardware transfer still require device testing.

## Captured dash compatibility exception

The exact supplied `dafit_captured_face.bin` has an invalid unused1×1 dash pointer. Python silently yields transparency; native parsing normally rejects it. A full-file SHA256-gated exception substitutes a transparent dash **only for that fixture**. Rebuilding emits a valid dash and matches Python's checksum. The first-test upload sends the unmodified original file, preserving the capture rather than this rebuilt form.

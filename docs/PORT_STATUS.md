# Native port status

## Implemented in the first native build

- Native Compose editor and Android graphics-based digit rendering. System font rasterization differs from browser fonts, so rendered text is not claimed to be pixel-identical to browser output.
- Background selection (sampled to limit memory), zoom/pan, fixed captured digit dimensions, clock layouts/position, outline and basic color/font choices.
- SAF export. The encoded face is snapshotted before export. Rotating while the file picker is open may cancel pending export; retry if necessary.
- JVM-only codec: RLE image encode/decode, complete supported face parse/build, file-size/dimension/offset/run/bounds validation.
- Kotlin test expectations derived independently from Python re-encoding of the captured and demo fixtures. Reference images and positions must survive round-trips.
- Independent repository signing credentials configured, build/prerelease workflow, five-prerelease retention and manual stable workflow.

## Not implemented / not verified

Native BLE upload, custom font files, editable face import UI, drag/snap, variable digit sizes, complete color picker, project-file persistence and multi-watch support. No hardware transfer was performed by the assistant.

## BLE implementation requirements before enabling upload

Target noted by the supplied prototype: Fire-Boltt Brillia, MOYOUNG-V2, firmware MOY-7QI2-2.0.1. Do not infer compatibility from a shared service UUID alone.

- Handle legacy location/Bluetooth permissions vs Android 12+ nearby-device permissions and explicit device selection.
- Serialize GATT operations and CCCD notification subscription; derive payload size from negotiated MTU and characteristic capabilities. Never assume 244 bytes without negotiation.
- Process framed/fragmented notifications by command instead of dropping unrelated replies; validate lengths and block indices. Install the next-block waiter before writes.
- Transfer in watch-requested 10,240-byte blocks with bounded retries/timeouts. On reduced write size, retry the **same byte offset**; the web prototype’s catch/loop fallback deserves correction rather than a literal port.
- Handle disconnect, cancellation, backgrounding, and state cleanup without reporting success early.
- Keep unresolved completion/check fields and `B4 11 B5 11` sequence explicit in the UI/documentation. Do not claim other faces are unaffected by failed transfers without hardware evidence.

Avoid parallel Da Fit/watch connections while testing. Follow the source’s charged-watch guidance and have a recovery plan. Exporting a file does not prove that installing it on the watch is safe.

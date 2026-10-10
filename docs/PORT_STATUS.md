# Native port status · 0.6.0

## Preview-first local studio

0.6.0 removes binary import entirely, adds adaptive/themed launcher resources and restores a single private editor draft across restarts. Photo/font files are retained locally. The preview stays outside the scrolling/resizable settings sheet, and remains alongside settings in wide windows. Pages separate crop, clock layout, style, preview/export, watch and information. Elastic overscroll uses the same Miuix factory as ShadowRPC. See [STUDIO_WORKSPACE.md](STUDIO_WORKSPACE.md).

## Available

Native Kotlin/Compose photo and digital-clock editing: sampled image import with EXIF handling, crop zoom/pan, linked/independent hour-minute sizing and placement, width/height/spacing, fonts including TTF/OTF, colors, outline, drag/snap, preview-time inspection, document-picker export and native BLE upload.

Pinned navigation is Photo / Clock / Watch. Normal generated faces have two glyph tables and four live positions selecting0/0/1/1. The supplied captured Da Fit face remains a separate confirmed upload action with its original bytes unchanged. Current digital codec output retains the independently checked Python golden behavior.

## Removed by request

Depth Lab and test stripes; inline and full-screen transparency brushes; draft mask history, mask tint and mask controls; extra foreground-image encoding; per-position masking export; related navigation, guides and depth-specific fixtures/tests. Historical implementation remains available in Git history, not the current app UI/runtime.

User photos, uploads and private diagnostic files are not committed or deleted by this app cleanup.

## Bluetooth and limits

Explicit device selection, Android10 Location/legacy Bluetooth permissions, Android12+ Nearby devices permissions, FEEA/FEE2/FEE3/FEE6 discovery, serialized GATT operations and notification subscription, negotiated MTU-derived packets, bounded watch-requested blocks/retries/timeouts, cancellation and diagnostic logs remain unchanged.

Uploads require the app in foreground; rotation is exempt from background cancellation. The watch's completion check and finalization fields are not fully understood. CI/build success does not establish device compatibility, safe recovery or a universal file-size limit. Follow [BLE_TESTING.md](BLE_TESTING.md).

## Hardware report and next work

The user previously reported captured/custom digital uploads and imported TTF fonts working. A later masked face displayed hours without minutes; its exact cause remains unconfirmed. Removing depth is a user-requested simplification, not a verified firmware diagnosis or repair.

Analogue and widget references have been received and privately inspected, but their editor/encoder support is not part of this UI update. Date/battery widgets and broader model compatibility remain unimplemented. No guessed fields have been shipped.

## Captured fixture exception

The exact original captured file contains a malformed unused1×1 dash pointer. Native parsing retains its full-file SHA256-gated transparent replacement, while rejecting arbitrary malformed files. Rebuilt bytes match the independent Python reference; captured-test upload still sends the untouched original.

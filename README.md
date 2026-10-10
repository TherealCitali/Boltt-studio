<div align="center">

# Boltt Studio

**Make time your own.**

A native Android watchface editor for the **Fire-Boltt Brillia** — based on the supplied MOYOUNG-V2 / API `0x23` reverse-engineering prototype.

[![Build](https://github.com/TherealCitali/Boltt-studio/actions/workflows/build.yml/badge.svg)](https://github.com/TherealCitali/Boltt-studio/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-GPL--3.0-orange)](LICENSE)

[Prerelease APKs](https://github.com/TherealCitali/Boltt-studio/releases) · [Protocol notes](docs/PROTOCOL.md) · [Port status](docs/PORT_STATUS.md)

</div>

> **0.3.1 beta / shared photo-depth editor.** Edit, export and send faces through Android Bluetooth. Test the **original captured Da Fit face first** on the documented Brillia firmware. CI validates codec/protocol logic, **not compatibility, safe recovery or installation on a physical watch**. Read the [hardware test checklist](docs/BLE_TESTING.md).

## Native editor

- Kotlin + Jetpack Compose Material 3; **not a WebView wrapper**.
- Package `dev.citali.bolttstudio`, Android 8.0+, JDK 21.
- 240 × 296 live preview with resizable hour/minute cells (default42×66).
- Background image picker, EXIF orientation, zoom/pan crop adjustments and reset.
- Linked or independent hour/minute size and movement, proportional scaling, width/height stretching, digit spacing, drag/snap and fit-to-canvas arrangements.
- Three system font families plus TTF/OTF import, separate hour/minute RGB/hex colors and outline.
- `.bin` export through Android’s document picker; no broad storage permission.
- Bounds-checked pure-Kotlin API `0x23` codec, tested against both supplied face files and Python-generated checksums.

- Explicit watch selection, Android-version Bluetooth/scan permissions, service discovery, MTU-derived packet sizes, serialized GATT writes and watch-requested blocks.
- Bounded timeouts/retries, progress, cancellation, diagnostics export, and a separate captured-face test action with confirmation.

Android10 uses legacy Bluetooth permissions and runtime Location for BLE scanning; Android12+ requests Nearby devices. There is **no Internet or broad storage permission**. Uploads require the app in the foreground. Editing state survives rotation, not process death. See [port status](docs/PORT_STATUS.md) for exact limits and unresolved protocol fields.

## Finding the controls

The pinned **Photo / Clock / Brush / Watch / Depth** buttons jump directly to each section; the top bar displays the installed version. Depth Lab now defaults to **Use selected photo & clock**, with shared crop, imported font, colors, resized/masked live digits and drag placement. Clock controls are also shown directly beneath its preview. Disable that switch only for the original fixed solid-background diagnostic. See [the 0.3.1 guide](docs/PHOTO_DEPTH_EDITOR.md).

## Depth lab — test before photo cutouts

The ordinary captured/custom upload path is [user-reported working](docs/HARDWARE_RESULTS.md). **Photo depth remains unverified.** This build adds a separate solid-background / live-digits / transparent-stripe probe, optional no-overlay control, simulated preview, measured file size and `.bin` export. Follow [DEPTH_TEST.md](docs/DEPTH_TEST.md) across minute changes and screen sleep/wake.

This is a firmware capability gate, **not the finished photo-depth workflow**. AI person segmentation, subject-mask editing and production foreground-image depth remain deferred. Version0.3.0 separately adds a **manual live-clock transparency brush** plus independent clock sizing/placement; see [clock editing](docs/CLOCK_EDITING.md). Manual glyph masking does not establish foreground-image layering support. No frozen-clock fallback or new network permission is introduced.

## Preserved reference implementation

| Location | Purpose |
|---|---|
| `web/index.html`, `web/moyface.js` | Original HTML editor and experimental Web Bluetooth uploader |
| `moyface.py`, `make_face.py` | Original Python encoder/decoder and CLI tool |
| `dafit_captured_face.bin` | Supplied Da Fit capture fixture |
| `demo_face.bin`, `demo_face_preview.png` | Supplied generated-face example |
| `roundtrip_test.py` | Portable reference regression test, now using repository-local fixtures |

The original web prototype requires HTTPS (or localhost) and a browser supporting Web Bluetooth. Its upload sequence contains unresolved fields; do not assume it is safe for other watches or firmware. Read the [captured protocol notes](docs/PROTOCOL.md). This project is independent, not an official Fire-Boltt or Da Fit application.

## Build and releases

```sh
# JDK 21 and Android SDK platform 37 required
./gradlew :app:testDebugUnitTest :app:lintDebug assembleDebug

# Reference codec checks
python -m pip install Pillow
python roundtrip_test.py
python depth_probe_test.py
```

Pushes to `main`/`dev` run tests, lint, build, sign and publish a commit prerelease. Pull requests run validation without signing or publication. Successful publication retains the latest **five prereleases**; drafts and stable releases are excluded from cleanup.

Stable releases are **manual** while the hardware port is in beta: run the **Stable release** workflow from `main`. It rejects existing version tags rather than overwriting them. Filename: `Boltt-Studio-Release-<version>-Universal.apk`, with SHA-256 checksum.

GitHub signing uses repository secrets `KEYSTORE`, `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`. Boltt Studio has its **own signing identity**, separate from ShadowRPC. No PAT, keystore, or signing password belongs in this repository. The private backup is delivered separately; keep it in encrypted offline storage.

## Next native milestones

1. Hardware validation on the documented Brillia firmware; completion-check/finalization research.
2. Saved projects and process-death restoration.
3. User-facing `.bin` import and format diagnostics.
4. Broader compatibility only after device-specific evidence.

## License and provenance

[GPL-3.0](LICENSE). The supplied Python source credits structure names to [david47k/extrathundertool](https://github.com/david47k/extrathundertool), identified there as GPL-2.0-or-later. That attribution is retained. Native codec logic is ported from this repository’s supplied Python/JavaScript reference. Hardware captures and artwork remain supplied reference assets; their presence is not a claim of manufacturer endorsement.

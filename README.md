<div align="center">

# Boltt Studio

**Make time your own.**

A native Android watchface editor for the **Fire-Boltt Brillia** — based on the supplied MOYOUNG-V2 / API `0x23` reverse-engineering prototype.

[![Build](https://github.com/TherealCitali/Boltt-studio/actions/workflows/build.yml/badge.svg)](https://github.com/TherealCitali/Boltt-studio/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-GPL--3.0-orange)](LICENSE)

[Prerelease APKs](https://github.com/TherealCitali/Boltt-studio/releases) · [Protocol notes](docs/PROTOCOL.md) · [Port status](docs/PORT_STATUS.md)

</div>

> **0.5.0 beta / digital editor.** Edit, export and send faces through Android Bluetooth. Test the **original captured Da Fit face first** on the documented Brillia firmware. CI validates codec/protocol logic, **not compatibility, safe recovery or installation on a physical watch**. Read the [hardware test checklist](docs/BLE_TESTING.md).

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

Pinned **Photo / Clock / Watch** buttons jump directly to each section. Clock controls include independent hour/minute sizing and movement, width/height stretching, spacing, imported fonts, colors and preview-time inspection.

Depth Lab, stripe probes, transparency masks and both brush editors were removed at the user's request in0.5.0. Generated faces now use the normal two-table digital path (hour set0, minute set1); no depth/masking state can be re-enabled. This removal is not a confirmed diagnosis or hardware fix for the previously reported missing minutes.

**Analogue faces are next, pending a supplied reference file.** A Base64 text file is acceptable. No analogue-hand encoding is guessed or enabled yet.

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
```

Pushes to `main`/`dev` run tests, lint, build, sign and publish a commit prerelease. Pull requests run validation without signing or publication. Successful publication retains the latest **five prereleases**; drafts and stable releases are excluded from cleanup.

Stable releases are **manual** while the hardware port is in beta: run the **Stable release** workflow from `main`. It rejects existing version tags rather than overwriting them. Filename: `Boltt-Studio-Release-<version>-Universal.apk`, with SHA-256 checksum.

GitHub signing uses repository secrets `KEYSTORE`, `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`. Boltt Studio has its **own signing identity**, separate from ShadowRPC. No PAT, keystore, or signing password belongs in this repository. The private backup is delivered separately; keep it in encrypted offline storage.

## Next native milestones

1. Inspect the supplied analogue reference before implementing live hands.
2. Hardware validation on the documented Brillia firmware; completion-check/finalization research.
3. Saved projects and process-death restoration.
4. User-facing `.bin` import and format diagnostics.
5. Broader compatibility only after device-specific evidence.

## License and provenance

[GPL-3.0](LICENSE). The supplied Python source credits structure names to [david47k/extrathundertool](https://github.com/david47k/extrathundertool), identified there as GPL-2.0-or-later. That attribution is retained. Native codec logic is ported from this repository’s supplied Python/JavaScript reference. Hardware captures and artwork remain supplied reference assets; their presence is not a claim of manufacturer endorsement.

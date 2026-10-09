<div align="center">

# Boltt Studio

**Make time your own.**

A native Android watchface editor for the **Fire-Boltt Brillia** — based on the supplied MOYOUNG-V2 / API `0x23` reverse-engineering prototype.

[![Build](https://github.com/TherealCitali/Boltt-studio/actions/workflows/build.yml/badge.svg)](https://github.com/TherealCitali/Boltt-studio/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-GPL--3.0-orange)](LICENSE)

[Prerelease APKs](https://github.com/TherealCitali/Boltt-studio/releases) · [Protocol notes](docs/PROTOCOL.md) · [Port status](docs/PORT_STATUS.md)

</div>

> **Beta / first native milestone.** The Kotlin app edits and exports `.bin` files locally. **Native Bluetooth transfer is not implemented yet.** CI validates codec behavior against the reference files, not compatibility or safety on a physical watch.

## Native editor

- Kotlin + Jetpack Compose Material 3; **not a WebView wrapper**.
- Package `dev.citali.bolttstudio`, Android 8.0+, JDK 21.
- 240 × 296 live preview with fixed 42 × 66 digit cells.
- Background image picker, zoom and crop adjustments.
- Stacked or single-line clock, position controls, centering, three system font families, separate hour/minute color choices, outline.
- `.bin` export through Android’s document picker; no broad storage permission.
- Bounds-checked pure-Kotlin API `0x23` codec, tested against both supplied face files and Python-generated checksums.

The native app requests **no Bluetooth or Internet permissions** at this milestone. Editing state survives configuration changes, but project persistence after process death is not yet implemented.

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

1. Custom TTF/OTF font import, drag/snap editing, and saved projects.
2. User-facing `.bin` import and format diagnostics.
3. Native BLE discovery, Android-version permissions, serialized GATT writes, MTU negotiation, cancellation and transfer state machine.
4. Hardware validation on the documented Brillia firmware before broadening compatibility.

## License and provenance

[GPL-3.0](LICENSE). The supplied Python source credits structure names to [david47k/extrathundertool](https://github.com/david47k/extrathundertool), identified there as GPL-2.0-or-later. That attribution is retained. Native codec logic is ported from this repository’s supplied Python/JavaScript reference. Hardware captures and artwork remain supplied reference assets; their presence is not a claim of manufacturer endorsement.

# Google Fonts library · 0.7.3

## Instant offline browsing
Each of the 1,723 catalog families now includes a small raster preview, rendered at build preparation time from the exact pinned font binary. Cards show 10:54 and all ten digits without downloading any font, license or image. Search and preview browsing work on a cold install without internet. Clearing downloaded fonts does not remove these bundled previews. No automatic network-preview setting remains.

The 720×96 transparent images are tinted to match light/dark UI. They preserve original font outlines/default variable axes and are illustrative raster previews, not a promise of pixel-identical Android rasterization. Get/Choose opens the native Android sample before Use font changes the editor. The existing TTF/OTF picker is retained.

## Source and generation
Source: Google Fonts family metadata and google/fonts revision bd8f81ddb5c74d5c8897b36ad88b440266245103. Same 1,723-family snapshot and preferred default/upright TTF per family as before; not every weight/italic variant.

`scripts/update_google_fonts.py` creates the catalog. `scripts/build_font_previews.py` fetches and verifies each pinned font and license, checks ASCII digit mappings with fontTools, and renders the thumbnails with Pillow/FreeType. Full font binaries are not bundled in release. Individual source licenses are included in `assets/google-fonts/preview-licenses`. Kumar One's problematic TrueType hint bytecode is omitted only in its offline preview generation; outlines are unchanged and the original downloadable file remains untouched.

Current output: 1,723 previews, no missing-digit exceptions; 10,840,887 bytes of PNG data before APK compression. This increases installation size but eliminates repeated preview bandwidth and cold-network wait times. Future catalog updates must regenerate previews and pass the coverage test.

## Runtime/storage
Visible/composed cards decode their small local PNG on Dispatchers.IO. A 4MiB bitmap LRU limits retained thumbnail memory; evicted bitmaps are not recycled while Compose might still hold them. Sample space is reserved to prevent list jumps.

Full-font downloads occur only after Get/Choose, through the existing fixed-host HTTPS downloader with bounded sizes, timeout/cancellation checks, exact Git blob identity and accompanying license. Downloaded native fonts still work offline. The full-font library retains its 64MiB cap. Use font writes an independent private font/license copy with the draft. No editor photos, designs or watch identifiers are uploaded.

The INTERNET permission remains for explicit font downloads; there is no account/API key, live catalog API, analytics or cloud sync. Google Fonts’ raw GitHub host sees normal request metadata only when a full font is requested. The earlier automatic-preview downloader has been removed, not merely sped up.

## Validation
JVM coverage checks every catalog family has a correctly sized PNG and source license (or an explicitly documented digit exception), with a total preview byte cap. Existing catalog/network-boundary/math/codec/BLE tests continue.

API35 emulator regression starts with an empty downloaded-font directory, scrolls to Roboto Mono and verifies its bundled preview is displayed, no full font is downloaded, no library selection is made, and the draft font stays unchanged. Existing keyboard/layout/wheel tests remain. This is not on-watch testing or proof that every font renders identically on every Android device.

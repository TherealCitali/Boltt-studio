# Google Fonts library · 0.7.2

## Using it
Type & color → Browse Google Fonts. Search the family name; filter Sans Serif, Serif, Display, Handwriting or Monospace. The catalog/search are offline. Visible cards automatically fetch their actual font and license and render a 10:54 · 0123456789 sample; browsing does not change the draft. Choose opens the full review, then Use font applies it. Automatic previews can be paused. Applying updates the pinned watch preview and saves a private draft copy. Inspect all digits before export/send. Use Downloaded to reuse saved library fonts offline. Clear downloads keeps the active draft font and license.

Existing TTF/OTF file import is retained and hardware-confirmed by the user. That confirmation is not blanket testing of all Google Fonts or variable fonts. The watch receives rasterized live digit tables, not font files. No new clock element encoding or protocol changes.

## Catalog and source
Bundled snapshot: 1,723 families. Sources: https://fonts.google.com/metadata/fonts and https://github.com/google/fonts at revision bd8f81ddb5c74d5c8897b36ad88b440266245103, fetched for this update. Metadata ranks common families first. scripts/update_google_fonts.py reproducibly builds the index from a complete repository tree plus family metadata.

Eligibility: Latin-supporting, non-color family; available upright-named TTF <=4 MiB with a matching local license <=64 KiB. Prefer Regular, then a variable/default file, then another upright file. Some families are excluded. This is not the entire live Google Fonts catalog and does not expose weight/italic/variable-axis controls. Variable fonts use Android's default axis values. File names are shown via family names; no network preview thumbnails are fetched automatically.

## Network/privacy
The app now declares INTERNET and disables cleartext traffic. Browsing never calls the Google Fonts API and needs no key, login, Google Play font provider or proprietary SDK. For visible automatic previews or an explicit Get action, Android downloads the exact font and license from raw.githubusercontent.com/google/fonts/<pinned revision>/.... The hosting service sees ordinary request metadata such as IP address and the font path. No editor images, designs, BLE device identifiers or account data are sent. No analytics or cloud sync.

HTTPS only, fixed host/revision, encoded validated relative paths, no redirects. 15-second connect/read timeouts plus a 45-second checked operation budget. Bounded stream reads, exact bundled sizes and Git blob SHA-1 identities detect corrupt/wrong downloads; SFNT signature and table bounds checked before Android Typeface construction. Git blob identity is a repository consistency check, not a new cryptographic authenticity promise; TLS/pinned source provide the transport/source boundary. Failed or cancelled downloads never replace the editor font. Viewport requests settle for 250ms, load sequentially, and stop as cards leave the viewport, the page closes, or the app backgrounds. Cancellation may wait for socket timeout. Up to 24 preview typefaces are kept in memory; downloaded font/license files share the existing 64 MiB storage cap. Failure cards show Retry, not fake samples. Clearing downloads pauses automatic previews to prevent immediate refilling. Blocking socket cancellation may wait for the read timeout.

## Storage/licensing
Font + license are downloaded/validated before publication as a private library directory. The library is capped at 64 MiB and each download at 4 MiB. Incomplete temporary folders are removed on next library initialization. Cached files are verified before reuse; a corrupt entry is removed and needs another explicit download. Clear downloads is confirmed.

Use font writes an independent synced UUID-named private draft font and accompanying license, then publishes the filename, label and selection together. Older v1 drafts without a font label still restore. The saved font's license is accessible under Style even after library copies are cleared. Licenses ship with downloaded fonts rather than an unlicensed font-only cache. App data deletion/uninstall removes both draft and downloads.

## Validation
JVM tests: full shipped catalog parsing, known families, paths/URL encoding, local search/categories, metadata bounds, exact Git blob identity/length, bounded and zero-read streams, cancellation callback, rejection of HTML/WOFF/broken SFNT headers, and old/new draft metadata. Existing codec and BLE tests continue.

Development network smoke checks downloaded Roboto, Montserrat, Poppins, Roboto Mono, Bebas Neue and Inconsolata font/license pairs at the pinned revision, verified sizes and Git blob hashes, and checked mappings for digits 0–9 using fontTools. These checks are not Android rendering or on-watch tests. Android UI/network/restart/font appearance still require device validation.

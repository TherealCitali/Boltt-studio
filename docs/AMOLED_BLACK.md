# AMOLED black correction · 0.7.4

User requested near-black greys be sent as pure black. A private exported face contained opaque RGB(16,16,16), not RGB zero; no firmware malfunction was established.

## Processing
After Android scales/composites the photo onto the opaque background, before clock composition or binary building, `AmoledBlack` checks each background pixel:
- max(R,G,B) <= cutoff (default24/255, selectable0–48);
- max(R,G,B) - min(R,G,B) <=8 (near-neutral, tolerates small quantization/compression tints).
Matching RGB channels become exactly0; alpha is preserved. Default settings cover both #111111 source grey and #101010 decoded grey. More saturated dark colors and pixels above the cutoff remain unchanged. Raising the cutoff intentionally flattens more near-neutral shadow detail.

The preview and encoded background share this corrected bitmap. Export and Send current face use the same renderer output. The native RGB565/RLE codec preserves zero. Digit tables, chosen clock colors and glyph alpha are not corrected. The original selected photo and saved private PNG are not overwritten, so turning correction off restores the old rendering.

## Settings / migration
Photo & crop → AMOLED pure black. Default on for new drafts and older v1 drafts without this setting. Both switch and cutoff persist; turning it off stays off across restart. Cutoff0 affects only pixels already black. Reset black cutoff restores24. No broad brightness threshold applied to the whole composed watchface.

## Exceptions and limits
Captured Da Fit test upload stays byte-identical and is intentionally outside this correction. No depth/transparency feature is restored. No BLE or finalization fields change. A pure-zero export is software-verifiable; physical display/firmware behavior still requires a watch test. Do not claim it forces OLED subpixels off on every firmware.

## Tests
JVM tests cover known grey values, neutral tolerance, colored/shadow preservation, alpha, cutoff bounds, metadata migration/roundtrip and zero through actual RGB565 RLE encoding. API35 emulator test renders a synthetic RGB17 photo, checks preview/background zero, builds/parses the entire binary and checks every background pixel remains zero; disabled output/source photo stay RGB17. No private user image or face was committed.

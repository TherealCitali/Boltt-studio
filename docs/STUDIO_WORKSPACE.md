# Studio workspace · 0.6.0 (extended in 0.7.0)

## Layout
- The preview is a non-scrolling sibling of the settings sheet. Expand never overlays it.
- Drag the grab area to resize, or tap Expand/Less (accessible non-gesture alternative).
- Wide windows use preview left / settings right. A compact window still reserves space for the preview.
- Each settings page scrolls independently with MiuixOverscrollFactory 0.9.3, matching ShadowRPC.
- Photo/crop, geometry, fonts/colors, preview/export and connection settings are separate. Color sliders remain inside the sheet, not an editor-covering modal.
- Android Back returns to Design; watch upload confirmation remains a deliberate safety dialog.
- Material You on Android 12+, warm fallback theme on older Android; Montserrat only changes app typography, not the chosen watch glyph font.

## Local draft
EditorState is now an AndroidViewModel backed by versioned metadata in private SharedPreferences. Every scalar edit queues an apply write; Android flushes these at normal lifecycle transitions. There is no debounce window or dependency on leaving a settings page. As with local storage generally, abrupt power loss or storage failure is not a guarantee of the last write.

Photo and font imports write UUID-named private files, sync them, then publish their filename in metadata. Original document URIs are not retained. The photo copy is orientation-corrected and sampled to approximately 1024px maximum dimension before saving as PNG. Imports remain bounded (font <=4MiB); metadata paths are validated as private basenames. Stale media is collected on the next launch, retaining referenced assets. Draft parsing bounds geometry, crop and preview time, rejects future versions, and handles missing media with a visible fallback notice.

Persisted: photo, font (also retained when switching temporarily to system fonts), crop, both clock groups' position/size/gap, independent/selected group, colors, outline, system family, custom-font switch, snapping, preview time.
0.7.0 also retains the chosen font family label and its license beside the draft font. The Google Fonts library has its own downloaded copies; clearing those does not delete the active draft font. See GOOGLE_FONTS.md.

Not persisted: BLE devices/connections, consent, pending transfers, pending export bytes across process death, diagnostics, multiple named projects. Clearing app data/uninstall removes the draft; old 0.5.x process-death losses cannot be recovered.

## Removed
The .bin import UI, loading model, importer codec wrapper, tests and documentation were removed. Photo and TTF/OTF pickers, file export, captured-face testing and current-face BLE sending remain. The digital parser's descriptor bounds remain intact. No depth features returned.

## Icon
API26 adaptive background/foreground, with watch/bolt artwork scaled into the safe zone. API33 adds a distinct transparent monochrome layer for themed launchers. Both normal and round icon references point at the adaptive asset. Launcher masking/themed behavior is controlled by the launcher, and its cache may take time to refresh.

## Validation
Automated checks cover draft default/custom roundtrips, persisted private media references, partial/corrupt/extreme metadata, path rejection, versions, and existing codec/transfer regressions. Python reference image roundtrips remain pixel-identical. CI runs Kotlin tests, lint and release build.

Manual device checks still needed: import a photo and font, change every setting, leave/reopen and force-stop/reopen after edits settle; rotate; expand/collapse with a large system font; test keyboard and landscape; scroll to both edges; inspect adaptive circle/squircle/themed icons. Test editor/captured transfers separately on the documented watch firmware. No automated test here proves hardware watch compatibility.

## Design and attribution
Original Boltt UI implementation, visually inspired by LunarTune (cognitiveshadows03), particularly its tonal rounded groups and typography hierarchy. No LunarTune application code or proprietary SF Pro font was copied. Miuix overscroll and Montserrat font notices are in app/src/main/assets/licenses.

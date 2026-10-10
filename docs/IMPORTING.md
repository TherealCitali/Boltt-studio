# Import .bin face · 0.5.1

## Use

1. Tap **Watch** in the pinned navigation.
2. Under **Import .bin face**, choose your decoded binary file. The picker permits all MIME types because providers often label `.bin` inconsistently; acceptance depends on file contents, not the extension.
3. Inspect the file name, embedded thumbnail, byte count, table count and SHA-256. The thumbnail is supplied by the file and can be stale or unrelated to actual rendering.
4. Connect to your intended Brillia, stop Da Fit and charge above30%.
5. Tap **Send imported face…**, review the selected watch and hash, acknowledge the experimental upload risk, and confirm.

The original bytes are uploaded unchanged. There is no parse/rebuild transformation, including for the exact captured file's known unused-dash quirk. The normal editor and its export remain separate. Importing does not connect/upload automatically.

## Scope and privacy

- Import-for-upload, **not editable project import**.
- Validated API0x23 digital faces,240×296 background and140×163 preview. Unknown element types/analogue formats are rejected, not sent through an unvalidated bypass.
- Raw `.bin` only; rename/base64 text is not a binary conversion. Chat uploads may still use Base64 for analysis, but the app importer expects decoded bytes.
- Bounded streaming reads, at most2MiB even when a provider reports no size. This is an application guard, not a firmware capacity guarantee.
- Validate image dimensions before decoding oversized face assets; existing offset/RLE/position validation remains. Four-table legacy files may parse, but the importer warns about more than two tables and does not promise their hardware compatibility. No removed depth creator/settings are reintroduced.
- Selection is held in a ViewModel memory snapshot across rotation. Original files are not rewritten. No broad storage permission, persistent file copy or network upload is added.
- Process death clears the selection. Starting a new import clears the old selection; a failed replacement cannot leave an old file silently ready to send. Cancelling the picker leaves the current selection alone.
- File name/hash are displayed for confirmation; BLE logs use a generic imported-file label rather than the user's file name. No image data is logged.
- Picking/replacing files is disabled during connection setup or upload. Send requires a ready connection and fresh explicit confirmation. Upload revalidates its copied bytes before starting the captured BLE protocol.

## Tests

Tests verify byte-for-byte preservation for captured/demo fixtures, SHA-256, snapshot isolation, rejection of text/truncation/unknown elements/oversized descriptors, bounded unknown-length reads, and short/zero-returning stream behavior. Build checks do not prove every custom file works on the watch; inspect the actual watch after uploading.

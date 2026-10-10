# Brillia hardware test checklist

**Experimental, reverse-engineered transfer. A failed/incompatible/interrupted upload can leave a partial or unusable face. Recovery is not guaranteed.** The assistant has not tested a physical watch.

## Before installing/sending

- Use the same Boltt Studio signing identity when updating. This build keeps package `dev.citali.bolttstudio`.
- Confirm Fire-Boltt Brillia, documented firmware `MOY-7QI2-2.0.1`. The app checks service properties, not firmware identity.
- Watch battery >30%; phone/watch close; Da Fit stopped/disconnected. Do not reset, rename, unpair or otherwise modify other devices as part of this test.
- Have the vendor's documented support/recovery instructions available. Do not assume a factory reset will repair an incompatible face.
- Android10: allow precise Location and enable system Location plus Bluetooth. Android12+: allow Nearby devices. No storage permission is needed.

## Baseline test: original captured file

1. Scan; choose your watch explicitly. If absent, enable **Show all nearby devices** and scan again. Check the name/address yourself.
2. Discovery must find FEEA/FEE2/FEE3/FEE6 and subscribe to notifications. Record the negotiated payload from diagnostics.
3. Tap **Send captured Da Fit face · test first**. The asset must be120,956bytes, identical to the repository's capture.
4. Read/accept the confirmation. Keep the app visible. Do not switch apps, open settings, turn off Bluetooth, or interrupt power during the baseline test.
5. Wait for **Watch reported complete** and final commands to be sent. Inspect the actual watch for the new face, correct digits, background and normal operation. The app's status is not visual verification.
6. Export diagnostics after the operation. Record Android version, watch firmware, packet size, elapsed time and the watch's observed result. Remove anything personal before sharing.

## Generated face test (only after baseline succeeds)

Use a simple background and built-in font first. Position the clock within the preview bounds. Send the current face and inspect it on the watch. Then test custom fonts, independent hour/minute colors, layouts and crop adjustments individually. Quantization to RGB565 is expected.

## If anything fails

Stop. Inspect the watch before retrying, and use the vendor's documented recovery process if required. Save diagnostics, exact error text and what the watch displays. Do not repeat uploads blindly. The app deliberately does not retry an ambiguous write at a different packet size. A manual retry starts a new explicit connection/upload; partial watch state may remain.

## Engineering checks, not baseline-user actions

Before declaring the port reliable, separately test denial/revocation of permissions, unknown devices, connection loss, bounded scan behavior, no-MTU fallback,20/244byte writes, split notifications, invalid/duplicate block requests, timeouts, rotation and cancellation/backgrounding. **Do not deliberately interrupt a real upload without accepting the recovery risk.** Prefer a test peripheral/simulator for fault injection.

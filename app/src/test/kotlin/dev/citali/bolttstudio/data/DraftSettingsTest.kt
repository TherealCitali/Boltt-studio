package dev.citali.bolttstudio.data

import dev.citali.bolttstudio.codec.ClockDesign
import dev.citali.bolttstudio.codec.ClockGroup
import org.junit.Assert.*
import org.junit.Test

class DraftSettingsTest {
    @Test fun defaultsRoundTrip() {
        assertEquals(DraftSettings(), DraftSettings.decode(DraftSettings().encode()))
    }
    @Test fun allEditsAndPrivateMediaNamesRoundTrip() {
        val draft = DraftSettings(ClockDesign(ClockGroup(8, 9, 33, 105, 4), ClockGroup(45, 168, 50, 80, 12)),
            true, false, 1439, false, "serif", true, true, 0xffee9933.toInt(), 0xff7799aa.toInt(),
            2.2f, -.3f, .8f, "12345678-1234-1234-1234-123456789abc.png", "abcdef01-1234-1234-1234-123456789abc.font", "Roboto Mono")
        assertEquals(draft, DraftSettings.decode(draft.encode()))
    }
    @Test fun invalidMetadataIsBoundedAndCannotReferenceExternalFiles() {
        val data = DraftSettings.decode("version=1\nh.x=-100\nh.width=100000\nm.height=-30\nzoom=NaN\npanX=Infinity\npanY=99\npreviewMinute=9999\nphoto=../../secret.png\nfont=/sdcard/a.font\nuseCustomFont=true\nfamily=anything")
        assertEquals(0, data.design.hours.x)
        assertTrue(data.design.hours.span <= 240)
        assertEquals(16, data.design.minutes.height)
        assertEquals(1f, data.zoom); assertEquals(0f, data.panX); assertEquals(1f, data.panY)
        assertEquals(1439, data.previewMinute)
        assertEquals("", data.photo); assertEquals("", data.font); assertFalse(data.useCustomFont)
        assertEquals("sans-serif", data.family)
    }
    @Test fun missingOptionalValuesUseDefaults() {
        assertEquals(DraftSettings(), DraftSettings.decode("version=1"))
    }
    @Test fun olderDraftsKeepTheirFontWithoutNeedingALabel() {
        val old = "version=1\nfont=abcdef01-1234-1234-1234-123456789abc.font\nuseCustomFont=true"
        val restored = DraftSettings.decode(old)
        assertTrue(restored.useCustomFont); assertEquals("Imported font", restored.fontLabel)
    }
    @Test fun layoutSelectionPersistsAndOldSideBySideDraftsMigrate() {
        val row = DraftSettings(design = ClockDesign().arrange(false), stacked = false)
        assertEquals(row, DraftSettings.decode(row.encode()))
        assertFalse(DraftSettings.decode("version=1\nh.x=15\nh.y=115\nm.x=125\nm.y=115").stacked)
        assertTrue(DraftSettings.decode("version=1").stacked)
    }
    @Test fun unknownVersionsAndHugeMetadataAreRejected() {
        assertTrue(runCatching { DraftSettings.decode("version=2") }.isFailure)
        assertTrue(runCatching { DraftSettings.decode("x".repeat(16385)) }.isFailure)
    }
}

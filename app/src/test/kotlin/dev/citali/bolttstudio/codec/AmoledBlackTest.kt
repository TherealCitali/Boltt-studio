package dev.citali.bolttstudio.codec

import dev.citali.bolttstudio.data.DraftSettings
import org.junit.Assert.*
import org.junit.Test

class AmoledBlackTest {
    @Test fun measuredGreysBecomeExactOpaqueBlack() {
        for (color in listOf(0xff111111.toInt(), 0xff101010.toInt(), 0xff101008.toInt(), 0xff181818.toInt())) {
            assertEquals(0xff000000.toInt(), AmoledBlack.correct(color))
        }
    }
    @Test fun coloredPixelsAndBrighterShadowDetailsArePreserved() {
        for (color in listOf(0xff96bb5c.toInt(), 0xff001800.toInt(), 0xff191919.toInt(), 0xff252525.toInt(), -1)) {
            assertEquals(color, AmoledBlack.correct(color))
        }
        assertEquals(0x80111111.toInt() and 0xff000000.toInt(), AmoledBlack.correct(0x80111111.toInt()))
    }
    @Test fun cutoffBoundsAndZeroAreRespected() {
        assertEquals(0xff111111.toInt(), AmoledBlack.correct(0xff111111.toInt(), 0))
        assertEquals(0xff000000.toInt(), AmoledBlack.correct(0xff252525.toInt(), 40))
        assertEquals(0xff313131.toInt(), AmoledBlack.correct(0xff313131.toInt(), 999))
    }
    @Test fun correctedBlackSurvivesActualRgb565RleEncoding() {
        val pixels = IntArray(240 * 296) { 0xff111111.toInt() }
        AmoledBlack.apply(pixels)
        val encoded = MoyFace.encodeImage(FaceImage(240, 296, pixels))
        val decoded = MoyFace.decodeImage(encoded, 0, 240, 296)
        assertTrue(decoded.pixels.all { it == 0xff000000.toInt() })
    }
    @Test fun correctionSettingsPersistAndExistingDraftsEnableIt() {
        assertTrue(DraftSettings.decode("version=1").amoledBlack)
        assertEquals(24, DraftSettings.decode("version=1").blackCutoff)
        val off = DraftSettings(amoledBlack = false, blackCutoff = 37)
        assertEquals(off, DraftSettings.decode(off.encode()))
        assertEquals(48, DraftSettings.decode("version=1\nblackCutoff=999").blackCutoff)
    }
}

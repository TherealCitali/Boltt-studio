package dev.citali.bolttstudio.ui

import org.junit.Assert.*
import org.junit.Test

class WheelColorTest {
    @Test fun rgbRoundTripsAndAlphaStaysOpaque() {
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val color = (0xff shl 24) or (r shl 16) or (g shl 8) or b
            assertEquals(color, WheelColor.fromArgb(color).argb())
        }
        assertEquals(0xffff0000.toInt(), WheelColor(360f, 1f, 1f).argb())
        assertEquals(0xff000000.toInt(), WheelColor(150f, 1f, 0f).argb())
        assertEquals(-1, WheelColor(150f, 0f, 1f).argb())
    }
    @Test fun diamondMappingMatchesAllFourCornersAndInterior() {
        val g = WheelGeometry(300f)
        for (s in listOf(0f, .2f, .5f, .8f, 1f)) for (v in listOf(0f, .2f, .5f, .8f, 1f)) {
            val p = g.point(s, v); val actual = g.sv(p.first, p.second)
            assertEquals(s, actual.first, .00001f); assertEquals(v, actual.second, .00001f)
        }
        val top = g.point(0f, 1f); assertEquals(g.center, top.first, .001f); assertTrue(top.second < g.center)
        val right = g.point(1f, 1f); assertTrue(right.first > g.center); assertEquals(g.center, right.second, .001f)
    }
    @Test fun ringAndDiamondAreDistinctAndTouchStaysBounded() {
        val g = WheelGeometry(300f)
        assertTrue(g.isRing(g.center + g.ringRadius, g.center)); assertFalse(g.isRing(g.center, g.center))
        assertTrue(g.isDiamond(g.center, g.center)); assertFalse(g.isDiamond(g.center + g.ringRadius, g.center))
        assertEquals(0f, g.hue(g.center + g.ringRadius, g.center), .001f)
        assertEquals(90f, g.hue(g.center, g.center + g.ringRadius), .001f)
        val out = g.sv(-1000f, 9999f); assertTrue(out.first in 0f..1f && out.second in 0f..1f)
    }
}

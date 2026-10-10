package dev.citali.bolttstudio.data

import dev.citali.bolttstudio.codec.ClockDesign
import dev.citali.bolttstudio.codec.ClockGroup
import java.util.Properties
import java.io.StringReader
import java.io.StringWriter

/** Versioned, local-only draft metadata. No Bluetooth session or pending send is persisted. */
data class DraftSettings(
    val design: ClockDesign = ClockDesign(),
    val independent: Boolean = false, val editHours: Boolean = true,
    val previewMinute: Int = 609, val outline: Boolean = true,
    val family: String = "sans-serif", val useCustomFont: Boolean = false,
    val snap: Boolean = false, val hours: Int = -1, val minutes: Int = -1,
    val zoom: Float = 1f, val panX: Float = 0f, val panY: Float = 0f,
    val photo: String = "", val font: String = "", val fontLabel: String = "Imported font",
) {
    fun encode(): String {
        val p = Properties()
        fun put(k: String, v: Any) { p.setProperty(k, v.toString()) }
        put("version", 1)
        fun group(prefix: String, g: ClockGroup) {
            put("$prefix.x", g.x); put("$prefix.y", g.y); put("$prefix.width", g.width)
            put("$prefix.height", g.height); put("$prefix.gap", g.gap)
        }
        group("h", design.hours); group("m", design.minutes)
        put("independent", independent); put("editHours", editHours); put("previewMinute", previewMinute)
        put("outline", outline); put("family", family); put("useCustomFont", useCustomFont)
        put("snap", snap); put("hours", hours); put("minutes", minutes)
        put("zoom", zoom); put("panX", panX); put("panY", panY); put("photo", photo); put("font", font); put("fontLabel", fontLabel)
        return StringWriter().also { p.store(it, "Boltt Studio local draft") }.toString()
    }
    companion object {
        fun decode(text: String): DraftSettings {
            require(text.length <= 16_384) { "Draft metadata is too large" }
            val p = Properties().apply { load(StringReader(text)) }
            require(p.getProperty("version") == "1") { "Unsupported draft version" }
            val defaults = DraftSettings()
            fun int(k: String, default: Int) = p.getProperty(k)?.toIntOrNull() ?: default
            fun bool(k: String, default: Boolean) = p.getProperty(k)?.toBooleanStrictOrNull() ?: default
            fun float(k: String, default: Float, range: ClosedFloatingPointRange<Float>): Float =
                p.getProperty(k)?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(range) ?: default
            fun group(prefix: String, g: ClockGroup) = ClockGroup(int("$prefix.x", g.x), int("$prefix.y", g.y),
                int("$prefix.width", g.width), int("$prefix.height", g.height), int("$prefix.gap", g.gap)).bounded()
            fun asset(k: String) = p.getProperty(k, "").takeIf { it.matches(Regex("[a-f0-9-]{36}\\.(png|font)")) } ?: ""
            val font = asset("font")
            return DraftSettings(
                design = ClockDesign(group("h", defaults.design.hours), group("m", defaults.design.minutes)),
                independent = bool("independent", false), editHours = bool("editHours", true),
                previewMinute = int("previewMinute", 609).coerceIn(0, 1439), outline = bool("outline", true),
                family = p.getProperty("family")?.takeIf { it in listOf("sans-serif", "serif", "monospace") } ?: "sans-serif",
                useCustomFont = bool("useCustomFont", false) && font.isNotEmpty(), snap = bool("snap", false),
                hours = int("hours", -1) or 0xff000000.toInt(), minutes = int("minutes", -1) or 0xff000000.toInt(),
                zoom = float("zoom", 1f, 1f..3f), panX = float("panX", 0f, -1f..1f), panY = float("panY", 0f, -1f..1f),
                photo = asset("photo"), font = font,
                fontLabel = p.getProperty("fontLabel", "Imported font").filterNot { it.isISOControl() }.take(120).ifBlank { "Imported font" },
            )
        }
    }
}

package dev.citali.bolttstudio

import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.lifecycle.ViewModelProvider
import dev.citali.bolttstudio.fonts.FontCatalog
import dev.citali.bolttstudio.fonts.FontLibraryState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkspaceRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private fun ready() { rule.waitUntil(20000) { rule.onAllNodesWithTag("settings-sheet").fetchSemanticsNodes().isNotEmpty() } }
    private fun imeVisible() = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    private fun screenshot(name: String) {
        val file = File(rule.activity.getExternalFilesDir(null), "ui-tests/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun keyboardCloseAndPageNavigationRestoreExpandedWorkspace() {
        ready()
        rule.onNodeWithText("Expand").performClick()
        val beforePreview = rule.onNodeWithTag("preview-region").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        val beforeSheet = rule.onNodeWithTag("settings-sheet").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }
        rule.onNodeWithText("Type & color").performScrollTo().performClick()
        rule.onNodeWithText("Browse Google Fonts").performScrollTo().performClick()
        rule.onNodeWithText("Search Google Fonts").performClick().performTextInput("Roboto")
        rule.waitUntil(15000) { imeVisible() }
        Espresso.closeSoftKeyboard()
        rule.waitUntil(15000) { !imeVisible() }
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Back").performClick()
        rule.waitUntil(10000) {
            kotlin.math.abs(rule.onNodeWithTag("settings-sheet").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value } - beforeSheet) < 3f
        }
        assertEquals(beforePreview, rule.onNodeWithTag("preview-region").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }, 3f)
        rule.onNodeWithText("Photo & crop").performScrollTo().assertIsDisplayed()
        screenshot("keyboard-restored")
    }
    @Test fun layoutToggleAndColourWheelStayInsidePreviewWorkspace() {
        ready()
        rule.onNodeWithText("Clock layout").performScrollTo().performClick()
        rule.onNodeWithText("Side by side").performClick().assertIsSelected()
        screenshot("side-by-side")
        rule.onNodeWithText("Stacked").performClick().assertIsSelected()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("Type & color").performScrollTo().performClick()
        rule.onNodeWithText("Hours colour wheel", substring = true).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Hours colour wheel").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Hours colour wheel").performTouchInput { click(center) }
        rule.onNodeWithTag("preview-region").assertIsDisplayed()
        screenshot("colour-wheel")
        rule.onNodeWithText("Hours hex · 6 digits").performScrollTo().performClick().performTextReplacement("96BB5C")
        rule.onNodeWithText("Hours hex · 6 digits").performImeAction()
        rule.onNodeWithText("Apply Hours hex").assertExists()
        rule.waitUntil(10000) { !imeVisible() }
    }
    @Test fun uncachedFontCardUsesBundledSampleWithoutDownloadingOrChangingDraft() {
        ready()
        val context = rule.activity
        val catalog = context.assets.open("google-fonts/catalog.tsv").bufferedReader().use { FontCatalog.parse(it.readText()) }
        val entry = catalog.fonts.single { it.family == "Roboto Mono" }
        // Cold library: no font binary or license available locally.
        File(context.filesDir, "google-fonts").deleteRecursively()
        var editor: EditorState? = null
        rule.runOnIdle { editor = ViewModelProvider(rule.activity)[EditorState::class.java] }
        val originalTypeface = editor!!.customTypeface
        val originalLabel = editor!!.fontLabel
        rule.onNodeWithText("Type & color").performScrollTo().performClick()
        rule.onNodeWithText("Browse Google Fonts").performScrollTo().performClick()
        // Asset parsing is IO work, not a Compose idling resource. Wait before querying its rows.
        rule.waitUntil(20000) {
            var loaded = false
            rule.runOnIdle { loaded = ViewModelProvider(rule.activity)[FontLibraryState::class.java].catalog != null }
            loaded
        }
        rule.onNodeWithText("Search Google Fonts").performClick().performTextInput("Roboto Mono")
        rule.onNodeWithText("Search Google Fonts").performImeAction()
        rule.onNodeWithTag("font-library-list").performScrollToNode(hasText("Roboto Mono"))
        rule.waitUntil(15000) { rule.onAllNodesWithTag("font-sample-Roboto Mono").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("font-sample-Roboto Mono").performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            val library = ViewModelProvider(rule.activity)[FontLibraryState::class.java]
            assertFalse(entry.blob in library.saved)
            assertFalse(File(context.filesDir, "google-fonts/${entry.blob}/font.ttf").exists())
            assertNull(library.selected)
            assertSame(originalTypeface, editor!!.customTypeface)
            assertEquals(originalLabel, editor!!.fontLabel)
        }
    }

    @Test fun nearBlackPhotoIsCorrectedInPreviewAndEncodedFaceWithoutChangingSource() {
        val photo = Bitmap.createBitmap(240, 296, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff111111.toInt()) }
        fun render(enabled: Boolean) = dev.citali.bolttstudio.ui.FaceRenderer.render(photo, 74, 41, true,
            "sans-serif", -1, -1, false, 1f, 0f, 0f, amoledBlack = enabled)
        rule.runOnIdle {
            val on = render(true)
            assertEquals(0xff000000.toInt(), on.image.getPixel(0, 0))
            assertTrue(on.face.background.pixels.all { it == 0xff000000.toInt() })
            val roundtrip = dev.citali.bolttstudio.codec.MoyFace.parse(dev.citali.bolttstudio.codec.MoyFace.build(on.face))
            assertTrue(roundtrip.background.pixels.all { it == 0xff000000.toInt() })
            val off = render(false)
            assertEquals(0xff111111.toInt(), off.image.getPixel(0, 0))
            assertEquals(0xff111111.toInt(), photo.getPixel(0, 0))
        }
    }

}

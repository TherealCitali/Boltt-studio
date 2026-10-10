package dev.citali.bolttstudio

import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
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
    @Test fun visibleFontCardAutomaticallyRendersVerifiedTypefaceWithoutChangingDraft() {
        ready()
        val context = rule.activity
        val catalog = context.assets.open("google-fonts/catalog.tsv").bufferedReader().use { FontCatalog.parse(it.readText()) }
        val entry = catalog.fonts.single { it.family == "Roboto Mono" }
        val directory = File(context.filesDir, "google-fonts/${entry.blob}").apply { mkdirs() }
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets
        fixture.open("font-preview-fixture/font.ttf").use { input -> File(directory, "font.ttf").outputStream().use { input.copyTo(it) } }
        fixture.open("font-preview-fixture/OFL.txt").use { input -> File(directory, "license.txt").outputStream().use { input.copyTo(it) } }
        var editor: EditorState? = null
        rule.runOnIdle { editor = ViewModelProvider(rule.activity)[EditorState::class.java] }
        val originalTypeface = editor!!.customTypeface
        val originalLabel = editor!!.fontLabel
        rule.onNodeWithText("Type & color").performScrollTo().performClick()
        rule.onNodeWithText("Browse Google Fonts").performScrollTo().performClick()
        rule.onNodeWithText("Search Google Fonts").performClick().performTextInput("Roboto Mono")
        rule.onNodeWithText("Search Google Fonts").performImeAction()
        rule.onNodeWithTag("font-library-list").performScrollToNode(hasText("Roboto Mono"))
        rule.waitUntil(15000) { rule.onAllNodesWithTag("font-sample-Roboto Mono").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("font-sample-Roboto Mono").assertIsDisplayed()
        rule.runOnIdle {
            val library = ViewModelProvider(rule.activity)[FontLibraryState::class.java]
            assertNotNull(library.cardFonts[entry.blob])
            assertNull(library.selected)
            assertSame(originalTypeface, editor!!.customTypeface)
            assertEquals(originalLabel, editor!!.fontLabel)
        }
    }

}

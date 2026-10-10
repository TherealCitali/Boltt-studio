package dev.citali.bolttstudio.ui

import android.os.Build
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.citali.bolttstudio.R
import top.yukonga.miuix.kmp.utils.MiuixOverscrollFactory

/** Original Boltt layout, visually inspired by LunarTune's tonal grouped settings.
 * ShadowRPC's Miuix edge factory is used without installing the Miuix UI theme. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StudioTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFFFB68D), primaryContainer = Color(0xFF743A17),
            secondaryContainer = Color(0xFF44352E), background = Color(0xFF141210), surface = Color(0xFF141210))
        else -> lightColorScheme(primary = Color(0xFF914B22), primaryContainer = Color(0xFFFFDCC7),
            secondaryContainer = Color(0xFFF1DFD3), background = Color(0xFFFFF8F3), surface = Color(0xFFFFF8F3))
    }
    val font = FontFamily(Font(R.font.montserrat_regular), Font(R.font.montserrat_semibold, FontWeight.SemiBold))
    val base = Typography()
    val typography = Typography(
        displaySmall = base.displaySmall.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = font), bodyMedium = base.bodyMedium.copy(fontFamily = font),
        bodySmall = base.bodySmall.copy(fontFamily = font), labelLarge = base.labelLarge.copy(fontFamily = font, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = font), labelSmall = base.labelSmall.copy(fontFamily = font),
    )
    CompositionLocalProvider(LocalOverscrollFactory provides MiuixOverscrollFactory) {
        MaterialTheme(colorScheme = colors, typography = typography, motionScheme = MotionScheme.expressive(),
            shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
                large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)), content = content)
    }
}

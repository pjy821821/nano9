package kr.techbridge.battery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kr.techbridge.battery.data.settings.ThemeMode

/** 상태색은 다이내믹 컬러와 무관하게 의미가 고정된다. 다크에서는 밝은 변형을 쓴다. */
object StateColors {
    val chargingLight = Color(0xFF146C32)
    val chargingDark = Color(0xFF7BD68E)
    val warningLight = Color(0xFF8A4B00)
    val warningDark = Color(0xFFF2B46B)
    val lowLight = Color(0xFFB3261E)
    val lowDark = Color(0xFFF2B8B5)

    @Composable @ReadOnlyComposable
    fun charging(): Color = if (isDarkScheme()) chargingDark else chargingLight

    @Composable @ReadOnlyComposable
    fun warning(): Color = if (isDarkScheme()) warningDark else warningLight

    @Composable @ReadOnlyComposable
    fun low(): Color = if (isDarkScheme()) lowDark else lowLight

    @Composable @ReadOnlyComposable
    private fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

@Composable
fun BatteryTheme(
    mode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    var scheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    if (mode == ThemeMode.AMOLED) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

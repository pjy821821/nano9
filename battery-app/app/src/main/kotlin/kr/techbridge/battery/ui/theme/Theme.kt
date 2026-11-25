package kr.techbridge.battery.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

/**
 * AMOLED: 다크 색상표에서 표면 계열을 모두 검정 계열로 바꾼다. 배경·탭바·대화상자 바탕은 순검정(픽셀 꺼짐),
 * 카드와 칩은 바탕과 구분되는 아주 어두운 무채색. 반만 바꾸면 검정 바탕에 회색 덩어리가 얼룩져 보인다.
 */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF1E1E1E),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF111111),   // 카드
    surfaceContainer = Color.Black,            // 하단 탭바
    surfaceContainerHigh = Color(0xFF1A1A1A),  // 대화상자
    surfaceContainerHighest = Color(0xFF222222),
    surfaceVariant = Color(0xFF1A1A1A),        // 수치 칩, 아이콘 바탕
    outlineVariant = Color(0xFF2A2A2A),        // 구분선
)

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
    if (mode == ThemeMode.AMOLED) scheme = scheme.amoled()

    // 상태바·내비게이션바 아이콘 색은 enableEdgeToEdge 기본값이 시스템 다크 모드만 보므로,
    // 앱이 고른 테마에 맞춰 여기서 다시 정한다. 그러지 않으면 시스템이 라이트일 때 다크·AMOLED에서 아이콘이 안 보인다.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

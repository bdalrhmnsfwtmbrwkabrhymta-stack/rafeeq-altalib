package com.hawas.study.rafid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.hawas.study.rafid.models.RafidLanguageMode
import java.util.Locale

data class RafidPalette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val heroBlue: Color,
    val heroBlueDarkPill: Color,
    val heroBackLayer1: Color,
    val heroBackLayer2: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val borderSubtle: Color,
    val emeraldTint: Color,
    val emeraldBg: Color,
    val emeraldText: Color,
    val dangerTint: Color,
    val warningTint: Color
)

object RafidColors {
    val HeroBlue = Color(0xFF1565C0)
    val LightBackground = Color(0xFFF8FAFC)
    val DarkBackground = Color(0xFF0B1120)

    fun palette(isDark: Boolean): RafidPalette {
        return if (isDark) {
            RafidPalette(
                isDark = true,
                background = DarkBackground,
                surface = Color(0xFF131C31),
                surfaceVariant = Color(0xFF1E293B),
                heroBlue = HeroBlue,
                heroBlueDarkPill = Color(0xFF0D47A1),
                heroBackLayer1 = HeroBlue.copy(alpha = 0.25f),
                heroBackLayer2 = HeroBlue.copy(alpha = 0.55f),
                textPrimary = Color(0xFFF8FAFC),
                textSecondary = Color(0xFF94A3B8),
                borderSubtle = Color(0xFF26334D),
                emeraldTint = Color(0xFF34D399),
                emeraldBg = Color(0xFF064E3B),
                emeraldText = Color(0xFFD1FAE5),
                dangerTint = Color(0xFFF87171),
                warningTint = Color(0xFFFBBF24)
            )
        } else {
            RafidPalette(
                isDark = false,
                background = LightBackground,
                surface = Color.White,
                surfaceVariant = Color(0xFFF1F5F9),
                heroBlue = HeroBlue,
                heroBlueDarkPill = Color(0xFF0D47A1),
                heroBackLayer1 = HeroBlue.copy(alpha = 0.22f),
                heroBackLayer2 = HeroBlue.copy(alpha = 0.48f),
                textPrimary = Color(0xFF0F172A),
                textSecondary = Color(0xFF64748B),
                borderSubtle = Color(0xFFE2E8F0),
                emeraldTint = Color(0xFF059669),
                emeraldBg = Color(0xFFD6F5E3),
                emeraldText = Color(0xFF065F46),
                dangerTint = Color(0xFFDC2626),
                warningTint = Color(0xFFD97706)
            )
        }
    }
}

val LocalRafidPalette = staticCompositionLocalOf { RafidColors.palette(false) }
val LocalRafidIsEnglish = staticCompositionLocalOf { false }

@Composable
fun RafidTheme(
    darkOverride: Boolean?,
    languageMode: RafidLanguageMode,
    content: @Composable () -> Unit
) {
    val isDark = darkOverride ?: isSystemInDarkTheme()
    val palette = RafidColors.palette(isDark)
    val isEnglish = when (languageMode) {
        RafidLanguageMode.ENGLISH -> true
        RafidLanguageMode.ARABIC -> false
        RafidLanguageMode.SYSTEM -> Locale.getDefault().language.equals("en", ignoreCase = true)
    }
    val layoutDirection = if (isEnglish) LayoutDirection.Ltr else LayoutDirection.Rtl

    CompositionLocalProvider(
        LocalRafidPalette provides palette,
        LocalRafidIsEnglish provides isEnglish,
        LocalLayoutDirection provides layoutDirection
    ) {
        content()
    }
}

@Composable
fun rafidStr(ar: String, en: String): String {
    return if (LocalRafidIsEnglish.current) en else ar
}

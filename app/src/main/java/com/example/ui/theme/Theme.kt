package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.preferences.UserSettings

fun parseHexColor(hexString: String, fallback: Color): Color {
    return try {
        val cleanHex = hexString.removePrefix("#").trim()
        val colorInt = when (cleanHex.length) {
            6 -> (0xFF000000 or cleanHex.toLong(16)).toInt()
            8 -> cleanHex.toLong(16).toInt()
            else -> return fallback
        }
        Color(colorInt)
    } catch (e: Exception) {
        fallback
    }
}

data class AccentThemeChoice(
    val id: String,
    val labelAr: String,
    val color: Color
)

val CURATED_ACCENT_CHOICES = listOf(
    AccentThemeChoice("ROYAL_BLUE", "الأزرق الملكي", Color(0xFF2563EB)),
    AccentThemeChoice("VIBRANT_PURPLE", "البنفسجي الحيوي", Color(0xFF6C5CE7)),
    AccentThemeChoice("EMERALD", "الأخضر الزمردي", Color(0xFF10B981)),
    AccentThemeChoice("AMBER", "الكهرماني", Color(0xFFF59E0B)),
    AccentThemeChoice("CORAL", "المرجاني الدافئ", Color(0xFFFF6B6B))
)

fun buildPresetColorScheme(
    preset: String,
    isDarkFallback: Boolean,
    customBgHex: String,
    customAppHex: String,
    customTextHex: String,
    customCardHex: String
): ColorScheme {
    return when (preset.uppercase()) {
        "ROYAL_BLUE", "BLUE_WHITE", "LIGHT_BLUE", "DEFAULT", "LIGHT", "" -> if (isDarkFallback) darkColorScheme(
            primary = Color(0xFF60A5FA),
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(0xFF1E3A8A),
            onPrimaryContainer = Color(0xFFEFF6FF),
            secondary = Color(0xFF3B82F6),
            onSecondary = Color.White,
            background = Color(0xFF0B132B),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF1C2541),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF2E3D66),
            onSurfaceVariant = Color(0xFFDBEAFE),
            outline = Color(0xFF60A5FA).copy(alpha = 0.4f),
            outlineVariant = Color(0xFF2E3D66)
        ) else lightColorScheme(
            primary = Color(0xFF0F62FE),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E3A8A),
            secondary = Color(0xFF1D4ED8),
            onSecondary = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0A0F1A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0A0F1A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF334155),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
        "VIBRANT_PURPLE" -> if (isDarkFallback) darkColorScheme(
            primary = Color(0xFFA29BFE),
            onPrimary = Color(0xFF1E1B4B),
            primaryContainer = Color(0xFF4C1D95),
            onPrimaryContainer = Color(0xFFF3E8FF),
            secondary = Color(0xFF6C5CE7),
            onSecondary = Color.White,
            background = Color(0xFF0F172A),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF1E293B),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF334155),
            onSurfaceVariant = Color(0xFFE2E8F0),
            outline = Color(0xFF6C5CE7).copy(alpha = 0.4f),
            outlineVariant = Color(0xFF334155)
        ) else lightColorScheme(
            primary = Color(0xFF6C5CE7),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEDE9FE),
            onPrimaryContainer = Color(0xFF4C1D95),
            secondary = Color(0xFF7C3AED),
            onSecondary = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
        "AMBER" -> if (isDarkFallback) darkColorScheme(
            primary = Color(0xFFFBBF24),
            onPrimary = Color(0xFF451A03),
            primaryContainer = Color(0xFF78350F),
            onPrimaryContainer = Color(0xFFFEF3C7),
            secondary = Color(0xFFF59E0B),
            onSecondary = Color(0xFF451A03),
            background = Color(0xFF0F172A),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF1E293B),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF334155),
            onSurfaceVariant = Color(0xFFE2E8F0),
            outline = Color(0xFFF59E0B).copy(alpha = 0.4f),
            outlineVariant = Color(0xFF334155)
        ) else lightColorScheme(
            primary = Color(0xFFF59E0B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFEF3C7),
            onPrimaryContainer = Color(0xFF78350F),
            secondary = Color(0xFFD97706),
            onSecondary = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
        "CORAL" -> if (isDarkFallback) darkColorScheme(
            primary = Color(0xFFFF8787),
            onPrimary = Color(0xFF450A0A),
            primaryContainer = Color(0xFF7F1D1D),
            onPrimaryContainer = Color(0xFFFEE2E2),
            secondary = Color(0xFFFF6B6B),
            onSecondary = Color.White,
            background = Color(0xFF0F172A),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF1E293B),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF334155),
            onSurfaceVariant = Color(0xFFE2E8F0),
            outline = Color(0xFFFF6B6B).copy(alpha = 0.4f),
            outlineVariant = Color(0xFF334155)
        ) else lightColorScheme(
            primary = Color(0xFFFF6B6B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE4E6),
            onPrimaryContainer = Color(0xFF881337),
            secondary = Color(0xFFF43F5E),
            onSecondary = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
        "ORANGE_WHITE" -> if (isDarkFallback) darkColorScheme(
            primary = Color(0xFFFB923C),
            onPrimary = Color(0xFF431407),
            primaryContainer = Color(0xFF7C2D12),
            onPrimaryContainer = Color(0xFFFFEDD5),
            secondary = Color(0xFFFDBA74),
            onSecondary = Color(0xFF431407),
            background = Color(0xFF0F172A),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF1E293B),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF334155),
            onSurfaceVariant = Color(0xFFCBD5E1),
            outline = Color(0xFFFB923C).copy(alpha = 0.4f),
            outlineVariant = Color(0xFF334155)
        ) else lightColorScheme(
            primary = Color(0xFFEA580C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFEDD5),
            onPrimaryContainer = Color(0xFF7C2D12),
            secondary = Color(0xFFF97316),
            onSecondary = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
        "DARK", "ORANGE_DARK" -> darkColorScheme(
            primary = Color(0xFFFB923C),
            onPrimary = Color(0xFF431407),
            primaryContainer = Color(0xFF7C2D12),
            onPrimaryContainer = Color(0xFFFFEDD5),
            secondary = Color(0xFFFDBA74),
            onSecondary = Color(0xFF431407),
            background = Color(0xFF0F172A),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF1E293B),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF334155),
            onSurfaceVariant = Color(0xFFCBD5E1),
            outline = Color(0xFF475569),
            outlineVariant = Color(0xFF334155)
        )
        "MONOCHROME", "BLACK_WHITE" -> if (isDarkFallback) darkColorScheme(
            primary = Color(0xFFE2E8F0),
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(0xFF334155),
            onPrimaryContainer = Color(0xFFF8FAFC),
            secondary = Color(0xFF94A3B8),
            onSecondary = Color(0xFF0F172A),
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF121212),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF27272A),
            onSurfaceVariant = Color(0xFFE4E4E7),
            outline = Color(0xFF52525B),
            outlineVariant = Color(0xFF27272A)
        ) else lightColorScheme(
            primary = Color(0xFF0F172A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE2E8F0),
            onPrimaryContainer = Color(0xFF0F172A),
            secondary = Color(0xFF475569),
            onSecondary = Color.White,
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF000000),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF334155),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
        "SPACE" -> darkColorScheme(
            primary = Color(0xFF38BDF8),
            onPrimary = Color(0xFF0F172A),
            primaryContainer = Color(0xFF0369A1),
            onPrimaryContainer = Color(0xFFE0F2FE),
            secondary = Color(0xFFF59E0B),
            onSecondary = Color(0xFF0F172A),
            background = Color(0xFF050B14),
            onBackground = Color(0xFFF1F5F9),
            surface = Color(0xFF0F172A),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF1E293B),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF38BDF8).copy(alpha = 0.4f),
            outlineVariant = Color(0xFF1E293B)
        )
        "PURPLE" -> darkColorScheme(
            primary = Color(0xFF9333EA),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF581C87),
            onPrimaryContainer = Color(0xFFF3E8FF),
            secondary = Color(0xFFA855F7),
            onSecondary = Color.White,
            background = Color(0xFF3B0764),
            onBackground = Color(0xFFF3E8FF),
            surface = Color(0xFF5B21B6),
            onSurface = Color(0xFFF3E8FF),
            surfaceVariant = Color(0xFF6B21A8),
            onSurfaceVariant = Color(0xFFE9D5FF),
            outline = Color(0xFF7E22CE),
            outlineVariant = Color(0xFF6B21A8)
        )
        "BLUE" -> darkColorScheme(
            primary = Color(0xFF3B82F6),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF1E40AF),
            onPrimaryContainer = Color(0xFFEFF6FF),
            secondary = Color(0xFF60A5FA),
            onSecondary = Color.White,
            background = Color(0xFF1E3A8A),
            onBackground = Color(0xFFEFF6FF),
            surface = Color(0xFF1D4ED8),
            onSurface = Color(0xFFEFF6FF),
            surfaceVariant = Color(0xFF2563EB),
            onSurfaceVariant = Color(0xFFDBEAFE),
            outline = Color(0xFF3B82F6),
            outlineVariant = Color(0xFF2563EB)
        )
        "GREEN", "EMERALD" -> if (isDarkFallback) darkColorScheme(
            primary = Color(0xFF34D399),
            onPrimary = Color(0xFF064E3B),
            primaryContainer = Color(0xFF065F46),
            onPrimaryContainer = Color(0xFFD1FAE5),
            secondary = Color(0xFF6EE7B7),
            onSecondary = Color(0xFF064E3B),
            background = Color(0xFF022C22),
            onBackground = Color(0xFFECFDF5),
            surface = Color(0xFF064E3B),
            onSurface = Color(0xFFECFDF5),
            surfaceVariant = Color(0xFF047857),
            onSurfaceVariant = Color(0xFFA7F3D0),
            outline = Color(0xFF34D399),
            outlineVariant = Color(0xFF047857)
        ) else lightColorScheme(
            primary = Color(0xFF10B981),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD1FAE5),
            onPrimaryContainer = Color(0xFF065F46),
            secondary = Color(0xFF34D399),
            onSecondary = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFCBD5E1),
            outlineVariant = Color(0xFFE2E8F0)
        )
        "ORANGE" -> if (isDarkFallback) darkColorScheme(
            primary = Color(0xFFFB923C),
            onPrimary = Color(0xFF431407),
            primaryContainer = Color(0xFF7C2D12),
            onPrimaryContainer = Color(0xFFFFEDD5),
            secondary = Color(0xFFFDBA74),
            onSecondary = Color(0xFF431407),
            background = Color(0xFF0F172A),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF1E293B),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF334155),
            onSurfaceVariant = Color(0xFFCBD5E1),
            outline = Color(0xFFEA580C),
            outlineVariant = Color(0xFFD97706)
        ) else lightColorScheme(
            primary = Color(0xFFEA580C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFEDD5),
            onPrimaryContainer = Color(0xFF7C2D12),
            secondary = Color(0xFFFB923C),
            onSecondary = Color.White,
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFFFF7ED),
            onSurfaceVariant = Color(0xFF431407),
            outline = Color(0xFFEA580C).copy(alpha = 0.25f),
            outlineVariant = Color(0xFFFED7AA)
        )
        "TEAL" -> darkColorScheme(
            primary = Color(0xFF14B8A6),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF115E59),
            onPrimaryContainer = Color(0xFFCCFBF1),
            secondary = Color(0xFF2DD4BF),
            onSecondary = Color.White,
            background = Color(0xFF134E4A),
            onBackground = Color(0xFFCCFBF1),
            surface = Color(0xFF0F766E),
            onSurface = Color(0xFFCCFBF1),
            surfaceVariant = Color(0xFF0D9488),
            onSurfaceVariant = Color(0xFF99F6E4),
            outline = Color(0xFF14B8A6),
            outlineVariant = Color(0xFF0D9488)
        )
        "CUSTOM" -> {
            val bg = parseHexColor(customBgHex, Color(0xFFF8FAFC))
            val app = parseHexColor(customAppHex, Color(0xFFEA580C))
            val txt = parseHexColor(customTextHex, Color(0xFF0F172A))
            val card = parseHexColor(customCardHex, Color(0xFFFFFFFF))
            val isDarkBg = (bg.red * 0.299 + bg.green * 0.587 + bg.blue * 0.114) < 0.5
            if (isDarkBg) {
                darkColorScheme(
                    primary = app,
                    onPrimary = Color.White,
                    primaryContainer = app.copy(alpha = 0.3f),
                    onPrimaryContainer = Color.White,
                    secondary = app,
                    onSecondary = Color.White,
                    background = bg,
                    onBackground = txt,
                    surface = card,
                    onSurface = txt,
                    surfaceVariant = card.copy(alpha = 0.85f),
                    onSurfaceVariant = txt.copy(alpha = 0.8f),
                    outline = app.copy(alpha = 0.4f),
                    outlineVariant = card.copy(alpha = 0.5f)
                )
            } else {
                lightColorScheme(
                    primary = app,
                    onPrimary = Color.White,
                    primaryContainer = app.copy(alpha = 0.15f),
                    onPrimaryContainer = txt,
                    secondary = app,
                    onSecondary = Color.White,
                    background = bg,
                    onBackground = txt,
                    surface = card,
                    onSurface = txt,
                    surfaceVariant = card.copy(alpha = 0.9f),
                    onSurfaceVariant = txt.copy(alpha = 0.7f),
                    outline = app.copy(alpha = 0.3f),
                    outlineVariant = card.copy(alpha = 0.5f)
                )
            }
        }
        else -> if (isDarkFallback) darkColorScheme(
            primary = Color(0xFFFB923C),
            onPrimary = Color(0xFF431407),
            primaryContainer = Color(0xFF7C2D12),
            onPrimaryContainer = Color(0xFFFFEDD5),
            background = Color(0xFF0F172A),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF1E293B),
            onSurface = Color(0xFFF8FAFC)
        ) else lightColorScheme(
            primary = Color(0xFFEA580C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFEDD5),
            onPrimaryContainer = Color(0xFF7C2D12),
            background = Color(0xFFF8FAFC),
            onBackground = Color(0xFF0F172A),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF0F172A)
        )
    }
}

@Composable
fun RafiqTheme(
    userSettings: UserSettings? = null,
    darkTheme: Boolean = when (userSettings?.isDarkMode) {
        true -> true
        else -> false
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (userSettings != null) {
        buildPresetColorScheme(
            preset = userSettings.appThemePreset,
            isDarkFallback = darkTheme,
            customBgHex = userSettings.customBgColorHex,
            customAppHex = userSettings.customAppColorHex,
            customTextHex = userSettings.customTextColorHex,
            customCardHex = userSettings.customCardColorHex
        )
    } else when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> buildPresetColorScheme("DEFAULT", true, "", "", "", "")
        else -> buildPresetColorScheme("DEFAULT", false, "", "", "", "")
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    userSettings: UserSettings? = null,
    darkTheme: Boolean = when (userSettings?.isDarkMode) {
        true -> true
        else -> false
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    RafiqTheme(userSettings = userSettings, darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
}


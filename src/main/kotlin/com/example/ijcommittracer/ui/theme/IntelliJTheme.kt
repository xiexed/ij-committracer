package com.example.ijcommittracer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.util.SystemInfo

// Light theme colors similar to IntelliJ Light theme
private val LightColors = lightColorScheme(
    primary = Color(0xFF2675BF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9F1F8),
    onPrimaryContainer = Color(0xFF173459),
    secondary = Color(0xFF4D7CC3),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EDF7),
    onSecondaryContainer = Color(0xFF1F3658),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E9F7),
    onTertiaryContainer = Color(0xFF3B2D42),
    error = Color(0xFFC7392B),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF2F2F2),
    onBackground = Color(0xFF1E1E1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1E1E1E),
    outline = Color(0xFFC9C9C9),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF424242)
)

// Dark theme colors similar to Darcula theme
private val DarkColors = darkColorScheme(
    primary = Color(0xFF3592C4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2B3C50),
    onPrimaryContainer = Color(0xFFBEDDFF),
    secondary = Color(0xFF548CDD),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF293952),
    onSecondaryContainer = Color(0xFFD6E3FF),
    tertiary = Color(0xFFC792EA),
    onTertiary = Color(0xFF332941),
    tertiaryContainer = Color(0xFF4A3B56),
    onTertiaryContainer = Color(0xFFFFD7FF),
    error = Color(0xFFFF6B70),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF2B2B2B),
    onBackground = Color(0xFFE1E1E1),
    surface = Color(0xFF3C3F41),
    onSurface = Color(0xFFE1E1E1),
    outline = Color(0xFF6E6E6E),
    surfaceVariant = Color(0xFF323232),
    onSurfaceVariant = Color(0xFFBBBBBB)
)

// Typography based on IntelliJ's default font settings
private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp
    )
)

/**
 * Custom theme that mimics IntelliJ IDEA's look and feel using Material3 components
 */
@Composable
fun IntelliJTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Default to dark theme if IntelliJ is using Darcula
    val isDarkTheme = darkTheme || try {
        val laf = LafManager.getInstance()
        laf.currentLookAndFeel.toString().contains("Darcula")
    } catch (e: Exception) {
        false
    }
    
    val colorScheme = if (isDarkTheme) DarkColors else LightColors
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

// Common colors used throughout the IntelliJ UI
object IntelliJColors {
    val Green = Color(0xFF59A869)
    val Red = Color(0xFFDB5860)
    val Yellow = Color(0xFFBB9040)
    val Blue = Color(0xFF3592C4)
    val Purple = Color(0xFFC792EA)
    val Gray = Color(0xFF808080)
    
    // For UI elements
    val Border = Color(0xFFBBBBBB)
    val TextFieldBg = Color(0xFFFAFAFA)
    
    // For syntax highlighting
    val Keyword = Purple
    val String = Color(0xFF6AAB73)
    val Number = Color(0xFF6897BB)
    val Comment = Color(0xFF808080)
}
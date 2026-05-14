package com.medreminder.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Brand colors
val MedBlue = Color(0xFF4A90D9)
val MedBlueDark = Color(0xFF3A7BC8)
val MedGreen = Color(0xFF2ECC71)
val MedGreenDark = Color(0xFF27AE60)
val MedOrange = Color(0xFFF39C12)
val MedRed = Color(0xFFE74C3C)
val MedPurple = Color(0xFF9B59B6)
val MedTeal = Color(0xFF1ABC9C)

// Medication colors for pills
val MedicationColors = listOf(
    Color(0xFF4A90D9), Color(0xFF2ECC71), Color(0xFFF39C12),
    Color(0xFFE74C3C), Color(0xFF9B59B6), Color(0xFF1ABC9C),
    Color(0xFFE67E22), Color(0xFF3498DB), Color(0xFF1ABC9C),
    Color(0xFFE91E63), Color(0xFF8BC34A), Color(0xFF00BCD4)
)

private val LightColorScheme = lightColorScheme(
    primary = MedBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8F7),
    onPrimaryContainer = Color(0xFF1A3A5C),
    secondary = MedGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4F5E2),
    onSecondaryContainer = Color(0xFF0A3D1F),
    tertiary = MedPurple,
    onTertiary = Color.White,
    error = MedRed,
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E8),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF5A5A5A),
    outline = Color(0xFFCCCCCC),
    outlineVariant = Color(0xFFE0E0E0)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7BB4E8),
    onPrimary = Color(0xFF0D2137),
    primaryContainer = Color(0xFF1E4976),
    onPrimaryContainer = Color(0xFFD6E8F7),
    secondary = Color(0xFF6FD99A),
    onSecondary = Color(0xFF0A3D1F),
    secondaryContainer = Color(0xFF175A34),
    onSecondaryContainer = Color(0xFFD4F5E2),
    tertiary = Color(0xFFBB86D4),
    onTertiary = Color(0xFF2D1540),
    error = Color(0xFFEF8A80),
    onError = Color(0xFF3B0B0B),
    errorContainer = Color(0xFF5C1A1A),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF3A3A3A)
)

// Modern shape system
val MedReminderShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Senior-friendly large typography
val SeniorTypography = Typography(
    displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal, lineHeight = 26.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

@Composable
fun MedReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SeniorTypography,
        shapes = MedReminderShapes,
        content = content
    )
}

package com.example.icara.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val LightColorScheme = darkColorScheme(
    primary = Skobeloff,
    secondary = DeepSpaceSparkle,
    tertiary = DarkElectricBlue,
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary =Color(0xFFFFFFFF),

    primaryContainer = Color(0xFFb2ebff),
    secondaryContainer = Color(0xFFcee7f0),
    tertiaryContainer = Color(0xFFdfe0ff),
    onPrimaryContainer = Color(0XFF004e5e),
    onSecondaryContainer = Color(0XFF344a52),
    onTertiaryContainer = Color(0XFF414465),

    error = Color(0xFFba1a1a),
    onError = Color(0xFFffffff),
    errorContainer = Color(0xFFffdad6),
    onErrorContainer = Color(0xFF93000a),

    surface = Color(0xFFF5FAFD),
    onSurface = Color(0xFF171C1E),

    surfaceContainer = Color(0xFFeaeff1),
    surfaceContainerLow = Color(0xFFeff4f7),
    surfaceContainerLowest = Color(0xFFffffff),
    surfaceContainerHigh = Color(0xFFe4e9eb),
    surfaceContainerHighest = Color(0xFFdee3e6),
)

private val DarkColorScheme = lightColorScheme(
    primary = SkyBlue,
    secondary = PastelBlue,
    tertiary = Vodka,
    onPrimary = Color(0xFF003642),
    onSecondary = Color(0xFF1d343b),
    onTertiary = Color(0xFF2a2e4d),

    primaryContainer = Color(0xFF004e5e),
    secondaryContainer = Color(0xFF344a52),
    tertiaryContainer = Color(0xFF414465),
    onPrimaryContainer = Color(0XFFb2ebff),
    onSecondaryContainer = Color(0XFFcee7f0),
    onTertiaryContainer = Color(0XFFdfe0ff),

    error = Color(0xFFffb4ab),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000a),
    onErrorContainer = Color(0xFFffdad6),

    surface = Color(0xFF0f1416),
    onSurface = Color(0xFFdee3e6),

    surfaceContainer = Color(0xFF1b2022),
    surfaceContainerLow = Color(0xFF171c1e),
    surfaceContainerLowest = Color(0xFF090f11),
    surfaceContainerHigh = Color(0xFF252b2d),
    surfaceContainerHighest = Color(0xFF303638),
    // background = Color(0xFFFFFBFE),
    // onBackground = Color(0xFF1C1B1F),
)

@Composable
fun ICaraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
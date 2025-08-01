package com.example.mindvault.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Amethyst,
    secondary = LightAmethyst,
    tertiary = OffWhite,
    background = DeepCharcoal,
    surface = MetallicGray,
    onPrimary = OffWhite,
    onSecondary = OffWhite,
    onTertiary = DeepCharcoal,
    onBackground = OffWhite,
    onSurface = OffWhite
)

private val LightColorScheme = lightColorScheme(
    primary = Amethyst,
    secondary = LightAmethyst,
    tertiary = DeepCharcoal,
    background = OffWhite,
    surface = Color.White,
    onPrimary = OffWhite,
    onSecondary = DeepCharcoal,
    onTertiary = OffWhite,
    onBackground = DeepCharcoal,
    onSurface = DeepCharcoal
)

@Composable
fun MindVaultTheme(
    darkTheme: Boolean = true, // Force dark theme for a premium look
    // Dynamic color is disabled to ensure a consistent brand experience
    dynamicColor: Boolean = false,
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
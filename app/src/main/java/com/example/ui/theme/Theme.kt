package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = EspressoLight,
    onPrimary = DarkRoast,
    primaryContainer = EspressoBrown,
    onPrimaryContainer = LatteCream,
    secondary = AmberGlow,
    onSecondary = DarkRoast,
    tertiary = FoamLight,
    background = NightCoffee,
    onBackground = LatteCream,
    surface = NightSurface,
    onSurface = LatteCream
)

private val LightColorScheme = lightColorScheme(
    primary = EspressoBrown,
    onPrimary = Color.White,
    primaryContainer = SandSurface,
    onPrimaryContainer = DarkRoast,
    secondary = CaramelAmber,
    onSecondary = Color.White,
    tertiary = GoldenFoam,
    background = LatteCream,
    onBackground = DarkRoast,
    surface = Color.White,
    onSurface = DarkRoast
)

@Composable
fun CaféFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our handcrafted rich café theme
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

    // Force Right-To-Left (RTL) for Arabic POS interface
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

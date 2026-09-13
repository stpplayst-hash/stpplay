package com.stpplay.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun IPTVPlayerTheme(
    primaryColor: Color = DefaultPrimary,
    content: @Composable () -> Unit
) {
    val darkColorScheme = darkColorScheme(
        primary = primaryColor,
        secondary = StpSecondary,
        background = StpBackground,
        surface = StpSurface,
        onPrimary = Color.Black,
        onBackground = StpOnSurface,
        onSurface = StpOnSurface,
        error = StpRed
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography,
        content = content
    )
}

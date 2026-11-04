package com.aira.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AiraDarkColorScheme = darkColorScheme(
    primary = AiraPurplePrimary,
    secondary = AiraBlueAccent,
    tertiary = AiraCyanGlow,
    background = AiraBackgroundDark,
    surface = AiraSurfaceDark,
    error = AiraErrorCoral,
    onPrimary = AiraTextPrimary,
    onSecondary = AiraBackgroundDark,
    onBackground = AiraTextPrimary,
    onSurface = AiraTextPrimary,
    onError = AiraBackgroundDark
)

@Composable
fun AiraTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AiraDarkColorScheme,
        typography = AiraTypography,
        content = content
    )
}

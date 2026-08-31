package com.example.trainerledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = OnTealContainer,
    secondary = Color(0xFF4A6363),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E7),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF8A5100),
    error = Coral,
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceContainerHighest = Color(0xFFE0E3E2),
    outlineVariant = Color(0xFFBEC9C8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5D5),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F),
    onPrimaryContainer = TealContainer,
    secondary = Color(0xFFB0CBCB),
    onSecondary = Color(0xFF1B3535),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E2),
    surfaceContainerHighest = Color(0xFF2A2F2E),
    outlineVariant = Color(0xFF3F4948),
)

@Composable
fun TrainerLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}

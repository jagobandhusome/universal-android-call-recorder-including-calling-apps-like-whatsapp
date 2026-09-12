package com.androidcallrecorder.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val LightColors = lightColorScheme(
    primary = IvacBlue,
    onPrimary = Color.White,
    primaryContainer = IvacSky,
    onPrimaryContainer = IvacInk,
    secondary = IvacCyan,
    onSecondary = IvacInk,
    tertiary = IvacViolet,
    onTertiary = Color.White,
    background = IvacBgTop,
    onBackground = IvacInk,
    surface = Color.White,
    onSurface = IvacInk,
    surfaceVariant = IvacSkySoft,
    onSurfaceVariant = IvacMuted,
    error = IvacCoral,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0B0B0),
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color(0xFFB8B8B8),
    secondary = Color(0xFF8A8A8A),
    onSecondary = Color(0xFF111111),
    tertiary = Color(0xFF7A7A7A),
    onTertiary = Color(0xFF111111),
    background = Color(0xFF070707),
    onBackground = Color(0xFFB0B0B0),
    surface = Color(0xFF0E0E0E),
    onSurface = Color(0xFFB0B0B0),
    surfaceVariant = Color(0xFF151515),
    onSurfaceVariant = Color(0xFF808080),
    error = Color(0xFFB07070),
    onError = Color(0xFF1A0A0A),
    errorContainer = Color(0xFF2A1515),
    onErrorContainer = Color(0xFFB08080),
    outline = Color(0xFF3A3A3A),
)

@Composable
fun CallRecorderTheme(
    darkTheme: Boolean? = null,
    accent: Color = IvacBlue,
    content: @Composable () -> Unit,
) {
    val useDark = darkTheme ?: isSystemInDarkTheme()
    val base = if (useDark) DarkColors else LightColors
    val scheme = if (useDark) {
        base.copy(primary = Color(0xFFB0B0B0), secondary = accent.copy(alpha = 0.7f))
    } else {
        base.copy(primary = accent, secondary = IvacCyan, primaryContainer = accent.copy(alpha = 0.12f))
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content,
    )
}

fun accentColor(option: com.androidcallrecorder.app.data.AccentOption): Color = when (option) {
    com.androidcallrecorder.app.data.AccentOption.BLUE -> IvacBlue
    com.androidcallrecorder.app.data.AccentOption.CYAN -> IvacCyan
    com.androidcallrecorder.app.data.AccentOption.VIOLET -> IvacViolet
}

@Composable
fun AppScreenBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (dark) {
                        listOf(DarkBgTop, DarkBgMid, DarkBgBottom)
                    } else {
                        listOf(IvacBgTop, IvacBgBottom, IvacSky)
                    },
                ),
            ),
    ) {
        content()
    }
}

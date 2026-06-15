package com.eteditor.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EtLightScheme = lightColorScheme(
    primary = Control,
    onPrimary = Panel,
    primaryContainer = ControlPressed,
    onPrimaryContainer = Control,
    secondary = InkSoft,
    onSecondary = Panel,
    secondaryContainer = PanelMuted,
    onSecondaryContainer = Ink,
    tertiary = ControlHover,
    onTertiary = Panel,
    background = Paper,
    onBackground = Ink,
    surface = Panel,
    onSurface = Ink,
    surfaceDim = PanelMuted,
    surfaceBright = Panel,
    surfaceContainerLowest = Panel,
    surfaceContainerLow = Panel,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelMuted,
    surfaceContainerHighest = PanelMuted,
    surfaceVariant = PanelMuted,
    onSurfaceVariant = InkSoft,
    outline = DividerLineStrong,
    outlineVariant = DividerLine,
    error = ErrorInk,
    onError = Panel,
    errorContainer = ErrorPanel,
    onErrorContainer = ErrorInk,
    inverseSurface = Ink,
    inverseOnSurface = Panel,
    inversePrimary = ControlPressed,
    scrim = Color(0x99000000),
    surfaceTint = Control
)

private val EtShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp)
)

@Composable
fun EtEditorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EtLightScheme,
        typography = Typography,
        shapes = EtShapes,
        content = content
    )
}

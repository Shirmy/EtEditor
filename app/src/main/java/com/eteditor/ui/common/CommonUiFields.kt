package com.eteditor

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val ControlShape = RoundedCornerShape(8.dp)
val RowShape = RoundedCornerShape(8.dp)
val PreviewShape = RoundedCornerShape(8.dp)
val DialogBorder = BorderStroke(1.dp, Color.Black)

fun Modifier.dialogBorder(): Modifier = border(DialogBorder, PreviewShape)

@Composable
fun DialogTitleWithClose(
    title: String,
    onDismiss: () -> Unit,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = style,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(19.dp))
        }
    }
}

internal val WorkspaceHeaderHeight = 64.dp
val CompactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)

enum class AdaptiveDialogWidth(
    val compactFraction: Float,
    val wideFraction: Float,
    val maxWidth: Dp
) {
    Narrow(compactFraction = 0.84f, wideFraction = 0.36f, maxWidth = 360.dp),
    Menu(compactFraction = 0.64f, wideFraction = 0.24f, maxWidth = 280.dp),
    Compact(compactFraction = 0.90f, wideFraction = 0.46f, maxWidth = 420.dp),
    Medium(compactFraction = 0.92f, wideFraction = 0.56f, maxWidth = 520.dp),
    Wide(compactFraction = 0.94f, wideFraction = 0.70f, maxWidth = 720.dp),
    Preview(compactFraction = 0.94f, wideFraction = 0.82f, maxWidth = 860.dp)
}

@Composable
fun Modifier.adaptiveDialogWidth(width: AdaptiveDialogWidth): Modifier {
    val configuration = LocalConfiguration.current
    val useWideWidth = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 720
    val fraction = if (useWideWidth) width.wideFraction else width.compactFraction
    val screenTarget = (configuration.screenWidthDp * fraction).dp
    val targetWidth = if (screenTarget < width.maxWidth) screenTarget else width.maxWidth
    return this.width(targetWidth)
}

@Composable
fun Modifier.fixedDialogWidth(
    fraction: Float,
    maxWidth: Dp
): Modifier {
    val screenTarget = (LocalConfiguration.current.screenWidthDp * fraction).dp
    val targetWidth = if (screenTarget < maxWidth) screenTarget else maxWidth
    return this.width(targetWidth)
}

@Composable
internal fun PanelDropdownField(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = modifier) {
        val selectedBorder = if (expanded) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
        Surface(
            onClick = { expanded = true },
            shape = ControlShape,
            color = if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, selectedBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { fieldWidthPx = it.width }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
            modifier = if (fieldWidthPx > 0) {
                Modifier
                    .width(with(density) { fieldWidthPx.toDp() })
                    .dialogBorder()
            } else {
                Modifier.dialogBorder()
            }
        ) {
            options.forEachIndexed { index, (key, title) ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
                val selected = key == value || title == value
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selected) {
                                    Text(
                                        text = "当前选择",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (selected) {
                                Icon(
                                    Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun NativeFormSection(
    title: String?,
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = PreviewShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!title.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 7.dp, end = 8.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    action()
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
internal fun appSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.surface,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.surface,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    disabledCheckedThumbColor = MaterialTheme.colorScheme.surface,
    disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
    disabledUncheckedThumbColor = MaterialTheme.colorScheme.surface,
    disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    disabledUncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
)

@Composable
internal fun ScreenHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

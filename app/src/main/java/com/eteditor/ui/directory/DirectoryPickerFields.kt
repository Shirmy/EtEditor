package com.eteditor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal const val MOVE_TARGET_BOOK_START = -1
internal const val MOVE_TARGET_BOOK_END = Int.MAX_VALUE

data class DirectoryPickerOption(
    val key: String,
    val label: String,
    val selected: Boolean = false,
    val current: Boolean = false,
    val enabled: Boolean = true,
    val tocLevel: Int = 0,
    val isVolume: Boolean = false,
    val isSpecial: Boolean = false
)

@Composable
internal fun DirectoryOptionButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            pressed -> MaterialTheme.colorScheme.primaryContainer
            hovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 120),
        label = "directoryOptionColor"
    )
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        pressed -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        shape = RowShape,
        color = containerColor,
        border = BorderStroke(
            1.dp,
            when {
                selected -> MaterialTheme.colorScheme.primary
                pressed -> MaterialTheme.colorScheme.primary.copy(alpha = 0.56f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        interactionSource = interactionSource,
        modifier = modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DirectoryIconOptionButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            pressed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
            hovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = 120),
        label = "directoryIconOptionColor"
    )
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        pressed -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconBorder = when {
        selected || pressed -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f))
        else -> null
    }

    Surface(
        onClick = onClick,
        shape = RowShape,
        color = containerColor,
        border = iconBorder,
        interactionSource = interactionSource,
        modifier = modifier.height(28.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
internal fun DirectoryCompactActionButton(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    outlined: Boolean = false,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary
            pressed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)
            hovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        animationSpec = tween(durationMillis = 120),
        label = "directoryCompactActionColor"
    )
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        pressed -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val actionBorder = when {
        selected || pressed -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (outlined) 0.62f else 0.34f))
        outlined -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
        else -> null
    }

    Surface(
        onClick = onClick,
        shape = RowShape,
        color = containerColor,
        border = actionBorder,
        interactionSource = interactionSource,
        modifier = modifier.height(28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun DirectoryPickerButton(
    value: String,
    dialogTitle: String,
    options: List<Pair<String, String>>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentLabel: String = "",
    allowReverse: Boolean = true,
    height: Dp = 42.dp
) {
    var showDialog by remember(dialogTitle, options) { mutableStateOf(false) }
    var reverseOrder by remember(showDialog) { mutableStateOf(false) }
    val displayedOptions = remember(options, reverseOrder) {
        if (reverseOrder) options.asReversed() else options
    }
    Surface(
        onClick = { showDialog = true },
        shape = ControlShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(height)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    if (showDialog) {
        Dialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = PreviewShape,
                color = MaterialTheme.colorScheme.surface,
                border = DialogBorder,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .adaptiveDialogWidth(AdaptiveDialogWidth.Narrow)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = dialogTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentLabel.isNotBlank()) {
                                Text(
                                    text = currentLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(
                            onClick = { showDialog = false },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (allowReverse) {
                        DirectoryOptionButton(
                            text = "逆序显示",
                            icon = Icons.Outlined.SwapVert,
                            selected = reverseOrder,
                            onClick = { reverseOrder = !reverseOrder },
                            modifier = Modifier.widthIn(min = 96.dp)
                        )
                    }
                    DirectoryPickerList(
                        options = displayedOptions.map { (key, label) ->
                            DirectoryPickerOption(
                                key = key,
                                label = label,
                                selected = key == selectedKey
                            )
                        },
                        onSelect = { key ->
                            onSelect(key)
                            showDialog = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DirectoryPickerList(
    options: List<DirectoryPickerOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollbarDirectDrag: Boolean = false,
    scrollbarThumbFollowsDrag: Boolean = false
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(options, key = { it.key }) { option ->
                Surface(
                    onClick = { if (option.enabled) onSelect(option.key) },
                    enabled = option.enabled,
                    shape = RowShape,
                    color = if (option.selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                    } else if (option.isSpecial) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = if (option.isSpecial) 8.dp else 8.dp + (option.tocLevel.coerceIn(0, 4) * 14).dp,
                            top = 9.dp,
                            end = 8.dp,
                            bottom = 9.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (option.tocLevel > 0) {
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                        }
                        if (option.isSpecial) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    text = "边界",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (option.isVolume || option.isSpecial) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                option.selected -> MaterialTheme.colorScheme.primary
                                !option.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                option.isSpecial -> MaterialTheme.colorScheme.primary
                                option.isVolume -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (option.current) {
                            Text(
                                text = "当前",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                            )
                        }
                        if (option.selected) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f))
            }
        }
        ContentScrollbar(
            state = listState,
            itemCount = options.size,
            directDrag = scrollbarDirectDrag,
            thumbFollowsDrag = scrollbarThumbFollowsDrag,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 2.dp)
        )
    }
}

@Composable
internal fun DirectoryMoveTargetMenu(
    currentIndex: Int,
    selectedTargetIndex: Int?,
    options: List<DirectoryPickerOption>,
    onSelect: (Int) -> Unit,
    label: String = "移动至",
    title: String = label,
    modifier: Modifier = Modifier
) {
    var showDialog by remember(currentIndex) { mutableStateOf(false) }
    var reverseOrder by remember(currentIndex) { mutableStateOf(false) }
    val selected = selectedTargetIndex != null
    val currentLabel = remember(options, currentIndex) {
        options.firstOrNull { it.key.toIntOrNull() == currentIndex }?.label.orEmpty()
    }
    val displayedOptions = remember(options, reverseOrder) {
        val specialOptions = options.filter { it.isSpecial }
        val chapterOptions = options.filterNot { it.isSpecial }
        specialOptions + if (reverseOrder) chapterOptions.asReversed() else chapterOptions
    }
    val pickerOptions = remember(displayedOptions, selectedTargetIndex, currentIndex) {
        displayedOptions.map { option ->
            val targetIndex = option.key.toIntOrNull()
            option.copy(
                selected = targetIndex == selectedTargetIndex,
                current = targetIndex == currentIndex,
                enabled = targetIndex != currentIndex
            )
        }
    }
    Box {
        Surface(
            onClick = { showDialog = true },
            shape = ControlShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = modifier.height(30.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 7.dp)
                    .height(30.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (showDialog) {
            Dialog(
                onDismissRequest = { showDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    shape = PreviewShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = DialogBorder,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .adaptiveDialogWidth(AdaptiveDialogWidth.Compact)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (currentLabel.isNotBlank()) {
                                    Text(
                                        text = currentLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showDialog = false },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DirectoryOptionButton(
                            text = "逆序显示",
                            icon = Icons.Outlined.SwapVert,
                            selected = reverseOrder,
                            onClick = { reverseOrder = !reverseOrder },
                            modifier = Modifier.widthIn(min = 96.dp)
                        )
                        DirectoryPickerList(
                            options = pickerOptions,
                            onSelect = { key ->
                                showDialog = false
                                onSelect(key.toInt())
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            scrollbarDirectDrag = true,
                            scrollbarThumbFollowsDrag = true
                        )
                    }
                }
            }
        }
    }
}

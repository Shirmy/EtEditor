package com.eteditor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind

@Composable
fun AppRail(
    toolsEnabled: Boolean,
    documentKind: DocumentKind = DocumentKind.None,
    documentControlsEnabled: Boolean = true,
    expanded: Boolean,
    fileOpen: Boolean,
    directoryOpen: Boolean,
    featuresOpen: Boolean,
    settingsOpen: Boolean,
    showDirectory: Boolean = false,
    showExit: Boolean = true,
    featureIcon: ImageVector = Icons.Outlined.FindReplace,
    featureLabel: String = "功能",
    hideDirectoryFileName: Boolean = false,
    onHideDirectoryFileNameChange: (Boolean) -> Unit = {},
    epubHideSection0001FromNcx: Boolean = false,
    onEpubHideSection0001FromNcxChange: (Boolean) -> Unit = {},
    epubWordCountEnabled: Boolean = true,
    onEpubWordCountClick: () -> Unit = {},
    onTxtLengthHintSettingsClick: () -> Unit = {},
    txtChapterNumberStartAtOneOnSave: Boolean = true,
    onTxtChapterNumberStartAtOneOnSaveChange: (Boolean) -> Unit = {},
    txtAutoNumberOnSave: Boolean = true,
    onTxtAutoNumberOnSaveChange: (Boolean) -> Unit = {},
    onFileClick: () -> Unit,
    onDirectoryClick: () -> Unit,
    onFeatureClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onExitClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(if (expanded) 88.dp else 40.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RailDocumentControls(
            documentKind = documentKind,
            enabled = documentControlsEnabled,
            hideDirectoryFileName = hideDirectoryFileName,
            onHideDirectoryFileNameChange = onHideDirectoryFileNameChange,
            epubHideSection0001FromNcx = epubHideSection0001FromNcx,
            onEpubHideSection0001FromNcxChange = onEpubHideSection0001FromNcxChange,
            epubWordCountEnabled = epubWordCountEnabled,
            onEpubWordCountClick = onEpubWordCountClick,
            onTxtLengthHintSettingsClick = onTxtLengthHintSettingsClick,
            txtChapterNumberStartAtOneOnSave = txtChapterNumberStartAtOneOnSave,
            onTxtChapterNumberStartAtOneOnSaveChange = onTxtChapterNumberStartAtOneOnSaveChange,
            txtAutoNumberOnSave = txtAutoNumberOnSave,
            onTxtAutoNumberOnSaveChange = onTxtAutoNumberOnSaveChange
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            RailIcon(
                icon = Icons.Outlined.FolderOpen,
                label = "文件",
                expanded = expanded,
                selected = fileOpen,
                selectedUsesBlack = true,
                onClick = onFileClick
            )
            if (showDirectory) {
                RailIcon(
                    icon = Icons.AutoMirrored.Outlined.List,
                    label = "目录",
                    expanded = expanded,
                    selected = directoryOpen,
                    selectedUsesBlack = true,
                    enabled = toolsEnabled,
                    onClick = onDirectoryClick
                )
            }
            RailIcon(
                icon = featureIcon,
                label = featureLabel,
                expanded = expanded,
                selected = featuresOpen,
                selectedUsesBlack = true,
                enabled = toolsEnabled,
                onClick = onFeatureClick
            )
            RailIcon(
                icon = Icons.Outlined.Settings,
                label = "设置",
                expanded = expanded,
                selected = settingsOpen,
                onClick = onSettingsClick
            )
            if (showExit) {
                RailIcon(
                    icon = Icons.AutoMirrored.Outlined.ExitToApp,
                    label = "退出",
                    expanded = expanded,
                    selected = false,
                    onClick = onExitClick
                )
            }
        }
    }
}

@Composable
private fun RailDocumentControls(
    documentKind: DocumentKind,
    enabled: Boolean,
    hideDirectoryFileName: Boolean,
    onHideDirectoryFileNameChange: (Boolean) -> Unit,
    epubHideSection0001FromNcx: Boolean,
    onEpubHideSection0001FromNcxChange: (Boolean) -> Unit,
    epubWordCountEnabled: Boolean,
    onEpubWordCountClick: () -> Unit,
    onTxtLengthHintSettingsClick: () -> Unit,
    txtChapterNumberStartAtOneOnSave: Boolean,
    onTxtChapterNumberStartAtOneOnSaveChange: (Boolean) -> Unit,
    txtAutoNumberOnSave: Boolean,
    onTxtAutoNumberOnSaveChange: (Boolean) -> Unit
) {
    if (documentKind == DocumentKind.None) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (documentKind) {
            DocumentKind.Epub -> {
                RailGlyphToggle(
                    label = "Ch",
                    selected = hideDirectoryFileName,
                    enabled = enabled,
                    diagonalSlash = !hideDirectoryFileName,
                    contentDescription = if (hideDirectoryFileName) "目录文件名隐藏已开启" else "目录文件名隐藏已关闭",
                    onClick = { onHideDirectoryFileNameChange(!hideDirectoryFileName) }
                )
                RailGlyphToggle(
                    label = "S1",
                    selected = epubHideSection0001FromNcx,
                    enabled = enabled,
                    diagonalSlash = !epubHideSection0001FromNcx,
                    contentDescription = if (epubHideSection0001FromNcx) {
                        "隐藏 Section0001 已开启"
                    } else {
                        "隐藏 Section0001 已关闭"
                    },
                    onClick = { onEpubHideSection0001FromNcxChange(!epubHideSection0001FromNcx) }
                )
                RailIconButton(
                    icon = Icons.Outlined.Calculate,
                    label = "字数统计",
                    enabled = enabled && epubWordCountEnabled,
                    onClick = onEpubWordCountClick
                )
            }
            DocumentKind.Txt -> {
                RailIconButton(
                    icon = Icons.Outlined.Straighten,
                    label = "长短章提示",
                    enabled = enabled,
                    onClick = onTxtLengthHintSettingsClick
                )
                RailGlyphToggle(
                    label = if (txtChapterNumberStartAtOneOnSave) "1" else "0",
                    selected = txtChapterNumberStartAtOneOnSave,
                    enabled = enabled,
                    contentDescription = if (txtChapterNumberStartAtOneOnSave) {
                        "章节编号从 1 开始导出已开启"
                    } else {
                        "章节编号从 0 开始导出已开启"
                    },
                    onClick = { onTxtChapterNumberStartAtOneOnSaveChange(!txtChapterNumberStartAtOneOnSave) }
                )
                RailGlyphToggle(
                    label = "序",
                    selected = txtAutoNumberOnSave,
                    enabled = enabled,
                    diagonalSlash = !txtAutoNumberOnSave,
                    contentDescription = if (txtAutoNumberOnSave) {
                        "章节编号自动排序已开启"
                    } else {
                        "章节编号自动排序已关闭"
                    },
                    onClick = { onTxtAutoNumberOnSaveChange(!txtAutoNumberOnSave) }
                )
            }
            DocumentKind.None -> Unit
        }
    }
}

@Composable
private fun RailGlyphToggle(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String,
    diagonalSlash: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = when {
        selected -> Color.Black
        enabled -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }
    val contentColor = when {
        selected -> Color.White
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, if (selected) Color.Black else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(32.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
            if (diagonalSlash) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    drawLine(
                        color = contentColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
private fun RailIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(32.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun RailIcon(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    selectedUsesBlack: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor by animateColorAsState(
        targetValue = when {
            selected && selectedUsesBlack -> Color.Black
            selected -> MaterialTheme.colorScheme.primaryContainer
            pressed -> MaterialTheme.colorScheme.surfaceVariant
            hovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            enabled -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        },
        animationSpec = tween(durationMillis = 140),
        label = "railIconColor"
    )
    val contentColor = when {
        selected && selectedUsesBlack -> Color.White
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    val railIconBorder = when {
        selected && selectedUsesBlack -> BorderStroke(1.dp, Color.Black.copy(alpha = 0.9f))
        selected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.26f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ControlShape,
        color = containerColor,
        contentColor = contentColor,
        border = railIconBorder,
        interactionSource = interactionSource,
        modifier = if (expanded) {
            Modifier
                .padding(horizontal = 4.dp)
                .fillMaxWidth()
                .height(32.dp)
        } else {
            Modifier.size(32.dp)
        }
    ) {
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

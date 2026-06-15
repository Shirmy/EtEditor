package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind

private val FeatureButtonShape = RoundedCornerShape(18.dp)
private val FormFieldHeight = 56.dp

internal enum class TxtFeaturePanel {
    Search
}

@Composable
internal fun DocumentToolsPanel(
    controller: EditorController,
    onSave: () -> Unit,
    onOpenFile: (() -> Unit)? = null,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker,
    documentSessionKey: Int = 0,
    resetKey: Int = 0,
    txtPanel: TxtFeaturePanel = TxtFeaturePanel.Search,
    onTextReplacePreviewModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    when (controller.kind) {
        DocumentKind.Epub -> EpubToolsPanel(
            controller = controller,
            onSave = onSave,
            onOpenFile = onOpenFile,
            onPickTextReplaceRuleFile = onPickTextReplaceRuleFile,
            documentSessionKey = documentSessionKey,
            resetKey = resetKey,
            onTextReplacePreviewModeChange = onTextReplacePreviewModeChange,
            modifier = modifier,
            compact = compact
        )
        DocumentKind.Txt -> TxtToolsPanel(
            controller = controller,
            onSave = onSave,
            onPickTextReplaceRuleFile = onPickTextReplaceRuleFile,
            documentSessionKey = documentSessionKey,
            resetKey = resetKey,
            panel = txtPanel,
            onTextReplacePreviewModeChange = onTextReplacePreviewModeChange,
            modifier = modifier,
            compact = compact
        )
        DocumentKind.None -> EmptyToolsPanel(
            controller = controller,
            onOpenFile = onOpenFile,
            modifier = modifier,
            compact = compact
        )
    }
}

@Composable
private fun EmptyToolsPanel(
    controller: EditorController,
    onOpenFile: (() -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (compact) 0.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WorkspaceHeaderHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "功能",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))
        if (onOpenFile != null) {
            NativeActionRow(
                icon = Icons.Outlined.FolderOpen,
                title = "打开文件",
                subtitle = "打开 EPUB / TXT 后再使用功能",
                actionLabel = "选择",
                enabled = !controller.busy,
                onClick = onOpenFile
            )
        } else {
            Text(
                text = "打开 EPUB / TXT 后再使用功能。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EpubToolsPanel(
    controller: EditorController,
    onSave: () -> Unit,
    onOpenFile: (() -> Unit)? = null,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker,
    documentSessionKey: Int = 0,
    resetKey: Int = 0,
    onTextReplacePreviewModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (controller.kind != DocumentKind.Epub) {
        EmptyToolsPanel(controller, onOpenFile, modifier, compact)
        return
    }
    var selectedToolId by remember(documentSessionKey, resetKey) { mutableStateOf<String?>(null) }
    val selectedTool = selectedToolId?.let { id ->
        controller.builtInToolsForCurrentDocument().firstOrNull { it.id == id }
    }

    fun resetAndOpenTool(toolId: String) {
        controller.resetBuiltInToolState(toolId)
        selectedToolId = toolId
    }

    LaunchedEffect(documentSessionKey, resetKey) {
        controller.resetBuiltInToolState()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (compact) 0.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        if (selectedTool == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WorkspaceHeaderHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "功能",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            EpubToolGrid(
                controller = controller,
                modifier = Modifier.weight(1f),
                onPickTool = { toolId ->
                    resetAndOpenTool(toolId)
                }
            )
        } else {
            ToolWorkbench(
                controller = controller,
                tool = selectedTool,
                documentSessionKey = documentSessionKey,
                resetKey = resetKey,
                modifier = Modifier
                    .fillMaxSize(),
                onBack = {
                    controller.resetBuiltInToolState(selectedTool.id)
                    selectedToolId = null
                },
                onSave = onSave,
                onTextReplacePreviewModeChange = onTextReplacePreviewModeChange,
                onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
            )
        }
    }
}

@Composable
private fun TxtToolsPanel(
    controller: EditorController,
    onSave: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker,
    documentSessionKey: Int = 0,
    resetKey: Int = 0,
    panel: TxtFeaturePanel = TxtFeaturePanel.Search,
    onTextReplacePreviewModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val tool = controller.builtInToolsForCurrentDocument().firstOrNull { it.id == "text_replace" }
    if (tool == null) {
        EmptyToolsPanel(controller, null, modifier, compact)
        return
    }
    ToolWorkbench(
        controller = controller,
        tool = tool,
        documentSessionKey = documentSessionKey,
        resetKey = resetKey,
        modifier = modifier
            .fillMaxSize()
            .padding(if (compact) 0.dp else 16.dp),
        onBack = null,
        onSave = onSave,
        onTextReplacePreviewModeChange = onTextReplacePreviewModeChange,
        onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
    )
}

@Composable
private fun EpubToolGrid(
    controller: EditorController,
    modifier: Modifier = Modifier,
    onPickTool: (String) -> Unit
) {
    val toolById = controller.builtInToolsForCurrentDocument().associateBy { it.id }
    val topTools = listOf(
        "text_replace",
        "file_rename",
        "title_format",
        "chapter_title_rename"
    )
        .mapNotNull(toolById::get)
    val bottomTools = listOf("fetch_info", "insert_chapter", "generate_cover")
        .mapNotNull(toolById::get)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(count = topTools.size, key = { index -> topTools[index].id }) { index ->
            val tool = topTools[index]
            BuiltInToolButton(
                tool = tool,
                onClick = { onPickTool(tool.id) }
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        items(count = bottomTools.size, key = { index -> bottomTools[index].id }) { index ->
            val tool = bottomTools[index]
            BuiltInToolButton(
                tool = tool,
                onClick = { onPickTool(tool.id) }
            )
        }
    }
}

@Composable
private fun BuiltInToolButton(
    tool: ToolDefinition,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val containerColor = when {
        pressed -> MaterialTheme.colorScheme.surfaceVariant
        hovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onClick,
        shape = FeatureButtonShape,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .height(FormFieldHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = builtInToolIcon(tool.id),
                contentDescription = null,
                tint = if (tool.implemented) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = tool.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (tool.implemented) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

internal fun builtInToolIcon(toolId: String): ImageVector {
    return when (toolId) {
        "file_rename" -> Icons.Outlined.FolderOpen
        "text_replace" -> Icons.Outlined.FindReplace
        "insert_chapter" -> Icons.Outlined.Add
        "chapter_title_rename" -> Icons.Outlined.Edit
        "title_format" -> Icons.Outlined.Build
        "fetch_info" -> Icons.Outlined.Download
        "generate_cover" -> Icons.Outlined.ContentPaste
        else -> Icons.Outlined.Build
    }
}


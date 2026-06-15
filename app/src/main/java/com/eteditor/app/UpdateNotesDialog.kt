package com.eteditor

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val UPDATE_NOTE_PREFS = "et_editor_update_notes"
private const val KEY_LAST_SHOWN_UPDATE_VERSION = "last_shown_update_version"

private fun loadUpdateNoteLines(context: Context): List<String> {
    val markdown = runCatching {
        context.assets.open("changelog/CHANGELOG.md").bufferedReader().use { it.readText() }
    }.getOrElse {
        """
        # 更新记录

        ## ${BuildConfig.VERSION_NAME}

        - 完成 EPUB / TXT 文件打开、预览与保存流程
        - 加入目录整理、标题格式化、文本替换、信息抓取和封面生成工具
        - 补充应用图标、版本信息和更新记录展示
        """.trimIndent()
    }
    return markdown
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "# 更新记录" }
        .toList()
}

private fun latestUpdateNoteLines(lines: List<String>): List<String> {
    val firstVersionIndex = lines.indexOfFirst { it.startsWith("##") }
    if (firstVersionIndex < 0) return lines
    val nextVersionOffset = lines
        .drop(firstVersionIndex + 1)
        .indexOfFirst { it.startsWith("##") }
    val endIndex = if (nextVersionOffset < 0) {
        lines.size
    } else {
        firstVersionIndex + 1 + nextVersionOffset
    }
    return lines.subList(firstVersionIndex, endIndex)
}

private fun markdownListItemText(line: String): String? {
    val text = markdownInlineText(line.removePrefix("#").trim())
    return when {
        text.startsWith("- ") -> text.removePrefix("- ").trimStart()
        text.startsWith("* ") -> text.removePrefix("* ").trimStart()
        text.startsWith("+ ") -> text.removePrefix("+ ").trimStart()
        else -> null
    }
}

private fun markdownInlineText(text: String): String {
    return text.replace("`", "")
}

@Composable
internal fun AutoUpdateNotesDialogHost() {
    val context = LocalContext.current
    var showUpdateNotes by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        val prefs = context.getSharedPreferences(UPDATE_NOTE_PREFS, Context.MODE_PRIVATE)
        val currentVersion = BuildConfig.VERSION_NAME
        if (prefs.getString(KEY_LAST_SHOWN_UPDATE_VERSION, null) != currentVersion) {
            showUpdateNotes = true
        }
    }
    if (showUpdateNotes) {
        UpdateNotesDialog(
            latestOnly = true,
            onDismiss = {
                context.getSharedPreferences(UPDATE_NOTE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_SHOWN_UPDATE_VERSION, BuildConfig.VERSION_NAME)
                    .apply()
                showUpdateNotes = false
            }
        )
    }
}

@Composable
fun UpdateNotesDialog(
    onDismiss: () -> Unit,
    latestOnly: Boolean = false
) {
    val context = LocalContext.current
    val updateNoteLines = remember(context, latestOnly) {
        val lines = loadUpdateNoteLines(context)
        if (latestOnly) latestUpdateNoteLines(lines) else lines
    }
    val noteScrollState = rememberScrollState()
    val noteItemSpacing = if (latestOnly) 4.dp else 7.dp
    val versionBlockSpacing = if (latestOnly) 0.dp else 14.dp
    val versionBottomPadding = if (latestOnly) 0.dp else 3.dp
    val itemVerticalPadding = if (latestOnly) 0.dp else 2.dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            modifier = Modifier
                .adaptiveDialogWidth(AdaptiveDialogWidth.Medium)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    UpdateNotesInlineButton(
                        onClick = null,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(28.dp)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(17.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(noteScrollState)
                            .padding(end = 16.dp, top = 2.dp, bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(noteItemSpacing)
                    ) {
                        updateNoteLines.forEachIndexed { index, line ->
                            if (line.startsWith("##")) {
                                val version = line.removePrefix("##").trim()
                                if (!latestOnly && updateNoteLines.take(index).any { it.startsWith("##") }) {
                                    Spacer(Modifier.height(versionBlockSpacing))
                                }
                                Text(
                                    text = if (version.startsWith("v", ignoreCase = true)) version else "v$version",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = versionBottomPadding)
                                )
                            } else {
                                val listItemText = markdownListItemText(line)
                                if (listItemText != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = itemVerticalPadding),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "·",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(14.dp)
                                        )
                                        Text(
                                            text = listItemText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = markdownInlineText(line.removePrefix("#").trim()),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    ContentScrollbar(
                        state = noteScrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 2.dp),
                        prominent = true
                    )
                }
            }
        }
    }
}

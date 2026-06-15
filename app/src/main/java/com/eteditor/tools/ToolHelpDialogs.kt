package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun FileRenameTemplateHelpDialog(onDismiss: () -> Unit) {
    ToolHelpDialogFrame(
        onDismiss = onDismiss,
        modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Medium),
        title = "命名格式"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "全部：命名格式是完整文件名，多章节需要 {z4}",
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Text(
                text = "{z4} 是 4 位编号，继续已有 Chapter 编号",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "{z4:2} 是 4 位编号，从 2 开始，0002、0003",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun TitleFormatHelpDialog(onDismiss: () -> Unit) {
    ToolHelpDialogFrame(
        onDismiss = onDismiss,
        modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Medium),
        title = "标题格式"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "自动判断：取“第xx章”后面的标题后缀，去掉前后空格和开头分隔符 |、-、—。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "标题只有“第x章”的空后缀占比达到 80%：无横线。",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "后缀超过短标题阈值（默认 6 字）的章节占比达到 60%：左竖线；否则用双横线。",
                style = MaterialTheme.typography.bodyMedium
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Text(
                text = "处理范围无论选全部 HTML 还是自选 HTML，都会排除 cover、Section0001、Section0002。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun InsertChapterHelpDialog(onDismiss: () -> Unit) {
    ToolHelpDialogFrame(
        onDismiss = onDismiss,
        modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Narrow),
        title = "插入章节说明"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = "上传来源：预览开启时弹出章节列表，默认全选，可取消后确认插入",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "废文执行：先匹配搜索结果，多结果先选书，再选章节并执行",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "插入后：普通章节自动连续编号，标题格式参考原书最后一章。",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun CoverTitleHelpDialog(onDismiss: () -> Unit) {
    ToolHelpDialogFrame(
        onDismiss = onDismiss,
        modifier = Modifier.adaptiveDialogWidth(AdaptiveDialogWidth.Compact),
        title = "封面标题"
    ) {
        Text(
            text = "封面标题为空：自动用当前书名",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ToolHelpDialogFrame(
    onDismiss: () -> Unit,
    modifier: Modifier,
    title: String,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier,
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = DialogBorder,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                DialogTitleWithClose(
                    title = title,
                    onDismiss = onDismiss,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

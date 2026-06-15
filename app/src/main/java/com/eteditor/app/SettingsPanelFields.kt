package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind

private val LeftPanelModeOptions = listOf(
    LEFT_PANEL_NONE to "不固定",
    LEFT_PANEL_DIRECTORY to "目录"
)

private val TxtLeftPanelModeOptions = listOf(
    LEFT_PANEL_NONE to "不固定",
    LEFT_PANEL_DIRECTORY to "目录"
)

private val RightPanelModeOptions = listOf(
    RIGHT_PANEL_NONE to "不固定",
    RIGHT_PANEL_FEATURES to "功能",
    RIGHT_PANEL_AUTOMATION to "执行"
)

private val TxtRightPanelModeOptions = listOf(
    RIGHT_PANEL_NONE to "不固定",
    RIGHT_PANEL_FEATURES to "搜索"
)

private fun leftPanelModeLabel(mode: String): String {
    return LeftPanelModeOptions.firstOrNull { it.first == mode }?.second ?: "不固定"
}

private fun rightPanelModeLabel(mode: String): String {
    return RightPanelModeOptions.firstOrNull { it.first == mode }?.second ?: "功能"
}

private fun txtRightPanelModeLabel(mode: String): String {
    return TxtRightPanelModeOptions.firstOrNull { it.first == mode }?.second ?: "不固定"
}

@Composable
private fun SettingsSection(
    title: String,
    action: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
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
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.82f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsDivider(indent: Dp = 0.dp) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f),
        modifier = Modifier.padding(start = indent + 2.dp, end = 2.dp)
    )
}

@Composable
private fun SettingsTextBlock(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    indent: Dp = 0.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .padding(start = indent, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsTextBlock(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = appSwitchColors()
        )
    }
}

@Composable
private fun SettingsConfigTransferRow(
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsTextBlock(
            title = "导入 / 导出配置",
            subtitle = "EPUB 执行链；TXT 书名、目录、净化规则、替换预设",
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = onImportConfig,
            shape = ControlShape,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("导入", style = MaterialTheme.typography.labelMedium)
        }
        Button(
            onClick = onExportConfig,
            shape = ControlShape,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
        ) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("导出", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SettingsAboutHeader(
    onShowUpdateNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appName = stringResource(id = R.string.app_name)
    val versionText = "版本 ${BuildConfig.VERSION_NAME}"
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppIconBadge(modifier = Modifier.size(58.dp))
        Text(
            text = appName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = versionText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            UpdateNotesInlineButton(onClick = onShowUpdateNotes)
        }
    }
}

@Composable
fun SettingsPanel(
    controller: EditorController,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val showEpubSettings = controller.kind == DocumentKind.Epub
    val showTxtSettings = controller.kind == DocumentKind.Txt
    var showUpdateNotes by remember { mutableStateOf(false) }
    val settingsScrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(settingsScrollState)
                .padding(if (compact) 12.dp else 16.dp)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            SettingsAboutHeader(onShowUpdateNotes = { showUpdateNotes = true })
            SettingsSection(title = "显示") {
                SettingsSwitchRow(
                    title = "左侧栏显示全名",
                    subtitle = "开启后保持展开，关闭后只显示图标",
                    checked = controller.leftRailExpanded,
                    onCheckedChange = controller::updateLeftRailExpanded
                )
            }
            SettingsSection(title = "配置") {
                SettingsConfigTransferRow(
                    onExportConfig = onExportConfig,
                    onImportConfig = onImportConfig
                )
            }
            if (showEpubSettings) {
                SettingsSection(title = "EPUB") {
                    SettingsSwitchRow(
                        title = "双击打开编辑",
                        checked = controller.epubDoubleTapEdit,
                        onCheckedChange = controller::updateEpubDoubleTapEdit
                    )
                    SettingsDivider()
                    SettingsTextBlock(
                        title = "打开文件后固定面板",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp)
                    )
                    FixedPanelModeRow(
                        leftValue = leftPanelModeLabel(controller.epubLeftPanelMode),
                        rightValue = rightPanelModeLabel(controller.epubRightPanelMode),
                        onLeftSelect = controller::updateEpubLeftPanelMode,
                        onRightSelect = controller::updateEpubRightPanelMode
                    )
                }
            }
            if (showTxtSettings) {
                SettingsSection(title = "TXT") {
                    SettingsSwitchRow(
                        title = "正文双击打开编辑",
                        checked = controller.txtDoubleTapEdit,
                        onCheckedChange = controller::updateTxtDoubleTapEdit
                    )
                    SettingsSwitchRow(
                        title = "标题双击编辑",
                        checked = controller.txtDoubleTapTitleEdit,
                        onCheckedChange = controller::updateTxtDoubleTapTitleEdit
                    )
                    SettingsDivider()
                    SettingsTextBlock(
                        title = "打开文件后固定面板",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp)
                    )
                    FixedPanelModeRow(
                        leftValue = leftPanelModeLabel(controller.txtLeftPanelMode),
                        rightValue = txtRightPanelModeLabel(controller.txtRightPanelMode),
                        onLeftSelect = controller::updateTxtLeftPanelMode,
                        onRightSelect = controller::updateTxtRightPanelMode,
                        leftOptions = TxtLeftPanelModeOptions,
                        rightOptions = TxtRightPanelModeOptions
                    )
                }
            }
        }
        if (showUpdateNotes) {
            UpdateNotesDialog(onDismiss = { showUpdateNotes = false })
        }
    }
}

@Composable
private fun SettingsPanelChoice(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        PanelDropdownField(
            value = value,
            options = options,
            onSelect = onSelect,
            height = 50.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FixedPanelModeRow(
    leftValue: String,
    rightValue: String,
    onLeftSelect: (String) -> Unit,
    onRightSelect: (String) -> Unit,
    leftOptions: List<Pair<String, String>> = LeftPanelModeOptions,
    rightOptions: List<Pair<String, String>> = RightPanelModeOptions
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SettingsPanelChoice(
            label = "左侧",
            value = leftValue,
            options = leftOptions,
            onSelect = onLeftSelect,
            modifier = Modifier.weight(1f)
        )
        SettingsPanelChoice(
            label = "右侧",
            value = rightValue,
            options = rightOptions,
            onSelect = onRightSelect,
            modifier = Modifier.weight(1f)
        )
    }
}

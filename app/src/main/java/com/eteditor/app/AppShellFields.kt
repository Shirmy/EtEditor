package com.eteditor

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind

private const val SIDE_WEIGHT = 1f
private const val DIRECTORY_SIDE_WEIGHT = 1f
private const val CENTER_WEIGHT_FULL = 3f
private const val CENTER_WEIGHT_DIRECTORY_ONLY = 2f
private const val CENTER_WEIGHT_RIGHT_ONLY = 2f
private const val CENTER_WEIGHT_BOTH_OPEN = 1f

private enum class WideRightPanel {
    Automation,
    Features,
    Settings
}

private fun String.toPinnedWidePanel(): WideRightPanel? {
    return when (this) {
        RIGHT_PANEL_AUTOMATION -> WideRightPanel.Automation
        RIGHT_PANEL_FEATURES -> WideRightPanel.Features
        else -> null
    }
}

@Composable
internal fun EtEditorApp(
    controller: EditorController,
    onOpenFile: () -> Unit,
    onSave: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onExit: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    val configuration = LocalConfiguration.current
    val isWide = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 720

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!isWide && controller.kind != DocumentKind.None) BottomNav(controller)
            }
        ) { innerPadding ->
            if (isWide) {
                WideLayout(
                    controller,
                    onOpenFile,
                    onSave,
                    onExportConfig,
                    onImportConfig,
                    onExit,
                    onPickTextReplaceRuleFile,
                    innerPadding
                )
            } else {
                PortraitLayout(
                    controller,
                    onOpenFile,
                    onSave,
                    onExportConfig,
                    onImportConfig,
                    onPickTextReplaceRuleFile,
                    innerPadding
                )
            }
        }
        AutoUpdateNotesDialogHost()
    }
}

@Composable
private fun PortraitLayout(
    controller: EditorController,
    onOpenFile: () -> Unit,
    onSave: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker,
    innerPadding: PaddingValues
) {
    Box(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
    ) {
        when (controller.selectedScreen) {
            AppScreen.Files -> {
                if (controller.kind == DocumentKind.None) {
                    FilePanel(
                        controller = controller,
                        onOpenFile = onOpenFile,
                        onSave = onSave,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        compact = true
                    )
                } else {
                    PortraitWorkspacePanel(controller, onOpenFile, onSave, onPickTextReplaceRuleFile)
                }
            }
            AppScreen.Automation -> {
                if (controller.kind == DocumentKind.Epub) {
                    AutomationPanel(
                        controller = controller,
                        modifier = Modifier.fillMaxSize(),
                        compact = false,
                        documentSessionKey = controller.documentSessionKey,
                        onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
                    )
                } else {
                    PortraitWorkspacePanel(controller, onOpenFile, onSave, onPickTextReplaceRuleFile)
                }
            }
            AppScreen.Features -> DocumentToolsPanel(
                controller = controller,
                onSave = onSave,
                onOpenFile = onOpenFile,
                documentSessionKey = controller.documentSessionKey,
                onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
            )
            AppScreen.Settings -> SettingsPanel(
                controller = controller,
                onExportConfig = onExportConfig,
                onImportConfig = onImportConfig
            )
        }
    }
}

@Composable
private fun PortraitWorkspacePanel(
    controller: EditorController,
    onOpenFile: () -> Unit,
    onSave: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilePanel(
            controller = controller,
            onOpenFile = onOpenFile,
            onSave = onSave,
            onOpenAutomation = {
                if (controller.kind == DocumentKind.Epub) {
                    controller.selectedScreen = AppScreen.Automation
                }
            },
            modifier = Modifier
                .weight(if (controller.kind == DocumentKind.Txt) 1f else 1.15f)
                .fillMaxWidth(),
            compact = true
        )
        if (controller.kind == DocumentKind.Epub) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AutomationPanel(
                controller = controller,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                compact = true,
                documentSessionKey = controller.documentSessionKey,
                onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
            )
        }
    }
}

@Composable
private fun WideLayout(
    controller: EditorController,
    onOpenFile: () -> Unit,
    onSave: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onExit: () -> Unit,
    onPickTextReplaceRuleFile: TextReplaceRuleFilePicker,
    innerPadding: PaddingValues
) {
    var directoryOpen by remember { mutableStateOf(false) }
    var rightPanel by remember { mutableStateOf<WideRightPanel?>(null) }
    var txtFeaturePanel by remember { mutableStateOf(TxtFeaturePanel.Search) }
    var automationResetKey by remember { mutableStateOf(0) }
    var featuresResetKey by remember { mutableStateOf(0) }
    var textReplacePreviewSplit by remember { mutableStateOf(false) }
    var showTxtLengthHintSettings by remember(controller.documentSessionKey) { mutableStateOf(false) }
    val documentOpen = controller.kind != DocumentKind.None
    val featuresOpen = rightPanel == WideRightPanel.Features
    val settingsOpen = rightPanel == WideRightPanel.Settings
    val textReplacePreviewSplitOpen = featuresOpen && textReplacePreviewSplit
    val automationSplitOpen = rightPanel == WideRightPanel.Automation && controller.kind == DocumentKind.Epub
    val directoryVisible = directoryOpen && !textReplacePreviewSplitOpen && !automationSplitOpen
    val centerWeight = when {
        textReplacePreviewSplitOpen -> SIDE_WEIGHT
        automationSplitOpen -> SIDE_WEIGHT
        directoryVisible && rightPanel != null -> CENTER_WEIGHT_BOTH_OPEN
        directoryVisible -> CENTER_WEIGHT_DIRECTORY_ONLY
        rightPanel != null -> CENTER_WEIGHT_RIGHT_ONLY
        else -> CENTER_WEIGHT_FULL
    }
    val txtMode = controller.kind == DocumentKind.Txt

    fun currentLeftPanelMode(): String {
        return when (controller.kind) {
            DocumentKind.Epub -> controller.epubLeftPanelMode
            DocumentKind.Txt -> controller.txtLeftPanelMode
            DocumentKind.None -> LEFT_PANEL_NONE
        }
    }

    fun currentRightPanelMode(): String {
        return when (controller.kind) {
            DocumentKind.Epub -> controller.epubRightPanelMode
            DocumentKind.Txt -> controller.txtRightPanelMode
            DocumentKind.None -> RIGHT_PANEL_NONE
        }
    }

    fun directoryAvailable(): Boolean {
        return documentOpen && (controller.kind == DocumentKind.Txt || controller.chapters.isNotEmpty())
    }

    fun pinnedDirectoryOpen(): Boolean {
        return documentOpen &&
            currentLeftPanelMode() == LEFT_PANEL_DIRECTORY &&
            directoryAvailable()
    }

    fun pinnedPanel(): WideRightPanel? {
        val panel = if (documentOpen) currentRightPanelMode().toPinnedWidePanel() else null
        return if (controller.kind == DocumentKind.Txt && panel == WideRightPanel.Automation) null else panel
    }

    fun applyInitialPinnedPanels() {
        val nextRightPanel = pinnedPanel()
        directoryOpen = if (nextRightPanel == WideRightPanel.Automation) {
            false
        } else {
            pinnedDirectoryOpen()
        }
        if (nextRightPanel == WideRightPanel.Features) {
            featuresResetKey += 1
        }
        rightPanel = nextRightPanel
    }

    fun toggleRightPanel(panel: WideRightPanel) {
        if (controller.kind == DocumentKind.Txt && panel == WideRightPanel.Automation) return
        val nextPanel = if (rightPanel == panel) {
            null
        } else {
            panel
        }
        if (nextPanel == WideRightPanel.Automation) {
            directoryOpen = false
        }
        rightPanel = nextPanel
    }

    fun openTopPanel(panel: WideRightPanel) {
        if (panel == WideRightPanel.Automation) {
            directoryOpen = false
        }
        when (panel) {
            WideRightPanel.Automation -> automationResetKey += 1
            WideRightPanel.Features -> featuresResetKey += 1
            else -> Unit
        }
        rightPanel = panel
    }

    fun openTxtFeaturePanel(panel: TxtFeaturePanel) {
        txtFeaturePanel = panel
        featuresResetKey += 1
        rightPanel = WideRightPanel.Features
    }

    fun updateTextReplacePreviewSplit(open: Boolean) {
        textReplacePreviewSplit = open
        if (open) {
            directoryOpen = false
            rightPanel = WideRightPanel.Features
        }
    }

    LaunchedEffect(controller.documentSessionKey) {
        txtFeaturePanel = TxtFeaturePanel.Search
        textReplacePreviewSplit = false
        automationResetKey += 1
        featuresResetKey += 1
    }

    LaunchedEffect(
        controller.documentSessionKey,
        controller.kind
    ) {
        if (documentOpen) {
            applyInitialPinnedPanels()
        } else {
            directoryOpen = false
            rightPanel = null
        }
    }

    LaunchedEffect(rightPanel) {
        if (rightPanel != WideRightPanel.Features) {
            textReplacePreviewSplit = false
        }
    }

    if (!documentOpen) {
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(8.dp)
        ) {
            FilePanel(
                controller = controller,
                onOpenFile = onOpenFile,
                onSave = onSave,
                modifier = Modifier.fillMaxSize(),
                compact = true
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .padding(start = 4.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        AppRail(
            toolsEnabled = documentOpen,
            documentKind = controller.kind,
            documentControlsEnabled = !controller.busy,
            expanded = controller.leftRailExpanded,
            fileOpen = !settingsOpen,
            directoryOpen = directoryVisible && !settingsOpen,
            featuresOpen = featuresOpen,
            settingsOpen = settingsOpen,
            showDirectory = false,
            showExit = true,
            featureIcon = if (txtMode) Icons.Outlined.Search else Icons.Outlined.Widgets,
            featureLabel = if (txtMode) "搜索" else "功能",
            hideDirectoryFileName = controller.hideDirectoryFileNameByDefault,
            onHideDirectoryFileNameChange = controller::updateHideDirectoryFileNameByDefault,
            epubHideSection0001FromNcx = controller.epubHideSection0001FromNcx,
            onEpubHideSection0001FromNcxChange = controller::updateEpubHideSection0001FromNcx,
            epubWordCountEnabled = controller.epubWordCountProgress == null,
            onEpubWordCountClick = controller::refreshEpubSummaryMeta,
            onTxtLengthHintSettingsClick = { showTxtLengthHintSettings = true },
            txtChapterNumberStartAtOneOnSave = controller.txtChapterNumberStartAtOneOnSave,
            onTxtChapterNumberStartAtOneOnSaveChange = controller::updateTxtChapterNumberStartAtOneOnSave,
            txtAutoNumberOnSave = controller.txtAutoNumberOnSave,
            onTxtAutoNumberOnSaveChange = controller::updateTxtAutoNumberOnSave,
            onFileClick = {
                rightPanel = null
                textReplacePreviewSplit = false
            },
            onDirectoryClick = {
                if (directoryAvailable()) {
                    directoryOpen = !directoryOpen
                }
            },
            onFeatureClick = {
                if (documentOpen) {
                    if (txtMode) {
                        if (rightPanel == WideRightPanel.Features) {
                            rightPanel = null
                        } else {
                            openTxtFeaturePanel(TxtFeaturePanel.Search)
                        }
                    } else {
                        if (rightPanel == WideRightPanel.Features) {
                            rightPanel = null
                        } else {
                            openTopPanel(WideRightPanel.Features)
                        }
                    }
                } else {
                    directoryOpen = false
                    rightPanel = null
                }
            },
            onSettingsClick = {
                toggleRightPanel(WideRightPanel.Settings)
            },
            onExitClick = onExit
        )
        VerticalDivider()
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 4.dp, end = 4.dp)
        ) {
            if (settingsOpen) {
                SettingsPanel(
                    controller = controller,
                    onExportConfig = onExportConfig,
                    onImportConfig = onImportConfig,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            } else {
                if (directoryVisible && directoryAvailable()) {
                    DirectoryPanel(
                        controller = controller,
                        modifier = Modifier
                            .weight(DIRECTORY_SIDE_WEIGHT)
                            .fillMaxHeight(),
                        onPickChapter = { index -> controller.selectPreviewChapter(index) }
                    )
                    VerticalDivider()
                }
                key("main-file-panel") {
                    FilePanel(
                        controller = controller,
                        onOpenFile = onOpenFile,
                        onSave = onSave,
                        directoryOpen = directoryVisible,
                        onToggleDirectory = {
                            if (directoryAvailable()) {
                                directoryOpen = !directoryOpen
                            }
                        },
                        onOpenAutomation = {
                            if (!txtMode) {
                                if (rightPanel == WideRightPanel.Automation) {
                                    rightPanel = null
                                } else {
                                    openTopPanel(WideRightPanel.Automation)
                                }
                            }
                        },
                        automationOpen = rightPanel == WideRightPanel.Automation,
                        modifier = Modifier
                            .weight(centerWeight)
                            .fillMaxHeight(),
                        compact = true
                    )
                }
                when (rightPanel) {
                    WideRightPanel.Automation -> if (!txtMode) {
                        VerticalDivider()
                        AutomationPanel(
                            controller = controller,
                            modifier = Modifier
                                .weight(SIDE_WEIGHT)
                                .fillMaxHeight(),
                            compact = true,
                            resetKey = automationResetKey,
                            documentSessionKey = controller.documentSessionKey,
                            onPickTextReplaceRuleFile = onPickTextReplaceRuleFile
                        )
                    }
                    WideRightPanel.Features -> {
                        VerticalDivider()
                        DocumentToolsPanel(
                            controller = controller,
                            onSave = onSave,
                            resetKey = featuresResetKey,
                            documentSessionKey = controller.documentSessionKey,
                            onPickTextReplaceRuleFile = onPickTextReplaceRuleFile,
                            txtPanel = txtFeaturePanel,
                            onTextReplacePreviewModeChange = ::updateTextReplacePreviewSplit,
                            modifier = Modifier
                                .weight(SIDE_WEIGHT)
                                .fillMaxHeight(),
                            compact = true
                        )
                    }
                    WideRightPanel.Settings,
                    null -> Unit
                }
            }
        }
    }
    if (showTxtLengthHintSettings) {
        TxtLengthHintSettingsDialog(
            controller = controller,
            onDismiss = { showTxtLengthHintSettings = false }
        )
    }
}

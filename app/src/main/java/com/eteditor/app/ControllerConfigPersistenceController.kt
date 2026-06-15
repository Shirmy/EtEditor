package com.eteditor

import android.net.Uri
import org.json.JSONObject

fun EditorController.configExportFileName(): String = "EtEditor_config.json"

suspend fun EditorController.exportConfigTo(uri: Uri) = runBusy("导出配置") {
    val bytes = buildConfigExportJson()
        .toString(2)
        .toByteArray(Charsets.UTF_8)
    writeDocumentBytes(appContext, uri, bytes)
    statusMessage = "配置已导出：设置、主页默认、EPUB 预设/执行链、替换预设、TXT 书名/目录/净化规则、长短章提示、替换预设"
}

suspend fun EditorController.importConfigFrom(uri: Uri) = runBusy("导入配置") {
    val root = JSONObject(
        readDocumentBytes(appContext, uri, CONFIG_IMPORT_MAX_BYTES, "配置文件")
            .toString(Charsets.UTF_8)
    )
    if (!isEtEditorConfigRoot(root, CONFIG_SCHEMA)) {
        statusMessage = "导入失败：不是 EtEditor 配置文件"
        return@runBusy
    }
    if (root.has("txt") && warnTxtMoveChapterSyncPending("导入配置")) {
        return@runBusy
    }

    val imported = mutableListOf<String>()
    var refreshTxtAfterImport = false
    root.optJSONObject("settings")?.let { settingsConfig ->
        refreshTxtAfterImport = importSettingsConfig(settingsConfig)
        imported += "设置"
    }
    root.optJSONObject("builtInDefaults")?.let { defaultsConfig ->
        val defaults = parseBuiltInDefaultOverrides(defaultsConfig, includeSensitive = false)
        replaceBuiltInDefaultsForImport(defaults)
        imported += "主页功能默认参数"
    }
    root.optJSONObject("epub")?.let { epubConfig ->
        val epubImport = parseEpubConfigImport(
            epubConfig = epubConfig,
            allowedToolIds = EPUB_EDITOR_TOOL_IDS,
            parseToolPresets = { array -> parseEditorTools(array, includeSensitive = false).orEmpty() },
            parseAutomationChains = { array -> parseAutomationChains(array, includeSensitive = false) },
            parseTextReplacePresets = { array -> parseEpubTextReplacePresets(array, includeSensitive = false).orEmpty() }
        )
        epubImport.toolPresets?.let { toolPresets ->
            replaceEditorToolsForImport(EPUB_EDITOR_TOOL_IDS, toolPresets)
            imported += "EPUB 预设 ${toolPresets.size} 条"
        }
        epubImport.automationChains?.let { chainImport ->
            automationChainGroups = chainImport.groups
            val (migratedChains, migrated) = migrateAutomationChainsToPresetSteps(
                chains = chainImport.chains,
                forceSnapshotPresets = true
            )
            automationChains = migratedChains
            selectedAutomationChainId = chainImport
                .selectedAutomationChainId
                .takeIf { selectedId -> automationChains.any { it.id == selectedId } }
                ?: automationChains.firstOrNull()?.id.orEmpty()
            syncAutomationCounters()
            if (migrated) persistEditorTools()
            persistAutomationChains()
            imported += "EPUB 执行链 ${automationChains.size} 条"
        }
        epubImport.textReplacePresets?.let { textReplacePresets ->
            replaceEpubTextReplacePresetsForImport(textReplacePresets)
            imported += "EPUB 替换预设 ${textReplacePresets.size} 条"
        }
    }

    root.optJSONObject("txt")?.let { txtConfig ->
        val txtImport = parseTxtConfigImport(txtConfig) { presetsJson ->
            parseTxtTextReplacePresets(presetsJson, includeSensitive = false).orEmpty()
        }
        txtImport.bookTitleRules?.let { rules ->
            txtBookTitleRulesText = rules
        }
        txtImport.chapterRules?.let { rules ->
            txtChapterRulesText = rules
        }
        txtImport.purifyRules?.let { rules ->
            txtPurifyRulesText = rules
        }
        txtImport.textReplacePresets?.let { textReplacePresets ->
            replaceTxtTextReplacePresetsForImport(textReplacePresets)
        }
        txtImport.chapterHints?.let { hints ->
            val current = txtChapterHintsConfigSnapshot()
            val next = hints.mergeWith(current)
            if (next != current) {
                applyTxtChapterHintsConfig(next)
                refreshTxtAfterImport = true
            }
        }
        imported += txtImport.importedMessages
        if (txtImport.rulesChanged) {
            settingsPreferences.applyTxtConfigRules(
                bookTitleRules = txtImport.bookTitleRules,
                chapterRules = txtImport.chapterRules,
                purifyRules = txtImport.purifyRules
            )
            refreshTxtDocumentChapters()
            refreshChapters()
            refreshPreview()
            refreshTxtAfterImport = false
        }
    }
    if (refreshTxtAfterImport) {
        refreshTxtDocumentChapters()
        refreshChapters()
        refreshPreview()
    }

    statusMessage = if (imported.isEmpty()) {
        "配置文件里没有可导入内容"
    } else {
        "\u914d\u7f6e\u5df2\u5bfc\u5165\uff1a${imported.joinToString("\u3001")}"
    }
}

private fun EditorController.buildConfigExportJson(): JSONObject {
    return buildConfigExportJsonModel(
        schema = CONFIG_SCHEMA,
        exportedAt = System.currentTimeMillis(),
        settings = settingsConfigSnapshot(),
        builtInDefaults = builtInDefaultsToJson(includeSensitive = false),
        epub = EpubConfigExportSnapshot(
            selectedAutomationChainId = selectedAutomationChainId,
            toolPresets = editorToolsToJson(
                tools = editorToolsForEpubAutomation(),
                includeSensitive = false
            ),
            automationChainGroups = automationChainGroups,
            automationChains = automationChainsToJson(automationChains, includeSensitive = false),
            textReplacePresets = editorToolsToJson(
                tools = epubTextReplacePresets,
                includeSensitive = false
            )
        ),
        txt = TxtConfigExportSnapshot(
            bookTitleRules = txtBookTitleRulesText,
            chapterRules = serializeTxtChapterRuleItems(parseTxtChapterRuleItems(txtChapterRulesText)),
            purifyRules = txtPurifyRulesText,
            chapterHints = txtChapterHintsConfigSnapshot(),
            textReplacePresets = editorToolsToJson(
                tools = txtTextReplacePresets,
                includeSensitive = false
            )
        )
    )
}

private fun EditorController.txtChapterHintsConfigSnapshot(): TxtChapterHintsConfigSnapshot {
    return TxtChapterHintsConfigSnapshot(
        wordCountHintsEnabled = true,
        shortHintEnabled = txtShortChapterHintEnabled,
        longHintEnabled = txtLongChapterHintEnabled,
        shortThreshold = txtShortChapterThreshold,
        longThreshold = txtLongChapterThreshold,
        txtChapterHintMode = txtChapterHintMode.takeIf { it in TXT_CHAPTER_HINT_MODES } ?: TXT_CHAPTER_HINT_MODE_AUTO
    )
}

private fun EditorController.applyTxtChapterHintsConfig(config: TxtChapterHintsConfigSnapshot) {
    txtChapterWordCountHintsEnabled = true
    txtShortChapterHintEnabled = config.shortHintEnabled
    txtLongChapterHintEnabled = config.longHintEnabled
    txtShortChapterThreshold = config.shortThreshold.coerceAtLeast(0)
    txtLongChapterThreshold = config.longThreshold.coerceAtLeast(0)
    txtChapterHintMode = config.txtChapterHintMode.takeIf { it in TXT_CHAPTER_HINT_MODES } ?: TXT_CHAPTER_HINT_MODE_AUTO
    settingsPreferences.saveTxtChapterHintSettings(
        wordCountHintsEnabled = true,
        shortHintEnabled = txtShortChapterHintEnabled,
        longHintEnabled = txtLongChapterHintEnabled,
        shortThreshold = txtShortChapterThreshold,
        longThreshold = txtLongChapterThreshold,
        hintMode = txtChapterHintMode
    )
}

private fun EditorController.settingsConfigSnapshot(): SettingsConfigSnapshot {
    return SettingsConfigSnapshot(
        leftRailExpanded = leftRailExpanded,
        epubDoubleTapEdit = epubDoubleTapEdit,
        epubLeftPanelMode = epubLeftPanelMode,
        epubRightPanelMode = epubRightPanelMode,
        txtLeftPanelMode = txtLeftPanelMode,
        txtRightPanelMode = txtRightPanelMode,
        txtDoubleTapEdit = txtDoubleTapEdit,
        txtDoubleTapTitleEdit = txtDoubleTapTitleEdit,
        txtShortChapterThreshold = txtShortChapterThreshold,
        txtLongChapterThreshold = txtLongChapterThreshold,
        txtChapterWordCountHintsEnabled = true,
        txtShortChapterHintEnabled = txtShortChapterHintEnabled,
        txtLongChapterHintEnabled = txtLongChapterHintEnabled,
        txtChapterHintMode = txtChapterHintMode.takeIf { it in TXT_CHAPTER_HINT_MODES } ?: TXT_CHAPTER_HINT_MODE_AUTO
    )
}

private fun EditorController.importSettingsConfig(settings: JSONObject): Boolean {
    val result = parseSettingsConfigImport(
        settings = settings,
        current = settingsConfigSnapshot(),
        prefKeys = editorSettingsConfigPreferenceKeys(),
        leftPanelModes = LEFT_PANEL_MODES,
        rightPanelModes = RIGHT_PANEL_MODES,
        txtRightPanelModes = RIGHT_PANEL_MODES - RIGHT_PANEL_AUTOMATION
    )
    applySettingsConfigSnapshot(result.snapshot)
    settingsPreferences.applySettingsPreferenceValues(result.preferences)
    return result.txtCatalogChanged
}

private fun EditorController.applySettingsConfigSnapshot(snapshot: SettingsConfigSnapshot) {
    leftRailExpanded = snapshot.leftRailExpanded
    epubDoubleTapEdit = snapshot.epubDoubleTapEdit
    epubLeftPanelMode = snapshot.epubLeftPanelMode
    epubRightPanelMode = snapshot.epubRightPanelMode
    txtLeftPanelMode = snapshot.txtLeftPanelMode
    txtRightPanelMode = snapshot.txtRightPanelMode
    txtDoubleTapEdit = snapshot.txtDoubleTapEdit
    txtDoubleTapTitleEdit = snapshot.txtDoubleTapTitleEdit
    txtShortChapterThreshold = snapshot.txtShortChapterThreshold
    txtLongChapterThreshold = snapshot.txtLongChapterThreshold
    txtChapterWordCountHintsEnabled = true
    txtShortChapterHintEnabled = snapshot.txtShortChapterHintEnabled
    txtLongChapterHintEnabled = snapshot.txtLongChapterHintEnabled
    txtChapterHintMode = snapshot.txtChapterHintMode.takeIf { it in TXT_CHAPTER_HINT_MODES } ?: TXT_CHAPTER_HINT_MODE_AUTO
}

private const val CONFIG_SCHEMA = "et_editor_config"

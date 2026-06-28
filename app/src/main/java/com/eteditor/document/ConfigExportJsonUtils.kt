package com.eteditor

import org.json.JSONArray
import org.json.JSONObject

internal data class SettingsConfigSnapshot(
    val leftRailExpanded: Boolean,
    val epubDoubleTapEdit: Boolean,
    val epubLeftPanelMode: String,
    val epubRightPanelMode: String,
    val txtLeftPanelMode: String,
    val txtRightPanelMode: String,
    val txtDoubleTapEdit: Boolean,
    val txtDoubleTapTitleEdit: Boolean,
    val txtShortChapterThreshold: Int,
    val txtLongChapterThreshold: Int,
    val txtShortChapterHintEnabled: Boolean,
    val txtLongChapterHintEnabled: Boolean,
    val txtChapterHintMode: String
)

internal data class EpubConfigExportSnapshot(
    val selectedAutomationChainId: String,
    val toolPresets: JSONArray,
    val automationChainGroups: List<String>,
    val automationChains: JSONArray,
    val textReplacePresets: JSONArray
)

internal data class TxtConfigExportSnapshot(
    val bookTitleRules: String,
    val chapterRules: String,
    val purifyRules: String,
    val chapterHints: TxtChapterHintsConfigSnapshot,
    val textReplacePresets: JSONArray
)

internal data class TxtChapterHintsConfigSnapshot(
    val shortHintEnabled: Boolean,
    val longHintEnabled: Boolean,
    val shortThreshold: Int,
    val longThreshold: Int,
    val txtChapterHintMode: String
)

internal data class TxtChapterHintsConfigImport(
    val shortHintEnabled: Boolean? = null,
    val longHintEnabled: Boolean? = null,
    val shortThreshold: Int? = null,
    val longThreshold: Int? = null,
    val txtChapterHintMode: String? = null
) {
    val hasValues: Boolean
        get() = shortHintEnabled != null ||
            longHintEnabled != null ||
            shortThreshold != null ||
            longThreshold != null ||
            txtChapterHintMode != null

    fun mergeWith(current: TxtChapterHintsConfigSnapshot): TxtChapterHintsConfigSnapshot {
        return TxtChapterHintsConfigSnapshot(
            shortHintEnabled = shortHintEnabled ?: current.shortHintEnabled,
            longHintEnabled = longHintEnabled ?: current.longHintEnabled,
            shortThreshold = shortThreshold ?: current.shortThreshold,
            longThreshold = longThreshold ?: current.longThreshold,
            txtChapterHintMode = txtChapterHintMode?.takeIf { it in TXT_CHAPTER_HINT_MODES }
                ?: current.txtChapterHintMode
        )
    }
}

internal data class TxtConfigImportResult(
    val bookTitleRules: String? = null,
    val chapterRules: String? = null,
    val purifyRules: String? = null,
    val chapterHints: TxtChapterHintsConfigImport? = null,
    val textReplacePresets: List<EditorTool>? = null,
    val importedMessages: List<String> = emptyList()
) {
    val rulesChanged: Boolean get() = bookTitleRules != null || chapterRules != null || purifyRules != null
}

internal data class EpubAutomationChainsConfigImport(
    val chains: List<AutomationChain>,
    val groups: List<String>,
    val selectedAutomationChainId: String
)

internal data class EpubConfigImportResult(
    val toolPresets: List<EditorTool>? = null,
    val automationChains: EpubAutomationChainsConfigImport? = null,
    val textReplacePresets: List<EditorTool>? = null
)

internal data class SettingsConfigPreferenceKeys(
    val leftRailExpanded: String,
    val epubDoubleTapEdit: String,
    val epubLeftPanelMode: String,
    val epubRightPanelMode: String,
    val txtLeftPanelMode: String,
    val txtRightPanelMode: String,
    val txtDoubleTapEdit: String,
    val txtDoubleTapTitleEdit: String,
    val txtShortChapterThreshold: String,
    val txtLongChapterThreshold: String,
    val txtShortChapterHintEnabled: String,
    val txtLongChapterHintEnabled: String,
    val txtChapterHintMode: String
)

internal sealed class SettingsPreferenceValue {
    abstract val key: String

    data class BooleanValue(
        override val key: String,
        val value: Boolean
    ) : SettingsPreferenceValue()

    data class IntValue(
        override val key: String,
        val value: Int
    ) : SettingsPreferenceValue()

    data class StringValue(
        override val key: String,
        val value: String
    ) : SettingsPreferenceValue()
}

internal data class SettingsConfigImportResult(
    val snapshot: SettingsConfigSnapshot,
    val preferences: List<SettingsPreferenceValue>,
    val txtCatalogChanged: Boolean = false
)

private data class TxtCatalogHintConfig(
    val shortThreshold: Int,
    val longThreshold: Int,
    val txtChapterHintMode: String
)

internal fun buildConfigExportJsonModel(
    schema: String,
    exportedAt: Long,
    settings: SettingsConfigSnapshot,
    builtInDefaults: JSONObject,
    epub: EpubConfigExportSnapshot,
    txt: TxtConfigExportSnapshot
): JSONObject {
    return JSONObject()
        .put("schema", schema)
        .put("version", 1)
        .put("exportedAt", exportedAt)
        .put("settings", settings.toJson())
        .put("builtInDefaults", builtInDefaults)
        .put("epub", epub.toJson())
        .put("txt", txt.toJson())
}

internal fun isEtEditorConfigRoot(root: JSONObject, schema: String): Boolean {
    return root.optString("schema") == schema &&
        (root.has("epub") || root.has("txt") || root.has("settings") || root.has("builtInDefaults"))
}

internal fun parseSettingsConfigImport(
    settings: JSONObject,
    current: SettingsConfigSnapshot,
    prefKeys: SettingsConfigPreferenceKeys,
    leftPanelModes: Set<String>,
    rightPanelModes: Set<String>,
    txtRightPanelModes: Set<String>
): SettingsConfigImportResult {
    var next = current
    val preferences = mutableListOf<SettingsPreferenceValue>()

    fun importBoolean(
        jsonKey: String,
        prefKey: String,
        currentValue: Boolean,
        applyValue: (SettingsConfigSnapshot, Boolean) -> SettingsConfigSnapshot
    ) {
        if (!settings.has(jsonKey)) return
        val value = settings.optBoolean(jsonKey, currentValue)
        next = applyValue(next, value)
        preferences += SettingsPreferenceValue.BooleanValue(prefKey, value)
    }

    fun importInt(
        jsonKey: String,
        prefKey: String,
        currentValue: Int,
        minValue: Int = 0,
        applyValue: (SettingsConfigSnapshot, Int) -> SettingsConfigSnapshot
    ) {
        if (!settings.has(jsonKey)) return
        val value = settings.optInt(jsonKey, currentValue).coerceAtLeast(minValue)
        next = applyValue(next, value)
        preferences += SettingsPreferenceValue.IntValue(prefKey, value)
    }

    fun importString(
        jsonKey: String,
        prefKey: String,
        currentValue: String,
        allowedValues: Set<String>,
        applyValue: (SettingsConfigSnapshot, String) -> SettingsConfigSnapshot
    ) {
        if (!settings.has(jsonKey)) return
        val value = settings.optString(jsonKey, currentValue)
            .takeIf { it in allowedValues }
            ?: currentValue
        next = applyValue(next, value)
        preferences += SettingsPreferenceValue.StringValue(prefKey, value)
    }

    importBoolean("leftRailExpanded", prefKeys.leftRailExpanded, current.leftRailExpanded) { state, value ->
        state.copy(leftRailExpanded = value)
    }
    importBoolean("epubDoubleTapEdit", prefKeys.epubDoubleTapEdit, current.epubDoubleTapEdit) { state, value ->
        state.copy(epubDoubleTapEdit = value)
    }
    importString("epubLeftPanelMode", prefKeys.epubLeftPanelMode, current.epubLeftPanelMode, leftPanelModes) { state, value ->
        state.copy(epubLeftPanelMode = value)
    }
    importString("epubRightPanelMode", prefKeys.epubRightPanelMode, current.epubRightPanelMode, rightPanelModes) { state, value ->
        state.copy(epubRightPanelMode = value)
    }
    importString("txtLeftPanelMode", prefKeys.txtLeftPanelMode, current.txtLeftPanelMode, leftPanelModes) { state, value ->
        state.copy(txtLeftPanelMode = value)
    }
    importString("txtRightPanelMode", prefKeys.txtRightPanelMode, current.txtRightPanelMode, txtRightPanelModes) { state, value ->
        state.copy(txtRightPanelMode = value)
    }
    importBoolean("txtDoubleTapEdit", prefKeys.txtDoubleTapEdit, current.txtDoubleTapEdit) { state, value ->
        state.copy(txtDoubleTapEdit = value)
    }
    importBoolean("txtDoubleTapTitleEdit", prefKeys.txtDoubleTapTitleEdit, current.txtDoubleTapTitleEdit) { state, value ->
        state.copy(txtDoubleTapTitleEdit = value)
    }
    importInt("txtShortChapterThreshold", prefKeys.txtShortChapterThreshold, current.txtShortChapterThreshold) { state, value ->
        state.copy(txtShortChapterThreshold = value)
    }
    importInt("txtLongChapterThreshold", prefKeys.txtLongChapterThreshold, current.txtLongChapterThreshold) { state, value ->
        state.copy(txtLongChapterThreshold = value)
    }
    importBoolean("txtShortChapterHintEnabled", prefKeys.txtShortChapterHintEnabled, current.txtShortChapterHintEnabled) { state, value ->
        state.copy(txtShortChapterHintEnabled = value)
    }
    importBoolean("txtLongChapterHintEnabled", prefKeys.txtLongChapterHintEnabled, current.txtLongChapterHintEnabled) { state, value ->
        state.copy(txtLongChapterHintEnabled = value)
    }
    importString("txtChapterHintMode", prefKeys.txtChapterHintMode, current.txtChapterHintMode, TXT_CHAPTER_HINT_MODES) { state, value ->
        state.copy(txtChapterHintMode = value)
    }

    return SettingsConfigImportResult(
        snapshot = next,
        preferences = preferences,
        txtCatalogChanged = current.txtCatalogHintConfig() != next.txtCatalogHintConfig()
    )
}

private fun SettingsConfigSnapshot.txtCatalogHintConfig(): TxtCatalogHintConfig {
    return TxtCatalogHintConfig(
        shortThreshold = if (txtShortChapterHintEnabled) {
            txtShortChapterThreshold
        } else {
            0
        },
        longThreshold = if (txtLongChapterHintEnabled) {
            txtLongChapterThreshold
        } else {
            0
        },
        txtChapterHintMode = txtChapterHintMode.takeIf { it in TXT_CHAPTER_HINT_MODES } ?: TXT_CHAPTER_HINT_MODE_AUTO
    )
}

internal fun parseTxtConfigImport(
    txtConfig: JSONObject,
    parseTextReplacePresets: (JSONArray) -> List<EditorTool>
): TxtConfigImportResult {
    var bookTitleRules: String? = null
    var chapterRules: String? = null
    var purifyRules: String? = null
    var chapterHints: TxtChapterHintsConfigImport? = null
    var textReplacePresets: List<EditorTool>? = null
    val imported = mutableListOf<String>()

    if (txtConfig.has("bookTitleRules")) {
        bookTitleRules = txtConfig.optString("bookTitleRules")
        imported += "TXT 书名规则"
    }
    if (txtConfig.has("chapterRules")) {
        chapterRules = serializeTxtChapterRuleItems(parseTxtChapterRuleItems(txtConfig.optString("chapterRules")))
        imported += "TXT 目录规则"
    }
    if (txtConfig.has("purifyRules")) {
        purifyRules = txtConfig.optString("purifyRules")
        imported += "TXT 净化规则"
    }
    parseTxtChapterHintsConfigImport(txtConfig.optJSONObject("chapterHints"))?.let { hints ->
        chapterHints = hints
        imported += "TXT 长短章提示"
    }
    if (txtConfig.has("textReplacePresets")) {
        txtConfig.optJSONArray("textReplacePresets")?.let { presetsJson ->
            textReplacePresets = parseTextReplacePresets(presetsJson)
            imported += "TXT 替换预设 ${textReplacePresets.size} 条"
        }
    }

    return TxtConfigImportResult(
        bookTitleRules = bookTitleRules,
        chapterRules = chapterRules,
        purifyRules = purifyRules,
        chapterHints = chapterHints,
        textReplacePresets = textReplacePresets,
        importedMessages = imported
    )
}

private fun parseTxtChapterHintsConfigImport(config: JSONObject?): TxtChapterHintsConfigImport? {
    if (config == null) return null
    val result = TxtChapterHintsConfigImport(
        shortHintEnabled = config.optBooleanIfPresent("shortHintEnabled"),
        longHintEnabled = config.optBooleanIfPresent("longHintEnabled"),
        shortThreshold = config.optIntIfPresent("shortThreshold")?.coerceAtLeast(0),
        longThreshold = config.optIntIfPresent("longThreshold")?.coerceAtLeast(0),
        txtChapterHintMode = (config.optStringIfPresent("mode") ?: config.optStringIfPresent("txtChapterHintMode"))
            ?.takeIf { it in TXT_CHAPTER_HINT_MODES }
    )
    return result.takeIf { it.hasValues }
}

internal fun parseEpubConfigImport(
    epubConfig: JSONObject,
    allowedToolIds: Set<String>,
    parseToolPresets: (JSONArray?) -> List<EditorTool>,
    parseAutomationChains: (JSONArray?) -> List<AutomationChain>?,
    parseTextReplacePresets: (JSONArray) -> List<EditorTool>
): EpubConfigImportResult {
    val toolPresets = if (epubConfig.has("toolPresets")) {
        parseToolPresets(epubConfig.optJSONArray("toolPresets"))
            .filter { tool -> tool.toolId in allowedToolIds }
    } else {
        null
    }
    val chains = parseAutomationChains(epubConfig.optJSONArray("automationChains"))
    val automationChains = chains?.let {
        EpubAutomationChainsConfigImport(
            chains = it,
            groups = parseAutomationChainGroups(epubConfig.optJSONArray("automationChainGroups")),
            selectedAutomationChainId = epubConfig.optString("selectedAutomationChainId")
        )
    }
    val textReplacePresets = if (epubConfig.has("textReplacePresets")) {
        epubConfig.optJSONArray("textReplacePresets")?.let(parseTextReplacePresets)
    } else {
        null
    }
    return EpubConfigImportResult(
        toolPresets = toolPresets,
        automationChains = automationChains,
        textReplacePresets = textReplacePresets
    )
}

internal fun SettingsConfigSnapshot.toJson(): JSONObject {
    return JSONObject()
        .put("leftRailExpanded", leftRailExpanded)
        .put("epubDoubleTapEdit", epubDoubleTapEdit)
        .put("epubLeftPanelMode", epubLeftPanelMode)
        .put("epubRightPanelMode", epubRightPanelMode)
        .put("txtLeftPanelMode", txtLeftPanelMode)
        .put("txtRightPanelMode", txtRightPanelMode)
        .put("txtDoubleTapEdit", txtDoubleTapEdit)
        .put("txtDoubleTapTitleEdit", txtDoubleTapTitleEdit)
        .put("txtShortChapterThreshold", txtShortChapterThreshold)
        .put("txtLongChapterThreshold", txtLongChapterThreshold)
        .put("txtShortChapterHintEnabled", txtShortChapterHintEnabled)
        .put("txtLongChapterHintEnabled", txtLongChapterHintEnabled)
        .put("txtChapterHintMode", txtChapterHintMode)
}

private fun EpubConfigExportSnapshot.toJson(): JSONObject {
    return JSONObject()
        .put("selectedAutomationChainId", selectedAutomationChainId)
        .put("toolPresets", toolPresets)
        .put("automationChainGroups", automationChainGroupsToJsonArray(automationChainGroups))
        .put("automationChains", automationChains)
        .put("textReplacePresets", textReplacePresets)
}

private fun TxtConfigExportSnapshot.toJson(): JSONObject {
    return JSONObject()
        .put("bookTitleRules", bookTitleRules)
        .put("chapterRules", chapterRules)
        .put("purifyRules", purifyRules)
        .put("chapterHints", chapterHints.toJson())
        .put("textReplacePresets", textReplacePresets)
}

private fun TxtChapterHintsConfigSnapshot.toJson(): JSONObject {
    return JSONObject()
        .put("shortHintEnabled", shortHintEnabled)
        .put("longHintEnabled", longHintEnabled)
        .put("shortThreshold", shortThreshold)
        .put("longThreshold", longThreshold)
        .put("mode", txtChapterHintMode)
}

private fun JSONObject.optBooleanIfPresent(key: String): Boolean? {
    return if (has(key)) optBoolean(key) else null
}

private fun JSONObject.optIntIfPresent(key: String): Int? {
    return if (has(key)) optInt(key) else null
}

private fun JSONObject.optStringIfPresent(key: String): String? {
    return if (has(key)) optString(key) else null
}

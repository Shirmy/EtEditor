package com.eteditor

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigExportJsonUtilsTest {
    @Test
    fun configRootRequiresMatchingSchemaAndImportableSection() {
        assertTrue(
            isEtEditorConfigRoot(
                root = JSONObject()
                    .put("schema", CONFIG_SCHEMA)
                    .put("settings", JSONObject()),
                schema = CONFIG_SCHEMA
            )
        )

        assertFalse(
            isEtEditorConfigRoot(
                root = JSONObject()
                    .put("schema", "other")
                    .put("settings", JSONObject()),
                schema = CONFIG_SCHEMA
            )
        )
        assertFalse(
            isEtEditorConfigRoot(
                root = JSONObject().put("schema", CONFIG_SCHEMA),
                schema = CONFIG_SCHEMA
            )
        )
    }

    @Test
    fun buildConfigExportJsonModelWritesRecognizableRoot() {
        val root = buildConfigExportJsonModel(
            schema = CONFIG_SCHEMA,
            exportedAt = 123L,
            settings = settingsSnapshot(),
            builtInDefaults = JSONObject(),
            epub = EpubConfigExportSnapshot(
                selectedAutomationChainId = "chain-1",
                toolPresets = org.json.JSONArray(),
                automationChainGroups = listOf("default"),
                automationChains = org.json.JSONArray(),
                textReplacePresets = org.json.JSONArray()
            ),
            txt = TxtConfigExportSnapshot(
                bookTitleRules = "book",
                chapterRules = "chapter",
                purifyRules = "purify",
                chapterHints = TxtChapterHintsConfigSnapshot(
                    wordCountHintsEnabled = true,
                    shortHintEnabled = true,
                    longHintEnabled = false,
                    shortThreshold = 800,
                    longThreshold = 9000,
                    txtChapterHintMode = TXT_CHAPTER_HINT_MODE_AUTO
                ),
                textReplacePresets = org.json.JSONArray()
            )
        )

        assertEquals(CONFIG_SCHEMA, root.getString("schema"))
        assertEquals(1, root.getInt("version"))
        assertEquals(123L, root.getLong("exportedAt"))
        assertTrue(isEtEditorConfigRoot(root, CONFIG_SCHEMA))
        val settings = root.getJSONObject("settings")
        assertFalse(settings.has("epubLongPressSplitChapter"))
        assertFalse(settings.has("txtSupplementLongPressMode"))
        assertEquals(TXT_CHAPTER_HINT_MODE_AUTO, settings.getString("txtChapterHintMode"))
        val chapterHints = root.getJSONObject("txt").getJSONObject("chapterHints")
        assertTrue(chapterHints.getBoolean("wordCountHintsEnabled"))
        assertEquals(TXT_CHAPTER_HINT_MODE_AUTO, chapterHints.getString("mode"))
        assertEquals(800, chapterHints.getInt("shortThreshold"))
        assertEquals(9000, chapterHints.getInt("longThreshold"))
    }

    @Test
    fun parseSettingsConfigImportFiltersInvalidModesAndClampsThresholds() {
        val current = settingsSnapshot()
        val result = parseSettingsConfigImport(
            settings = JSONObject()
                .put("epubLeftPanelMode", "invalid-left")
                .put("epubRightPanelMode", "log")
                .put("txtRightPanelMode", "invalid-right")
                .put("txtShortChapterThreshold", -20)
                .put("txtLongChapterThreshold", 5000)
                .put("txtDoubleTapEdit", false),
            current = current,
            prefKeys = prefKeys(),
            leftPanelModes = setOf("catalog", "tools"),
            rightPanelModes = setOf("preview", "log"),
            txtRightPanelModes = setOf("preview", "search")
        )

        assertEquals("catalog", result.snapshot.epubLeftPanelMode)
        assertEquals("log", result.snapshot.epubRightPanelMode)
        assertEquals("preview", result.snapshot.txtRightPanelMode)
        assertEquals(0, result.snapshot.txtShortChapterThreshold)
        assertEquals(5000, result.snapshot.txtLongChapterThreshold)
        assertFalse(result.snapshot.txtDoubleTapEdit)
        assertEquals(6, result.preferences.size)
        assertTrue(result.txtCatalogChanged)
    }

    @Test
    fun parseSettingsConfigImportMarksTxtCatalogChangedWhenThresholdChangesWithHintsAlwaysOn() {
        val current = settingsSnapshot().copy(
            txtChapterWordCountHintsEnabled = false,
            txtShortChapterThreshold = 1000
        )
        val result = parseSettingsConfigImport(
            settings = JSONObject()
                .put("txtShortChapterThreshold", 2000),
            current = current,
            prefKeys = prefKeys(),
            leftPanelModes = setOf("catalog", "tools"),
            rightPanelModes = setOf("preview", "log"),
            txtRightPanelModes = setOf("preview", "search")
        )

        assertEquals(2000, result.snapshot.txtShortChapterThreshold)
        assertTrue(result.txtCatalogChanged)
    }

    @Test
    fun parseSettingsConfigImportOnlyRecordsPresentPreferenceKeys() {
        val result = parseSettingsConfigImport(
            settings = JSONObject()
                .put("leftRailExpanded", true),
            current = settingsSnapshot(),
            prefKeys = prefKeys(),
            leftPanelModes = setOf("catalog", "tools"),
            rightPanelModes = setOf("preview", "log"),
            txtRightPanelModes = setOf("preview", "search")
        )

        assertEquals(
            listOf(SettingsPreferenceValue.BooleanValue("leftRailExpanded", true)),
            result.preferences
        )
        assertEquals(true, result.snapshot.leftRailExpanded)
        assertFalse(result.txtCatalogChanged)
    }

    @Test
    fun parseSettingsConfigImportRecordsCurrentValueForInvalidPresentMode() {
        val result = parseSettingsConfigImport(
            settings = JSONObject()
                .put("epubRightPanelMode", "invalid"),
            current = settingsSnapshot().copy(epubRightPanelMode = "preview"),
            prefKeys = prefKeys(),
            leftPanelModes = setOf("catalog", "tools"),
            rightPanelModes = setOf("preview", "log"),
            txtRightPanelModes = setOf("preview", "search")
        )

        assertEquals("preview", result.snapshot.epubRightPanelMode)
        assertEquals(
            listOf(SettingsPreferenceValue.StringValue("epubRightPanelMode", "preview")),
            result.preferences
        )
        assertFalse(result.txtCatalogChanged)
    }

    @Test
    fun parseSettingsConfigImportForcesTxtChapterWordCountHintsOnWithoutCatalogChange() {
        val result = parseSettingsConfigImport(
            settings = JSONObject()
                .put("txtChapterWordCountHintsEnabled", false),
            current = settingsSnapshot().copy(
                txtChapterWordCountHintsEnabled = false,
                txtShortChapterHintEnabled = true,
                txtLongChapterHintEnabled = true,
                txtShortChapterThreshold = 800,
                txtLongChapterThreshold = 8000
            ),
            prefKeys = prefKeys(),
            leftPanelModes = setOf("catalog", "tools"),
            rightPanelModes = setOf("preview", "log"),
            txtRightPanelModes = setOf("preview", "search")
        )

        assertEquals(true, result.snapshot.txtChapterWordCountHintsEnabled)
        assertEquals(
            listOf(SettingsPreferenceValue.BooleanValue("txtChapterWordCountHintsEnabled", true)),
            result.preferences
        )
        assertFalse(result.txtCatalogChanged)
    }

    @Test
    fun parseSettingsConfigImportRecordsTxtChapterHintMode() {
        val result = parseSettingsConfigImport(
            settings = JSONObject()
                .put("txtChapterHintMode", TXT_CHAPTER_HINT_MODE_MANUAL),
            current = settingsSnapshot(),
            prefKeys = prefKeys(),
            leftPanelModes = setOf("catalog", "tools"),
            rightPanelModes = setOf("preview", "log"),
            txtRightPanelModes = setOf("preview", "search")
        )

        assertEquals(TXT_CHAPTER_HINT_MODE_MANUAL, result.snapshot.txtChapterHintMode)
        assertEquals(
            listOf(SettingsPreferenceValue.StringValue("txtChapterHintMode", TXT_CHAPTER_HINT_MODE_MANUAL)),
            result.preferences
        )
        assertTrue(result.txtCatalogChanged)
    }

    @Test
    fun parseTxtConfigImportCollectsRulesAndPresets() {
        val preset = EditorTool(
            id = "preset-1",
            name = "Replace",
            toolId = "text_replace"
        )

        val result = parseTxtConfigImport(
            txtConfig = JSONObject()
                .put("bookTitleRules", "book-title-rules")
                .put("purifyRules", "purify-rules")
                .put("textReplacePresets", JSONArray().put(JSONObject())),
            parseTextReplacePresets = { listOf(preset) }
        )

        assertEquals("book-title-rules", result.bookTitleRules)
        assertEquals("purify-rules", result.purifyRules)
        assertEquals(listOf(preset), result.textReplacePresets)
        assertTrue(result.rulesChanged)
        assertTrue(result.importedMessages.any { it.contains("TXT 书名规则") })
        assertTrue(result.importedMessages.any { it.contains("TXT 替换预设 1 条") })
    }

    @Test
    fun parseTxtConfigImportNormalizesChapterRulesToCurrentTabFormat() {
        val result = parseTxtConfigImport(
            txtConfig = JSONObject()
                .put("chapterRules", "# 章节\t^第(\\d+)章\t第{index}章\n^番外.*$"),
            parseTextReplacePresets = { error("text replace presets should not be parsed") }
        )

        assertEquals(
            "章节\t^第(\\d+)章\t第{index}章\n规则 2\t^番外.*$\t",
            result.chapterRules
        )
        assertTrue(result.rulesChanged)
        assertEquals(listOf("TXT 目录规则"), result.importedMessages)
    }

    @Test
    fun parseTxtConfigImportCollectsChapterHintsAndClampsThresholds() {
        val result = parseTxtConfigImport(
            txtConfig = JSONObject()
                .put(
                    "chapterHints",
                    JSONObject()
                        .put("wordCountHintsEnabled", true)
                        .put("shortHintEnabled", false)
                        .put("longHintEnabled", true)
                        .put("shortThreshold", -10)
                        .put("longThreshold", 12000)
                        .put("mode", TXT_CHAPTER_HINT_MODE_MANUAL)
                ),
            parseTextReplacePresets = { error("text replace presets should not be parsed") }
        )

        val hints = result.chapterHints
        assertEquals(true, hints?.wordCountHintsEnabled)
        assertEquals(false, hints?.shortHintEnabled)
        assertEquals(true, hints?.longHintEnabled)
        assertEquals(0, hints?.shortThreshold)
        assertEquals(12000, hints?.longThreshold)
        assertEquals(TXT_CHAPTER_HINT_MODE_MANUAL, hints?.txtChapterHintMode)
        assertEquals(listOf("TXT 长短章提示"), result.importedMessages)
    }

    @Test
    fun parseTxtConfigImportLeavesMissingSectionsUnset() {
        val result = parseTxtConfigImport(
            txtConfig = JSONObject(),
            parseTextReplacePresets = { error("text replace presets should not be parsed") }
        )

        assertNull(result.bookTitleRules)
        assertNull(result.chapterRules)
        assertNull(result.purifyRules)
        assertNull(result.chapterHints)
        assertNull(result.textReplacePresets)
        assertFalse(result.rulesChanged)
        assertEquals(emptyList<String>(), result.importedMessages)
    }

    @Test
    fun parseTxtConfigImportIgnoresMalformedTextReplacePresetSection() {
        val result = parseTxtConfigImport(
            txtConfig = JSONObject()
                .put("textReplacePresets", "not-an-array"),
            parseTextReplacePresets = { error("text replace presets should not be parsed") }
        )

        assertNull(result.textReplacePresets)
        assertFalse(result.rulesChanged)
        assertEquals(emptyList<String>(), result.importedMessages)
    }

    @Test
    fun parseEpubConfigImportFiltersToolsAndKeepsAutomationSelection() {
        val allowedTool = EditorTool(
            id = "preset-1",
            name = "Fetch",
            toolId = "fetch_info"
        )
        val disallowedTool = EditorTool(
            id = "preset-2",
            name = "Unknown",
            toolId = "unknown_tool"
        )
        val chain = AutomationChain(
            id = "chain-1",
            name = "Chain",
            group = "default",
            steps = emptyList()
        )
        val replacePreset = EditorTool(
            id = "replace-1",
            name = "Replace",
            toolId = "text_replace"
        )

        val result = parseEpubConfigImport(
            epubConfig = JSONObject()
                .put("toolPresets", JSONArray())
                .put("automationChainGroups", JSONArray().put("default"))
                .put("automationChains", JSONArray())
                .put("selectedAutomationChainId", "chain-1")
                .put("textReplacePresets", JSONArray()),
            allowedToolIds = setOf("fetch_info"),
            parseToolPresets = { listOf(allowedTool, disallowedTool) },
            parseAutomationChains = { listOf(chain) },
            parseTextReplacePresets = { listOf(replacePreset) }
        )

        assertEquals(listOf(allowedTool), result.toolPresets)
        assertEquals(listOf(chain), result.automationChains?.chains)
        assertEquals(listOf("default"), result.automationChains?.groups)
        assertEquals("chain-1", result.automationChains?.selectedAutomationChainId)
        assertEquals(listOf(replacePreset), result.textReplacePresets)
    }

    @Test
    fun parseEpubConfigImportIgnoresAutomationGroupsAndSelectionWithoutChainArray() {
        var parserReceivedMissingChainArray = false

        val result = parseEpubConfigImport(
            epubConfig = JSONObject()
                .put("automationChainGroups", JSONArray().put("default"))
                .put("selectedAutomationChainId", "chain-1")
                .put("automationChains", "not-an-array"),
            allowedToolIds = setOf("fetch_info"),
            parseToolPresets = { error("tool presets should not be parsed") },
            parseAutomationChains = { array ->
                parserReceivedMissingChainArray = array == null
                null
            },
            parseTextReplacePresets = { error("text replace presets should not be parsed") }
        )

        assertTrue(parserReceivedMissingChainArray)
        assertNull(result.toolPresets)
        assertNull(result.automationChains)
        assertNull(result.textReplacePresets)
    }

    @Test
    fun parseEpubConfigImportLeavesAbsentSectionsUnset() {
        val result = parseEpubConfigImport(
            epubConfig = JSONObject(),
            allowedToolIds = setOf("fetch_info"),
            parseToolPresets = { error("tool presets should not be parsed") },
            parseAutomationChains = { null },
            parseTextReplacePresets = { error("text replace presets should not be parsed") }
        )

        assertNull(result.toolPresets)
        assertNull(result.automationChains)
        assertNull(result.textReplacePresets)
    }

    @Test
    fun parseEpubConfigImportIgnoresMalformedTextReplacePresetSection() {
        val result = parseEpubConfigImport(
            epubConfig = JSONObject()
                .put("textReplacePresets", "not-an-array"),
            allowedToolIds = setOf("fetch_info"),
            parseToolPresets = { error("tool presets should not be parsed") },
            parseAutomationChains = { null },
            parseTextReplacePresets = { error("text replace presets should not be parsed") }
        )

        assertNull(result.toolPresets)
        assertNull(result.automationChains)
        assertNull(result.textReplacePresets)
    }

    @Test
    fun jsonObjectToStringMapKeepsOnlyNonBlankStringValues() {
        val map = JSONObject()
            .put("name", "Book")
            .put("empty", "")
            .put("blank", " ")
            .put("number", 12)
            .toStringMap()

        assertEquals(mapOf("name" to "Book", "number" to "12"), map)
        assertEquals(emptyMap<String, String>(), (null as JSONObject?).toStringMap())
    }

    private fun settingsSnapshot(): SettingsConfigSnapshot {
        return SettingsConfigSnapshot(
            leftRailExpanded = false,
            epubDoubleTapEdit = true,
            epubLeftPanelMode = "catalog",
            epubRightPanelMode = "preview",
            txtLeftPanelMode = "catalog",
            txtRightPanelMode = "preview",
            txtDoubleTapEdit = true,
            txtDoubleTapTitleEdit = true,
            txtShortChapterThreshold = 1000,
            txtLongChapterThreshold = 10000,
            txtChapterWordCountHintsEnabled = true,
            txtShortChapterHintEnabled = true,
            txtLongChapterHintEnabled = false,
            txtChapterHintMode = TXT_CHAPTER_HINT_MODE_AUTO
        )
    }

    private fun prefKeys(): SettingsConfigPreferenceKeys {
        return SettingsConfigPreferenceKeys(
            leftRailExpanded = "leftRailExpanded",
            epubDoubleTapEdit = "epubDoubleTapEdit",
            epubLeftPanelMode = "epubLeftPanelMode",
            epubRightPanelMode = "epubRightPanelMode",
            txtLeftPanelMode = "txtLeftPanelMode",
            txtRightPanelMode = "txtRightPanelMode",
            txtDoubleTapEdit = "txtDoubleTapEdit",
            txtDoubleTapTitleEdit = "txtDoubleTapTitleEdit",
            txtShortChapterThreshold = "txtShortChapterThreshold",
            txtLongChapterThreshold = "txtLongChapterThreshold",
            txtChapterWordCountHintsEnabled = "txtChapterWordCountHintsEnabled",
            txtShortChapterHintEnabled = "txtShortChapterHintEnabled",
            txtLongChapterHintEnabled = "txtLongChapterHintEnabled",
            txtChapterHintMode = "txtChapterHintMode"
        )
    }

    private companion object {
        const val CONFIG_SCHEMA = "et_editor_config"
    }
}

package com.eteditor

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ToolConfigJsonUtilsTest {
    @Test
    fun exportToolParameterOverridesDropsSensitiveKeysWhenRequested() {
        val exported = exportToolParameterOverrides(
            toolId = "fetch_info",
            overrides = mapOf(
                FETCH_INFO_PARAM_QUERY to "Book",
                FETCH_INFO_PARAM_AUTH_COOKIE to "secret-cookie"
            ),
            includeSensitive = false,
            sensitiveParameterKeys = sensitiveKeys,
            cleanParameterOverrides = ::keepAllOverrides
        )

        assertEquals(mapOf(FETCH_INFO_PARAM_QUERY to "Book"), exported)
    }

    @Test
    fun exportToolParameterOverridesKeepsSensitiveKeysWhenExplicitlyRequested() {
        val exported = exportToolParameterOverrides(
            toolId = "fetch_info",
            overrides = mapOf(
                FETCH_INFO_PARAM_QUERY to "Book",
                FETCH_INFO_PARAM_AUTH_COOKIE to "secret-cookie"
            ),
            includeSensitive = true,
            sensitiveParameterKeys = sensitiveKeys,
            cleanParameterOverrides = ::keepAllOverrides
        )

        assertEquals("secret-cookie", exported[FETCH_INFO_PARAM_AUTH_COOKIE])
        assertEquals("Book", exported[FETCH_INFO_PARAM_QUERY])
    }

    @Test
    fun parseBuiltInDefaultsFiltersSensitiveAndUnknownTools() {
        val root = JSONObject()
            .put(
                "fetch_info",
                JSONObject()
                    .put(FETCH_INFO_PARAM_QUERY, "Book")
                    .put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
            )
            .put(
                "unknown_tool",
                JSONObject().put(FETCH_INFO_PARAM_QUERY, "Ignored")
            )

        val parsed = parseBuiltInToolDefaultOverridesJson(
            root = root,
            includeSensitive = false,
            allowedToolIds = setOf("fetch_info"),
            sensitiveParameterKeys = sensitiveKeys,
            cleanOverridesForSave = ::keepAllOverrides
        )

        assertEquals(
            mapOf("fetch_info" to mapOf(FETCH_INFO_PARAM_QUERY to "Book")),
            parsed
        )
    }

    @Test
    fun builtInDefaultsJsonOmitsToolsLeftEmptyAfterCleaningAndSensitiveFiltering() {
        val json = builtInToolDefaultsToJson(
            savedDefaults = mapOf(
                "fetch_info" to mapOf(FETCH_INFO_PARAM_AUTH_COOKIE to "secret-cookie"),
                "text_replace" to mapOf(
                    TEXT_REPLACE_PARAM_FIND to "needle",
                    "drop" to "ignored"
                )
            ),
            includeSensitive = false,
            sensitiveParameterKeys = sensitiveKeys,
            cleanOverridesForSave = { _, overrides -> overrides - "drop" }
        )

        assertFalse(json.has("fetch_info"))
        assertEquals(
            "needle",
            json.getJSONObject("text_replace").getString(TEXT_REPLACE_PARAM_FIND)
        )
    }

    @Test
    fun parseBuiltInDefaultsStoreJsonHandlesBlankMalformedAndCleansValues() {
        assertEquals(
            emptyMap<String, Map<String, String>>(),
            parseBuiltInToolDefaultsStoreJson(
                rawDefaults = "",
                allowedToolIds = setOf("fetch_info"),
                sensitiveParameterKeys = sensitiveKeys,
                cleanOverridesForSave = ::keepAllOverrides
            )
        )
        assertEquals(
            emptyMap<String, Map<String, String>>(),
            parseBuiltInToolDefaultsStoreJson(
                rawDefaults = "{",
                allowedToolIds = setOf("fetch_info"),
                sensitiveParameterKeys = sensitiveKeys,
                cleanOverridesForSave = ::keepAllOverrides
            )
        )

        val raw = JSONObject()
            .put(
                "fetch_info",
                JSONObject()
                    .put(FETCH_INFO_PARAM_QUERY, "Book")
                    .put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
                    .put("drop", "ignored")
            )
            .put("unknown_tool", JSONObject().put(FETCH_INFO_PARAM_QUERY, "Ignored"))
            .toString()

        val parsed = parseBuiltInToolDefaultsStoreJson(
            rawDefaults = raw,
            allowedToolIds = setOf("fetch_info"),
            sensitiveParameterKeys = sensitiveKeys,
            cleanOverridesForSave = { _, overrides -> overrides - "drop" }
        )

        assertEquals(
            mapOf("fetch_info" to mapOf(FETCH_INFO_PARAM_QUERY to "Book")),
            parsed
        )
    }

    @Test
    fun editorToolJsonExportFiltersSensitiveParameters() {
        val tools = listOf(
            EditorTool(
                id = "preset-1",
                name = "Fetch",
                toolId = "fetch_info",
                parameterOverrides = mapOf(
                    FETCH_INFO_PARAM_QUERY to "Book",
                    FETCH_INFO_PARAM_AUTH_COOKIE to "secret-cookie"
                )
            )
        )

        val json = editorToolListStoreJson(
            tools = tools,
            includeSensitive = false,
            exportParameterOverrides = ::exportOverridesForTest
        )
        val params = json.getJSONObject(0).getJSONObject("parameterOverrides")

        assertEquals("Book", params.getString(FETCH_INFO_PARAM_QUERY))
        assertFalse(params.has(FETCH_INFO_PARAM_AUTH_COOKIE))
    }

    @Test
    fun parseEditorToolsFiltersSensitiveParametersOnImport() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("id", "preset-1")
                    .put("name", "Fetch")
                    .put("toolId", "fetch_info")
                    .put(
                        "parameterOverrides",
                        JSONObject()
                            .put(FETCH_INFO_PARAM_QUERY, "Book")
                            .put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
                    )
            )

        val imported = parseEditorToolsJson(
            array = json,
            allowedToolIds = setOf("fetch_info"),
            includeSensitive = false,
            exportParameterOverrides = ::exportOverridesForTest
        ).orEmpty()

        assertEquals(
            mapOf(FETCH_INFO_PARAM_QUERY to "Book"),
            imported.single().parameterOverrides
        )
    }

    @Test
    fun parseEditorToolsSkipsInvalidItemsAndAppliesNormalizer() {
        val json = JSONArray()
            .put("not-an-object")
            .put(
                JSONObject()
                    .put("name", "Missing Id")
                    .put("toolId", "fetch_info")
            )
            .put(
                JSONObject()
                    .put("id", "missing-name")
                    .put("toolId", "fetch_info")
            )
            .put(
                JSONObject()
                    .put("id", "unknown-tool")
                    .put("name", "Unknown")
                    .put("toolId", "unknown_tool")
            )
            .put(
                JSONObject()
                    .put("id", "drop")
                    .put("name", "Drop")
                    .put("toolId", "fetch_info")
            )
            .put(
                JSONObject()
                    .put("id", "keep")
                    .put("name", "Keep")
                    .put("group", " 资料 ")
                    .put("toolId", "fetch_info")
            )

        val imported = parseEditorToolsJson(
            array = json,
            allowedToolIds = setOf("fetch_info"),
            includeSensitive = false,
            exportParameterOverrides = ::exportOverridesForTest,
            normalize = { tool -> tool.takeIf { it.name == "Keep" } }
        ).orEmpty()

        assertEquals(listOf("keep"), imported.map { it.id })
        assertEquals("资料", imported.single().group)
    }

    @Test
    fun parseEditorToolsKeepsSensitiveParametersWhenImportAllowsThem() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("id", "preset-1")
                    .put("name", "Fetch")
                    .put("toolId", "fetch_info")
                    .put(
                        "parameterOverrides",
                        JSONObject()
                            .put(FETCH_INFO_PARAM_QUERY, "Book")
                            .put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
                    )
            )

        val imported = parseEditorToolsJson(
            array = json,
            allowedToolIds = setOf("fetch_info"),
            includeSensitive = true,
            exportParameterOverrides = ::exportOverridesForTest
        ).orEmpty()

        assertEquals(
            mapOf(
                FETCH_INFO_PARAM_QUERY to "Book",
                FETCH_INFO_PARAM_AUTH_COOKIE to "secret-cookie"
            ),
            imported.single().parameterOverrides
        )
    }

    @Test
    fun parsePersistedEditorToolListsParsesStoresIndependentlyAndAppliesPresetNormalizers() {
        val rawTools = JSONArray()
            .put(
                JSONObject()
                    .put("id", "tool-1")
                    .put("name", "Drop")
                    .put("group", " 常用 ")
                    .put("toolId", "fetch_info")
                    .put(
                        "parameterOverrides",
                        JSONObject()
                            .put(FETCH_INFO_PARAM_QUERY, "Book")
                            .put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
                    )
            )
            .toString()
        val rawTxtPresets = JSONArray()
            .put(
                JSONObject()
                    .put("id", "txt-1")
                    .put("name", "Drop")
                    .put("toolId", "text_replace")
                    .put("parameterOverrides", JSONObject().put(TEXT_REPLACE_PARAM_FIND, "drop"))
            )
            .put(
                JSONObject()
                    .put("id", "txt-2")
                    .put("name", "Keep")
                    .put("toolId", "text_replace")
                    .put("parameterOverrides", JSONObject().put(TEXT_REPLACE_PARAM_FIND, "needle"))
            )
            .toString()

        val loaded = parsePersistedEditorToolLists(
            rawTools = rawTools,
            rawTxtPresets = rawTxtPresets,
            rawEpubPresets = "{",
            allowedToolIds = setOf("fetch_info", "text_replace"),
            exportParameterOverrides = ::exportOverridesForTest,
            normalizeTxtPreset = { tool -> tool.takeIf { it.name == "Keep" }?.copy(name = "TXT ${tool.name}") },
            normalizeEpubPreset = { tool -> tool.copy(name = "EPUB ${tool.name}") }
        )

        assertEquals(listOf("tool-1"), loaded.tools.map { it.id })
        assertEquals("Drop", loaded.tools.single().name)
        assertEquals("常用", loaded.tools.single().group)
        assertEquals(mapOf(FETCH_INFO_PARAM_QUERY to "Book"), loaded.tools.single().parameterOverrides)
        assertEquals(listOf("txt-2"), loaded.txtTextReplacePresets.map { it.id })
        assertEquals("TXT Keep", loaded.txtTextReplacePresets.single().name)
        assertEquals(emptyList<EditorTool>(), loaded.epubTextReplacePresets)
    }

    @Test
    fun storeJsonSensitiveDetectorsFindSensitiveParameters() {
        val defaults = JSONObject()
            .put(
                "fetch_info",
                JSONObject().put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
            )
        val tools = JSONArray()
            .put(
                JSONObject()
                    .put(
                        "parameterOverrides",
                        JSONObject().put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
                    )
            )

        assertEquals(
            true,
            builtInToolDefaultsStoreJsonContainsSensitiveParameters(
                rawDefaults = defaults.toString(),
                sensitiveParameterKeys = sensitiveKeys
            )
        )
        assertEquals(
            true,
            editorToolListStoreJsonContainsSensitiveParameters(
                rawTools = tools.toString(),
                sensitiveParameterKeys = sensitiveKeys
            )
        )
        assertEquals(
            false,
            editorToolListStoreJsonContainsSensitiveParameters(
                rawTools = tools.toString(),
                sensitiveParameterKeys = setOf("other")
            )
        )
    }

    @Test
    fun storeJsonSensitiveDetectorsReturnFalseForMalformedOrEmptyInputs() {
        assertFalse(
            builtInToolDefaultsStoreJsonContainsSensitiveParameters(
                rawDefaults = "{",
                sensitiveParameterKeys = sensitiveKeys
            )
        )
        assertFalse(
            builtInToolDefaultsStoreJsonContainsSensitiveParameters(
                rawDefaults = """{"fetch_info":{"${FETCH_INFO_PARAM_AUTH_COOKIE}":"secret-cookie"}}""",
                sensitiveParameterKeys = emptySet()
            )
        )
        assertFalse(
            editorToolListStoreJsonContainsSensitiveParameters(
                rawTools = "[",
                sensitiveParameterKeys = sensitiveKeys
            )
        )
        assertFalse(
            editorToolListStoreJsonContainsSensitiveParameters(
                rawTools = "",
                sensitiveParameterKeys = sensitiveKeys
            )
        )
    }

    @Test
    fun withoutSensitiveParametersDropsAuthCookiesBeforeSavingDefaults() {
        val cleaned = mapOf(
            FETCH_INFO_PARAM_QUERY to "Book",
            FETCH_INFO_PARAM_AUTH_COOKIE to "secret-cookie",
            INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE to "sosad-cookie"
        ).withoutSensitiveParameters()

        assertEquals(mapOf(FETCH_INFO_PARAM_QUERY to "Book"), cleaned)
    }

    private fun keepAllOverrides(
        @Suppress("UNUSED_PARAMETER") toolId: String,
        overrides: Map<String, String>
    ): Map<String, String> = overrides

    private fun exportOverridesForTest(
        toolId: String,
        overrides: Map<String, String>,
        includeSensitive: Boolean
    ): Map<String, String> {
        return exportToolParameterOverrides(
            toolId = toolId,
            overrides = overrides,
            includeSensitive = includeSensitive,
            sensitiveParameterKeys = sensitiveKeys,
            cleanParameterOverrides = ::keepAllOverrides
        )
    }

    private companion object {
        val sensitiveKeys = setOf(
            FETCH_INFO_PARAM_AUTH_COOKIE,
            INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE
        )
    }
}

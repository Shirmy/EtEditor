package com.eteditor

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationChainJsonUtilsTest {
    @Test
    fun automationChainExportFiltersSensitiveStepParameters() {
        val chains = listOf(
            AutomationChain(
                id = "chain-1",
                name = "Chain",
                steps = listOf(
                    AutomationStep(
                        id = "step-1",
                        name = "Fetch",
                        toolId = "fetch_info",
                        parameterOverrides = mapOf(
                            FETCH_INFO_PARAM_QUERY to "Book",
                            FETCH_INFO_PARAM_AUTH_COOKIE to "secret-cookie"
                        )
                    )
                )
            )
        )

        val json = automationChainsToJsonArray(
            chains = chains,
            includeSensitive = false,
            sourceToolForStep = { null },
            exportParameterOverrides = ::exportOverridesForTest
        )
        val params = json
            .getJSONObject(0)
            .getJSONArray("steps")
            .getJSONObject(0)
            .getJSONObject("parameterOverrides")

        assertEquals("Book", params.getString(FETCH_INFO_PARAM_QUERY))
        assertFalse(params.has(FETCH_INFO_PARAM_AUTH_COOKIE))
    }

    @Test
    fun automationChainExportUsesSourcePresetParametersWhenPresent() {
        val chains = listOf(
            AutomationChain(
                id = "chain-1",
                name = "Chain",
                steps = listOf(
                    AutomationStep(
                        id = "step-1",
                        name = "Stale Step",
                        toolId = "fetch_info",
                        presetId = "preset-1",
                        parameterOverrides = mapOf(FETCH_INFO_PARAM_QUERY to "Stale")
                    )
                )
            )
        )
        val sourcePreset = EditorTool(
            id = "preset-1",
            name = "Preset Fetch",
            toolId = "fetch_info",
            parameterOverrides = mapOf(
                FETCH_INFO_PARAM_QUERY to "Preset Book",
                FETCH_INFO_PARAM_AUTH_COOKIE to "secret-cookie"
            )
        )

        val json = automationChainsToJsonArray(
            chains = chains,
            includeSensitive = false,
            sourceToolForStep = { step -> sourcePreset.takeIf { it.id == step.presetId } },
            exportParameterOverrides = ::exportOverridesForTest
        )
        val stepJson = json
            .getJSONObject(0)
            .getJSONArray("steps")
            .getJSONObject(0)
        val params = stepJson.getJSONObject("parameterOverrides")

        assertEquals("Preset Fetch", stepJson.getString("name"))
        assertEquals("fetch_info", stepJson.getString("toolId"))
        assertEquals("preset-1", stepJson.getString("presetId"))
        assertEquals("Preset Book", params.getString(FETCH_INFO_PARAM_QUERY))
        assertFalse(params.has(FETCH_INFO_PARAM_AUTH_COOKIE))
    }

    @Test
    fun automationChainsStoreJsonWritesSelectionGroupsAndFilteredChains() {
        val chains = listOf(
            AutomationChain(
                id = "chain-1",
                name = "Chain",
                group = "资料",
                steps = listOf(
                    AutomationStep(
                        id = "step-1",
                        name = "Fetch",
                        toolId = "fetch_info",
                        parameterOverrides = mapOf(
                            FETCH_INFO_PARAM_QUERY to "Book",
                            FETCH_INFO_PARAM_AUTH_COOKIE to "secret-cookie"
                        )
                    )
                )
            )
        )

        val store = automationChainsStoreJson(
            selectedAutomationChainId = "chain-1",
            groups = listOf("资料", "归档"),
            chains = chains,
            includeSensitive = false,
            sourceToolForStep = { null },
            exportParameterOverrides = ::exportOverridesForTest
        )

        assertEquals("chain-1", store.getString("selectedAutomationChainId"))
        assertEquals("资料", store.getJSONArray("automationChainGroups").getString(0))
        assertEquals("归档", store.getJSONArray("automationChainGroups").getString(1))
        val chainJson = store.getJSONArray("automationChains").getJSONObject(0)
        val stepParams = chainJson
            .getJSONArray("steps")
            .getJSONObject(0)
            .getJSONObject("parameterOverrides")
        assertEquals("资料", chainJson.getString("group"))
        assertEquals("Book", stepParams.getString(FETCH_INFO_PARAM_QUERY))
        assertFalse(stepParams.has(FETCH_INFO_PARAM_AUTH_COOKIE))
    }

    @Test
    fun parseAutomationChainsFiltersSensitiveParametersAndUnknownTools() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("id", "chain-1")
                    .put("name", "Chain")
                    .put(
                        "steps",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("id", "step-1")
                                    .put("name", "Fetch")
                                    .put("toolId", "fetch_info")
                                    .put(
                                        "parameterOverrides",
                                        JSONObject()
                                            .put(FETCH_INFO_PARAM_QUERY, "Book")
                                            .put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
                                    )
                            )
                            .put(
                                JSONObject()
                                    .put("id", "step-2")
                                    .put("name", "Unknown")
                                    .put("toolId", "unknown_tool")
                            )
                    )
            )

        val chains = parseAutomationChainsJson(
            array = json,
            includeSensitive = false,
            allowedToolIds = setOf("fetch_info"),
            exportParameterOverrides = ::exportOverridesForTest
        ).orEmpty()

        val steps = chains.single().steps
        assertEquals(1, steps.size)
        assertEquals(
            mapOf(FETCH_INFO_PARAM_QUERY to "Book"),
            steps.single().parameterOverrides
        )
    }

    @Test
    fun parseAutomationChainsSkipsMalformedItemsAndUsesRawIndexFallbackIds() {
        val json = JSONArray()
            .put("not-a-chain")
            .put(
                JSONObject()
                    .put("id", "")
                    .put("name", "Chain")
                    .put("group", " 资料 ")
                    .put(
                        "steps",
                        JSONArray()
                            .put("not-a-step")
                            .put(
                                JSONObject()
                                    .put("id", "")
                                    .put("name", "Unknown")
                                    .put("toolId", "unknown_tool")
                            )
                            .put(
                                JSONObject()
                                    .put("id", "")
                                    .put("name", "Fetch")
                                    .put("toolId", "fetch_info")
                                    .put("presetId", " preset-1 ")
                                    .put(
                                        "parameterOverrides",
                                        JSONObject()
                                            .put(FETCH_INFO_PARAM_QUERY, "Book")
                                            .put("blank", "")
                                    )
                            )
                    )
            )

        val chains = parseAutomationChainsJson(
            array = json,
            includeSensitive = false,
            allowedToolIds = setOf("fetch_info"),
            exportParameterOverrides = ::exportOverridesForTest
        ).orEmpty()

        val chain = chains.single()
        val step = chain.steps.single()
        assertEquals("chain-2", chain.id)
        assertEquals("资料", chain.group)
        assertEquals("step-3", step.id)
        assertEquals("preset-1", step.presetId)
        assertEquals(mapOf(FETCH_INFO_PARAM_QUERY to "Book"), step.parameterOverrides)
    }

    @Test
    fun parseAutomationChainsStoreJsonHandlesInvalidInputAndMissingFields() {
        assertNull(
            parseAutomationChainsStoreJson(
                rawChains = "",
                allowedToolIds = setOf("fetch_info"),
                exportParameterOverrides = ::exportOverridesForTest
            )
        )
        assertNull(
            parseAutomationChainsStoreJson(
                rawChains = "{",
                allowedToolIds = setOf("fetch_info"),
                exportParameterOverrides = ::exportOverridesForTest
            )
        )

        val raw = JSONObject()
            .put("selectedAutomationChainId", "chain-2")
            .put(
                "automationChainGroups",
                JSONArray()
                    .put(" 资料 ")
                    .put("未分组")
                    .put("资料")
            )
            .put(
                "automationChains",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("group", " 未分组 ")
                            .put(
                                "steps",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put("toolId", "fetch_info")
                                            .put(
                                                "parameterOverrides",
                                                JSONObject()
                                                    .put(FETCH_INFO_PARAM_QUERY, "Book")
                                                    .put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
                                            )
                                    )
                            )
                    )
            )
            .toString()

        val store = parseAutomationChainsStoreJson(
            rawChains = raw,
            allowedToolIds = setOf("fetch_info"),
            exportParameterOverrides = ::exportOverridesForTest
        )
        val chain = store?.chains?.single()
        val step = chain?.steps?.single()

        assertEquals("chain-2", store?.selectedAutomationChainId)
        assertEquals(listOf("资料", "未分组"), store?.groups)
        assertEquals("chain-1", chain?.id)
        assertEquals("", chain?.name)
        assertEquals("", chain?.group)
        assertEquals("step-1", step?.id)
        assertEquals("", step?.presetId)
        assertEquals(mapOf(FETCH_INFO_PARAM_QUERY to "Book"), step?.parameterOverrides)
    }

    @Test
    fun automationChainsStoreJsonDetectsSensitiveParameters() {
        val root = JSONObject()
            .put(
                "automationChains",
                JSONArray()
                    .put(
                        JSONObject()
                            .put(
                                "steps",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put(
                                                "parameterOverrides",
                                                JSONObject()
                                                    .put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
                                            )
                                    )
                            )
                    )
            )

        assertTrue(
            automationChainsStoreJsonContainsSensitiveParameters(
                rawChains = root.toString(),
                sensitiveParameterKeys = sensitiveKeys
            )
        )
        assertFalse(
            automationChainsStoreJsonContainsSensitiveParameters(
                rawChains = root.toString(),
                sensitiveParameterKeys = setOf("other")
            )
        )
    }

    @Test
    fun automationChainsStoreJsonSensitiveDetectionTreatsUnscannableDataAsClean() {
        val malformedSteps = JSONObject()
            .put(
                "automationChains",
                JSONArray()
                    .put("not-a-chain")
                    .put(
                        JSONObject()
                            .put(
                                "steps",
                                JSONArray()
                                    .put("not-a-step")
                                    .put(
                                        JSONObject()
                                            .put("parameterOverrides", "not-an-object")
                                    )
                            )
                    )
            )
            .toString()
        val sensitiveRoot = JSONObject()
            .put(
                "automationChains",
                JSONArray()
                    .put(
                        JSONObject()
                            .put(
                                "steps",
                                JSONArray()
                                    .put(
                                        JSONObject()
                                            .put(
                                                "parameterOverrides",
                                                JSONObject()
                                                    .put(FETCH_INFO_PARAM_AUTH_COOKIE, "secret-cookie")
                                            )
                                    )
                            )
                    )
            )
            .toString()

        assertFalse(
            automationChainsStoreJsonContainsSensitiveParameters(
                rawChains = "",
                sensitiveParameterKeys = sensitiveKeys
            )
        )
        assertFalse(
            automationChainsStoreJsonContainsSensitiveParameters(
                rawChains = "{",
                sensitiveParameterKeys = sensitiveKeys
            )
        )
        assertFalse(
            automationChainsStoreJsonContainsSensitiveParameters(
                rawChains = JSONObject().put("automationChains", "not-an-array").toString(),
                sensitiveParameterKeys = sensitiveKeys
            )
        )
        assertFalse(
            automationChainsStoreJsonContainsSensitiveParameters(
                rawChains = malformedSteps,
                sensitiveParameterKeys = sensitiveKeys
            )
        )
        assertFalse(
            automationChainsStoreJsonContainsSensitiveParameters(
                rawChains = sensitiveRoot,
                sensitiveParameterKeys = emptySet()
            )
        )
    }

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
            cleanParameterOverrides = { _, cleanOverrides -> cleanOverrides }
        )
    }

    private companion object {
        val sensitiveKeys = setOf(
            FETCH_INFO_PARAM_AUTH_COOKIE,
            INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE
        )
    }
}

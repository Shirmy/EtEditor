package com.eteditor

import org.json.JSONArray
import org.json.JSONObject

internal data class PersistedAutomationChainsStore(
    val selectedAutomationChainId: String,
    val groups: List<String>,
    val chains: List<AutomationChain>
)

internal fun automationChainsToJsonArray(
    chains: List<AutomationChain>,
    includeSensitive: Boolean,
    sourceToolForStep: (AutomationStep) -> EditorTool?,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>
): JSONArray {
    val array = JSONArray()
    chains.forEach { chain ->
        array.put(
            JSONObject()
                .put("id", chain.id)
                .put("name", chain.name)
                .put("group", chain.group)
                .put(
                    "steps",
                    JSONArray().also { steps ->
                        chain.steps.forEach { step ->
                            val sourceTool = sourceToolForStep(step)
                            val exportName = sourceTool?.name ?: step.name
                            val exportToolId = sourceTool?.toolId ?: step.toolId
                            val exportParameters = sourceTool?.parameterOverrides ?: step.parameterOverrides
                            steps.put(
                                JSONObject()
                                    .put("id", step.id)
                                    .put("presetId", step.presetId)
                                    .put("name", exportName)
                                    .put("toolId", exportToolId)
                                    .put(
                                        "parameterOverrides",
                                        JSONObject().also { params ->
                                            exportParameterOverrides(
                                                exportToolId,
                                                exportParameters,
                                                includeSensitive
                                            ).forEach { (key, value) -> params.put(key, value) }
                                        }
                                    )
                            )
                        }
                    }
                )
        )
    }
    return array
}

internal fun automationChainsStoreJson(
    selectedAutomationChainId: String,
    groups: List<String>,
    chains: List<AutomationChain>,
    includeSensitive: Boolean,
    sourceToolForStep: (AutomationStep) -> EditorTool?,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>
): JSONObject {
    return JSONObject()
        .put("selectedAutomationChainId", selectedAutomationChainId)
        .put("automationChainGroups", automationChainGroupsToJsonArray(groups))
        .put(
            "automationChains",
            automationChainsToJsonArray(
                chains = chains,
                includeSensitive = includeSensitive,
                sourceToolForStep = sourceToolForStep,
                exportParameterOverrides = exportParameterOverrides
            )
        )
}

internal fun parseAutomationChainsStoreJson(
    rawChains: String,
    allowedToolIds: Set<String>,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>
): PersistedAutomationChainsStore? {
    if (rawChains.isBlank()) return null
    val root = runCatching { JSONObject(rawChains) }.getOrNull() ?: return null
    return PersistedAutomationChainsStore(
        selectedAutomationChainId = root.optString("selectedAutomationChainId"),
        groups = parseAutomationChainGroups(root.optJSONArray("automationChainGroups")),
        chains = parseAutomationChainsJson(
            array = root.optJSONArray("automationChains"),
            includeSensitive = false,
            allowedToolIds = allowedToolIds,
            exportParameterOverrides = exportParameterOverrides
        ).orEmpty()
    )
}

internal fun parseAutomationChainsJson(
    array: JSONArray?,
    includeSensitive: Boolean,
    allowedToolIds: Set<String>,
    exportParameterOverrides: (String, Map<String, String>, Boolean) -> Map<String, String>
): List<AutomationChain>? {
    if (array == null) return null
    return buildList {
        for (index in 0 until array.length()) {
            val chainJson = array.optJSONObject(index) ?: continue
            val fallbackChainId = "chain-${index + 1}"
            val stepsJson = chainJson.optJSONArray("steps") ?: JSONArray()
            val steps = buildList {
                for (stepIndex in 0 until stepsJson.length()) {
                    val stepJson = stepsJson.optJSONObject(stepIndex) ?: continue
                    val toolId = stepJson.optString("toolId").takeIf { it in allowedToolIds } ?: continue
                    add(
                        AutomationStep(
                            id = stepJson.optString("id").ifBlank { "step-${stepIndex + 1}" },
                            name = stepJson.optString("name"),
                            toolId = toolId,
                            parameterOverrides = exportParameterOverrides(
                                toolId,
                                stepJson.optJSONObject("parameterOverrides").toStringMap(),
                                includeSensitive
                            ),
                            presetId = stepJson.optString("presetId").trim()
                        )
                    )
                }
            }
            add(
                AutomationChain(
                    id = chainJson.optString("id").ifBlank { fallbackChainId },
                    name = chainJson.optString("name"),
                    group = cleanAutomationChainGroup(chainJson.optString("group")),
                    steps = steps
                )
            )
        }
    }
}

internal fun automationChainsStoreJsonContainsSensitiveParameters(
    rawChains: String,
    sensitiveParameterKeys: Set<String>
): Boolean {
    if (rawChains.isBlank() || sensitiveParameterKeys.isEmpty()) return false
    return runCatching {
        val chains = JSONObject(rawChains).optJSONArray("automationChains") ?: return@runCatching false
        for (chainIndex in 0 until chains.length()) {
            val steps = chains.optJSONObject(chainIndex)?.optJSONArray("steps") ?: continue
            for (stepIndex in 0 until steps.length()) {
                val params = steps.optJSONObject(stepIndex)?.optJSONObject("parameterOverrides")
                if (params != null && params.containsAnyKey(sensitiveParameterKeys)) return@runCatching true
            }
        }
        false
    }.getOrElse { false }
}

private fun JSONObject.containsAnyKey(targetKeys: Set<String>): Boolean {
    val iterator = keys()
    while (iterator.hasNext()) {
        if (iterator.next() in targetKeys) return true
    }
    return false
}

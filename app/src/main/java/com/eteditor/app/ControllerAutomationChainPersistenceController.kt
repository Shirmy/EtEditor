package com.eteditor

import org.json.JSONArray

internal fun EditorController.automationChainsToJson(
    chains: List<AutomationChain>,
    includeSensitive: Boolean = false
): JSONArray {
    return automationChainsToJsonArray(
        chains = chains,
        includeSensitive = includeSensitive,
        sourceToolForStep = { step -> automationStepSourceTool(step) },
        exportParameterOverrides = { toolId, overrides, includeSensitive ->
            exportParameterOverrides(toolId, overrides, includeSensitive)
        }
    )
}

internal fun EditorController.parseAutomationChains(
    array: JSONArray?,
    includeSensitive: Boolean = false
): List<AutomationChain>? {
    return parseAutomationChainsJson(
        array = array,
        includeSensitive = includeSensitive,
        allowedToolIds = EPUB_EDITOR_TOOL_IDS,
        exportParameterOverrides = { toolId, overrides, includeSensitive ->
            exportParameterOverrides(toolId, overrides, includeSensitive)
        }
    )
}

internal fun EditorController.migrateAutomationChainsToPresetSteps(
    chains: List<AutomationChain>,
    forceSnapshotPresets: Boolean = false
): Pair<List<AutomationChain>, Boolean> {
    var changed = false
    val tools = editorTools.toMutableList()

    fun findOrCreatePreset(step: AutomationStep): String? {
        val toolId = step.toolId.takeIf { it in EPUB_EDITOR_TOOL_IDS } ?: return null
        val cleanParameters = cleanParameterOverrides(toolId, step.parameterOverrides)
        val cleanName = step.name.ifBlank { toolLabel(toolId) }
        val existing = tools.firstOrNull { tool ->
            tool.toolId == toolId &&
                tool.name == cleanName &&
                tool.parameterOverrides == cleanParameters
        }
        if (existing != null) return existing.id

        val saved = EditorTool(
            id = "tool-${nextEditorToolNumber++}",
            name = cleanName,
            toolId = toolId,
            parameterOverrides = cleanParameters
        )
        tools += saved
        return saved.id
    }

    val migratedChains = chains.map { chain ->
        chain.copy(
            steps = chain.steps.map stepMap@ { step ->
                val presetExists = step.presetId.isNotBlank() && tools.any { it.id == step.presetId }
                if (presetExists && !forceSnapshotPresets) {
                    step
                } else if (step.presetId.isNotBlank() && !forceSnapshotPresets) {
                    step
                } else {
                    val presetId = findOrCreatePreset(step) ?: return@stepMap step
                    val cleanParameters = cleanParameterOverrides(step.toolId, step.parameterOverrides)
                    val cleanName = step.name.ifBlank { toolLabel(step.toolId) }
                    val migrated = step.copy(
                        name = cleanName,
                        parameterOverrides = cleanParameters,
                        presetId = presetId
                    )
                    if (migrated != step) changed = true
                    migrated
                }
            }
        )
    }

    if (tools.size != editorTools.size) {
        editorTools = tools
        syncEditorToolCounter()
        changed = true
    }
    return migratedChains to changed
}

internal fun EditorController.syncAutomationCounters() {
    nextAutomationChainNumber = nextAutomationChainNumberFor(automationChains)
    nextAutomationStepNumber = nextAutomationStepNumberFor(automationChains)
}

internal fun EditorController.loadPersistedAutomationChains() {
    val rawChains = jsonPreferences.readAutomationChains()
    val containsSensitiveParameters = automationChainsStoreJsonContainsSensitiveParameters(
        rawChains = rawChains,
        sensitiveParameterKeys = SENSITIVE_PARAMETER_KEYS
    )
    val persisted = parseAutomationChainsStoreJson(
        rawChains = rawChains,
        allowedToolIds = EDITOR_TOOL_IDS,
        exportParameterOverrides = { toolId, overrides, includeSensitive ->
            exportParameterOverrides(toolId, overrides, includeSensitive)
        }
    ) ?: return
    val chains = persisted.chains
    automationChainGroups = persisted.groups
    if (chains.isEmpty() || chains.isLegacyDefaultAutomationChains()) {
        automationChains = emptyList()
        selectedAutomationChainId = ""
        syncAutomationCounters()
        persistAutomationChains()
        return
    }
    val (migratedChains, migrated) = migrateAutomationChainsToPresetSteps(chains)
    automationChains = migratedChains
    selectedAutomationChainId = persisted.selectedAutomationChainId
        .takeIf { selectedId -> automationChains.any { it.id == selectedId } }
        ?: automationChains.firstOrNull()?.id.orEmpty()
    syncAutomationCounters()
    if (migrated) {
        persistEditorTools()
    }
    if (migrated || containsSensitiveParameters) {
        persistAutomationChains()
    }
}

internal fun EditorController.persistAutomationChains() {
    jsonPreferences.writeAutomationChains(
        automationChainsStoreJson(
            selectedAutomationChainId = selectedAutomationChainId,
            groups = automationChainGroups,
            chains = automationChains,
            includeSensitive = false,
            sourceToolForStep = { step -> automationStepSourceTool(step) },
            exportParameterOverrides = { toolId, overrides, includeSensitive ->
                exportParameterOverrides(toolId, overrides, includeSensitive)
            }
        )
            .toString()
    )
}

internal fun EditorController.updateAutomationChain(updated: AutomationChain) {
    if (draftAutomationChain?.id == updated.id) {
        draftAutomationChain = updated
        return
    }
    automationChains = updateAutomationChainById(automationChains, updated)
    persistAutomationChains()
}

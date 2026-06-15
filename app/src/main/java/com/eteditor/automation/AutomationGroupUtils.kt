package com.eteditor

import org.json.JSONArray

private const val AUTOMATION_UNGROUPED_LABEL = "未分组"

internal data class AutomationGroupEditResult(
    val success: Boolean,
    val groups: List<String> = emptyList(),
    val chains: List<AutomationChain> = emptyList(),
    val groupName: String = "",
    val message: String = ""
)

internal fun cleanAutomationChainGroup(group: String): String {
    val cleanGroup = group.trim()
    return if (cleanGroup == AUTOMATION_UNGROUPED_LABEL) "" else cleanGroup
}

internal fun moveAutomationChainWithinDisplayGroupModel(
    chains: List<AutomationChain>,
    group: String,
    chainIds: List<String>,
    fromIndex: Int,
    toIndex: Int
): List<AutomationChain>? {
    if (fromIndex !in chainIds.indices || toIndex !in chainIds.indices || fromIndex == toIndex) return null
    val displayGroup = group.ifBlank { AUTOMATION_UNGROUPED_LABEL }
    val nextIds = chainIds.toMutableList()
    val item = nextIds.removeAt(fromIndex)
    nextIds.add(toIndex, item)
    val chainsById = chains.associateBy { it.id }
    var groupCursor = 0
    return chains.map { chain ->
        val chainGroup = chain.group.ifBlank { AUTOMATION_UNGROUPED_LABEL }
        if (chainGroup != displayGroup) {
            chain
        } else {
            val nextId = nextIds.getOrNull(groupCursor++)
            chainsById[nextId] ?: chain
        }
    }
}

internal fun automationChainGroupOptions(
    savedGroups: List<String>,
    chains: List<AutomationChain>
): List<String> {
    val used = chains.mapNotNull { chain -> chain.group.takeIf { it.isNotBlank() } }
    return (savedGroups.filterNot { it == AUTOMATION_UNGROUPED_LABEL } + used).distinct()
}

internal fun addAutomationChainGroupModel(
    groups: List<String>,
    name: String
): AutomationGroupEditResult {
    val cleanName = cleanAutomationChainGroup(name)
    if (cleanName.isBlank()) {
        return AutomationGroupEditResult(success = false, message = "请输入分组名")
    }
    if (cleanName in groups) {
        return AutomationGroupEditResult(success = false, message = "分组已存在")
    }
    return AutomationGroupEditResult(
        success = true,
        groups = groups + cleanName,
        groupName = cleanName
    )
}

internal fun renameAutomationChainGroupModel(
    groups: List<String>,
    chains: List<AutomationChain>,
    group: String,
    newName: String
): AutomationGroupEditResult {
    if (group == AUTOMATION_UNGROUPED_LABEL) {
        return AutomationGroupEditResult(success = false, message = "未分组不能改名")
    }
    val cleanName = cleanAutomationChainGroup(newName)
    if (cleanName.isBlank()) {
        return AutomationGroupEditResult(success = false, message = "请输入分组名")
    }
    if (cleanName == group) {
        return AutomationGroupEditResult(success = true, groups = groups, chains = chains, groupName = cleanName)
    }
    if (cleanName in automationChainGroupOptions(groups, chains)) {
        return AutomationGroupEditResult(success = false, message = "分组已存在")
    }
    val nextGroups = (
        if (group in groups) {
            groups.map { current -> if (current == group) cleanName else current }
        } else {
            groups + cleanName
        }
    ).distinct()
    val nextChains = chains.map { chain ->
        if (chain.group == group) chain.copy(group = cleanName) else chain
    }
    return AutomationGroupEditResult(
        success = true,
        groups = nextGroups,
        chains = nextChains,
        groupName = cleanName
    )
}

internal fun moveAutomationChainGroupModel(
    groups: List<String>,
    fromIndex: Int,
    toIndex: Int
): List<String>? {
    if (fromIndex !in groups.indices || toIndex !in groups.indices) return null
    val next = groups.toMutableList()
    val item = next.removeAt(fromIndex)
    next.add(toIndex, item)
    return next
}

internal fun moveAutomationChainDisplayGroupModel(
    displayGroups: List<String>,
    fromIndex: Int,
    toIndex: Int
): List<String>? {
    if (fromIndex !in displayGroups.indices || toIndex !in displayGroups.indices) return null
    val next = displayGroups.toMutableList()
    val item = next.removeAt(fromIndex)
    next.add(toIndex, item)
    return next.filter { it.isNotBlank() }.distinct()
}

internal fun deleteAutomationChainGroupModel(
    groups: List<String>,
    chains: List<AutomationChain>,
    group: String
): AutomationGroupEditResult {
    if (group == AUTOMATION_UNGROUPED_LABEL) {
        return AutomationGroupEditResult(success = false)
    }
    return AutomationGroupEditResult(
        success = true,
        groups = groups.filterNot { it == group },
        chains = chains.map { chain ->
            if (chain.group == group) chain.copy(group = "") else chain
        }
    )
}

internal fun parseAutomationChainGroups(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val rawValue = array.optString(index).trim()
            val value = if (rawValue == AUTOMATION_UNGROUPED_LABEL) rawValue else cleanAutomationChainGroup(rawValue)
            if (value.isNotBlank() && value !in this) add(value)
        }
    }
}

internal fun automationChainGroupsToJsonArray(groups: List<String>): JSONArray {
    return JSONArray().also { array -> groups.forEach(array::put) }
}

internal fun nextAutomationChainNumberFor(chains: List<AutomationChain>): Int {
    return ((chains.mapNotNull { chain ->
        chain.id.removePrefix("chain-").toIntOrNull()
    }.maxOrNull() ?: chains.size) + 1).coerceAtLeast(1)
}

internal fun nextAutomationStepNumberFor(chains: List<AutomationChain>): Int {
    return ((chains.flatMap { it.steps }.mapNotNull { step ->
        step.id.removePrefix("step-").toIntOrNull()
    }.maxOrNull() ?: 0) + 1).coerceAtLeast(1)
}

internal fun List<AutomationChain>.isLegacyDefaultAutomationChains(): Boolean {
    val chain = singleOrNull() ?: return false
    return chain.id == "chain-1" && chain.name.isBlank() && chain.steps.isEmpty()
}

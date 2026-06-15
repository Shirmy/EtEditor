package com.eteditor

fun EditorController.createAutomationChain() {
    val result = createAutomationChainDraftState(
        selectedChainId = selectedAutomationChainId,
        nextNumber = nextAutomationChainNumber
    )
    nextAutomationChainNumber = result.nextNumber
    draftAutomationChainPreviousSelectionId = result.previousSelectedChainId
    draftAutomationChain = result.draft
    selectedAutomationChainId = result.selectedChainId
    appendAutomationLog("\u65b0\u5efa\u6267\u884c\u94fe\u8349\u7a3f")
}

fun EditorController.saveAutomationChainDraft(): Boolean {
    val draft = draftAutomationChain ?: return true
    val cleanName = draft.name.trim()
    if (cleanName.isBlank()) {
        statusMessage = "\u8bf7\u8f93\u5165\u6267\u884c\u94fe\u540d"
        return false
    }
    val result = saveAutomationChainDraftState(automationChains, draft, cleanName)
    automationChains = result.chains
    selectedAutomationChainId = result.selectedChainId
    draftAutomationChain = null
    draftAutomationChainPreviousSelectionId = ""
    persistAutomationChains()
    appendAutomationLog("\u65b0\u5efa\u6267\u884c\u94fe\uff1a${result.savedChain.name}")
    return true
}

fun EditorController.discardAutomationChainDraft() {
    draftAutomationChain = null
    selectedAutomationChainId = selectedAutomationChainIdAfterDraftDiscard(
        chains = automationChains,
        previousSelectedChainId = draftAutomationChainPreviousSelectionId
    )
    draftAutomationChainPreviousSelectionId = ""
    appendAutomationLog("\u5df2\u53d6\u6d88\u65b0\u5efa\u6267\u884c\u94fe")
}

fun EditorController.selectAutomationChain(chainId: String) {
    if (automationChains.none { it.id == chainId }) return
    draftAutomationChain = null
    draftAutomationChainPreviousSelectionId = ""
    selectedAutomationChainId = chainId
    persistAutomationChains()
}

fun EditorController.removeAutomationChain(chainId: String) {
    val result = removeAutomationChainById(automationChains, selectedAutomationChainId, chainId) ?: return
    automationChains = result.chains
    selectedAutomationChainId = result.selectedChainId
    persistAutomationChains()
    appendAutomationLog("删除执行链：${result.removedChain.name}")
}

fun EditorController.moveAutomationChainWithinDisplayGroup(
    group: String,
    chainIds: List<String>,
    fromIndex: Int,
    toIndex: Int
) {
    automationChains = moveAutomationChainWithinDisplayGroupModel(
        chains = automationChains,
        group = group,
        chainIds = chainIds,
        fromIndex = fromIndex,
        toIndex = toIndex
    ) ?: return
    persistAutomationChains()
}

fun EditorController.renameAutomationChain(name: String) {
    val chain = selectedAutomationChain ?: return
    updateAutomationChain(chain.copy(name = name.trim()))
}

fun EditorController.updateAutomationChainGroup(group: String) {
    val chain = selectedAutomationChain ?: return
    val cleanGroup = cleanAutomationChainGroup(group)
    if (cleanGroup.isNotBlank() && cleanGroup !in automationChainGroups) {
        automationChainGroups = automationChainGroups + cleanGroup
        persistAutomationChains()
    }
    updateAutomationChain(chain.copy(group = cleanGroup))
}

fun EditorController.addAutomationChainGroup(name: String): Boolean {
    val result = addAutomationChainGroupModel(automationChainGroups, name)
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    automationChainGroups = result.groups
    persistAutomationChains()
    statusMessage = "已添加分组：${result.groupName}"
    return true
}

fun EditorController.renameAutomationChainGroup(group: String, newName: String): Boolean {
    val result = renameAutomationChainGroupModel(
        groups = automationChainGroups,
        chains = automationChains,
        group = group,
        newName = newName
    )
    if (!result.success) {
        if (result.message.isNotBlank()) statusMessage = result.message
        return false
    }
    if (result.groupName == group) return true
    automationChainGroups = result.groups
    automationChains = result.chains
    persistAutomationChains()
    statusMessage = "已改名分组：${result.groupName}"
    return true
}

fun EditorController.moveAutomationChainDisplayGroup(groups: List<String>, fromIndex: Int, toIndex: Int) {
    automationChainGroups = moveAutomationChainDisplayGroupModel(groups, fromIndex, toIndex) ?: return
    persistAutomationChains()
}

fun EditorController.deleteAutomationChainGroup(group: String) {
    val result = deleteAutomationChainGroupModel(automationChainGroups, automationChains, group)
    if (!result.success) return
    automationChainGroups = result.groups
    automationChains = result.chains
    persistAutomationChains()
}

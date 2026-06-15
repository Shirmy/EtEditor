package com.eteditor

internal data class AutomationChainDraftCreateResult(
    val draft: AutomationChain,
    val selectedChainId: String,
    val previousSelectedChainId: String,
    val nextNumber: Int
)

internal data class AutomationChainDraftSaveResult(
    val chains: List<AutomationChain>,
    val savedChain: AutomationChain,
    val selectedChainId: String
)

internal data class AutomationChainRemoveResult(
    val chains: List<AutomationChain>,
    val removedChain: AutomationChain,
    val selectedChainId: String
)

internal fun createAutomationChainDraftState(
    selectedChainId: String,
    nextNumber: Int
): AutomationChainDraftCreateResult {
    val draft = AutomationChain(
        id = "chain-$nextNumber",
        name = "",
        steps = emptyList()
    )
    return AutomationChainDraftCreateResult(
        draft = draft,
        selectedChainId = draft.id,
        previousSelectedChainId = selectedChainId,
        nextNumber = nextNumber + 1
    )
}

internal fun saveAutomationChainDraftState(
    chains: List<AutomationChain>,
    draft: AutomationChain,
    cleanName: String
): AutomationChainDraftSaveResult {
    val saved = draft.copy(name = cleanName)
    return AutomationChainDraftSaveResult(
        chains = chains + saved,
        savedChain = saved,
        selectedChainId = saved.id
    )
}

internal fun selectedAutomationChainIdAfterDraftDiscard(
    chains: List<AutomationChain>,
    previousSelectedChainId: String
): String {
    return previousSelectedChainId
        .takeIf { previousId -> chains.any { it.id == previousId } }
        ?: chains.firstOrNull()?.id.orEmpty()
}

internal fun removeAutomationChainById(
    chains: List<AutomationChain>,
    selectedChainId: String,
    chainId: String
): AutomationChainRemoveResult? {
    val removed = chains.firstOrNull { it.id == chainId } ?: return null
    val nextChains = chains.filterNot { it.id == chainId }
    return AutomationChainRemoveResult(
        chains = nextChains,
        removedChain = removed,
        selectedChainId = if (selectedChainId == chainId) {
            nextChains.firstOrNull()?.id.orEmpty()
        } else {
            selectedChainId
        }
    )
}

internal fun updateAutomationChainById(
    chains: List<AutomationChain>,
    updated: AutomationChain
): List<AutomationChain> {
    return chains.map { chain ->
        if (chain.id == updated.id) updated else chain
    }
}

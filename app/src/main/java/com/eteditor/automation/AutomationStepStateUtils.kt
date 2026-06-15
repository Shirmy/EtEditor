package com.eteditor

internal data class NumberedAutomationStepAddResult(
    val chain: AutomationChain,
    val step: AutomationStep,
    val nextNumber: Int
)

internal fun appendNumberedAutomationStep(
    chain: AutomationChain,
    nextNumber: Int,
    name: String,
    toolId: String,
    parameterOverrides: Map<String, String>,
    presetId: String = ""
): NumberedAutomationStepAddResult {
    val step = AutomationStep(
        id = "step-$nextNumber",
        name = name,
        toolId = toolId,
        parameterOverrides = parameterOverrides,
        presetId = presetId
    )
    return NumberedAutomationStepAddResult(
        chain = chain.copy(steps = chain.steps + step),
        step = step,
        nextNumber = nextNumber + 1
    )
}

internal fun removeAutomationStepAt(
    chain: AutomationChain,
    index: Int
): AutomationChain? {
    if (index !in chain.steps.indices) return null
    return chain.copy(steps = chain.steps.toMutableList().also { it.removeAt(index) })
}

internal fun moveAutomationStepAt(
    chain: AutomationChain,
    fromIndex: Int,
    toIndex: Int
): AutomationChain? {
    if (fromIndex !in chain.steps.indices || toIndex !in chain.steps.indices || fromIndex == toIndex) {
        return null
    }
    val nextSteps = chain.steps.toMutableList()
    val moved = nextSteps.removeAt(fromIndex)
    nextSteps.add(toIndex, moved)
    return chain.copy(steps = nextSteps)
}

internal fun updateAutomationStepById(
    chain: AutomationChain,
    updated: AutomationStep
): AutomationChain {
    return chain.copy(
        steps = chain.steps.map { step ->
            if (step.id == updated.id) updated else step
        }
    )
}

internal fun automationConfirmationStepForRequest(
    chains: List<AutomationChain>,
    request: AutomationConfirmationRequest
): AutomationStep? {
    val chain = chains.firstOrNull { it.id == request.chainId } ?: return null
    return automationConfirmationStepForRequest(chain, request)
}

internal fun automationConfirmationStepForRequest(
    chain: AutomationChain,
    request: AutomationConfirmationRequest
): AutomationStep? {
    return chain.steps.getOrNull(request.stepIndex)
        ?.takeIf { step -> step.id == request.stepId }
}

internal fun isAutomationConfirmationStepCurrent(
    chain: AutomationChain,
    request: AutomationConfirmationRequest
): Boolean {
    return automationConfirmationStepForRequest(chain, request) != null
}

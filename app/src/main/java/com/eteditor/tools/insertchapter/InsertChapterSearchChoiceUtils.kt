package com.eteditor

/**
 * Validation for picking one row from the insert-chapter sosad search dialog.
 * On success, callers must clear the choice request **before** fetching catalog
 * (same order as fetch-info select), so failure cannot leave the dialog hanging.
 */
internal data class InsertSosadSearchChoiceValidation(
    val ok: Boolean,
    val errorMessage: String = "",
    /** True only when validation passed; UI request must be cleared before fetch. */
    val clearChoiceRequestBeforeFetch: Boolean = false
)

internal fun validateInsertSosadSearchChoiceSelection(
    request: FetchInfoSearchChoiceRequest?,
    toolId: String,
    choice: FetchInfoSearchChoice,
    expectedSource: String = FetchInfoSources.SOSAD
): InsertSosadSearchChoiceValidation {
    if (request == null) {
        return InsertSosadSearchChoiceValidation(
            ok = false,
            errorMessage = "没有可选择的搜索结果"
        )
    }
    if (request.toolId != toolId) {
        return InsertSosadSearchChoiceValidation(
            ok = false,
            errorMessage = "搜索结果已变化"
        )
    }
    if (request.parameters.source != expectedSource) {
        return InsertSosadSearchChoiceValidation(
            ok = false,
            errorMessage = "搜索结果来源不匹配"
        )
    }
    if (request.choices.none { it.detailUrl == choice.detailUrl }) {
        return InsertSosadSearchChoiceValidation(
            ok = false,
            errorMessage = "搜索结果已失效"
        )
    }
    return InsertSosadSearchChoiceValidation(
        ok = true,
        clearChoiceRequestBeforeFetch = true
    )
}

package com.eteditor

/**
 * 抓取预览界面里的「简介规则」即时编辑。
 *
 * 结构与目录规则（[fetchInfoCatalogRuleItems] 等）完全平行，规则保存在抓取参数
 * introFilter（结构化 JSON）里。简介不分章节/净化，所有规则统一归「净化」类别。
 * 在预览界面编辑时：
 * 1) 更新当前预览（重新过滤 raw -> filtered）实现所见即所得；
 * 2) 以 clearPreview=false 写回内存工具参数（会话内/换书前有效，且不清空预览）；
 * 3) 通过 persistFetchInfoIntroRulesToDisk 落盘到内置默认值，使其重启/换书后仍在。
 */
fun EditorController.fetchInfoIntroRuleItems(): List<FetchCatalogRuleItem> {
    val preview = fetchInfoPreview ?: return emptyList()
    return parseFetchCatalogRuleItems(preview.parameters.introFilter)
}

private fun EditorController.commitFetchInfoIntroRules(newIntroFilter: String) {
    val preview = fetchInfoPreview ?: return
    val nextParameters = preview.parameters.copy(introFilter = newIntroFilter)
    val (filtered, issues) = FetchInfoFilter.apply(preview.raw, nextParameters)
    fetchInfoPreview = preview.copy(
        parameters = nextParameters,
        filtered = filtered,
        filterIssues = issues
    )
    updateBuiltInToolParameter(
        preview.toolId,
        FETCH_INFO_PARAM_INTRO_FILTER,
        newIntroFilter,
        clearPreview = false
    )
    persistFetchInfoIntroRulesToDisk(preview.toolId, newIntroFilter)
}

// 把简介规则写进落盘的内置默认值，重启/换书后由启动加载流程自动恢复。
private fun EditorController.persistFetchInfoIntroRulesToDisk(toolId: String, introFilter: String) {
    val current = savedBuiltInDefaultOverrides[toolId].orEmpty().toMutableMap()
    if (introFilter.isBlank()) {
        current.remove(FETCH_INFO_PARAM_INTRO_FILTER)
    } else {
        current[FETCH_INFO_PARAM_INTRO_FILTER] = introFilter
    }
    savedBuiltInDefaultOverrides = saveBuiltInDefaultParameterOverrides(
        savedDefaults = savedBuiltInDefaultOverrides,
        toolId = toolId,
        overrides = current,
        cleanOverridesForSave = ::cleanBuiltInDefaultOverridesForSave
    )
    persistBuiltInToolDefaults()
}

fun EditorController.addFetchInfoIntroRule(
    name: String,
    search: String,
    replacement: String,
    regex: Boolean
) {
    val current = fetchInfoPreview?.parameters?.introFilter ?: return
    commitFetchInfoIntroRules(
        addFetchCatalogRule(current, FETCH_CATALOG_RULE_CATEGORY_PURIFY, name, search, replacement, regex)
    )
}

fun EditorController.updateFetchInfoIntroRule(
    index: Int,
    name: String,
    search: String,
    replacement: String,
    regex: Boolean
) {
    val current = fetchInfoPreview?.parameters?.introFilter ?: return
    commitFetchInfoIntroRules(
        updateFetchCatalogRule(current, index, FETCH_CATALOG_RULE_CATEGORY_PURIFY, name, search, replacement, regex)
    )
}

fun EditorController.setFetchInfoIntroRuleEnabled(index: Int, enabled: Boolean) {
    val current = fetchInfoPreview?.parameters?.introFilter ?: return
    commitFetchInfoIntroRules(setFetchCatalogRuleEnabled(current, index, enabled))
}

fun EditorController.deleteFetchInfoIntroRule(index: Int) {
    val current = fetchInfoPreview?.parameters?.introFilter ?: return
    commitFetchInfoIntroRules(deleteFetchCatalogRule(current, index))
}

fun EditorController.moveFetchInfoIntroRule(fromIndex: Int, toIndex: Int) {
    val current = fetchInfoPreview?.parameters?.introFilter ?: return
    commitFetchInfoIntroRules(moveFetchCatalogRule(current, fromIndex, toIndex))
}

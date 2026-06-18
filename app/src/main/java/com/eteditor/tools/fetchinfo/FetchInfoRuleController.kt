package com.eteditor

/**
 * 抓取预览界面里的"目录过滤/规则"即时编辑。
 *
 * 规则保存在抓取参数 catalogFilter（结构化 JSON）里。在预览界面编辑时：
 * 1) 更新当前预览（重新过滤 raw -> filtered）实现所见即所得；
 * 2) 以 clearPreview=false 写回内存工具参数（会话内/换书前有效，且不清空预览）；
 * 3) 通过 persistFetchInfoCatalogRulesToDisk 落盘到内置默认值，使其重启/换书后仍在。
 */
fun EditorController.fetchInfoCatalogRuleItems(): List<FetchCatalogRuleItem> {
    val preview = fetchInfoPreview ?: return emptyList()
    return parseFetchCatalogRuleItems(preview.parameters.catalogFilter)
}

private fun EditorController.commitFetchInfoCatalogRules(newCatalogFilter: String) {
    val preview = fetchInfoPreview ?: return
    val nextParameters = preview.parameters.copy(catalogFilter = newCatalogFilter)
    val (filtered, issues) = FetchInfoFilter.apply(preview.raw, nextParameters)
    fetchInfoPreview = preview.copy(
        parameters = nextParameters,
        filtered = filtered,
        filterIssues = issues
    )
    updateBuiltInToolParameter(
        preview.toolId,
        FETCH_INFO_PARAM_CATALOG_FILTER,
        newCatalogFilter,
        clearPreview = false
    )
    persistFetchInfoCatalogRulesToDisk(preview.toolId, newCatalogFilter)
}

// 把目录规则写进落盘的内置默认值，重启/换书后由启动加载流程自动恢复。
private fun EditorController.persistFetchInfoCatalogRulesToDisk(toolId: String, catalogFilter: String) {
    val current = savedBuiltInDefaultOverrides[toolId].orEmpty().toMutableMap()
    if (catalogFilter.isBlank()) {
        current.remove(FETCH_INFO_PARAM_CATALOG_FILTER)
    } else {
        current[FETCH_INFO_PARAM_CATALOG_FILTER] = catalogFilter
    }
    savedBuiltInDefaultOverrides = saveBuiltInDefaultParameterOverrides(
        savedDefaults = savedBuiltInDefaultOverrides,
        toolId = toolId,
        overrides = current,
        cleanOverridesForSave = ::cleanBuiltInDefaultOverridesForSave
    )
    persistBuiltInToolDefaults()
}

fun EditorController.addFetchInfoCatalogRule(
    category: String,
    name: String,
    search: String,
    replacement: String,
    regex: Boolean
) {
    val current = fetchInfoPreview?.parameters?.catalogFilter ?: return
    commitFetchInfoCatalogRules(
        addFetchCatalogRule(current, category, name, search, replacement, regex)
    )
}

fun EditorController.updateFetchInfoCatalogRule(
    index: Int,
    category: String,
    name: String,
    search: String,
    replacement: String,
    regex: Boolean
) {
    val current = fetchInfoPreview?.parameters?.catalogFilter ?: return
    commitFetchInfoCatalogRules(
        updateFetchCatalogRule(current, index, category, name, search, replacement, regex)
    )
}

fun EditorController.setFetchInfoCatalogRuleEnabled(index: Int, enabled: Boolean) {
    val current = fetchInfoPreview?.parameters?.catalogFilter ?: return
    commitFetchInfoCatalogRules(setFetchCatalogRuleEnabled(current, index, enabled))
}

fun EditorController.deleteFetchInfoCatalogRule(index: Int) {
    val current = fetchInfoPreview?.parameters?.catalogFilter ?: return
    commitFetchInfoCatalogRules(deleteFetchCatalogRule(current, index))
}

fun EditorController.moveFetchInfoCatalogRule(fromIndex: Int, toIndex: Int) {
    val current = fetchInfoPreview?.parameters?.catalogFilter ?: return
    commitFetchInfoCatalogRules(moveFetchCatalogRule(current, fromIndex, toIndex))
}

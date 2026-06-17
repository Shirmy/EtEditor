package com.eteditor

/**
 * 抓取预览界面里的"目录过滤/规则"即时编辑。
 *
 * 规则保存在抓取参数 catalogFilter（结构化 JSON）里。在预览界面编辑时，这里只更新
 * 当前预览（重新过滤 raw -> filtered），实现所见即所得；不调用 updateBuiltInToolParameter，
 * 因为那会清空预览。持久化的基线规则仍在工具参数面板的"目录过滤"里配置。
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
}

fun EditorController.addFetchInfoCatalogRule(
    category: String,
    name: String,
    search: String,
    replacement: String,
    regex: Boolean,
    action: String
) {
    val current = fetchInfoPreview?.parameters?.catalogFilter ?: return
    commitFetchInfoCatalogRules(
        addFetchCatalogRule(current, category, name, search, replacement, regex, action)
    )
}

fun EditorController.updateFetchInfoCatalogRule(
    index: Int,
    category: String,
    name: String,
    search: String,
    replacement: String,
    regex: Boolean,
    action: String
) {
    val current = fetchInfoPreview?.parameters?.catalogFilter ?: return
    commitFetchInfoCatalogRules(
        updateFetchCatalogRule(current, index, category, name, search, replacement, regex, action)
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

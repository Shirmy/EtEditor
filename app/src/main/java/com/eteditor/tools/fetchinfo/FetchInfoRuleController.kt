package com.eteditor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val rawSnapshot = preview.raw
    // 规则即时生效（列表立刻更新），过滤计算放后台执行，避免极端低效正则卡住界面；
    // 写错的正则在过滤时即被跳过并记为问题，跑完自动刷新预览（不设超时，跑到完为止）。
    fetchInfoPreview = preview.copy(parameters = nextParameters)
    fetchInfoFiltering = true
    // 规则是 fetch_info 工具的全局默认设置，必须以工具真实 id "fetch_info" 存取；
    // preview.toolId 是预览实例 id（内置工具为 "builtin-fetch_info"），用它会导致写回与落盘全部落空。
    updateBuiltInToolParameter(
        "fetch_info",
        FETCH_INFO_PARAM_CATALOG_FILTER,
        newCatalogFilter,
        clearPreview = false
    )
    persistFetchInfoCatalogRulesToDisk("fetch_info", newCatalogFilter)
    controllerScope.launch {
        try {
            val (filtered, issues) = withContext(Dispatchers.Default) {
                FetchInfoFilter.apply(rawSnapshot, nextParameters)
            }
            // 只有当前预览仍是同一次抓取、且参数未被更晚的编辑覆盖时才回填，避免旧结果盖掉新结果。
            val current = fetchInfoPreview
            if (current != null && current.raw === rawSnapshot && current.parameters == nextParameters) {
                fetchInfoPreview = current.copy(filtered = filtered, filterIssues = issues)
            }
        } finally {
            // 仅当本任务仍是最新（未被更晚的编辑覆盖）时才复位"识别中"，
            // 否则交给更晚的任务自己结束时复位，避免较早任务提前关掉最新任务的提示。
            val current = fetchInfoPreview
            if (current != null && current.raw === rawSnapshot && current.parameters == nextParameters) {
                fetchInfoFiltering = false
            }
        }
    }
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

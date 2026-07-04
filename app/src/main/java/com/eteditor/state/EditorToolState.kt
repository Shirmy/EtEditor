package com.eteditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class EditorToolState {
    var textSearchResults by mutableStateOf<List<TextSearchResult>>(emptyList())
    // 真实命中总数（来自轻量计数扫描，不随展示批次变化）；0 表示未计数或无命中。
    var textSearchTotalMatchCount by mutableStateOf(0)
    // 每条规则的真实命中总数；key = ruleIndex。
    var textSearchRuleTotalCounts by mutableStateOf<Map<Int, Int>>(emptyMap())
    // 每条规则的增量建详情游标，供"展开更多"接着扫用；key = ruleIndex。
    var textSearchRuleCursors by mutableStateOf<Map<Int, TextSearchIncrementalCursor>>(emptyMap())
    var textSearchToolId by mutableStateOf<String?>(null)
    var replacementFilePreview by mutableStateOf<ReplacementFilePreview?>(null)
    var fetchInfoPreview by mutableStateOf<FetchInfoPreview?>(null)
    var fetchInfoSearchChoiceRequest by mutableStateOf<FetchInfoSearchChoiceRequest?>(null)
    var fetchInfoRetryRequest by mutableStateOf<FetchInfoRetryRequest?>(null)
    var fetchInfoProgress by mutableStateOf(0f)
    var fetchInfoFiltering by mutableStateOf(false)
    var sosadLoginInvalid by mutableStateOf(false)
    var selectedReplacementPreviewMatchId by mutableStateOf<String?>(null)
    var textReplaceRuntimeFiles by mutableStateOf<Map<String, String>>(emptyMap())
    var selectedTextSearchResultId by mutableStateOf<String?>(null)
    var fileRenamePlan by mutableStateOf<List<FileRenamePlanItem>>(emptyList())
    var fileRenamePlanToolId by mutableStateOf<String?>(null)
    var titleRenamePlan by mutableStateOf<List<TitleRenamePlanItem>>(emptyList())
    var titleRenamePlanToolId by mutableStateOf<String?>(null)
    var titleFormatPlan by mutableStateOf<List<TitleFormatPlanItem>>(emptyList())
    var titleFormatPlanToolId by mutableStateOf<String?>(null)
    var titleFormatPlanAllowsStyleEdit by mutableStateOf(false)
    var titleFormatPlanLogicText by mutableStateOf("")
    var generatedCoverPreview by mutableStateOf<GeneratedCoverPreview?>(null)
    var insertChapterSourcePreview by mutableStateOf<InsertChapterSourcePreview?>(null)
    var insertChapterSourceData: InsertChapterSourceData? = null
    var builtInParameterOverrides by mutableStateOf<Map<String, Map<String, String>>>(emptyMap())
    var savedBuiltInDefaultOverrides by mutableStateOf<Map<String, Map<String, String>>>(emptyMap())
    val availableTools = defaultEditorToolDefinitions()
    var editorTools by mutableStateOf<List<EditorTool>>(emptyList())
    var txtTextReplacePresets by mutableStateOf<List<EditorTool>>(emptyList())
    var epubTextReplacePresets by mutableStateOf<List<EditorTool>>(emptyList())
    var selectedEditorToolId by mutableStateOf<String?>(null)
    var draftEditorTool by mutableStateOf<EditorTool?>(null)
    var nextEditorToolNumber = 1
    var nextTxtTextReplacePresetNumber = 1
    var nextEpubTextReplacePresetNumber = 1
}

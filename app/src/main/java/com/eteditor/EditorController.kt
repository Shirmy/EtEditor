package com.eteditor

import android.content.Context
import android.net.Uri
import com.eteditor.core.ChapterDetector
import com.eteditor.core.ChapterInfo
import com.eteditor.core.CheckReport
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import com.eteditor.core.TxtDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class EditorController(internal val appContext: Context) {
    internal val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val jsonPreferences = EditorJsonPreferences(appContext, SETTINGS_PREFS)
    internal val settingsPreferences = EditorSettingsPreferences(appContext, SETTINGS_PREFS)
    private val initialSettings = settingsPreferences.load(
        defaultTxtChapterRules = ChapterDetector.defaultTxtChapterRules(),
        defaultEpubLeftPanelMode = LEFT_PANEL_NONE,
        defaultEpubRightPanelMode = RIGHT_PANEL_FEATURES,
        defaultTxtLeftPanelMode = LEFT_PANEL_DIRECTORY,
        defaultTxtRightPanelMode = RIGHT_PANEL_NONE,
        leftPanelModes = LEFT_PANEL_MODES,
        rightPanelModes = RIGHT_PANEL_MODES,
        txtRightPanelDisallowedMode = RIGHT_PANEL_AUTOMATION
    )
    private val documentState = EditorDocumentState(initialSettings)
    private val txtState = EditorTxtState(initialSettings)
    private val previewState = EditorPreviewState()
    private val toolState = EditorToolState()
    private val automationState = EditorAutomationState()

    var kind: DocumentKind
        get() = documentState.kind
        internal set(value) { documentState.kind = value }
    var documentSessionKey: Int
        get() = documentState.documentSessionKey
        internal set(value) { documentState.documentSessionKey = value }
    var documentContentVersion: Int
        get() = documentState.documentContentVersion
        internal set(value) { documentState.documentContentVersion = value }
    var title: String
        get() = documentState.title
        internal set(value) { documentState.title = value }
    var subtitle: String
        get() = documentState.subtitle
        internal set(value) { documentState.subtitle = value }
    var epubSummaryMeta: String
        get() = documentState.epubSummaryMeta
        internal set(value) { documentState.epubSummaryMeta = value }
    var epubFileSizeBytes: Long?
        get() = documentState.epubFileSizeBytes
        internal set(value) { documentState.epubFileSizeBytes = value }
    var epubWordCountProgress: Float?
        get() = documentState.epubWordCountProgress
        internal set(value) { documentState.epubWordCountProgress = value }
    var epubWordCountProgressText: String
        get() = documentState.epubWordCountProgressText
        internal set(value) { documentState.epubWordCountProgressText = value }
    internal var epubWordCountJob: Job?
        get() = documentState.epubWordCountJob
        set(value) { documentState.epubWordCountJob = value }
    var hasUnsavedChanges: Boolean
        get() = documentState.hasUnsavedChanges
        internal set(value) { documentState.hasUnsavedChanges = value }
    var busy: Boolean
        get() = documentState.busy
        internal set(value) { documentState.busy = value }
    var saveProgress: Float?
        get() = documentState.saveProgress
        internal set(value) { documentState.saveProgress = value }
    var saveProgressText: String
        get() = documentState.saveProgressText
        internal set(value) { documentState.saveProgressText = value }
    var bodyOperationProgress: Float?
        get() = documentState.bodyOperationProgress
        internal set(value) { documentState.bodyOperationProgress = value }
    var bodyOperationProgressText: String
        get() = documentState.bodyOperationProgressText
        internal set(value) { documentState.bodyOperationProgressText = value }
    var saveFailureMessage: String
        get() = documentState.saveFailureMessage
        internal set(value) { documentState.saveFailureMessage = value }
    var chapters: List<ChapterInfo>
        get() = documentState.chapters
        internal set(value) { documentState.chapters = value }
    var checkReport: CheckReport?
        get() = documentState.checkReport
        internal set(value) { documentState.checkReport = value }
    var selectedScreen: AppScreen
        get() = documentState.selectedScreen
        set(value) { documentState.selectedScreen = value }
    var leftRailExpanded: Boolean
        get() = documentState.leftRailExpanded
        internal set(value) { documentState.leftRailExpanded = value }
    var hideDirectoryFileNameByDefault: Boolean
        get() = documentState.hideDirectoryFileNameByDefault
        internal set(value) { documentState.hideDirectoryFileNameByDefault = value }
    var epubHideSection0001FromNcx: Boolean
        get() = documentState.epubHideSection0001FromNcx
        internal set(value) { documentState.epubHideSection0001FromNcx = value }
    var epubLongPressSplitChapter: Boolean
        get() = documentState.epubLongPressSplitChapter
        internal set(value) { documentState.epubLongPressSplitChapter = value }
    var epubDoubleTapEdit: Boolean
        get() = documentState.epubDoubleTapEdit
        internal set(value) { documentState.epubDoubleTapEdit = value }
    var epubLeftPanelMode: String
        get() = documentState.epubLeftPanelMode
        internal set(value) { documentState.epubLeftPanelMode = value }
    var epubRightPanelMode: String
        get() = documentState.epubRightPanelMode
        internal set(value) { documentState.epubRightPanelMode = value }
    var statusMessage: String
        get() = documentState.statusMessage
        internal set(value) { documentState.statusMessage = value }
    internal var sourceUri: Uri?
        get() = documentState.sourceUri
        set(value) { documentState.sourceUri = value }
    internal var epub: EpubBook?
        get() = documentState.epub
        set(value) { documentState.epub = value }

    internal var txtCatalogParseJob: Job?
        get() = txtState.txtCatalogParseJob
        set(value) { txtState.txtCatalogParseJob = value }
    internal var txtMoveChapterSyncJob: Job?
        get() = txtState.txtMoveChapterSyncJob
        set(value) { txtState.txtMoveChapterSyncJob = value }
    internal var txtMoveChapterSyncRevision: Int
        get() = txtState.txtMoveChapterSyncRevision
        set(value) { txtState.txtMoveChapterSyncRevision = value }
    internal var txtChapterRulesRefreshDeferred: Boolean
        get() = txtState.txtChapterRulesRefreshDeferred
        set(value) { txtState.txtChapterRulesRefreshDeferred = value }
    internal var txtTextReplacementRefreshDeferred: Boolean
        get() = txtState.txtTextReplacementRefreshDeferred
        set(value) { txtState.txtTextReplacementRefreshDeferred = value }
    internal var txtTextReplacementRefreshApplying: Boolean
        get() = txtState.txtTextReplacementRefreshApplying
        set(value) { txtState.txtTextReplacementRefreshApplying = value }
    var txtLeftPanelMode: String
        get() = txtState.txtLeftPanelMode
        internal set(value) { txtState.txtLeftPanelMode = value }
    var txtRightPanelMode: String
        get() = txtState.txtRightPanelMode
        internal set(value) { txtState.txtRightPanelMode = value }
    var txtChapterRulesText: String
        get() = txtState.txtChapterRulesText
        internal set(value) { txtState.txtChapterRulesText = value }
    internal var txtEnabledChapterRuleKeys: Set<String>
        get() = txtState.txtEnabledChapterRuleKeys
        set(value) { txtState.txtEnabledChapterRuleKeys = value }
    var txtPurifyRulesText: String
        get() = txtState.txtPurifyRulesText
        internal set(value) { txtState.txtPurifyRulesText = value }
    var txtBookTitleRulesText: String
        get() = txtState.txtBookTitleRulesText
        internal set(value) { txtState.txtBookTitleRulesText = value }
    var txtShortChapterThreshold: Int
        get() = txtState.txtShortChapterThreshold
        internal set(value) { txtState.txtShortChapterThreshold = value }
    var txtLongChapterThreshold: Int
        get() = txtState.txtLongChapterThreshold
        internal set(value) { txtState.txtLongChapterThreshold = value }
    var txtShortChapterHintEnabled: Boolean
        get() = txtState.txtShortChapterHintEnabled
        internal set(value) { txtState.txtShortChapterHintEnabled = value }
    var txtLongChapterHintEnabled: Boolean
        get() = txtState.txtLongChapterHintEnabled
        internal set(value) { txtState.txtLongChapterHintEnabled = value }
    var txtChapterHintMode: String
        get() = txtState.txtChapterHintMode
        internal set(value) { txtState.txtChapterHintMode = value }
    var txtAutoNumberOnSave: Boolean
        get() = txtState.txtAutoNumberOnSave
        internal set(value) { txtState.txtAutoNumberOnSave = value }
    var txtChapterNumberStartAtOneOnSave: Boolean
        get() = txtState.txtChapterNumberStartAtOneOnSave
        internal set(value) { txtState.txtChapterNumberStartAtOneOnSave = value }
    var txtDoubleTapEdit: Boolean
        get() = txtState.txtDoubleTapEdit
        internal set(value) { txtState.txtDoubleTapEdit = value }
    var txtDoubleTapTitleEdit: Boolean
        get() = txtState.txtDoubleTapTitleEdit
        internal set(value) { txtState.txtDoubleTapTitleEdit = value }
    var txtSupplementLongPressMode: Boolean
        get() = txtState.txtSupplementLongPressMode
        internal set(value) { txtState.txtSupplementLongPressMode = value }
    var txtCatalogParsing: Boolean
        get() = txtState.txtCatalogParsing
        internal set(value) { txtState.txtCatalogParsing = value }
    var txtMoveChapterSyncPending: Boolean
        get() = txtState.txtMoveChapterSyncPending
        internal set(value) { txtState.txtMoveChapterSyncPending = value }
    var txtMoveChapterSyncWarningMessage: String?
        get() = txtState.txtMoveChapterSyncWarningMessage
        internal set(value) { txtState.txtMoveChapterSyncWarningMessage = value }
    var txtBulkMoveChapterProgress: Float?
        get() = txtState.txtBulkMoveChapterProgress
        internal set(value) { txtState.txtBulkMoveChapterProgress = value }
    var txtBulkMoveChapterProgressText: String
        get() = txtState.txtBulkMoveChapterProgressText
        internal set(value) { txtState.txtBulkMoveChapterProgressText = value }
    internal var txt: TxtDocument?
        get() = txtState.txt
        set(value) { txtState.txt = value }
    internal var txtHiddenCatalogLineIndices: Set<Int>
        get() = txtState.txtHiddenCatalogLineIndices
        set(value) { txtState.txtHiddenCatalogLineIndices = value }
    internal var txtSupplementedCatalogLines: List<TxtSupplementedCatalogLine>
        get() = txtState.txtSupplementedCatalogLines
        set(value) { txtState.txtSupplementedCatalogLines = value }

    var previewTitle: String
        get() = previewState.previewTitle
        internal set(value) { previewState.previewTitle = value }
    var previewText: String
        get() = previewState.previewText
        internal set(value) { previewState.previewText = value }
    var previewHighlightStart: Int
        get() = previewState.previewHighlightStart
        internal set(value) { previewState.previewHighlightStart = value }
    var previewHighlightEnd: Int
        get() = previewState.previewHighlightEnd
        internal set(value) { previewState.previewHighlightEnd = value }
    var previewChapterIndex: Int
        get() = previewState.previewChapterIndex
        internal set(value) { previewState.previewChapterIndex = value }
    internal var previewDisplayChapterIndexOverride: Int?
        get() = previewState.previewDisplayChapterIndexOverride
        set(value) { previewState.previewDisplayChapterIndexOverride = value }
    var previewChapterCount: Int
        get() = previewState.previewChapterCount
        internal set(value) { previewState.previewChapterCount = value }
    var txtPreviewMode: String
        get() = previewState.txtPreviewMode
        internal set(value) { previewState.txtPreviewMode = value }
    internal var txtFullPreviewCachedAnchor: TxtFullPreviewAnchor?
        get() = previewState.txtFullPreviewCachedAnchor
        set(value) { previewState.txtFullPreviewCachedAnchor = value }
    internal var previewHighlightChapterIndex: Int?
        get() = previewState.previewHighlightChapterIndex
        set(value) { previewState.previewHighlightChapterIndex = value }
    internal var previewHighlightSourceStart: Int
        get() = previewState.previewHighlightSourceStart
        set(value) { previewState.previewHighlightSourceStart = value }
    internal var previewHighlightSourceEnd: Int
        get() = previewState.previewHighlightSourceEnd
        set(value) { previewState.previewHighlightSourceEnd = value }
    internal var previewVisibleSourceOffset: Int
        get() = previewState.previewVisibleSourceOffset
        set(value) { previewState.previewVisibleSourceOffset = value }
    internal var previewVisibleSourceLineOffset: Int
        get() = previewState.previewVisibleSourceLineOffset
        set(value) { previewState.previewVisibleSourceLineOffset = value }
    internal var epubVisibleBodyCache: Pair<String, HtmlBodyContentParts>?
        get() = previewState.epubVisibleBodyCache
        set(value) { previewState.epubVisibleBodyCache = value }

    var textSearchResults: List<TextSearchResult>
        get() = toolState.textSearchResults
        internal set(value) { toolState.textSearchResults = value }
    var textSearchToolId: String?
        get() = toolState.textSearchToolId
        internal set(value) { toolState.textSearchToolId = value }
    var replacementFilePreview: ReplacementFilePreview?
        get() = toolState.replacementFilePreview
        internal set(value) { toolState.replacementFilePreview = value }
    var fetchInfoPreview: FetchInfoPreview?
        get() = toolState.fetchInfoPreview
        internal set(value) { toolState.fetchInfoPreview = value }
    var fetchInfoSearchChoiceRequest: FetchInfoSearchChoiceRequest?
        get() = toolState.fetchInfoSearchChoiceRequest
        internal set(value) { toolState.fetchInfoSearchChoiceRequest = value }
    var fetchInfoRetryRequest: FetchInfoRetryRequest?
        get() = toolState.fetchInfoRetryRequest
        internal set(value) { toolState.fetchInfoRetryRequest = value }
    var fetchInfoProgress: Float
        get() = toolState.fetchInfoProgress
        internal set(value) { toolState.fetchInfoProgress = value }
    var fetchInfoFiltering: Boolean
        get() = toolState.fetchInfoFiltering
        internal set(value) { toolState.fetchInfoFiltering = value }
    var sosadLoginInvalid: Boolean
        get() = toolState.sosadLoginInvalid
        internal set(value) { toolState.sosadLoginInvalid = value }
    var selectedReplacementPreviewMatchId: String?
        get() = toolState.selectedReplacementPreviewMatchId
        internal set(value) { toolState.selectedReplacementPreviewMatchId = value }
    internal var textReplaceRuntimeFiles: Map<String, String>
        get() = toolState.textReplaceRuntimeFiles
        set(value) { toolState.textReplaceRuntimeFiles = value }
    var selectedTextSearchResultId: String?
        get() = toolState.selectedTextSearchResultId
        internal set(value) { toolState.selectedTextSearchResultId = value }
    var fileRenamePlan: List<FileRenamePlanItem>
        get() = toolState.fileRenamePlan
        internal set(value) { toolState.fileRenamePlan = value }
    var fileRenamePlanToolId: String?
        get() = toolState.fileRenamePlanToolId
        internal set(value) { toolState.fileRenamePlanToolId = value }
    var titleRenamePlan: List<TitleRenamePlanItem>
        get() = toolState.titleRenamePlan
        internal set(value) { toolState.titleRenamePlan = value }
    var titleRenamePlanToolId: String?
        get() = toolState.titleRenamePlanToolId
        internal set(value) { toolState.titleRenamePlanToolId = value }
    var titleFormatPlan: List<TitleFormatPlanItem>
        get() = toolState.titleFormatPlan
        internal set(value) { toolState.titleFormatPlan = value }
    var titleFormatPlanToolId: String?
        get() = toolState.titleFormatPlanToolId
        internal set(value) { toolState.titleFormatPlanToolId = value }
    var titleFormatPlanAllowsStyleEdit: Boolean
        get() = toolState.titleFormatPlanAllowsStyleEdit
        internal set(value) { toolState.titleFormatPlanAllowsStyleEdit = value }
    var titleFormatPlanLogicText: String
        get() = toolState.titleFormatPlanLogicText
        internal set(value) { toolState.titleFormatPlanLogicText = value }
    var generatedCoverPreview: GeneratedCoverPreview?
        get() = toolState.generatedCoverPreview
        internal set(value) { toolState.generatedCoverPreview = value }
    var insertChapterSourcePreview: InsertChapterSourcePreview?
        get() = toolState.insertChapterSourcePreview
        internal set(value) { toolState.insertChapterSourcePreview = value }
    internal var insertChapterSourceData: InsertChapterSourceData?
        get() = toolState.insertChapterSourceData
        set(value) { toolState.insertChapterSourceData = value }
    var builtInParameterOverrides: Map<String, Map<String, String>>
        get() = toolState.builtInParameterOverrides
        internal set(value) { toolState.builtInParameterOverrides = value }
    internal var savedBuiltInDefaultOverrides: Map<String, Map<String, String>>
        get() = toolState.savedBuiltInDefaultOverrides
        set(value) { toolState.savedBuiltInDefaultOverrides = value }
    val availableTools: List<ToolDefinition>
        get() = toolState.availableTools
    var editorTools: List<EditorTool>
        get() = toolState.editorTools
        internal set(value) { toolState.editorTools = value }
    var txtTextReplacePresets: List<EditorTool>
        get() = toolState.txtTextReplacePresets
        internal set(value) { toolState.txtTextReplacePresets = value }
    var epubTextReplacePresets: List<EditorTool>
        get() = toolState.epubTextReplacePresets
        internal set(value) { toolState.epubTextReplacePresets = value }
    var selectedEditorToolId: String?
        get() = toolState.selectedEditorToolId
        internal set(value) { toolState.selectedEditorToolId = value }
    var draftEditorTool: EditorTool?
        get() = toolState.draftEditorTool
        internal set(value) { toolState.draftEditorTool = value }
    internal var nextEditorToolNumber: Int
        get() = toolState.nextEditorToolNumber
        set(value) { toolState.nextEditorToolNumber = value }
    internal var nextTxtTextReplacePresetNumber: Int
        get() = toolState.nextTxtTextReplacePresetNumber
        set(value) { toolState.nextTxtTextReplacePresetNumber = value }
    internal var nextEpubTextReplacePresetNumber: Int
        get() = toolState.nextEpubTextReplacePresetNumber
        set(value) { toolState.nextEpubTextReplacePresetNumber = value }

    var automationChains: List<AutomationChain>
        get() = automationState.automationChains
        internal set(value) { automationState.automationChains = value }
    var automationChainGroups: List<String>
        get() = automationState.automationChainGroups
        internal set(value) { automationState.automationChainGroups = value }
    var selectedAutomationChainId: String
        get() = automationState.selectedAutomationChainId
        internal set(value) { automationState.selectedAutomationChainId = value }
    var automationLog: List<String>
        get() = automationState.automationLog
        internal set(value) { automationState.automationLog = value }
    var automationRunStepStatuses: Map<String, AutomationRunStepStatus>
        get() = automationState.automationRunStepStatuses
        internal set(value) { automationState.automationRunStepStatuses = value }
    var automationConfirmationRequest: AutomationConfirmationRequest?
        get() = automationState.automationConfirmationRequest
        internal set(value) { automationState.automationConfirmationRequest = value }
    var automationPendingPreviewToolId: String?
        get() = automationState.automationPendingPreviewToolId
        internal set(value) { automationState.automationPendingPreviewToolId = value }
    var automationPendingPreviewDataId: String?
        get() = automationState.automationPendingPreviewDataId
        internal set(value) { automationState.automationPendingPreviewDataId = value }
    var automationPendingPreviewLabel: String?
        get() = automationState.automationPendingPreviewLabel
        internal set(value) { automationState.automationPendingPreviewLabel = value }
    internal var automationRunExecuted: Int
        get() = automationState.automationRunExecuted
        set(value) { automationState.automationRunExecuted = value }
    internal var automationRunSkipped: Int
        get() = automationState.automationRunSkipped
        set(value) { automationState.automationRunSkipped = value }
    internal var automationRunFailed: Int
        get() = automationState.automationRunFailed
        set(value) { automationState.automationRunFailed = value }
    internal var automationRunStopRequested: Boolean
        get() = automationState.automationRunStopRequested
        set(value) { automationState.automationRunStopRequested = value }
    internal var automationRunStopped: Boolean
        get() = automationState.automationRunStopped
        set(value) { automationState.automationRunStopped = value }
    var draftAutomationChain: AutomationChain?
        get() = automationState.draftAutomationChain
        internal set(value) { automationState.draftAutomationChain = value }
    internal var draftAutomationChainPreviousSelectionId: String
        get() = automationState.draftAutomationChainPreviousSelectionId
        set(value) { automationState.draftAutomationChainPreviousSelectionId = value }
    internal var nextAutomationChainNumber: Int
        get() = automationState.nextAutomationChainNumber
        set(value) { automationState.nextAutomationChainNumber = value }
    internal var nextAutomationStepNumber: Int
        get() = automationState.nextAutomationStepNumber
        set(value) { automationState.nextAutomationStepNumber = value }
    internal var fetchInfoRunResolvedUrls: Map<String, String>
        get() = automationState.fetchInfoRunResolvedUrls
        set(value) { automationState.fetchInfoRunResolvedUrls = value }

    val selectedAutomationChain: AutomationChain?
        get() = draftAutomationChain?.takeIf { it.id == selectedAutomationChainId }
            ?: automationChains.firstOrNull { it.id == selectedAutomationChainId }
    val selectedEditorTool: EditorTool?
        get() = draftEditorTool ?: editorTools.firstOrNull { it.id == selectedEditorToolId }
    val editingToolDraft: Boolean
        get() = draftEditorTool != null
    val creatingToolDraft: Boolean
        get() = draftEditorTool?.id == DRAFT_EDITOR_TOOL_ID
    val creatingAutomationChainDraft: Boolean
        get() = draftAutomationChain?.id == selectedAutomationChainId

    init {
        loadPersistedBuiltInToolDefaults()
        resetBuiltInToolState()
        loadPersistedEditorTools()
        loadPersistedAutomationChains()
        log("EtEditor MVP 已就绪")
    }

    fun dispose() {
        cancelTxtCatalogDetection()
        cancelTxtMoveChapterSync()
        cancelEpubWordCountCalculation()
        controllerScope.cancel()
    }

    companion object {
        private const val SETTINGS_PREFS = "et_editor_settings"
    }
}

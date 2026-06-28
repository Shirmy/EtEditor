package com.eteditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eteditor.core.TxtDocument
import kotlinx.coroutines.Job

internal class EditorTxtState(initialSettings: EditorSettingsPreferenceState) {
    var txtCatalogParseJob: Job? = null
    var txtMoveChapterSyncJob: Job? = null
    var txtMoveChapterSyncRevision = 0
    var txtChapterRulesRefreshDeferred = false
    var txtTextReplacementRefreshDeferred = false
    var txtTextReplacementRefreshApplying = false
    var txtLeftPanelMode by mutableStateOf(initialSettings.txtLeftPanelMode)
    var txtRightPanelMode by mutableStateOf(initialSettings.txtRightPanelMode)
    var txtChapterRulesText by mutableStateOf(initialSettings.txtChapterRulesText)
    var txtEnabledChapterRuleKeys by mutableStateOf<Set<String>>(emptySet())
    var txtPurifyRulesText by mutableStateOf(initialSettings.txtPurifyRulesText)
    var txtBookTitleRulesText by mutableStateOf(initialSettings.txtBookTitleRulesText)
    var txtShortChapterThreshold by mutableStateOf(initialSettings.txtShortChapterThreshold)
    var txtLongChapterThreshold by mutableStateOf(initialSettings.txtLongChapterThreshold)
    var txtShortChapterHintEnabled by mutableStateOf(initialSettings.txtShortChapterHintEnabled)
    var txtLongChapterHintEnabled by mutableStateOf(initialSettings.txtLongChapterHintEnabled)
    var txtChapterHintMode by mutableStateOf(initialSettings.txtChapterHintMode)
    var txtAutoNumberOnSave by mutableStateOf(initialSettings.txtAutoNumberOnSave)
    var txtChapterNumberStartAtOneOnSave by mutableStateOf(initialSettings.txtChapterNumberStartAtOneOnSave)
    var txtDoubleTapEdit by mutableStateOf(initialSettings.txtDoubleTapEdit)
    var txtDoubleTapTitleEdit by mutableStateOf(initialSettings.txtDoubleTapTitleEdit)
    var txtSupplementLongPressMode by mutableStateOf(initialSettings.txtSupplementLongPressMode)
    var txtCatalogParsing by mutableStateOf(false)
    var txtMoveChapterSyncPending by mutableStateOf(false)
    var txtMoveChapterSyncWarningMessage by mutableStateOf<String?>(null)
    var txtBulkMoveChapterProgress by mutableStateOf<Float?>(null)
    var txtBulkMoveChapterProgressText by mutableStateOf("")
    var txt: TxtDocument? = null
    var txtHiddenCatalogLineIndices: Set<Int> = emptySet()
    var txtSupplementedCatalogLines: List<TxtSupplementedCatalogLine> = emptyList()
}

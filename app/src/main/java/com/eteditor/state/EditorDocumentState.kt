package com.eteditor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eteditor.core.ChapterInfo
import com.eteditor.core.CheckReport
import com.eteditor.core.DocumentKind
import com.eteditor.core.EpubBook
import kotlinx.coroutines.Job

internal class EditorDocumentState(initialSettings: EditorSettingsPreferenceState) {
    var kind by mutableStateOf(DocumentKind.None)
    var documentSessionKey by mutableStateOf(0)
    var documentContentVersion by mutableStateOf(0)
    var title by mutableStateOf("未打开文件")
    var subtitle by mutableStateOf("等待打开 EPUB 或 TXT")
    var epubSummaryMeta by mutableStateOf("")
    var epubFileSizeBytes by mutableStateOf<Long?>(null)
    var epubWordCountProgress by mutableStateOf<Float?>(null)
    var epubWordCountProgressText by mutableStateOf("")
    var epubWordCountJob: Job? = null
    var hasUnsavedChanges by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var saveProgress by mutableStateOf<Float?>(null)
    var saveProgressText by mutableStateOf("")
    var bodyOperationProgress by mutableStateOf<Float?>(null)
    var bodyOperationProgressText by mutableStateOf("")
    var saveFailureMessage by mutableStateOf("")
    var chapters by mutableStateOf<List<ChapterInfo>>(emptyList())
    var checkReport by mutableStateOf<CheckReport?>(null)
    var selectedScreen by mutableStateOf(AppScreen.Files)
    var leftRailExpanded by mutableStateOf(initialSettings.leftRailExpanded)
    var hideDirectoryFileNameByDefault by mutableStateOf(initialSettings.hideDirectoryFileNameByDefault)
    var epubHideSection0001FromNcx by mutableStateOf(initialSettings.epubHideSection0001FromNcx)
    var epubLongPressSplitChapter by mutableStateOf(initialSettings.epubLongPressSplitChapter)
    var epubDoubleTapEdit by mutableStateOf(initialSettings.epubDoubleTapEdit)
    var epubLeftPanelMode by mutableStateOf(initialSettings.epubLeftPanelMode)
    var epubRightPanelMode by mutableStateOf(initialSettings.epubRightPanelMode)
    var statusMessage by mutableStateOf("")
    var sourceUri: Uri? = null
    var epub: EpubBook? = null
}

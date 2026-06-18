package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PLAIN_TEXT_FIELD_KEYBOARD_OPTIONS = KeyboardOptions(
    keyboardType = KeyboardType.Text,
    imeAction = ImeAction.None,
    platformImeOptions = PlatformImeOptions(
        "com.google.android.inputmethod.latin.noMicrophoneKey;com.google.android.inputmethod.latin.noPersonalizedLearning"
    ),
    autoCorrectEnabled = false
)

private const val TXT_FULL_EDIT_WINDOW_EXPAND_CHARS = 50_000
private const val TXT_FULL_EDIT_WINDOW_ALIGN_LIMIT = 2_000

private data class TxtFullEditWindowUiState(
    val sourceText: String,
    val sourceStart: Int,
    val sourceEnd: Int,
    val text: String,
    val version: Int,
    val targetLineIndex: Int?,
    val selectionIndex: Int?
) {
    val sourceLength: Int get() = sourceText.length
}

private data class BodyEditTarget(
    val kind: DocumentKind,
    val chapterIndex: Int,
    val txtPreviewMode: String
) {
    val key: String get() = "${kind.name}:$txtPreviewMode:$chapterIndex"
}

private fun alignTxtFullEditWindowStart(text: String, roughStart: Int): Int {
    if (roughStart <= 0) return 0
    val previousBreak = text.lastIndexOf('\n', roughStart)
    val aligned = if (previousBreak >= 0) (previousBreak + 1).coerceIn(0, text.length) else roughStart
    return if (roughStart - aligned <= TXT_FULL_EDIT_WINDOW_ALIGN_LIMIT) aligned else roughStart
}

private fun alignTxtFullEditWindowEnd(text: String, roughEnd: Int): Int {
    if (roughEnd >= text.length) return text.length
    val nextBreak = text.indexOf('\n', roughEnd)
    val aligned = if (nextBreak >= 0) (nextBreak + 1).coerceIn(0, text.length) else roughEnd
    return if (aligned - roughEnd <= TXT_FULL_EDIT_WINDOW_ALIGN_LIMIT) aligned else roughEnd
}

@Composable
internal fun BodyPreview(
    controller: EditorController,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val previewScrollState = rememberScrollState()
    val editScrollState = rememberScrollState()
    val editApproxLineHeightPx = with(LocalDensity.current) { 22.dp.toPx() }
    val isTxtFullPreview = controller.kind == DocumentKind.Txt &&
        controller.txtPreviewMode == TXT_PREVIEW_MODE_FULL
    val previewDisplayChapterIndex = controller.previewDisplayChapterIndex()
    val fullPreviewState = if (isTxtFullPreview && !editing) {
        controller.txtFullPreviewState()
    } else {
        null
    }
    val fullPreviewHighlightRange = if (isTxtFullPreview) fullPreviewState?.highlightRange else null
    val useNativeFullTextEditor = controller.kind == DocumentKind.Txt || controller.kind == DocumentKind.Epub
    var txtFullEditWindow by remember(controller.documentSessionKey) { mutableStateOf<TxtFullEditWindowUiState?>(null) }
    var txtEditAnchorSourceOffset by remember(controller.documentSessionKey) { mutableStateOf<Int?>(null) }
    val useWindowedFullTextEditor = editing && isTxtFullPreview && txtFullEditWindow != null
    var bodyDraft by remember(controller.documentSessionKey) { mutableStateOf(TextFieldValue("")) }
    var editTarget by remember(controller.documentSessionKey) { mutableStateOf<BodyEditTarget?>(null) }
    var editTargetOriginalText by remember(controller.documentSessionKey) { mutableStateOf("") }
    var bodyEditScrollTarget by remember(controller.documentSessionKey) { mutableStateOf(0) }
    var bodyEditCursorScrollOffset by remember(controller.documentSessionKey) { mutableStateOf<Int?>(null) }
    var bodyEditLayoutResult by remember(controller.documentSessionKey) { mutableStateOf<TextLayoutResult?>(null) }
    var modifiedEditTargetKeys by remember(controller.documentSessionKey) { mutableStateOf<Set<String>>(emptySet()) }
    var nativeBodyEditor by remember { mutableStateOf<io.github.rosemoe.sora.widget.CodeEditor?>(null) }
    var bodyEditorFocused by remember { mutableStateOf(false) }
    val bodyFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val hostView = LocalView.current
    var showBodyKeyboardOnEditStart by remember(controller.documentSessionKey) { mutableStateOf(false) }
    val epubLongPressSplitChapter = controller.epubLongPressSplitChapter
    val txtSupplementLongPressMode = controller.txtSupplementLongPressMode
    var supplementDialogLineIndex by remember(controller.documentSessionKey) { mutableStateOf<Int?>(null) }
    var epubSplitDialogRequest by remember(controller.documentSessionKey) { mutableStateOf<Pair<Int, Int>?>(null) }
    fun runBodyStructureOperation(label: String, action: () -> Boolean) {
        if (controller.busy) return
        scope.launch {
            controller.busy = true
            controller.setBodyOperationProgress(0f, "$label：准备")
            try {
                delay(16)
                controller.setBodyOperationProgress(0.45f, "$label：写入")
                delay(16)
                val success = action()
                val resultMessage = controller.statusMessage
                controller.setBodyOperationProgress(1f, "$label：${if (success) "完成" else "失败"}")
                if (!success && resultMessage.isNotBlank()) {
                    controller.statusMessage = resultMessage
                }
                delay(if (success) 260 else 520)
            } catch (error: Throwable) {
                val errorMessage = "$label 失败：${error.message ?: error.javaClass.simpleName}"
                controller.statusMessage = errorMessage
                controller.setBodyOperationProgress(1f, "$label：失败")
                controller.statusMessage = errorMessage
                delay(520)
            } finally {
                controller.busy = false
                controller.clearBodyOperationProgress()
            }
        }
    }
    fun currentBodyEditTarget(): BodyEditTarget {
        return BodyEditTarget(
            kind = controller.kind,
            chapterIndex = controller.previewDisplayChapterIndex(),
            txtPreviewMode = controller.txtPreviewMode
        )
    }
    fun textForBodyEditTarget(target: BodyEditTarget): String {
        return controller.editableBodyTextAt(target.kind, target.chapterIndex, target.txtPreviewMode)
    }
    fun setBodyDraftForTarget(target: BodyEditTarget, selection: Int? = null) {
        val text = textForBodyEditTarget(target)
        editTargetOriginalText = text
        val cursorTarget = selection?.coerceIn(0, text.length)
        bodyEditScrollTarget = 0
        bodyEditCursorScrollOffset = cursorTarget
        bodyEditLayoutResult = null
        bodyDraft = if (selection != null) {
            TextFieldValue(text, TextRange(selection.coerceIn(0, text.length)))
        } else {
            TextFieldValue(text)
        }
    }
    fun createTxtFullEditWindow(anchorSourceOffset: Int?): TxtFullEditWindowUiState? {
        if (!controller.shouldUseTxtFullEditWindow()) return null
        val seed = controller.txtFullEditWindowSeed(anchorSourceOffset)
        if (!seed.windowed) return null
        return TxtFullEditWindowUiState(
            sourceText = seed.sourceText,
            sourceStart = seed.startOffset,
            sourceEnd = seed.endOffset,
            text = seed.sourceText.substring(seed.startOffset, seed.endOffset),
            version = 0,
            targetLineIndex = seed.targetLineIndex.coerceAtLeast(0),
            selectionIndex = seed.targetOffset.coerceAtLeast(0)
        )
    }
    fun startBodyEditing(
        anchorSourceOffset: Int? = null,
        initialSelection: Int? = null,
        showKeyboard: Boolean = false
    ) {
        if (controller.kind == DocumentKind.Txt && controller.txtMoveChapterSyncPending) {
            scope.launchAfterTxtMoveChapterSync(controller, "编辑正文") {
                startBodyEditing(anchorSourceOffset, initialSelection, showKeyboard)
            }
            return
        }
        if (controller.kind == DocumentKind.Txt && controller.warnTxtMoveChapterSyncPending("编辑正文")) return
        val target = currentBodyEditTarget()
        editTarget = target
        showBodyKeyboardOnEditStart = showKeyboard
        txtEditAnchorSourceOffset = anchorSourceOffset
        txtFullEditWindow = if (isTxtFullPreview) createTxtFullEditWindow(anchorSourceOffset) else null
        if (useNativeFullTextEditor) {
            val text = textForBodyEditTarget(target)
            editTargetOriginalText = text
            bodyEditCursorScrollOffset = initialSelection?.coerceIn(0, text.length)
        }
        if (controller.kind == DocumentKind.Txt && !useNativeFullTextEditor && anchorSourceOffset != null) {
            val text = textForBodyEditTarget(target)
            editTargetOriginalText = text
            val selection = controller.txtEditableBodyOffsetFromSourceOffset(anchorSourceOffset)
                .coerceIn(0, text.length)
            bodyDraft = TextFieldValue(text, TextRange(selection))
        }
        if (!useNativeFullTextEditor && !(controller.kind == DocumentKind.Txt && anchorSourceOffset != null)) {
            setBodyDraftForTarget(target, initialSelection)
        }
        onEditingChange(true)
    }
    fun expandTxtFullEditWindow(
        edge: TxtFullEditWindowEdge,
        currentText: String,
        visibleLineIndex: Int,
        cursorIndex: Int
    ) {
        val current = txtFullEditWindow ?: return
        when (edge) {
            TxtFullEditWindowEdge.Start -> {
                if (current.sourceStart <= 0) return
                val roughStart = (current.sourceStart - TXT_FULL_EDIT_WINDOW_EXPAND_CHARS).coerceAtLeast(0)
                val newStart = alignTxtFullEditWindowStart(current.sourceText, roughStart)
                if (newStart >= current.sourceStart) return
                val prefix = current.sourceText.substring(newStart, current.sourceStart)
                txtFullEditWindow = current.copy(
                    sourceStart = newStart,
                    text = prefix + currentText,
                    version = current.version + 1,
                    targetLineIndex = visibleLineIndex + prefix.count { it == '\n' },
                    selectionIndex = (cursorIndex + prefix.length).coerceAtLeast(0)
                )
            }
            TxtFullEditWindowEdge.End -> {
                if (current.sourceEnd >= current.sourceLength) return
                val roughEnd = (current.sourceEnd + TXT_FULL_EDIT_WINDOW_EXPAND_CHARS).coerceAtMost(current.sourceLength)
                val newEnd = alignTxtFullEditWindowEnd(current.sourceText, roughEnd)
                if (newEnd <= current.sourceEnd) return
                val suffix = current.sourceText.substring(current.sourceEnd, newEnd)
                txtFullEditWindow = current.copy(
                    sourceEnd = newEnd,
                    text = currentText + suffix,
                    version = current.version + 1,
                    targetLineIndex = visibleLineIndex,
                    selectionIndex = cursorIndex.coerceAtLeast(0)
                )
            }
        }
    }
    fun openSupplementChapterDialog(lineIndex: Int) {
        if (controller.kind == DocumentKind.Txt && controller.txtMoveChapterSyncPending) {
            scope.launchAfterTxtMoveChapterSync(controller, "补章节") {
                openSupplementChapterDialog(lineIndex)
            }
            return
        }
        if (controller.warnTxtMoveChapterSyncPending("补章节")) return
        supplementDialogLineIndex = lineIndex
    }
    fun openEpubSplitDialog(lineIndex: Int) {
        if (controller.kind != DocumentKind.Epub || controller.busy) return
        epubSplitDialogRequest = controller.previewDisplayChapterIndex() to lineIndex
    }
    fun setEpubVolumeFromSelection(sourceStart: Int, sourceEnd: Int) {
        if (controller.kind != DocumentKind.Epub || controller.busy) return
        val chapterIndex = controller.previewDisplayChapterIndex()
        runBodyStructureOperation("EPUB 分卷") {
            if (controller.isEpubPackageTextPreviewSource()) {
                controller.setEpubPackageTextVolumeFromBodySelection(sourceStart, sourceEnd)
            } else {
                controller.setEpubVolumeFromBodySelection(chapterIndex, sourceStart, sourceEnd)
            }
        }
    }
    fun deleteEpubSelection(sourceStart: Int, sourceEnd: Int) {
        if (controller.kind != DocumentKind.Epub || controller.busy) return
        if (controller.isEpubPackageTextPreviewSource()) {
            controller.deleteEpubPackageTextBodySelection(sourceStart, sourceEnd)
        } else {
            controller.deleteEpubBodySelection(controller.previewDisplayChapterIndex(), sourceStart, sourceEnd)
        }
    }
    fun wrapEpubSelection(sourceStart: Int, sourceEnd: Int) {
        if (controller.kind != DocumentKind.Epub || controller.busy) return
        if (controller.isEpubPackageTextPreviewSource()) {
            controller.wrapEpubPackageTextBodySelection(sourceStart, sourceEnd)
        } else {
            controller.wrapEpubBodySelectionWithParagraphs(controller.previewDisplayChapterIndex(), sourceStart, sourceEnd)
        }
    }
    fun deleteTxtSelection(sourceStart: Int, sourceEnd: Int) {
        if (controller.kind == DocumentKind.Txt && controller.txtMoveChapterSyncPending) {
            scope.launchAfterTxtMoveChapterSync(controller, "删除正文行") {
                deleteTxtSelection(sourceStart, sourceEnd)
            }
            return
        }
        if (controller.warnTxtMoveChapterSyncPending("删除正文行")) return
        controller.deleteTxtBodySelection(sourceStart, sourceEnd)
    }
    val txtDoubleTapEditEnabled = controller.kind == DocumentKind.Txt &&
        controller.txtDoubleTapEdit &&
        !controller.busy
    val epubDoubleTapEditEnabled = controller.kind == DocumentKind.Epub &&
        controller.epubDoubleTapEdit &&
        !editing &&
        !controller.busy
    fun openTxtEditFromPreview(visibleOffset: Int) {
        if (txtDoubleTapEditEnabled) {
            startBodyEditing(
                anchorSourceOffset = controller.txtPreviewSourceOffsetFromVisibleOffset(visibleOffset),
                showKeyboard = true
            )
        }
    }
    fun openEpubEditFromPreview(visibleOffset: Int) {
        if (epubDoubleTapEditEnabled) {
            val editableText = textForBodyEditTarget(currentBodyEditTarget())
            // 直接用已记录的可见窗口源偏移，而不是用 indexOf 反查窗口起点：
            // 章节较长、从中间开窗显示时，预览文本可能在本章前面有重复内容，
            // indexOf 会命中错误的第一处导致光标定位偏移；选区操作一直用的就是这个偏移。
            val windowStart = controller.previewVisibleSourceOffsetValue()
            startBodyEditing(
                initialSelection = (windowStart + visibleOffset).coerceIn(0, editableText.length),
                showKeyboard = true
            )
        }
    }
    LaunchedEffect(
        editing,
        controller.kind,
        previewDisplayChapterIndex,
        controller.txtPreviewMode,
        controller.documentContentVersion,
        controller.selectedTextSearchResultId,
        controller.selectedReplacementPreviewMatchId,
        controller.previewHighlightStart,
        controller.previewHighlightEnd,
        fullPreviewHighlightRange
    ) {
        val hasPendingHighlight = if (isTxtFullPreview) {
            fullPreviewHighlightRange != null
        } else {
            controller.previewHighlightStart >= 0 &&
                controller.previewHighlightEnd > controller.previewHighlightStart
        }
        if (!hasPendingHighlight) {
            previewScrollState.scrollTo(0)
        }
        var editScrollTarget = if (editing && !useNativeFullTextEditor) bodyEditScrollTarget else 0
        if (editing && !useNativeFullTextEditor && editTarget == null) {
            val target = currentBodyEditTarget()
            val text = textForBodyEditTarget(target)
            editTarget = target
            editTargetOriginalText = text
            val anchorOffset = txtEditAnchorSourceOffset
            bodyDraft = if (anchorOffset != null && controller.kind == DocumentKind.Txt) {
                val selection = controller.txtEditableBodyOffsetFromSourceOffset(anchorOffset)
                    .coerceIn(0, text.length)
                editScrollTarget = (text
                    .take(selection)
                    .count { it == '\n' } * editApproxLineHeightPx)
                    .roundToInt()
                TextFieldValue(text, TextRange(selection))
            } else {
                TextFieldValue(text)
            }
            bodyEditScrollTarget = editScrollTarget
        }
        editScrollState.scrollTo(editScrollTarget.coerceAtLeast(0))
        if (editScrollTarget > 0) {
            delay(40)
            editScrollState.scrollTo(editScrollTarget.coerceIn(0, editScrollState.maxValue))
        }
    }
    LaunchedEffect(editing, useNativeFullTextEditor, bodyEditLayoutResult, bodyEditCursorScrollOffset) {
        if (!editing || useNativeFullTextEditor) return@LaunchedEffect
        val layout = bodyEditLayoutResult ?: return@LaunchedEffect
        val offset = bodyEditCursorScrollOffset ?: return@LaunchedEffect
        val safeOffset = offset.coerceIn(0, layout.layoutInput.text.length)
        val line = layout.getLineForOffset(safeOffset)
        val lineTop = (layout.getLineTop(line) - editApproxLineHeightPx).roundToInt().coerceAtLeast(0)
        delay(16)
        editScrollState.scrollTo(lineTop.coerceIn(0, editScrollState.maxValue))
        bodyEditCursorScrollOffset = null
    }
    LaunchedEffect(editing, useNativeFullTextEditor) {
        if (!editing) {
            txtEditAnchorSourceOffset = null
            editTarget = null
            editTargetOriginalText = ""
            bodyEditScrollTarget = 0
            bodyEditCursorScrollOffset = null
            bodyEditLayoutResult = null
            modifiedEditTargetKeys = emptySet()
        }
        if (!editing) {
            nativeBodyEditor = null
        }
        if (!editing) {
            txtFullEditWindow = null
        }
    }
    LaunchedEffect(editing, useNativeFullTextEditor, showBodyKeyboardOnEditStart, nativeBodyEditor) {
        if (!editing || !showBodyKeyboardOnEditStart) return@LaunchedEffect
        if (useNativeFullTextEditor) {
            val editor = nativeBodyEditor ?: return@LaunchedEffect
            // 开着替换预览(分屏)时原生编辑器排版较慢,原本固定等 120ms 只试一次,
            // 会在编辑器就绪前抢焦点,导致进了编辑态却没有光标。改为等编辑器完成排版并可见后再要焦点,
            // 最多等约 1.5 秒兜底,拿到焦点即收手。
            var waited = 0
            while (
                waited < 1500 &&
                !(editor.isAttachedToWindow &&
                    editor.isLaidOut &&
                    editor.width > 0 &&
                    editor.height > 0 &&
                    editor.alpha >= 1f)
            ) {
                delay(32)
                waited += 32
            }
            editor.requestFocusAndShowKeyboard()
        } else {
            delay(120)
            runCatching { bodyFocusRequester.requestFocus() }
            hostView.showSoftKeyboard()
        }
        showBodyKeyboardOnEditStart = false
    }
    fun currentEditorTextForTarget(target: BodyEditTarget): String {
        return if (target.kind == DocumentKind.Txt || target.kind == DocumentKind.Epub) {
            nativeBodyEditor?.getText()?.toString() ?: txtFullEditWindow?.text ?: textForBodyEditTarget(target)
        } else {
            bodyDraft.text
        }
    }
    fun saveBodyEditingToTarget(target: BodyEditTarget, showNoChangeMessage: Boolean): Boolean {
        val nextText = currentEditorTextForTarget(target)
        val activeWindow = txtFullEditWindow
        val changed = if (
            target.kind == DocumentKind.Txt &&
            target.txtPreviewMode == TXT_PREVIEW_MODE_FULL &&
            activeWindow != null
        ) {
            activeWindow.text != nextText
        } else {
            textForBodyEditTarget(target) != nextText
        }
        val saved = if (target.kind == DocumentKind.Txt && target.txtPreviewMode == TXT_PREVIEW_MODE_FULL && txtFullEditWindow != null) {
            val window = activeWindow
            window != null && controller.updateTxtFullEditWindowText(nextText, window.sourceStart, window.sourceEnd)
        } else {
            controller.updateEditableBodyTextAt(
                targetKind = target.kind,
                chapterIndex = target.chapterIndex,
                previewMode = target.txtPreviewMode,
                text = nextText,
                showNoChangeMessage = showNoChangeMessage
            )
        }
        if (saved && changed) {
            modifiedEditTargetKeys = modifiedEditTargetKeys + target.key
            if (target == editTarget) {
                editTargetOriginalText = nextText
            }
        }
        return saved
    }
    LaunchedEffect(editing, controller.kind, previewDisplayChapterIndex, controller.txtPreviewMode) {
        if (!editing) return@LaunchedEffect
        val nextTarget = currentBodyEditTarget()
        val previousTarget = editTarget
        if (previousTarget == null) {
            editTarget = nextTarget
            if (!useNativeFullTextEditor) {
                setBodyDraftForTarget(nextTarget)
            } else {
                editTargetOriginalText = textForBodyEditTarget(nextTarget)
            }
        } else if (previousTarget != nextTarget) {
            saveBodyEditingToTarget(previousTarget, showNoChangeMessage = false)
            editTarget = nextTarget
            txtEditAnchorSourceOffset = null
            txtFullEditWindow = if (isTxtFullPreview) createTxtFullEditWindow(null) else null
            bodyEditCursorScrollOffset = null
            if (!useNativeFullTextEditor) {
                nativeBodyEditor = null
                setBodyDraftForTarget(nextTarget)
            } else {
                editTargetOriginalText = textForBodyEditTarget(nextTarget)
            }
        }
    }
    fun cancelBodyEditing() {
        focusManager.clearFocus()
        txtEditAnchorSourceOffset = null
        onEditingChange(false)
    }
    fun saveBodyEditing() {
        if (controller.kind == DocumentKind.Txt && controller.txtMoveChapterSyncPending) {
            scope.launchAfterTxtMoveChapterSync(controller, "编辑正文") {
                saveBodyEditing()
            }
            return
        }
        val target = editTarget ?: currentBodyEditTarget()
        val nativeCursorIndex = nativeBodyEditor?.let { editor ->
            runCatching { editor.getCursor().getLeft().coerceAtLeast(0) }.getOrNull()
        }
        val epubEditCursorIndex = if (controller.kind == DocumentKind.Epub) nativeCursorIndex else null
        val txtEditAnchorOffset = if (controller.kind == DocumentKind.Txt && nativeCursorIndex != null) {
            val window = txtFullEditWindow
            when {
                target.txtPreviewMode == TXT_PREVIEW_MODE_FULL && window != null -> {
                    window.sourceStart + nativeCursorIndex
                }
                target.txtPreviewMode == TXT_PREVIEW_MODE_FULL -> nativeCursorIndex
                else -> {
                    val document = controller.txt
                    val chapter = document?.chapters?.getOrNull(target.chapterIndex)
                    val chapterStart = chapter?.startIndex ?: 0
                    chapterStart + nativeCursorIndex
                }
            }
        } else null
        val saved = saveBodyEditingToTarget(target, showNoChangeMessage = true)
        if (saved) {
            if (controller.kind == DocumentKind.Epub && epubEditCursorIndex != null && epubEditCursorIndex >= 0) {
                controller.locateEpubPreviewAtBodyOffset(target.chapterIndex, epubEditCursorIndex)
            } else if (controller.kind == DocumentKind.Txt && txtEditAnchorOffset != null) {
                val document = controller.txt
                if (document != null) {
                    controller.restoreTxtPreviewPositionForSourceOffset(document, txtEditAnchorOffset)
                    controller.refreshPreview()
                }
            }
            txtFullEditWindow = null
            txtEditAnchorSourceOffset = null
            focusManager.clearFocus()
            onEditingChange(false)
        }
    }
    val currentEditTarget = editTarget
    val editModifiedCount = if (
        editing &&
        currentEditTarget != null &&
        !useNativeFullTextEditor &&
        bodyDraft.text != editTargetOriginalText
    ) {
        (modifiedEditTargetKeys + currentEditTarget.key).size
    } else {
        modifiedEditTargetKeys.size
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BodyPreviewHeader(
            controller = controller,
            editing = editing,
            modifiedCount = editModifiedCount,
            onPreviousChapter = {
                scope.launchAfterTxtMoveChapterSync(controller, "切换章节预览") {
                    controller.previousPreviewChapter()
                }
            },
            onNextChapter = {
                scope.launchAfterTxtMoveChapterSync(controller, "切换章节预览") {
                    controller.nextPreviewChapter()
                }
            },
            onToggleEpubLongPressSplitChapter = {
                controller.updateEpubLongPressSplitChapter(!epubLongPressSplitChapter)
            },
            onToggleTxtSupplementLongPressMode = {
                controller.updateTxtSupplementLongPressMode(!txtSupplementLongPressMode)
            },
            onFormatTxt = {
                scope.launchAfterTxtMoveChapterSync(controller, "格式整理") {
                    controller.formatTxtDefault()
                }
            },
            onStartEditing = { startBodyEditing() },
            onSaveEditing = { saveBodyEditing() },
            onCancelEditing = { cancelBodyEditing() }
        )
        Surface(
            shape = PreviewShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (editing) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        // 编辑态底部不留白：键盘弹起时（imePadding 已把底部抬到键盘上方），
                        // 去掉这 8dp 下内边距，编辑器底部直接贴键盘上沿，不再露出 Surface 白底那条带。
                        .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (useNativeFullTextEditor) {
                            val window = txtFullEditWindow
                            val editAnchorOffset = txtEditAnchorSourceOffset
                            val nativeEditorText = if (useWindowedFullTextEditor && window != null) {
                                window.text
                            } else {
                                textForBodyEditTarget(editTarget ?: currentBodyEditTarget())
                            }
                            val nativeSelectionTargetIndex = if (useWindowedFullTextEditor) {
                                window?.selectionIndex
                            } else {
                                editAnchorOffset
                                    ?.let(controller::txtEditableBodyOffsetFromSourceOffset)
                                    ?.coerceIn(0, nativeEditorText.length)
                                    ?: bodyEditCursorScrollOffset?.coerceIn(0, nativeEditorText.length)
                            }
                            val nativeScrollTargetLineIndex = if (useWindowedFullTextEditor) {
                                window?.targetLineIndex
                            } else {
                                nativeSelectionTargetIndex?.let { selection ->
                                    nativeEditorText
                                        .take(selection.coerceIn(0, nativeEditorText.length))
                                        .count { it == '\n' }
                                }
                            }
                            LargeBodyCodeEditor(
                                text = nativeEditorText,
                                contentKey = if (useWindowedFullTextEditor && window != null) {
                                    listOf(
                                        "txt-full-edit-window",
                                        controller.documentSessionKey,
                                        controller.documentContentVersion,
                                        window.sourceStart,
                                        window.sourceEnd,
                                        window.version
                                    )
                                } else {
                                    listOf(
                                        controller.documentSessionKey,
                                        controller.documentContentVersion,
                                        previewDisplayChapterIndex,
                                        controller.txtPreviewMode
                                    )
                                },
                                scrollTargetLineIndex = nativeScrollTargetLineIndex,
                                selectionTargetIndex = nativeSelectionTargetIndex,
                                windowSourceStart = window?.sourceStart,
                                windowSourceEnd = window?.sourceEnd,
                                windowSourceLength = window?.sourceLength,
                                onWindowEdgeReached = if (useWindowedFullTextEditor) {
                                    { edge, currentText, visibleLineIndex, cursorIndex ->
                                        expandTxtFullEditWindow(edge, currentText, visibleLineIndex, cursorIndex)
                                    }
                                } else {
                                    null
                                },
                                onEditorReady = { nativeBodyEditor = it },
                                onFocusChanged = { bodyEditorFocused = it },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(end = 4.dp)
                            )
                        } else {
                            BasicTextField(
                                value = bodyDraft,
                                onValueChange = { nextValue ->
                                    bodyDraft = nextValue
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = PLAIN_TEXT_FIELD_KEYBOARD_OPTIONS,
                                onTextLayout = { bodyEditLayoutResult = it },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusRequester(bodyFocusRequester)
                                    .verticalScroll(editScrollState)
                                    .padding(end = 16.dp)
                                    .onFocusChanged { bodyEditorFocused = it.isFocused }
                            )
                            ContentScrollbar(
                                state = editScrollState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(vertical = 2.dp),
                                prominent = true
                            )
                        }
                    }
                }
            } else {
                BodyReadOnlyPreviewContent(
                    controller = controller,
                    isTxtFullPreview = isTxtFullPreview,
                    fullPreviewState = fullPreviewState,
                    fullPreviewHighlightRange = fullPreviewHighlightRange,
                    previewScrollState = previewScrollState,
                    editing = editing,
                    txtSupplementLongPressMode = txtSupplementLongPressMode,
                    txtDoubleTapEditEnabled = txtDoubleTapEditEnabled,
                    epubDoubleTapEditEnabled = epubDoubleTapEditEnabled,
                    onOpenTxtEditFromPreview = { visibleOffset -> openTxtEditFromPreview(visibleOffset) },
                    onOpenEpubEditFromPreview = { visibleOffset -> openEpubEditFromPreview(visibleOffset) },
                    onOpenSupplementChapterDialog = { lineIndex -> openSupplementChapterDialog(lineIndex) },
                    onOpenEpubSplitDialog = { lineIndex -> openEpubSplitDialog(lineIndex) },
                    onSetEpubVolumeFromSelection = { sourceStart, sourceEnd ->
                        setEpubVolumeFromSelection(sourceStart, sourceEnd)
                    },
                    onWrapEpubSelection = { sourceStart, sourceEnd -> wrapEpubSelection(sourceStart, sourceEnd) },
                    onDeleteEpubSelection = { sourceStart, sourceEnd -> deleteEpubSelection(sourceStart, sourceEnd) },
                    onDeleteTxtSelection = { sourceStart, sourceEnd -> deleteTxtSelection(sourceStart, sourceEnd) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    supplementDialogLineIndex?.let { lineIndex ->
        TxtSupplementChapterDialog(
            lineText = controller.txtLineText(lineIndex),
            initialChapterNumber = controller.suggestTxtSupplementChapterNumber(lineIndex),
            onDismiss = { supplementDialogLineIndex = null },
            onConfirm = { chapterNumber ->
                supplementDialogLineIndex = null
                runBodyStructureOperation("TXT 分章") {
                    controller.supplementTxtChapterLine(lineIndex, chapterNumber)
                }
                null
            }
        )
    }
    epubSplitDialogRequest?.let { (chapterIndex, lineIndex) ->
        EpubLongPressSplitChapterDialog(
            controller = controller,
            chapterIndex = chapterIndex,
            lineIndex = lineIndex,
            onDismiss = { epubSplitDialogRequest = null },
            onConfirm = { title ->
                epubSplitDialogRequest = null
                runBodyStructureOperation("EPUB 分章") {
                    controller.splitEpubChapterAtBodyLine(chapterIndex, lineIndex, title)
                }
            }
        )
    }
}

package com.eteditor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind

@Composable
internal fun BodyReadOnlyPreviewContent(
    controller: EditorController,
    isTxtFullPreview: Boolean,
    fullPreviewState: TxtFullPreviewState?,
    fullPreviewHighlightRange: Pair<Int, Int>?,
    previewScrollState: ScrollState,
    editing: Boolean,
    txtSupplementLongPressMode: Boolean,
    txtDoubleTapEditEnabled: Boolean,
    epubDoubleTapEditEnabled: Boolean,
    onOpenTxtEditFromPreview: (Int) -> Unit,
    onOpenEpubEditFromPreview: (Int) -> Unit,
    onOpenSupplementChapterDialog: (Int) -> Unit,
    onOpenEpubSplitDialog: (Int) -> Unit,
    onSetEpubVolumeFromSelection: (Int, Int) -> Unit,
    onWrapEpubSelection: (Int, Int) -> Unit,
    onDeleteEpubSelection: (Int, Int) -> Unit,
    onDeleteTxtSelection: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasPreview = controller.previewText.isNotBlank()
    val previewText = controller.previewText.ifBlank { "没有可预览的正文" }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        if (controller.kind == DocumentKind.Txt && isTxtFullPreview) {
            TxtFullReadOnlyPreviewContent(
                controller = controller,
                isTxtFullPreview = isTxtFullPreview,
                fullPreviewState = fullPreviewState,
                fullPreviewHighlightRange = fullPreviewHighlightRange,
                previewText = previewText,
                hasPreview = hasPreview,
                txtSupplementLongPressMode = txtSupplementLongPressMode,
                txtDoubleTapEditEnabled = txtDoubleTapEditEnabled,
                onOpenTxtEditFromPreview = onOpenTxtEditFromPreview,
                onOpenSupplementChapterDialog = onOpenSupplementChapterDialog,
                onDeleteTxtSelection = onDeleteTxtSelection,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
            )
        } else {
            BodyPlainReadOnlyPreviewContent(
                controller = controller,
                isTxtFullPreview = isTxtFullPreview,
                fullPreviewHighlightRange = fullPreviewHighlightRange,
                editing = editing,
                txtSupplementLongPressMode = txtSupplementLongPressMode,
                txtDoubleTapEditEnabled = txtDoubleTapEditEnabled,
                epubDoubleTapEditEnabled = epubDoubleTapEditEnabled,
                hasPreview = hasPreview,
                previewText = previewText,
                onOpenTxtEditFromPreview = onOpenTxtEditFromPreview,
                onOpenEpubEditFromPreview = onOpenEpubEditFromPreview,
                onOpenSupplementChapterDialog = onOpenSupplementChapterDialog,
                onOpenEpubSplitDialog = onOpenEpubSplitDialog,
                onSetEpubVolumeFromSelection = onSetEpubVolumeFromSelection,
                onWrapEpubSelection = onWrapEpubSelection,
                onDeleteEpubSelection = onDeleteEpubSelection,
                onDeleteTxtSelection = onDeleteTxtSelection,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun TxtFullReadOnlyPreviewContent(
    controller: EditorController,
    isTxtFullPreview: Boolean,
    fullPreviewState: TxtFullPreviewState?,
    fullPreviewHighlightRange: Pair<Int, Int>?,
    previewText: String,
    hasPreview: Boolean,
    txtSupplementLongPressMode: Boolean,
    txtDoubleTapEditEnabled: Boolean,
    onOpenTxtEditFromPreview: (Int) -> Unit,
    onOpenSupplementChapterDialog: (Int) -> Unit,
    onDeleteTxtSelection: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val startLineIndex = controller.txtPreviewVisibleStartLineIndex()
    val chapterPreviewHighlightRange = if (!isTxtFullPreview) {
        val start = controller.previewHighlightStart
        val end = controller.previewHighlightEnd
        (start to end).takeIf {
            hasPreview &&
                start >= 0 &&
                end > start &&
                start <= previewText.length
        }?.let { (rangeStart, rangeEnd) ->
            rangeStart.coerceIn(0, previewText.length) to
                rangeEnd.coerceIn(rangeStart.coerceIn(0, previewText.length), previewText.length)
        }
    } else {
        null
    }
    val txtPreviewScrollTarget = if (isTxtFullPreview && fullPreviewHighlightRange == null) {
        fullPreviewState?.scrollTargetOffset
    } else {
        null
    }
    val txtPreviewScrollTargetLine = if (isTxtFullPreview && fullPreviewHighlightRange == null) {
        fullPreviewState?.scrollTargetLineIndex
    } else {
        null
    }
    val fullPreviewSourceText = fullPreviewState?.text.orEmpty()
    val fullPreviewWindowKey = fullPreviewState?.windowKey.orEmpty()
    val fullPreviewStartLineIndex = fullPreviewState?.startLineIndex ?: 0
    val fullPreviewStartOffset = fullPreviewWindowKey.substringBefore(':').toIntOrNull() ?: 0
    val fullPreviewText = fullPreviewSourceText.ifBlank { "没有可预览的正文" }
    val previewDisplayChapterIndex = controller.previewDisplayChapterIndex()
    TxtCachedReadOnlyPreview(
        mode = controller.txtPreviewMode,
        fullText = fullPreviewText,
        fullContentKey = listOf(
            "txt-full",
            controller.documentSessionKey,
            controller.documentContentVersion,
            fullPreviewWindowKey
        ),
        fullPositionKey = listOf(
            "txt-full-position",
            controller.txtPreviewMode,
            fullPreviewWindowKey,
            fullPreviewHighlightRange,
            txtPreviewScrollTarget,
            txtPreviewScrollTargetLine,
            controller.selectedTextSearchResultId,
            controller.selectedReplacementPreviewMatchId
        ),
        fullHighlightRange = fullPreviewHighlightRange,
        fullScrollTargetOffset = txtPreviewScrollTarget,
        fullScrollTargetLineIndex = txtPreviewScrollTargetLine,
        chapterText = if (isTxtFullPreview) "" else previewText,
        chapterContentKey = listOf(
            "txt-chapter",
            controller.documentSessionKey,
            controller.documentContentVersion,
            previewDisplayChapterIndex,
            startLineIndex,
            controller.previewText.length,
            controller.selectedTextSearchResultId,
            controller.selectedReplacementPreviewMatchId
        ),
        chapterPositionKey = listOf(
            "txt-chapter-position",
            controller.previewHighlightStart,
            controller.previewHighlightEnd,
            startLineIndex,
            controller.selectedTextSearchResultId,
            controller.selectedReplacementPreviewMatchId
        ),
        chapterHighlightRange = chapterPreviewHighlightRange,
        onDoubleTap = if (txtDoubleTapEditEnabled) {
            { visibleOffset -> onOpenTxtEditFromPreview(visibleOffset) }
        } else {
            null
        },
        onFullLongPressLine = if (fullPreviewSourceText.isNotBlank()) {
            if (txtSupplementLongPressMode) {
                { lineIndex ->
                    onOpenSupplementChapterDialog(fullPreviewStartLineIndex + lineIndex)
                }
            } else {
                null
            }
        } else {
            null
        },
        onChapterLongPressLine = if (hasPreview) {
            if (txtSupplementLongPressMode) {
                { lineIndex ->
                    onOpenSupplementChapterDialog(startLineIndex + lineIndex)
                }
            } else {
                null
            }
        } else {
            null
        },
        onFullDeleteSelection = if (fullPreviewSourceText.isNotBlank() && !txtSupplementLongPressMode) {
            { selectionStart, selectionEnd ->
                onDeleteTxtSelection(
                    fullPreviewStartOffset + selectionStart,
                    fullPreviewStartOffset + selectionEnd
                )
            }
        } else {
            null
        },
        modifier = modifier
    )
}

@Composable
private fun BodyPlainReadOnlyPreviewContent(
    controller: EditorController,
    isTxtFullPreview: Boolean,
    fullPreviewHighlightRange: Pair<Int, Int>?,
    editing: Boolean,
    txtSupplementLongPressMode: Boolean,
    txtDoubleTapEditEnabled: Boolean,
    epubDoubleTapEditEnabled: Boolean,
    hasPreview: Boolean,
    previewText: String,
    onOpenTxtEditFromPreview: (Int) -> Unit,
    onOpenEpubEditFromPreview: (Int) -> Unit,
    onOpenSupplementChapterDialog: (Int) -> Unit,
    onOpenEpubSplitDialog: (Int) -> Unit,
    onSetEpubVolumeFromSelection: (Int, Int) -> Unit,
    onWrapEpubSelection: (Int, Int) -> Unit,
    onDeleteEpubSelection: (Int, Int) -> Unit,
    onDeleteTxtSelection: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (!hasPreview) {
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            )
            return@Box
        }
        val previewHighlightStart = if (isTxtFullPreview) {
            fullPreviewHighlightRange?.first ?: -1
        } else {
            controller.previewHighlightStart
        }
        val previewHighlightEnd = if (isTxtFullPreview) {
            fullPreviewHighlightRange?.second ?: -1
        } else {
            controller.previewHighlightEnd
        }
        val epubPackageTextPreview = controller.isEpubPackageTextPreviewSource()
        val epubLongPressActionEnabled = controller.kind == DocumentKind.Epub &&
            hasPreview &&
            !editing &&
            !controller.busy &&
            !epubPackageTextPreview
        val epubLongPressSplitEnabled = epubLongPressActionEnabled &&
            controller.epubLongPressSplitChapter
        val startLineIndex = when (controller.kind) {
            DocumentKind.Epub -> controller.epubPreviewVisibleStartLineIndex()
            DocumentKind.Txt -> controller.txtPreviewVisibleStartLineIndex()
            DocumentKind.None -> 0
        }
        val previewDisplayChapterIndex = controller.previewDisplayChapterIndex()
        val directLongPressGestureEnabled =
            (controller.kind == DocumentKind.Txt && txtSupplementLongPressMode && hasPreview) ||
                epubLongPressSplitEnabled
        val previewHighlightRange = (previewHighlightStart to previewHighlightEnd).takeIf { (start, end) ->
            hasPreview &&
                start >= 0 &&
                end > start &&
                start <= previewText.length
        }?.let { (start, end) ->
            start.coerceIn(0, previewText.length) to
                end.coerceIn(start.coerceIn(0, previewText.length), previewText.length)
        }
        val onDoubleTap = if (txtDoubleTapEditEnabled || (epubDoubleTapEditEnabled && !epubPackageTextPreview)) {
            { visibleOffset: Int ->
                if (controller.kind == DocumentKind.Epub) {
                    onOpenEpubEditFromPreview(visibleOffset)
                } else {
                    onOpenTxtEditFromPreview(visibleOffset)
                }
            }
        } else {
            null
        }
        val onLongPressLine = if (directLongPressGestureEnabled) {
            { visibleLineIndex: Int ->
                val sourceLine = startLineIndex + visibleLineIndex
                if (controller.kind == DocumentKind.Epub) {
                    onOpenEpubSplitDialog(sourceLine)
                } else if (txtSupplementLongPressMode) {
                    onOpenSupplementChapterDialog(sourceLine)
                }
            }
        } else {
            null
        }
        val selectionActions = if (!hasPreview || directLongPressGestureEnabled || controller.busy) {
            emptyList()
        } else when (controller.kind) {
            DocumentKind.Epub -> listOf(
                BodyReadOnlySelectionAction("分卷") { selectionStart, selectionEnd ->
                    val sourceStart = controller.previewVisibleSourceOffsetValue() +
                        selectionStart.coerceIn(0, previewText.length)
                    val sourceEnd = controller.previewVisibleSourceOffsetValue() +
                        selectionEnd.coerceIn(0, previewText.length)
                    onSetEpubVolumeFromSelection(sourceStart.coerceAtMost(sourceEnd), sourceStart.coerceAtLeast(sourceEnd))
                },
                BodyReadOnlySelectionAction("加标签") { selectionStart, selectionEnd ->
                    val sourceStart = controller.previewVisibleSourceOffsetValue() +
                        selectionStart.coerceIn(0, previewText.length)
                    val sourceEnd = controller.previewVisibleSourceOffsetValue() +
                        selectionEnd.coerceIn(0, previewText.length)
                    onWrapEpubSelection(sourceStart.coerceAtMost(sourceEnd), sourceStart.coerceAtLeast(sourceEnd))
                },
                BodyReadOnlySelectionAction("删除") { selectionStart, selectionEnd ->
                    val sourceStart = controller.previewVisibleSourceOffsetValue() +
                        selectionStart.coerceIn(0, previewText.length)
                    val sourceEnd = controller.previewVisibleSourceOffsetValue() +
                        selectionEnd.coerceIn(0, previewText.length)
                    onDeleteEpubSelection(sourceStart.coerceAtMost(sourceEnd), sourceStart.coerceAtLeast(sourceEnd))
                }
            )
            DocumentKind.Txt -> listOf(
                BodyReadOnlySelectionAction("删除") { selectionStart, selectionEnd ->
                    val sourceStart = controller.txtPreviewSourceOffsetFromVisibleOffset(
                        selectionStart.coerceIn(0, previewText.length)
                    )
                    val sourceEnd = controller.txtPreviewSourceOffsetFromVisibleOffset(
                        selectionEnd.coerceIn(0, previewText.length)
                    )
                    onDeleteTxtSelection(sourceStart.coerceAtMost(sourceEnd), sourceStart.coerceAtLeast(sourceEnd))
                }
            )
            DocumentKind.None -> emptyList()
        }
        LargeBodyReadOnlyPreview(
            text = previewText,
            contentKey = listOf(
                "plain-read-only",
                controller.kind,
                controller.documentSessionKey,
                controller.documentContentVersion,
                previewDisplayChapterIndex,
                controller.txtPreviewMode,
                startLineIndex,
                previewText.length,
                controller.selectedTextSearchResultId,
                controller.selectedReplacementPreviewMatchId
            ),
            positionKey = listOf(
                "plain-read-only-position",
                controller.previewHighlightStart,
                controller.previewHighlightEnd,
                startLineIndex,
                controller.selectedTextSearchResultId,
                controller.selectedReplacementPreviewMatchId
            ),
            highlightRange = previewHighlightRange,
            scrollTargetOffset = if (
                previewHighlightRange == null &&
                hasPreview &&
                previewHighlightStart >= 0 &&
                previewHighlightStart <= previewText.length
            ) {
                previewHighlightStart
            } else {
                null
            },
            scrollTargetLineIndex = null,
            interactive = hasPreview,
            showLoading = true,
            onDoubleTap = onDoubleTap,
            onLongPressLine = onLongPressLine,
            selectionActions = selectionActions,
            modifier = Modifier.fillMaxSize()
        )
    }
}

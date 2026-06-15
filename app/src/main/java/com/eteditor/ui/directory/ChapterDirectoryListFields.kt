package com.eteditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eteditor.core.ChapterInfo
import com.eteditor.core.DocumentKind

@Composable
internal fun ChapterDirectoryList(
    controller: EditorController,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
    hideFileName: Boolean = false,
    reverseOrder: Boolean = false,
    expandAllRequest: Int = 0,
    collapseAllRequest: Int = 0,
    scrollToChapterIndexRequest: Int? = null,
    onScrollToChapterConsumed: () -> Unit = {},
    onVolumeCollapseStateChange: (Boolean) -> Unit = {},
    moveMode: Boolean = false,
    bulkRemoveMode: Boolean = false,
    bulkMoveMode: Boolean = false,
    bulkDeleteMode: Boolean = false,
    bulkRemoveSelectedChapterIndexes: Set<Int> = emptySet(),
    bulkMoveSelectedChapterIndexes: Set<Int> = emptySet(),
    bulkDeleteSelectedChapterIndexes: Set<Int> = emptySet(),
    moveTargetsByChapter: Map<Int, Int> = emptyMap(),
    onMoveTargetSelected: (Int, Int) -> Unit = { _, _ -> },
    onLongPressChapter: ((ChapterInfo) -> Unit)? = null,
    onPickChapter: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val chapterKeysByIndex = remember(controller.kind, controller.chapters) {
        controller.chapters.associate { chapter ->
            chapter.index to directoryChapterStableKey(controller.kind, chapter)
        }
    }
    val hierarchyKey = remember(controller.kind, controller.chapters) {
        controller.chapters.map { chapter ->
            Triple(directoryChapterStableKey(controller.kind, chapter), chapter.tocLevel, chapter.fileName to chapter.isVolume)
        }
    }
    var collapsedChapterIndexes by remember(controller.documentSessionKey) { mutableStateOf<Set<Int>>(emptySet()) }
    val prefaceChapter = remember(controller.kind, controller.chapters, controller.documentContentVersion) {
        if (controller.kind == DocumentKind.Txt && controller.txtHasPreface()) {
            ChapterInfo(
                index = 0,
                title = "前言",
                wordCount = controller.txtPrefaceWordCount(),
                source = "第 1 行",
                fileName = "第 1 行",
                lineNumber = 1
            )
        } else {
            null
        }
    }
    val hasChildrenByIndex = remember(hierarchyKey) {
        controller.chapters.mapIndexed { chapterIndex, chapter ->
            val nextChapter = controller.chapters.getOrNull(chapterIndex + 1)
            chapter.index to (nextChapter != null && nextChapter.tocLevel > chapter.tocLevel)
        }.toMap()
    }
    val collapsibleVolumeIndexes = remember(hierarchyKey, hasChildrenByIndex) {
        controller.chapters
            .filter { chapter -> hasChildrenByIndex[chapter.index] == true && chapter.isVolume }
            .map { it.index }
            .toSet()
    }
    val displayedChapters = remember(
        controller.chapters,
        prefaceChapter,
        collapsedChapterIndexes,
        reverseOrder,
        controller.kind,
        controller.epubHideSection0001FromNcx
    ) {
        val visibleChapters = mutableListOf<ChapterInfo>()
        var hiddenParentLevel: Int? = null
            val sourceChapters = controller.chapters.filterNot { chapter ->
                controller.kind == DocumentKind.Epub &&
                    controller.isSection0001HiddenFromDirectoryToc(chapter.source)
            }
        sourceChapters.forEach { chapter ->
            val parentLevel = hiddenParentLevel
            if (parentLevel != null && chapter.tocLevel > parentLevel) {
                return@forEach
            }
            if (parentLevel != null && chapter.tocLevel <= parentLevel) {
                hiddenParentLevel = null
            }
            visibleChapters += chapter
            if (chapter.index in collapsedChapterIndexes) {
                hiddenParentLevel = chapter.tocLevel
            }
        }
        val realChapters = if (reverseOrder) visibleChapters.asReversed() else visibleChapters
        if (prefaceChapter == null) realChapters else listOf(prefaceChapter) + realChapters
    }
    val epubShowFileName = controller.kind == DocumentKind.Epub && !hideFileName
    val fixedDirectoryItemHeight = if (controller.kind == DocumentKind.Epub) {
        epubDirectoryRowHeight(epubShowFileName) + 3.dp
    } else {
        null
    }
    var initialCurrentChapterScrollPending by remember(controller.kind, controller.documentSessionKey) {
        mutableStateOf(true)
    }
    fun displayIndexForPreviewChapter(chapterIndex: Int): Int {
        return displayedChapters.indexOfFirst { chapter ->
            if (chapterIndex < 0) {
                chapter.index <= 0
            } else {
                chapter.index > 0 && chapter.index - 1 == chapterIndex
            }
        }
    }
    val moveTargetOptions = remember(controller.kind, controller.chapters, moveMode) {
        if (!moveMode) {
            emptyList()
        } else {
            listOf(
                DirectoryPickerOption(
                    key = MOVE_TARGET_BOOK_START.toString(),
                    label = "书籍开头",
                    isSpecial = true
                ),
                DirectoryPickerOption(
                    key = MOVE_TARGET_BOOK_END.toString(),
                    label = "书籍结尾",
                    isSpecial = true
                )
            ) + controller.chapters.map { chapter ->
                val title = chapter.title.ifBlank { chapter.fileName }
                DirectoryPickerOption(
                    key = (chapter.index - 1).toString(),
                    label = if (controller.kind == DocumentKind.Txt) "$title 后" else title,
                    tocLevel = chapter.tocLevel,
                    isVolume = chapter.isVolume
                )
            }
        }
    }

    LaunchedEffect(hierarchyKey, collapsibleVolumeIndexes) {
        collapsedChapterIndexes = collapsedChapterIndexes.intersect(collapsibleVolumeIndexes)
    }

    LaunchedEffect(expandAllRequest) {
        if (expandAllRequest > 0) {
            collapsedChapterIndexes = collapsedChapterIndexes - collapsibleVolumeIndexes
        }
    }

    LaunchedEffect(collapseAllRequest) {
        if (collapseAllRequest > 0) {
            collapsedChapterIndexes = collapsedChapterIndexes + collapsibleVolumeIndexes
        }
    }

    LaunchedEffect(collapsedChapterIndexes, collapsibleVolumeIndexes) {
        onVolumeCollapseStateChange(
            collapsibleVolumeIndexes.isNotEmpty() && collapsedChapterIndexes.containsAll(collapsibleVolumeIndexes)
        )
    }

    LaunchedEffect(reverseOrder) {
        if (!initialCurrentChapterScrollPending && displayedChapters.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(displayedChapters, controller.previewChapterIndex, initialCurrentChapterScrollPending) {
        if (!initialCurrentChapterScrollPending || displayedChapters.isEmpty()) return@LaunchedEffect
        val displayIndex = displayIndexForPreviewChapter(controller.previewChapterIndex)
        if (displayIndex >= 0) {
            listState.scrollToItem(displayIndex)
            initialCurrentChapterScrollPending = false
        }
    }

    LaunchedEffect(scrollToChapterIndexRequest, displayedChapters) {
        val targetChapterIndex = scrollToChapterIndexRequest ?: return@LaunchedEffect
        val displayIndex = displayIndexForPreviewChapter(targetChapterIndex)
        if (displayIndex >= 0) {
            listState.animateScrollToItem(displayIndex)
        }
        onScrollToChapterConsumed()
    }

    Box(modifier.fillMaxSize()) {
        if (displayedChapters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (controller.kind == DocumentKind.Txt && controller.txtCatalogParsing) {
                        "目录识别中"
                    } else {
                        "未识别到目录"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 10.dp),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                itemsIndexed(
                    displayedChapters,
                    key = { _, chapter ->
                        if (chapter.index <= 0) {
                            "preface:${chapter.source}:${chapter.title}"
                        } else {
                            chapterKeysByIndex[chapter.index]
                                ?: directoryChapterStableKey(controller.kind, chapter)
                        }
                    },
                    contentType = { _, chapter ->
                        when {
                            chapter.index <= 0 -> "preface"
                            moveMode -> "move"
                            controller.kind == DocumentKind.Txt -> "txt"
                            chapter.tocLevel > 0 -> "nested"
                            else -> "chapter"
                        }
                    }
                ) { displayIndex, chapter ->
                    val longPressHandler = onLongPressChapter
                    val rowBoxPosition = directoryRowBoxPosition(
                        chapter = chapter,
                        previous = displayedChapters.getOrNull(displayIndex - 1),
                        next = displayedChapters.getOrNull(displayIndex + 1)
                    )
                    DirectoryRow(
                        controller = controller,
                        index = chapter.index,
                        fileName = chapter.fileName,
                        title = chapter.title,
                        sequenceLabel = if (controller.kind == DocumentKind.Txt && chapter.index > 0) {
                            chapter.index.toString().padStart(2, '0')
                        } else {
                            null
                        },
                        lineNumber = chapter.lineNumber,
                        wordCount = chapter.wordCount,
                        status = chapter.status,
                        selected = chapter.index - 1 == controller.previewChapterIndex,
                        showFileName = controller.kind == DocumentKind.Epub && !hideFileName,
                        hiddenFromToc = controller.isSection0001HiddenFromDirectoryToc(chapter.source),
                        tocLevel = chapter.tocLevel,
                        isVolume = chapter.isVolume,
                        rowBoxPosition = rowBoxPosition,
                        bulkSelectMode = (bulkRemoveMode || bulkMoveMode || bulkDeleteMode) && controller.kind == DocumentKind.Txt && chapter.index > 0,
                        bulkSelected = chapter.index > 0 && (
                            (chapter.index - 1) in bulkRemoveSelectedChapterIndexes ||
                                (chapter.index - 1) in bulkMoveSelectedChapterIndexes ||
                                (chapter.index - 1) in bulkDeleteSelectedChapterIndexes
                            ),
                        hasChildren = chapter.index > 0 && hasChildrenByIndex[chapter.index] == true,
                        expanded = chapter.index !in collapsedChapterIndexes,
                        onToggleExpanded = {
                            collapsedChapterIndexes = if (chapter.index in collapsedChapterIndexes) {
                                collapsedChapterIndexes - chapter.index
                            } else {
                                collapsedChapterIndexes + chapter.index
                            }
                        },
                        moveMode = moveMode && chapter.index > 0,
                        moveTargetOptions = moveTargetOptions,
                        selectedMoveTargetIndex = moveTargetsByChapter[chapter.index - 1],
                        onMoveTargetSelected = { targetIndex ->
                            onMoveTargetSelected(chapter.index - 1, targetIndex)
                        },
                        onClick = { onPickChapter(chapter.index - 1) },
                        onLongPress = if (
                            !bulkRemoveMode &&
                            !bulkMoveMode &&
                            !bulkDeleteMode &&
                            (controller.kind == DocumentKind.Epub || controller.kind == DocumentKind.Txt) &&
                            chapter.index > 0 &&
                            longPressHandler != null
                        ) {
                            { longPressHandler(chapter) }
                        } else {
                            null
                        }
                    )
                    if (rowBoxPosition == DirectoryRowBoxPosition.Standalone) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
                    }
                }
            }

            ContentScrollbar(
                state = listState,
                itemCount = displayedChapters.size,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(top = 4.dp, bottom = 4.dp, end = 2.dp),
                fixedItemHeight = fixedDirectoryItemHeight,
                thumbFollowsDrag = true
            )
        }
    }
}

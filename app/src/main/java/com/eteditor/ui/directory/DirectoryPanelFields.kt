package com.eteditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eteditor.core.ChapterInfo
import com.eteditor.core.DocumentKind

private enum class DirectoryActionMode {
    MoveChapter,
    AddVolume,
    RemoveCatalogItem,
    DeleteChapterBlock
}

@Composable
internal fun DirectoryPanel(
    controller: EditorController,
    modifier: Modifier = Modifier,
    onPickChapter: (Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    var reverseOrderSelected by remember(controller.documentSessionKey) { mutableStateOf(false) }
    var actionMode by remember(controller.documentSessionKey) { mutableStateOf<DirectoryActionMode?>(null) }
    var bulkRemoveCatalogMode by remember(controller.documentSessionKey) { mutableStateOf(false) }
    var bulkRemoveCatalogIndexes by remember(controller.documentSessionKey) { mutableStateOf<Set<Int>>(emptySet()) }
    var bulkMoveChapterMode by remember(controller.documentSessionKey) { mutableStateOf(false) }
    var bulkMoveChapterIndexes by remember(controller.documentSessionKey) { mutableStateOf<Set<Int>>(emptySet()) }
    var bulkDeleteChapterMode by remember(controller.documentSessionKey) { mutableStateOf(false) }
    var bulkDeleteChapterIndexes by remember(controller.documentSessionKey) { mutableStateOf<Set<Int>>(emptySet()) }
    var showBulkMoveChapterTargetDialog by remember(controller.documentSessionKey) { mutableStateOf(false) }
    var moveTargetsByChapter by remember(controller.documentSessionKey) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var expandAllRequest by remember(controller.documentSessionKey) { mutableStateOf(0) }
    var collapseAllRequest by remember(controller.documentSessionKey) { mutableStateOf(0) }
    var allVolumesCollapsed by remember(controller.documentSessionKey) { mutableStateOf(false) }
    var deleteConfirm by remember(controller.documentSessionKey) { mutableStateOf<DeleteConfirmRequest?>(null) }
    var directoryScrollRequest by remember(controller.documentSessionKey) { mutableStateOf<Int?>(null) }
    var epubMenuChapter by remember(controller.documentSessionKey) { mutableStateOf<ChapterInfo?>(null) }
    var epubEditChapter by remember(controller.documentSessionKey) { mutableStateOf<ChapterInfo?>(null) }
    var epubMoveChapter by remember(controller.documentSessionKey) { mutableStateOf<ChapterInfo?>(null) }
    var epubAddVolumeAfterChapter by remember(controller.documentSessionKey) { mutableStateOf<ChapterInfo?>(null) }
    var txtMenuChapter by remember(controller.documentSessionKey) { mutableStateOf<ChapterInfo?>(null) }
    var txtDeleteOptionsChapter by remember(controller.documentSessionKey) { mutableStateOf<ChapterInfo?>(null) }
    var txtMoveChapter by remember(controller.documentSessionKey) { mutableStateOf<ChapterInfo?>(null) }
    fun runAfterTxtMoveSync(action: String, block: () -> Unit) {
        scope.launchAfterTxtMoveChapterSync(controller, action) {
            block()
        }
    }
    fun cancelBulkRemoveCatalog() {
        bulkRemoveCatalogMode = false
        bulkRemoveCatalogIndexes = emptySet()
    }
    fun cancelBulkMoveChapter() {
        bulkMoveChapterMode = false
        bulkMoveChapterIndexes = emptySet()
        showBulkMoveChapterTargetDialog = false
    }
    fun cancelBulkDeleteChapter() {
        bulkDeleteChapterMode = false
        bulkDeleteChapterIndexes = emptySet()
    }
    fun startBulkRemoveCatalog(chapterIndex: Int) {
        if (chapterIndex !in controller.chapters.indices) return
        actionMode = null
        moveTargetsByChapter = emptyMap()
        cancelBulkMoveChapter()
        cancelBulkDeleteChapter()
        bulkRemoveCatalogMode = true
        bulkRemoveCatalogIndexes = setOf(chapterIndex)
    }
    fun startBulkMoveChapter(chapterIndex: Int) {
        if (chapterIndex !in controller.chapters.indices) return
        actionMode = null
        moveTargetsByChapter = emptyMap()
        cancelBulkRemoveCatalog()
        cancelBulkDeleteChapter()
        bulkMoveChapterMode = true
        bulkMoveChapterIndexes = setOf(chapterIndex)
    }
    fun startBulkDeleteChapter(chapterIndex: Int) {
        if (chapterIndex !in controller.chapters.indices) return
        actionMode = null
        moveTargetsByChapter = emptyMap()
        cancelBulkRemoveCatalog()
        cancelBulkMoveChapter()
        bulkDeleteChapterMode = true
        bulkDeleteChapterIndexes = setOf(chapterIndex)
    }
    fun toggleBulkRemoveCatalog(chapterIndex: Int) {
        if (chapterIndex !in controller.chapters.indices) return
        bulkRemoveCatalogIndexes = if (chapterIndex in bulkRemoveCatalogIndexes) {
            bulkRemoveCatalogIndexes - chapterIndex
        } else {
            bulkRemoveCatalogIndexes + chapterIndex
        }
    }
    fun toggleBulkMoveChapter(chapterIndex: Int) {
        if (chapterIndex !in controller.chapters.indices) return
        bulkMoveChapterIndexes = if (chapterIndex in bulkMoveChapterIndexes) {
            bulkMoveChapterIndexes - chapterIndex
        } else {
            bulkMoveChapterIndexes + chapterIndex
        }
    }
    fun toggleBulkDeleteChapter(chapterIndex: Int) {
        if (chapterIndex !in controller.chapters.indices) return
        bulkDeleteChapterIndexes = if (chapterIndex in bulkDeleteChapterIndexes) {
            bulkDeleteChapterIndexes - chapterIndex
        } else {
            bulkDeleteChapterIndexes + chapterIndex
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(Modifier.fillMaxSize()) {
            if (controller.kind == DocumentKind.Txt) {
                val bulkMoveProgress = controller.txtBulkMoveChapterProgress
                if (bulkMoveProgress != null) {
                    SaveProgressIndicator(
                        text = controller.txtBulkMoveChapterProgressText.ifBlank { "批量移动章节" },
                        progress = bulkMoveProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                    )
                } else if (bulkMoveChapterMode) {
                    TxtBulkMoveChapterBar(
                        selectedCount = bulkMoveChapterIndexes.size,
                        onCancel = { cancelBulkMoveChapter() },
                        onConfirm = {
                            if (bulkMoveChapterIndexes.isNotEmpty()) {
                                showBulkMoveChapterTargetDialog = true
                            }
                        }
                    )
                } else if (bulkRemoveCatalogMode) {
                    TxtBulkRemoveCatalogBar(
                        selectedCount = bulkRemoveCatalogIndexes.size,
                        onCancel = { cancelBulkRemoveCatalog() },
                        onConfirm = {
                            val selectedIndexes = bulkRemoveCatalogIndexes
                                .filter { index -> index in controller.chapters.indices }
                                .toSet()
                            if (selectedIndexes.isNotEmpty()) {
                                deleteConfirm = DeleteConfirmRequest(
                                title = "\u786e\u8ba4\u79fb\u9664\u76ee\u5f55\u9879",
                                message = "\u786e\u5b9a\u4ece TXT \u76ee\u5f55\u4e2d\u79fb\u9664\u9009\u4e2d\u7684 ${selectedIndexes.size} \u9879\u5417\uff1f\u6b63\u6587\u5185\u5bb9\u4e0d\u4f1a\u5220\u9664\u3002",
                                    confirmLabel = "\u79fb\u9664",
                                    onConfirm = {
                                        if (controller.removeTxtCatalogItems(selectedIndexes)) {
                                            cancelBulkRemoveCatalog()
                                        }
                                    }
                                )
                            }
                        }
                    )
                } else if (bulkDeleteChapterMode) {
                    TxtBulkDeleteChapterBar(
                        selectedCount = bulkDeleteChapterIndexes.size,
                        onCancel = { cancelBulkDeleteChapter() },
                        onConfirm = {
                            val selectedIndexes = bulkDeleteChapterIndexes
                                .filter { index -> index in controller.chapters.indices }
                                .toSet()
                            if (selectedIndexes.isNotEmpty()) {
                                deleteConfirm = DeleteConfirmRequest(
                                    title = "确认删除章节",
                                    message = "确定从 TXT 正文中删除选中的 ${selectedIndexes.size} 章吗？将同时删除目录标题和正文，刷新目录无法恢复。",
                                    onConfirm = {
                                        if (controller.deleteTxtChapterBlocks(selectedIndexes)) {
                                            cancelBulkDeleteChapter()
                                        }
                                    }
                                )
                            }
                        }
                    )
                } else {
                    TxtDirectoryOptionBar(
                        controller = controller,
                        documentSessionKey = controller.documentSessionKey,
                        reverseOrderSelected = reverseOrderSelected,
                        onToggleReverseOrder = { reverseOrderSelected = !reverseOrderSelected },
                        onRefreshCatalog = {
                            runAfterTxtMoveSync("刷新目录") {
                                actionMode = null
                                moveTargetsByChapter = emptyMap()
                                controller.refreshTxtCatalog()
                            }
                        },
                        onPickChapter = { index ->
                            actionMode = null
                            moveTargetsByChapter = emptyMap()
                            directoryScrollRequest = index
                        }
                    )
                }
            } else {
                DirectoryOptionBar(
                    reverseOrderSelected = reverseOrderSelected,
                    onToggleReverseOrder = { reverseOrderSelected = !reverseOrderSelected },
                    allVolumesCollapsed = allVolumesCollapsed,
                    onToggleAllVolumes = {
                        if (allVolumesCollapsed) {
                            expandAllRequest += 1
                        } else {
                            collapseAllRequest += 1
                        }
                    }
                )
            }
            ChapterDirectoryList(
                controller = controller,
                bottomPadding = 4.dp,
                modifier = Modifier.weight(1f),
                hideFileName = controller.hideDirectoryFileNameByDefault,
                reverseOrder = reverseOrderSelected,
                expandAllRequest = expandAllRequest,
                collapseAllRequest = collapseAllRequest,
                scrollToChapterIndexRequest = directoryScrollRequest,
                onScrollToChapterConsumed = { directoryScrollRequest = null },
                onVolumeCollapseStateChange = { collapsed -> allVolumesCollapsed = collapsed },
                moveMode = controller.kind == DocumentKind.Txt && actionMode == DirectoryActionMode.MoveChapter,
                bulkRemoveMode = controller.kind == DocumentKind.Txt && bulkRemoveCatalogMode,
                bulkMoveMode = controller.kind == DocumentKind.Txt && bulkMoveChapterMode,
                bulkDeleteMode = controller.kind == DocumentKind.Txt && bulkDeleteChapterMode,
                bulkRemoveSelectedChapterIndexes = bulkRemoveCatalogIndexes,
                bulkMoveSelectedChapterIndexes = bulkMoveChapterIndexes,
                bulkDeleteSelectedChapterIndexes = bulkDeleteChapterIndexes,
                moveTargetsByChapter = moveTargetsByChapter,
                onMoveTargetSelected = { chapterIndex, targetIndex ->
                    if (controller.kind == DocumentKind.Txt) {
                        runAfterTxtMoveSync("移动章节") {
                            if (controller.txtMoveChapterBlock(chapterIndex, targetIndex)) {
                                moveTargetsByChapter = emptyMap()
                            }
                        }
                    } else {
                        if (controller.epubMoveChapterAfter(chapterIndex, targetIndex)) {
                            moveTargetsByChapter = emptyMap()
                            actionMode = null
                        }
                    }
                },
                onPickChapter = { index ->
                    if (controller.kind == DocumentKind.Txt && bulkMoveChapterMode) {
                        toggleBulkMoveChapter(index)
                    } else if (controller.kind == DocumentKind.Txt && bulkRemoveCatalogMode) {
                        toggleBulkRemoveCatalog(index)
                    } else if (controller.kind == DocumentKind.Txt && bulkDeleteChapterMode) {
                        toggleBulkDeleteChapter(index)
                    } else if (controller.kind == DocumentKind.Txt && actionMode == DirectoryActionMode.RemoveCatalogItem) {
                        runAfterTxtMoveSync("移除目录项") {
                            val title = controller.chapters.getOrNull(index)?.title.orEmpty()
                            deleteConfirm = DeleteConfirmRequest(
                                title = "确认移除目录项",
                                message = "确定从 TXT 目录中移除“${title.ifBlank { "该章" }}”吗？正文内容不会删除。",
                                confirmLabel = "\u79fb\u9664",
                                onConfirm = {
                                    if (controller.removeTxtCatalogItem(index)) {
                                        actionMode = null
                                    }
                                }
                            )
                        }
                    } else if (controller.kind == DocumentKind.Txt && actionMode == DirectoryActionMode.DeleteChapterBlock) {
                        runAfterTxtMoveSync("删除章节") {
                            val title = controller.chapters.getOrNull(index)?.title.orEmpty()
                            deleteConfirm = DeleteConfirmRequest(
                                title = "确认删除章节",
                                message = "确定从 TXT 正文中删除“${title.ifBlank { "该章" }}”整章内容吗？刷新目录无法恢复。",
                                onConfirm = {
                                    if (controller.deleteTxtChapterBlock(index)) {
                                        actionMode = null
                                    }
                                }
                            )
                        }
                    } else {
                        runAfterTxtMoveSync("切换章节预览") {
                            onPickChapter(index)
                        }
                    }
                },
                onLongPressChapter = { chapter ->
                    if (chapter.index > 0) {
                        if (controller.kind == DocumentKind.Epub) {
                            epubMenuChapter = chapter
                        } else if (controller.kind == DocumentKind.Txt) {
                            txtMenuChapter = chapter
                        }
                    }
                }
            )
        }
    }

    txtMenuChapter?.let { chapter ->
        TxtDirectoryItemMenuDialog(
            chapter = chapter,
            onDismiss = { txtMenuChapter = null },
            onMove = {
                txtMenuChapter = null
                txtMoveChapter = chapter
            },
            onStartBulkMove = {
                txtMenuChapter = null
                startBulkMoveChapter(chapter.index - 1)
            },
            onRemove = {
                txtMenuChapter = null
                runAfterTxtMoveSync("移除目录项") {
                    deleteConfirm = DeleteConfirmRequest(
                        title = "确认移除目录项",
                        message = "确定从 TXT 目录中移除“${chapter.title.ifBlank { "该章" }}”吗？正文内容不会删除。",
                        confirmLabel = "\u79fb\u9664",
                        onConfirm = {
                            controller.removeTxtCatalogItem(chapter.index - 1)
                        }
                    )
                }
            },
            onStartBulkRemove = {
                txtMenuChapter = null
                startBulkRemoveCatalog(chapter.index - 1)
            },
            onDelete = {
                txtMenuChapter = null
                runAfterTxtMoveSync("删除章节") {
                    deleteConfirm = DeleteConfirmRequest(
                        title = "确认删除章节",
                        message = "确定从 TXT 正文中删除“${chapter.title.ifBlank { "该章" }}”整章内容吗？刷新目录无法恢复。",
                        onConfirm = {
                            controller.deleteTxtChapterBlock(chapter.index - 1)
                        }
                    )
                }
            },
            onStartBulkDelete = {
                txtMenuChapter = null
                txtDeleteOptionsChapter = chapter
            }
        )
    }
    txtDeleteOptionsChapter?.let { chapter ->
        TxtDirectoryDeleteOptionsDialog(
            chapter = chapter,
            onDismiss = { txtDeleteOptionsChapter = null },
            onStartBulkDelete = {
                txtDeleteOptionsChapter = null
                startBulkDeleteChapter(chapter.index - 1)
            },
            onDeleteEmptyChapters = {
                txtDeleteOptionsChapter = null
                runAfterTxtMoveSync("\u4e00\u952e\u5220\u9664\u91cd\u590d\u7ae0\u8282") {
                    val emptyChapterIndexes = controller.chapters
                        .mapIndexedNotNull { index, item ->
                            index.takeIf { item.wordCount == 0 }
                        }
                        .toSet()
                    if (emptyChapterIndexes.isEmpty()) {
                        controller.showStatusMessage("\u672a\u53d1\u73b0\u5b57\u6570\u4e3a 0 \u7684\u7ae0\u8282")
                    } else {
                        deleteConfirm = DeleteConfirmRequest(
                            title = "\u786e\u8ba4\u5220\u9664\u91cd\u590d\u7ae0\u8282",
                            message = "\u5c06\u5220\u9664\u5f53\u524d TXT \u4e2d\u5b57\u6570\u4e3a 0 \u7684 ${emptyChapterIndexes.size} \u7ae0\u3002\u6b64\u64cd\u4f5c\u4f1a\u5220\u9664\u5bf9\u5e94\u76ee\u5f55\u6807\u9898\u548c\u6b63\u6587\u5757\uff0c\u5237\u65b0\u76ee\u5f55\u540e\u65e0\u6cd5\u6062\u590d\u3002",
                            onConfirm = {
                                controller.deleteTxtChapterBlocks(emptyChapterIndexes)
                            }
                        )
                    }
                }
            }
        )
    }
    if (showBulkMoveChapterTargetDialog) {
        TxtBulkMoveChapterTargetDialog(
            controller = controller,
            selectedChapterIndexes = bulkMoveChapterIndexes,
            onDismiss = { showBulkMoveChapterTargetDialog = false },
            onSelectTarget = { targetIndex ->
                val selectedIndexes = bulkMoveChapterIndexes
                    .filter { index -> index in controller.chapters.indices }
                    .toSet()
                showBulkMoveChapterTargetDialog = false
                if (selectedIndexes.isNotEmpty()) {
                    runAfterTxtMoveSync("批量移动章节") {
                        if (controller.txtMoveChapterBlocks(selectedIndexes, targetIndex)) {
                            cancelBulkMoveChapter()
                        }
                    }
                }
            }
        )
    }
    txtMoveChapter?.let { chapter ->
        TxtMoveChapterDialog(
            controller = controller,
            chapter = chapter,
            onDismiss = { txtMoveChapter = null }
        )
    }
    epubMenuChapter?.let { chapter ->
        val allowStructureActions = controller.epub
            ?.chapters
            ?.getOrNull(chapter.index - 1)
            ?.isCoverSection0001Or0002() != true
        EpubDirectoryItemMenuDialog(
            chapter = chapter,
            allowStructureActions = allowStructureActions,
            onDismiss = { epubMenuChapter = null },
            onEdit = {
                epubMenuChapter = null
                epubEditChapter = chapter
            },
            onMove = {
                epubMenuChapter = null
                epubMoveChapter = chapter
            },
            onAddVolume = {
                epubMenuChapter = null
                epubAddVolumeAfterChapter = chapter
            },
            onDelete = {
                epubMenuChapter = null
                deleteConfirm = DeleteConfirmRequest(
                    title = "确认删除章节",
                    message = "确定删除“${chapter.title.ifBlank { chapter.fileName }}”吗？\n\n将删除 HTML 文件 ${chapter.source}\n并同步移除 spine、manifest、toc.ncx/nav 引用。",
                    onConfirm = {
                        controller.deleteEpubChapter(chapter.index - 1)
                    }
                )
            }
        )
    }
    epubEditChapter?.let { chapter ->
        EpubDirectoryEditDialog(
            controller = controller,
            chapter = chapter,
            onDismiss = { epubEditChapter = null }
        )
    }
    epubMoveChapter?.let { chapter ->
        EpubMoveChapterDialog(
            controller = controller,
            chapter = chapter,
            onDismiss = { epubMoveChapter = null }
        )
    }
    epubAddVolumeAfterChapter?.let { chapter ->
        AddVolumeDialog(
            controller = controller,
            fixedInsertIndex = chapter.index,
            onDismiss = { epubAddVolumeAfterChapter = null }
        )
    }
    deleteConfirm?.let { request ->
        DeleteConfirmDialog(
            request = request,
            onDismiss = { deleteConfirm = null }
        )
    }
}

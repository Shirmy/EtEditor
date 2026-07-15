package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.ChapterInfo

internal enum class BulkTitleEditSelectionKind {
    Empty,
    Chapters,
    SingleVolume,
    MultiVolumes,
    Mixed
}

internal data class BulkTitleEditResolveResult(
    val kind: BulkTitleEditSelectionKind,
    val targetIndexes: List<Int> = emptyList(),
    val scopeLabel: String = "",
    val message: String = ""
)

internal data class BulkTitleEditPlanItem(
    val chapterIndex: Int,
    val oldTitle: String,
    val newTitle: String
) {
    val changed: Boolean
        get() = ChapterDetector.cleanTitle(oldTitle) != ChapterDetector.cleanTitle(newTitle)
}

internal data class BulkTitleEditPlanBuildResult(
    val items: List<BulkTitleEditPlanItem> = emptyList(),
    val changedCount: Int = 0,
    val skippedEmptyCount: Int = 0,
    val message: String = ""
)

internal fun classifyBulkTitleEditSelection(
    chapters: List<ChapterInfo>,
    selectedIndexes: Set<Int>
): BulkTitleEditSelectionKind {
    val selected = selectedIndexes
        .filter { it in chapters.indices }
        .sorted()
    if (selected.isEmpty()) return BulkTitleEditSelectionKind.Empty
    val volumeCount = selected.count { chapters[it].isVolume }
    val chapterCount = selected.size - volumeCount
    return when {
        volumeCount > 0 && chapterCount > 0 -> BulkTitleEditSelectionKind.Mixed
        volumeCount == 1 && chapterCount == 0 -> BulkTitleEditSelectionKind.SingleVolume
        volumeCount > 1 && chapterCount == 0 -> BulkTitleEditSelectionKind.MultiVolumes
        else -> BulkTitleEditSelectionKind.Chapters
    }
}

internal fun canToggleBulkTitleEditSelection(
    chapters: List<ChapterInfo>,
    selectedIndexes: Set<Int>,
    toggleIndex: Int
): String? {
    if (toggleIndex !in chapters.indices) return "无效目录项"
    if (toggleIndex in selectedIndexes) return null
    if (selectedIndexes.isEmpty()) return null
    val kind = classifyBulkTitleEditSelection(chapters, selectedIndexes)
    val toggleIsVolume = chapters[toggleIndex].isVolume
    return when (kind) {
        BulkTitleEditSelectionKind.Chapters ->
            if (toggleIsVolume) "已选章节，不能再选卷" else null
        BulkTitleEditSelectionKind.SingleVolume,
        BulkTitleEditSelectionKind.MultiVolumes ->
            if (!toggleIsVolume) "已选卷，不能再选章节" else null
        BulkTitleEditSelectionKind.Mixed -> "不能同时选择卷和章节"
        BulkTitleEditSelectionKind.Empty -> null
    }
}

/** 0-based directory indexes under a volume, until the next volume. */
internal fun directoryVolumeChildChapterIndexes(
    chapters: List<ChapterInfo>,
    volumeIndex: Int,
    isEditable: (Int) -> Boolean = { true }
): List<Int> {
    if (volumeIndex !in chapters.indices || !chapters[volumeIndex].isVolume) return emptyList()
    val children = mutableListOf<Int>()
    for (index in (volumeIndex + 1) until chapters.size) {
        if (chapters[index].isVolume) break
        if (isEditable(index)) {
            children += index
        }
    }
    return children
}

internal fun resolveBulkTitleEditTargets(
    chapters: List<ChapterInfo>,
    selectedIndexes: Set<Int>,
    isEditable: (Int) -> Boolean = { true }
): BulkTitleEditResolveResult {
    val selected = selectedIndexes
        .filter { it in chapters.indices && isEditable(it) }
        .toSet()
    val kind = classifyBulkTitleEditSelection(chapters, selected)
    return when (kind) {
        BulkTitleEditSelectionKind.Empty -> BulkTitleEditResolveResult(
            kind = kind,
            message = "请选择章节或卷"
        )
        BulkTitleEditSelectionKind.Mixed -> BulkTitleEditResolveResult(
            kind = kind,
            message = "不能同时选择卷和章节"
        )
        BulkTitleEditSelectionKind.Chapters -> {
            val targets = selected.filter { !chapters[it].isVolume }.sorted()
            if (targets.isEmpty()) {
                BulkTitleEditResolveResult(kind = kind, message = "没有可改的章节")
            } else {
                BulkTitleEditResolveResult(
                    kind = kind,
                    targetIndexes = targets,
                    scopeLabel = "将处理：已选 ${targets.size} 章"
                )
            }
        }
        BulkTitleEditSelectionKind.SingleVolume -> {
            val volumeIndex = selected.single()
            val children = directoryVolumeChildChapterIndexes(chapters, volumeIndex, isEditable)
            val volumeTitle = chapters[volumeIndex].title.ifBlank { "该卷" }
            if (children.isEmpty()) {
                BulkTitleEditResolveResult(
                    kind = kind,
                    message = "「$volumeTitle」下没有可改章节"
                )
            } else {
                BulkTitleEditResolveResult(
                    kind = kind,
                    targetIndexes = children,
                    scopeLabel = "将处理：「$volumeTitle」下 ${children.size} 章"
                )
            }
        }
        BulkTitleEditSelectionKind.MultiVolumes -> {
            val volumes = selected.filter { chapters[it].isVolume }.sorted()
            BulkTitleEditResolveResult(
                kind = kind,
                targetIndexes = volumes,
                scopeLabel = "将处理：已选 ${volumes.size} 个卷名"
            )
        }
    }
}

internal fun applyDirectoryTitleFindReplace(
    title: String,
    find: String,
    replace: String,
    regex: Boolean
): Result<String> {
    if (find.isEmpty()) {
        return Result.failure(IllegalArgumentException("请输入查找内容"))
    }
    val next = if (regex) {
        val pattern = runCatching { Regex(find) }.getOrElse { error ->
            return Result.failure(IllegalArgumentException(error.message ?: "正则无效"))
        }
        try {
            pattern.replace(title, replace)
        } catch (error: Exception) {
            return Result.failure(IllegalArgumentException(error.message ?: "正则替换失败"))
        }
    } else {
        if (!title.contains(find)) title else title.replace(find, replace)
    }
    return Result.success(next)
}

internal fun buildBulkTitleEditPlan(
    chapters: List<ChapterInfo>,
    targetIndexes: List<Int>,
    find: String,
    replace: String,
    regex: Boolean
): BulkTitleEditPlanBuildResult {
    if (find.isEmpty()) {
        return BulkTitleEditPlanBuildResult(message = "请输入查找内容")
    }
    if (regex) {
        runCatching { Regex(find) }.getOrElse { error ->
            return BulkTitleEditPlanBuildResult(message = error.message ?: "正则无效")
        }
    }
    val items = mutableListOf<BulkTitleEditPlanItem>()
    var skippedEmpty = 0
    for (index in targetIndexes) {
        val oldTitle = chapters.getOrNull(index)?.title.orEmpty()
        val replaced = applyDirectoryTitleFindReplace(oldTitle, find, replace, regex)
            .getOrElse { error ->
                return BulkTitleEditPlanBuildResult(message = error.message ?: "替换失败")
            }
        val cleaned = ChapterDetector.cleanTitle(replaced)
        if (cleaned.isBlank()) {
            skippedEmpty += 1
            continue
        }
        items += BulkTitleEditPlanItem(
            chapterIndex = index,
            oldTitle = oldTitle,
            newTitle = cleaned
        )
    }
    val changed = items.count { it.changed }
    val message = when {
        items.isEmpty() && skippedEmpty > 0 -> "替换后标题为空，已跳过 $skippedEmpty 项"
        items.isEmpty() -> "没有匹配项"
        changed == 0 && skippedEmpty == 0 -> "没有需要修改的标题"
        skippedEmpty > 0 -> "匹配 ${items.size} 项，将修改 $changed 项，跳过空标题 $skippedEmpty 项"
        else -> "匹配 ${items.size} 项，将修改 $changed 项"
    }
    return BulkTitleEditPlanBuildResult(
        items = items,
        changedCount = changed,
        skippedEmptyCount = skippedEmpty,
        message = message
    )
}

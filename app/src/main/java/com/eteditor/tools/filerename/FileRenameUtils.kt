package com.eteditor

import com.eteditor.core.EpubBook

data class FileRenamePlanItem(
    val chapterIndex: Int,
    val spineIndex: Int,
    val chapterTitle: String,
    val oldPath: String,
    val oldFileName: String,
    val extension: String,
    val newPath: String,
    val newFileName: String
) {
    val changed: Boolean get() = oldPath != newPath
}

internal data class TemplateContext(
    val spineIndex: Int,
    val sequenceIndex: Int,
    val total: Int,
    val title: String,
    val oldFileName: String? = null,
    val oldExtension: String? = null,
    val nextSequenceStart: Int? = null
)

internal fun renderTemplate(
    pattern: String,
    context: TemplateContext
): String {
    return applyTemplatePlaceholders(
        pattern = pattern.ifBlank { "{}" },
        index = context.spineIndex,
        total = context.total,
        title = context.title,
        sequenceIndex = context.sequenceIndex,
        oldFileName = context.oldFileName,
        oldExtension = context.oldExtension,
        nextSequenceStart = context.nextSequenceStart
    )
}

// 命名格式里的编号占位符匹配规则编译一次、整批复用：批量改名时这个函数每章都会调用一次，
// 若每次都重新构造正则会平白增加大书的后台开销(对应总报告第 80 项,与第 11/75 项同源)。
private val fileNameLowerSequenceRegex = Regex("""\{z(\d+)(?:[:：]([^}]+))?\}""")
private val fileNameUpperSequenceRegex = Regex("""\{Z(\d+)(?:[:：]([^}]+))?\}""")

internal fun applyTemplatePlaceholders(
    pattern: String,
    index: Int,
    total: Int,
    title: String,
    sequenceIndex: Int = index,
    oldFileName: String? = null,
    oldExtension: String? = null,
    templateStart: Int? = null,
    templateDigits: Int? = null,
    templateStep: Int? = null,
    nextSequenceStart: Int? = null
): String {
    val oneBased = (index + 1).toString()
    val padded = oneBased.padStart(total.toString().length.coerceAtLeast(2), '0')
    val sequence = (templateStart ?: 0) + sequenceIndex * (templateStep ?: 1)
    val sequenceText = sequence.toString()
    val paddedSequence = sequenceText.padStart(templateDigits ?: 4, '0')
    val withFixedZeroIndex = fileNameLowerSequenceRegex.replace(pattern) { match ->
        val width = match.groupValues[1].toIntOrNull()?.coerceIn(1, 12) ?: 4
        val startToken = match.groupValues.getOrNull(2)?.trim().orEmpty()
        val startAt = when {
            startToken.equals("next", ignoreCase = true) -> nextSequenceStart ?: 0
            startToken.equals("auto", ignoreCase = true) -> nextSequenceStart ?: 0
            startToken.isNotBlank() -> startToken.toIntOrNull() ?: 0
            else -> nextSequenceStart ?: 0
        }
        (startAt + sequenceIndex * (templateStep ?: 1)).toString().padStart(width, '0')
    }
    val withUpperSequence = fileNameUpperSequenceRegex.replace(withFixedZeroIndex) { match ->
        val width = match.groupValues[1].toIntOrNull()?.coerceIn(0, 12) ?: 0
        val startToken = match.groupValues.getOrNull(2)?.trim().orEmpty()
        val startAt = when {
            startToken.equals("next", ignoreCase = true) -> nextSequenceStart ?: templateStart ?: 0
            startToken.equals("auto", ignoreCase = true) -> nextSequenceStart ?: templateStart ?: 0
            startToken.isNotBlank() -> startToken.toIntOrNull() ?: templateStart ?: 0
            else -> templateStart ?: nextSequenceStart ?: 0
        }
        val value = (startAt + sequenceIndex * (templateStep ?: 1)).toString()
        if (width > 0) value.padStart(width, '0') else value
    }
    val withChapterFields = withUpperSequence
        .replace("{}", paddedSequence)
        .replace("{num}", paddedSequence)
        .replace("{raw}", sequenceText)
        .replace("{n}", padded)
        .replace("{i}", oneBased)
        .replace("{title}", title)

    if (oldFileName == null || oldExtension == null) {
        return withChapterFields
    }

    return withChapterFields
        .replace("{file}", oldFileName.substringBeforeLast('.', oldFileName))
        .replace("{ext}", oldExtension)
}

internal fun buildFileRenamePlanItems(
    book: EpubBook,
    parameters: FileRenameParameters,
    targetIndices: List<Int>,
    onError: (String) -> Unit = {}
): List<FileRenamePlanItem> {
    if (book.chapters.isEmpty()) {
        onError("没有可重命名的章节文件")
        return emptyList()
    }
    if (targetIndices.isEmpty()) {
        onError("没有可重命名的正文 HTML")
        return emptyList()
    }
    if (targetIndices.size > 1 && !parameters.namingFormat.contains('{')) {
        onError("多章节重命名时，命名格式需要包含 {z4}")
        return emptyList()
    }
    val nextSequenceStart = nextChapterSequenceStart(book, targetIndices)
    val currentPaths = targetIndices.map { book.chapters[it].path }.toSet()
    val plan = mutableListOf<FileRenamePlanItem>()
    val targetPaths = mutableSetOf<String>()

    targetIndices.forEachIndexed { targetPosition, index ->
        val chapter = book.chapters[index]
        val oldFileName = chapter.path.substringAfterLast('/')
        val oldExtension = oldFileName.substringAfterLast('.', missingDelimiterValue = "")
        val rawName = renderTemplate(
            pattern = parameters.namingFormat,
            context = TemplateContext(
                spineIndex = index,
                sequenceIndex = targetPosition,
                total = book.chapters.size,
                title = chapter.title.ifBlank { oldFileName.substringBeforeLast('.', oldFileName) },
                oldFileName = oldFileName,
                oldExtension = oldExtension,
                nextSequenceStart = nextSequenceStart
            )
        )
        val cleanFileName = normalizeChapterFileName(
            input = rawName,
            oldPath = chapter.path,
            keepExtension = true,
            onError = onError
        ) ?: return emptyList()
        val newPath = chapter.path.replaceFileName(cleanFileName)
        if (!targetPaths.add(newPath)) {
            onError("文件名重复：$cleanFileName；多章节请使用 {z4}")
            return emptyList()
        }
        if (book.entries.containsKey(newPath) && newPath !in currentPaths) {
            onError("文件名已存在：$cleanFileName")
            return emptyList()
        }
        plan += FileRenamePlanItem(
            chapterIndex = index,
            spineIndex = index + 1,
            chapterTitle = chapter.title,
            oldPath = chapter.path,
            oldFileName = oldFileName,
            extension = oldExtension,
            newPath = newPath,
            newFileName = cleanFileName
        )
    }
    return plan
}

internal fun applyFileRenamePlanToEpub(book: EpubBook, plan: List<FileRenamePlanItem>): Int {
    var renamed = 0
    val movedEntries = plan.mapNotNull { item ->
        if (!item.changed) return@mapNotNull null
        val chapter = book.chapters.getOrNull(item.chapterIndex) ?: return@mapNotNull null
        val bytes = book.entries.remove(item.oldPath)
        renamed += 1
        Triple(chapter, item, bytes)
    }

    val renamedPaths = linkedMapOf<String, String>()
    movedEntries.forEach { (chapter, item, bytes) ->
        val newHref = chapter.href.replaceHrefFileName(item.newFileName)
        if (bytes != null) {
            book.entries[item.newPath] = bytes
        }
        chapter.pathAliases += item.oldPath
        chapter.pathAliases += item.newPath
        chapter.path = item.newPath
        chapter.href = newHref
        book.manifest[chapter.id]?.path = item.newPath
        book.manifest[chapter.id]?.href = newHref
        if (!item.oldPath.equals(item.newPath, ignoreCase = true)) {
            renamedPaths[normalizeEpubPath(item.oldPath).lowercase()] = item.newPath
        }
    }
    rewriteEpubBodyLinksForRenamedPaths(book, renamedPaths)
    return renamed
}

private fun nextChapterSequenceStart(book: EpubBook, targetIndices: List<Int>): Int {
    val targetSet = targetIndices.toSet()
    val chapterPattern = Regex("""(?i)^Chapter(\d+)${'$'}""")
    val maxNumber = book.chapters.mapIndexedNotNull { index, chapter ->
        if (index in targetSet) return@mapIndexedNotNull null
        val stem = chapter.path.substringAfterLast('/').substringBeforeLast('.')
        chapterPattern.matchEntire(stem)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }.maxOrNull()
    return (maxNumber ?: -1) + 1
}

internal fun normalizeChapterFileName(
    input: String,
    oldPath: String,
    keepExtension: Boolean = true,
    onError: (String) -> Unit = {}
): String? {
    val trimmed = input.trim()
    if (trimmed.isBlank()) {
        onError("文件名不能为空")
        return null
    }
    if (trimmed.contains('/') || trimmed.contains('\\')) {
        onError("文件名不要包含路径")
        return null
    }
    if (trimmed == "." || trimmed == "..") {
        onError("文件名无效")
        return null
    }
    val oldExtension = oldPath.substringAfterLast('.', missingDelimiterValue = "")
    val hasExtension = trimmed.substringAfterLast('.', missingDelimiterValue = "").isNotBlank() && trimmed.contains('.')
    return if (keepExtension && !hasExtension && oldExtension.isNotBlank()) "$trimmed.$oldExtension" else trimmed
}

internal fun String.replaceFileName(fileName: String): String {
    val dir = substringBeforeLast('/', missingDelimiterValue = "")
    return if (dir.isBlank()) fileName else "$dir/$fileName"
}

internal fun String.replaceHrefFileName(fileName: String): String {
    val body = substringBefore('#')
    val fragment = substringAfter('#', missingDelimiterValue = "")
    val replaced = body.replaceFileName(fileName)
    return if (fragment.isBlank()) replaced else "$replaced#$fragment"
}

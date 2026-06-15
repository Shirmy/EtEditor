package com.eteditor

import com.eteditor.core.EpubBook

private const val DEFAULT_FETCH_INFO_INTRO_TARGET_HTML = "OEBPS/Text/Section0002.html"

internal fun resolveFetchInfoIntroTarget(rawPath: String, book: EpubBook?): String {
    val path = normalizeEpubPath(rawPath.trim())
    if (path.isBlank()) {
        return defaultFetchInfoIntroTarget(book)
    }
    return existingEpubPath(path, book) ?: path
}

internal fun defaultFetchInfoIntroTarget(book: EpubBook?): String {
    return listOf(
        DEFAULT_FETCH_INFO_INTRO_TARGET,
        DEFAULT_FETCH_INFO_INTRO_TARGET_HTML
    ).firstNotNullOfOrNull { candidate -> existingEpubPath(candidate, book) }
        ?: DEFAULT_FETCH_INFO_INTRO_TARGET
}

internal fun buildFetchInfoIntroTargetOptions(
    book: EpubBook?,
    currentPath: String
): List<Pair<String, String>> {
    val paths = mutableListOf<String>()

    fun addPath(path: String) {
        val normalized = normalizeEpubPath(path.trim())
        if (normalized.isBlank() || !isHtmlPath(normalized)) return
        val resolved = existingEpubPath(normalized, book) ?: normalized
        if (paths.none { normalizeEpubPath(it).equals(normalized, ignoreCase = true) }) {
            paths += resolved
        }
    }

    addPath(currentPath)
    addPath(DEFAULT_FETCH_INFO_INTRO_TARGET)
    addPath(DEFAULT_FETCH_INFO_INTRO_TARGET_HTML)
    if (book != null) {
        book.chapters.forEach { chapter -> addPath(chapter.path) }
        book.entries.keys.forEach(::addPath)
        book.manifest.values.forEach { item -> addPath(item.path) }
    }
    return paths.map { path -> path to path.substringAfterLast('/') }
}

internal fun existingEpubPath(candidate: String, book: EpubBook?): String? {
    val normalized = normalizeEpubPath(candidate)
    if (book == null || normalized.isBlank()) return null
    return (book.entries.keys.asSequence() +
        book.manifest.values.asSequence().map { it.path } +
        book.chapters.asSequence().map { it.path })
        .firstOrNull { path -> normalizeEpubPath(path).equals(normalized, ignoreCase = true) }
}

internal fun isDefaultFetchInfoIntroTargetOverride(path: String): Boolean {
    val normalized = normalizeEpubPath(path.trim())
    return normalized.isBlank() ||
        normalized.equals(DEFAULT_FETCH_INFO_INTRO_TARGET, ignoreCase = true)
}

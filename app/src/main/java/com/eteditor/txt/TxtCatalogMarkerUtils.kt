package com.eteditor

import com.eteditor.core.ChapterDetector
import com.eteditor.core.TxtDocument

internal fun txtHiddenCatalogMarkers(
    document: TxtDocument,
    hiddenLineIndices: Set<Int>
): List<TxtHiddenCatalogMarker> {
    if (hiddenLineIndices.isEmpty()) return emptyList()
    val lines = document.text.split('\n').map { it.removeSuffix("\r") }
    return hiddenLineIndices
        .sorted()
        .mapNotNull { lineIndex ->
            val normalized = normalizedTxtCatalogMarkerLine(lines.getOrNull(lineIndex).orEmpty())
            normalized.takeIf { it.isNotBlank() }?.let {
                TxtHiddenCatalogMarker(lineIndex = lineIndex, normalizedLine = it)
            }
        }
}

internal fun remapTxtSupplementedCatalogLines(
    text: String,
    records: List<TxtSupplementedCatalogLine>
): List<TxtSupplementedCatalogLine> {
    if (records.isEmpty()) return emptyList()
    val lines = text.split('\n').map { it.removeSuffix("\r") }
    val used = mutableSetOf<Int>()
    var searchFrom = 0
    return records.mapNotNull { record ->
        val normalized = normalizedTxtCatalogMarkerLine(record.supplementedLine)
        if (normalized.isBlank()) return@mapNotNull null
        val directIndex = record.lineIndex.takeIf { index ->
            index in lines.indices &&
                index !in used &&
                normalizedTxtCatalogMarkerLine(lines[index]) == normalized
        }
        val targetIndex = directIndex
            ?: findTxtCatalogMarkerLine(lines, normalized, searchFrom, used)
            ?: findTxtCatalogMarkerLine(lines, normalized, 0, used)
        if (targetIndex != null) {
            used += targetIndex
            searchFrom = targetIndex + 1
            record.copy(
                lineIndex = targetIndex,
                supplementedLine = lines[targetIndex]
            )
        } else {
            null
        }
    }
}

internal fun restoreTxtSupplementedCatalogLinesInText(
    text: String,
    records: List<TxtSupplementedCatalogLine>
): Pair<String, Int> {
    if (records.isEmpty()) return text to 0
    val lines = text.split('\n').map { it.removeSuffix("\r") }.toMutableList()
    var restored = 0
    records.forEach { record ->
        val directIndex = record.lineIndex.takeIf { it in lines.indices && lines[it] == record.supplementedLine }
        val targetIndex = directIndex ?: lines
            .mapIndexedNotNull { index, line -> index.takeIf { line == record.supplementedLine } }
            .singleOrNull()
        if (targetIndex != null) {
            lines[targetIndex] = record.originalLine
            restored += 1
        }
    }
    return lines.joinToString("\n") to restored
}

internal fun remapTxtHiddenCatalogLineIndices(
    text: String,
    markers: List<TxtHiddenCatalogMarker>
): Set<Int> {
    if (markers.isEmpty()) return emptySet()
    val lines = text.split('\n').map { it.removeSuffix("\r") }
    val used = mutableSetOf<Int>()
    var searchFrom = 0
    return markers
        .mapNotNull { marker ->
            val target = findTxtCatalogMarkerLine(lines, marker.normalizedLine, searchFrom, used)
                ?: findTxtCatalogMarkerLine(lines, marker.normalizedLine, 0, used)
            if (target != null) {
                used += target
                searchFrom = target + 1
            }
            target
        }
        .toSet()
}

private fun findTxtCatalogMarkerLine(
    lines: List<String>,
    normalizedLine: String,
    startIndex: Int,
    used: Set<Int>
): Int? {
    for (index in startIndex.coerceAtLeast(0) until lines.size) {
        if (index !in used && normalizedTxtCatalogMarkerLine(lines[index]) == normalizedLine) {
            return index
        }
    }
    return null
}

private fun normalizedTxtCatalogMarkerLine(line: String): String {
    return ChapterDetector.cleanTitle(line.trim())
}

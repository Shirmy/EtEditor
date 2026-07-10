package com.eteditor

internal fun txtLineBreakLengthAt(text: String, index: Int): Int {
    if (index !in text.indices) return 0
    return when (text[index]) {
        '\r' -> if (index + 1 < text.length && text[index + 1] == '\n') 2 else 1
        '\n' -> if (index > 0 && text[index - 1] == '\r') 0 else 1
        else -> 0
    }
}

internal fun countLineBreaksBefore(text: String, endExclusive: Int): Int {
    val end = endExclusive.coerceIn(0, text.length)
    var count = 0
    var index = 0
    while (index < end) {
        val length = txtLineBreakLengthAt(text, index)
        if (length == 0) {
            index += 1
        } else if (index + length <= end) {
            count += 1
            index += length
        } else {
            break
        }
    }
    return count
}

internal fun txtLineBreakCount(text: String): Int = countLineBreaksBefore(text, text.length)

internal fun txtLineBreakDeltaAfterReplacement(
    sourceText: String,
    sourceStart: Int,
    sourceEnd: Int,
    replacementText: String
): Int {
    val start = sourceStart.coerceIn(0, sourceText.length)
    val end = sourceEnd.coerceIn(start, sourceText.length)
    val contextStart = (start - 1).coerceAtLeast(0)
    val contextEnd = (end + 1).coerceAtMost(sourceText.length)
    val before = sourceText.substring(contextStart, contextEnd)
    val after = buildString(before.length + replacementText.length - (end - start)) {
        append(sourceText, contextStart, start)
        append(replacementText)
        append(sourceText, end, contextEnd)
    }
    return txtLineBreakCount(after) - txtLineBreakCount(before)
}

internal fun txtLineStartOffsets(text: String): List<Int> {
    val offsets = mutableListOf(0)
    var index = 0
    while (index < text.length) {
        val length = txtLineBreakLengthAt(text, index)
        if (length > 0) {
            index += length
            offsets += index
        } else {
            index += 1
        }
    }
    return offsets
}

internal fun txtLineOffsets(text: String): List<Int> {
    return txtLineStartOffsets(text) + text.length
}

internal fun txtAlignedLineStart(text: String, roughStart: Int): Int {
    val safeStart = roughStart.coerceIn(0, text.length)
    if (safeStart < text.length) {
        val length = txtLineBreakLengthAt(text, safeStart)
        if (length > 0) return safeStart + length
        if (text[safeStart] == '\n' && safeStart > 0 && text[safeStart - 1] == '\r') {
            return safeStart + 1
        }
    }
    var index = safeStart - 1
    while (index >= 0) {
        when (text[index]) {
            '\n' -> return index + 1
            '\r' -> return index + txtLineBreakLengthAt(text, index)
        }
        index -= 1
    }
    return 0
}

internal fun txtLineStartAtOffset(text: String, offset: Int): Int {
    var index = offset.coerceIn(0, text.length) - 1
    while (index >= 0) {
        when (text[index]) {
            '\n' -> return index + 1
            '\r' -> {
                val length = txtLineBreakLengthAt(text, index)
                if (index + length <= offset) return index + length
            }
        }
        index -= 1
    }
    return 0
}

internal fun txtAlignedLineEnd(text: String, roughEnd: Int): Int {
    var index = roughEnd.coerceIn(0, text.length)
    if (index > 0 && index < text.length && text[index] == '\n' && text[index - 1] == '\r') {
        return index + 1
    }
    while (index < text.length) {
        val length = txtLineBreakLengthAt(text, index)
        if (length > 0) return index + length
        index += 1
    }
    return text.length
}

internal fun txtLineBreakStartAtOrAfter(text: String, offset: Int): Int {
    var index = offset.coerceIn(0, text.length)
    if (index > 0 && index < text.length && text[index] == '\n' && text[index - 1] == '\r') {
        return index - 1
    }
    while (index < text.length) {
        if (txtLineBreakLengthAt(text, index) > 0) return index
        index += 1
    }
    return text.length
}

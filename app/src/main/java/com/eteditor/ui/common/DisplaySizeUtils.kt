package com.eteditor

internal fun compactByteSize(bytes: Int): String {
    return compactByteSize(bytes.toLong())
}

internal fun compactByteSize(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L -> compactByteDecimal(bytes, 1024L * 1024L * 1024L, "GB")
        bytes >= 1024L * 1024L -> compactByteDecimal(bytes, 1024L * 1024L, "MB")
        bytes >= 1024L -> compactByteDecimal(bytes, 1024L, "KB")
        else -> "${bytes.coerceAtLeast(0)}B"
    }
}

private fun compactByteDecimal(count: Long, divisor: Long, suffix: String): String {
    val tenths = (count * 10 + divisor / 2) / divisor
    val whole = tenths / 10
    val fraction = tenths % 10
    return if (fraction == 0L) "$whole$suffix" else "$whole.$fraction$suffix"
}

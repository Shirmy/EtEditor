package com.eteditor.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
private val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
private const val BOM_CHAR = '\uFEFF'
private const val UTF_8_DECLARATION = "utf-8"
private const val HTML_HEAD_SAMPLE_BYTES = 8192

private val xmlEncodingRegex = Regex(
    """(<\?xml\b[^>]*\bencoding\s*=\s*)(['"])([^'"]*)(['"])""",
    RegexOption.IGNORE_CASE
)
private val htmlMetaTagRegex = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
private val htmlCharsetAssignmentRegex = Regex(
    """(\bcharset\s*=\s*)(["']?)([^"'\s;/>]+)(["']?)""",
    RegexOption.IGNORE_CASE
)
private val declaredXmlEncodingRegex = Regex(
    """<\?xml\b[^>]*\bencoding\s*=\s*['"]\s*([^'"]+)\s*['"]""",
    RegexOption.IGNORE_CASE
)
private val declaredHtmlCharsetRegex = Regex(
    """\bcharset\s*=\s*["']?\s*([A-Za-z0-9._:\-]+)""",
    RegexOption.IGNORE_CASE
)

internal fun decodeEpubHtmlBytes(bytes: ByteArray): String {
    decodeBomMarkedHtml(bytes)?.let { return it }

    declaredHtmlCharset(bytes)?.let { charset ->
        decodeStrict(bytes, charset)?.let { return it.trimBomChar() }
        return String(bytes, charset).trimBomChar()
    }

    decodeStrict(bytes, StandardCharsets.UTF_8)?.let { return it.trimBomChar() }
    fallbackHtmlCharsets().forEach { charset ->
        decodeStrict(bytes, charset)?.let { return it.trimBomChar() }
    }
    return String(bytes, StandardCharsets.UTF_8).trimBomChar()
}

internal fun normalizeEpubHtmlUtf8Declaration(html: String): String {
    val xmlNormalized = xmlEncodingRegex.replace(html) { match ->
        "${match.groupValues[1]}${match.groupValues[2]}$UTF_8_DECLARATION${match.groupValues[4]}"
    }
    return htmlMetaTagRegex.replace(xmlNormalized) { match ->
        htmlCharsetAssignmentRegex.replace(match.value) { charsetMatch ->
            val prefix = charsetMatch.groupValues[1]
            val openQuote = charsetMatch.groupValues[2]
            val closeQuote = charsetMatch.groupValues[4]
            "$prefix$openQuote$UTF_8_DECLARATION$closeQuote"
        }
    }
}

internal fun encodeEpubHtmlUtf8(html: String): ByteArray {
    return normalizeEpubHtmlUtf8Declaration(html).toByteArray(StandardCharsets.UTF_8)
}

internal fun updateEpubChapterHtmlEntry(book: EpubBook, chapter: EpubChapter) {
    chapter.html = normalizeEpubHtmlUtf8Declaration(chapter.html)
    book.entries[chapter.path] = chapter.html.toByteArray(StandardCharsets.UTF_8)
}

private fun decodeBomMarkedHtml(bytes: ByteArray): String? {
    if (bytes.startsWith(utf8Bom)) {
        val data = bytes.copyOfRange(utf8Bom.size, bytes.size)
        decodeStrict(data, StandardCharsets.UTF_8)?.let { return it.trimBomChar() }
    }
    if (bytes.startsWith(utf16LeBom)) {
        val data = bytes.copyOfRange(utf16LeBom.size, bytes.size)
        decodeStrict(data, StandardCharsets.UTF_16LE)?.let { return it.trimBomChar() }
    }
    if (bytes.startsWith(utf16BeBom)) {
        val data = bytes.copyOfRange(utf16BeBom.size, bytes.size)
        decodeStrict(data, StandardCharsets.UTF_16BE)?.let { return it.trimBomChar() }
    }
    return null
}

private fun declaredHtmlCharset(bytes: ByteArray): Charset? {
    val sampleSize = bytes.size.coerceAtMost(HTML_HEAD_SAMPLE_BYTES)
    val head = String(bytes.copyOfRange(0, sampleSize), StandardCharsets.ISO_8859_1)
    val charsetName = declaredXmlEncodingRegex.find(head)?.groupValues?.getOrNull(1)
        ?: declaredHtmlCharsetRegex.find(head)?.groupValues?.getOrNull(1)
    return charsetName?.let(::htmlCharsetOrNull)
}

private fun htmlCharsetOrNull(name: String): Charset? {
    val clean = name.trim().trim('"', '\'')
    val normalized = clean.lowercase().replace('_', '-')
    val candidate = when (normalized) {
        "utf8" -> "UTF-8"
        "gb2312", "gb-2312", "gbk", "x-gbk", "cp936", "ms936", "windows-936" -> "GB18030"
        "gb18030", "gb-18030" -> "GB18030"
        "big5", "big-5", "x-big5", "big5-hkscs" -> "Big5"
        else -> clean
    }
    return runCatching { Charset.forName(candidate) }.getOrNull()
}

private fun fallbackHtmlCharsets(): List<Charset> {
    return listOf("GBK", "GB18030", "Big5").mapNotNull { name ->
        runCatching { Charset.forName(name) }.getOrNull()
    }
}

private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
    return try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    return prefix.indices.all { this[it] == prefix[it] }
}

private fun String.trimBomChar(): String {
    return if (firstOrNull() == BOM_CHAR) drop(1) else this
}

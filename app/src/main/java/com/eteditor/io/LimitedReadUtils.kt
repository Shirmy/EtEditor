package com.eteditor

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

internal const val TXT_DOCUMENT_MAX_BYTES = 30L * 1024L * 1024L
internal const val EPUB_DOCUMENT_MAX_BYTES = 80L * 1024L * 1024L
internal const val CONFIG_IMPORT_MAX_BYTES = 2L * 1024L * 1024L
internal const val REPLACEMENT_RULE_FILE_MAX_BYTES = 2L * 1024L * 1024L
internal const val REPLACEMENT_RULE_MAX_COUNT = 5000
internal const val HTTP_TEXT_RESPONSE_MAX_BYTES = 5L * 1024L * 1024L
internal const val HTTP_IMAGE_RESPONSE_MAX_BYTES = 10L * 1024L * 1024L
internal const val IMAGE_FILE_MAX_BYTES = 10L * 1024L * 1024L
internal const val IMAGE_MAX_EDGE = 6000
internal const val IMAGE_MAX_PIXELS = 16_000_000L
internal const val EPUB_ZIP_MAX_ENTRY_COUNT = 3000
internal const val EPUB_ZIP_MAX_ENTRY_BYTES = 32L * 1024L * 1024L
internal const val EPUB_ZIP_MAX_TOTAL_BYTES = 150L * 1024L * 1024L

private const val LIMITED_READ_BUFFER_SIZE = 8192
private const val LIMITED_READ_ZERO_READ_LIMIT = 16

internal fun InputStream.readBytesLimited(maxBytes: Long, label: String): ByteArray {
    require(maxBytes >= 0L) { "maxBytes must be non-negative" }
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(LIMITED_READ_BUFFER_SIZE)
    var total = 0L
    var zeroReads = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        if (read == 0) {
            zeroReads += 1
            if (zeroReads > LIMITED_READ_ZERO_READ_LIMIT) {
                error("$label 读取没有进展，请重新选择文件或稍后重试")
            }
            Thread.yield()
            continue
        }
        zeroReads = 0
        total += read.toLong()
        requireSizeWithinLimit(total, maxBytes, label)
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

internal fun File.readBytesLimited(maxBytes: Long, label: String): ByteArray {
    val size = length().takeIf { it >= 0L }
    if (size != null) {
        requireSizeWithinLimit(size, maxBytes, label)
    }
    return inputStream().use { stream -> stream.readBytesLimited(maxBytes, label) }
}

internal fun ContentResolver.readUriBytesLimited(
    uri: Uri,
    maxBytes: Long,
    label: String,
    openError: String = "无法打开输入文件"
): ByteArray {
    queryOpenableSize(uri)?.let { size ->
        requireSizeWithinLimit(size, maxBytes, label)
    }
    return openInputStream(uri)?.use { stream -> stream.readBytesLimited(maxBytes, label) }
        ?: error(openError)
}

internal fun requireSizeWithinLimit(size: Long, maxBytes: Long, label: String) {
    if (size > maxBytes) {
        error("$label 过大，最多支持 ${compactByteSize(maxBytes)}")
    }
}

private fun ContentResolver.queryOpenableSize(uri: Uri): Long? {
    return runCatching {
        val cursor = query(uri, arrayOf(OpenableColumns.SIZE), null, null, null) ?: return@runCatching null
        cursor.use {
            if (!it.moveToFirst()) {
                null
            } else {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex < 0 || it.isNull(sizeIndex)) {
                    null
                } else {
                    it.getLong(sizeIndex).takeIf { size -> size >= 0L }
                }
            }
        }
    }.getOrNull()
}

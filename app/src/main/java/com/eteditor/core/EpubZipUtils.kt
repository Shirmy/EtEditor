package com.eteditor.core

import com.eteditor.EPUB_ZIP_MAX_ENTRY_BYTES
import com.eteditor.EPUB_ZIP_MAX_ENTRY_COUNT
import com.eteditor.EPUB_ZIP_MAX_TOTAL_BYTES
import com.eteditor.compactByteSize
import com.eteditor.readBytesLimited
import com.eteditor.requireSizeWithinLimit
import java.io.ByteArrayInputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
    val entries = linkedMapOf<String, ByteArray>()
    var entryCount = 0
    var totalBytes = 0L
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entryCount += 1
            if (entryCount > EPUB_ZIP_MAX_ENTRY_COUNT) {
                error("EPUB 条目过多，最多支持 $EPUB_ZIP_MAX_ENTRY_COUNT 个条目")
            }
            if (!entry.isDirectory) {
                entry.size.takeIf { it >= 0L }?.let { size ->
                    requireSizeWithinLimit(size, EPUB_ZIP_MAX_ENTRY_BYTES, "EPUB 条目 ${entry.name}")
                }
                val data = zip.readBytesLimited(EPUB_ZIP_MAX_ENTRY_BYTES, "EPUB 条目 ${entry.name}")
                totalBytes += data.size.toLong()
                if (totalBytes > EPUB_ZIP_MAX_TOTAL_BYTES) {
                    error("EPUB 解压后总大小过大，最多支持 ${compactByteSize(EPUB_ZIP_MAX_TOTAL_BYTES)}")
                }
                entries[entry.name] = data
            }
            zip.closeEntry()
        }
    }
    return entries
}

internal fun ZipOutputStream.putStoredEntry(name: String, data: ByteArray) {
    val crc = CRC32()
    crc.update(data)
    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = data.size.toLong()
        compressedSize = data.size.toLong()
        this.crc = crc.value
    }
    putNextEntry(entry)
    write(data)
    closeEntry()
}

internal fun ZipOutputStream.putDeflatedEntry(name: String, data: ByteArray) {
    val crc = CRC32()
    crc.update(data)
    val entry = ZipEntry(name).apply {
        method = ZipEntry.DEFLATED
        size = data.size.toLong()
        compressedSize = deflatedSize(data)
        this.crc = crc.value
    }
    putNextEntry(entry)
    write(data)
    closeEntry()
}

private fun deflatedSize(data: ByteArray): Long {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    return try {
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(8192)
        var total = 0L
        while (!deflater.finished()) {
            total += deflater.deflate(buffer).toLong()
        }
        total
    } finally {
        deflater.end()
    }
}

package com.eteditor.core

import com.eteditor.EPUB_ZIP_MAX_ENTRY_BYTES
import com.eteditor.EPUB_ZIP_MAX_ENTRY_COUNT
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EpubZipUtilsTest {
    @Test
    fun unzipReadsFileEntriesInArchiveOrder() {
        val zipBytes = zipBytes {
            putDeflatedEntry("mimetype", "application/epub+zip".bytes())
            putDeflatedEntry("OEBPS/content.opf", "<package />".bytes())
            putNextEntry(ZipEntry("OEBPS/Text/"))
            closeEntry()
        }

        val entries = unzip(zipBytes)

        assertEquals(listOf("mimetype", "OEBPS/content.opf"), entries.keys.toList())
        assertArrayEquals("application/epub+zip".bytes(), entries.getValue("mimetype"))
        assertArrayEquals("<package />".bytes(), entries.getValue("OEBPS/content.opf"))
    }

    @Test
    fun unzipRejectsTooManyEntries() {
        val zipBytes = zipBytes {
            repeat(EPUB_ZIP_MAX_ENTRY_COUNT + 1) { index ->
                putDeflatedEntry("entry-$index.txt", ByteArray(0))
            }
        }

        assertThrows(IllegalStateException::class.java) {
            unzip(zipBytes)
        }
    }

    @Test
    fun unzipRejectsEntryWithDeclaredSizeOverLimit() {
        val zipBytes = storedLocalFileHeader(
            name = "OEBPS/oversized.xhtml",
            declaredSize = EPUB_ZIP_MAX_ENTRY_BYTES + 1
        )

        assertThrows(IllegalStateException::class.java) {
            unzip(zipBytes)
        }
    }

    @Test
    fun putStoredEntryWritesUncompressedEntryWithReadableData() {
        val data = "application/epub+zip".bytes()
        val zipBytes = zipBytes {
            putStoredEntry("mimetype", data)
        }

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            val entry = zip.nextEntry
            assertEquals("mimetype", entry.name)
            assertEquals(ZipEntry.STORED, entry.method)
            assertEquals(data.size.toLong(), entry.size)
            assertEquals(data.size.toLong(), entry.compressedSize)
            assertArrayEquals(data, zip.readBytes())
            zip.closeEntry()
        }
        assertArrayEquals(data, unzip(zipBytes).getValue("mimetype"))
    }

    @Test
    fun putDeflatedEntryWritesCompressedEntryWithReadableData() {
        val data = "<package><metadata><title>Book</title></metadata></package>".bytes()
        val zipBytes = zipBytes {
            putDeflatedEntry("OEBPS/content.opf", data)
        }

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            val entry = zip.nextEntry
            assertEquals("OEBPS/content.opf", entry.name)
            assertEquals(ZipEntry.DEFLATED, entry.method)
            assertEquals(data.size.toLong(), entry.size)
            assertArrayEquals(data, zip.readBytes())
            zip.closeEntry()
        }
        assertArrayEquals(data, unzip(zipBytes).getValue("OEBPS/content.opf"))
    }

    private fun zipBytes(block: ZipOutputStream.() -> Unit): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> zip.block() }
        return output.toByteArray()
    }

    private fun storedLocalFileHeader(name: String, declaredSize: Long): ByteArray {
        val nameBytes = name.bytes()
        return ByteBuffer.allocate(30 + nameBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x04034b50)
            .putShort(20.toShort())
            .putShort(0.toShort())
            .putShort(0.toShort())
            .putShort(0.toShort())
            .putShort(0.toShort())
            .putInt(0)
            .putInt(declaredSize.toInt())
            .putInt(declaredSize.toInt())
            .putShort(nameBytes.size.toShort())
            .putShort(0.toShort())
            .put(nameBytes)
            .array()
    }

    private fun String.bytes(): ByteArray = toByteArray(StandardCharsets.UTF_8)
}

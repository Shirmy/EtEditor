package com.eteditor

import com.eteditor.core.EpubBook
import com.eteditor.core.ManifestItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets

class CoverImageUtilsTest {
    @Test
    fun coverMediaTypeNormalizesContentTypeAndFileExtension() {
        assertEquals("image/jpeg", coverMediaType("cover.jfif", ""))
        assertEquals("image/jpeg", coverMediaType("cover", "image/jpg; charset=binary"))
        assertEquals("image/png", coverMediaType("cover", "image/x-png"))
        assertEquals("image/webp", coverMediaType("cover.webp?x=1", ""))
        assertEquals("image/jpeg", coverMediaType("cover.unknown", ""))
    }

    @Test
    fun coverMediaTypePrefersSupportedContentTypeBeforeFileExtension() {
        assertEquals("image/jpeg", coverMediaType("cover.png", " image/pjpeg ; charset=binary"))
        assertEquals("image/png", coverMediaType("cover.jpg", "IMAGE/X-PNG"))
        assertEquals("image/jpeg", coverMediaType("cover.bin#download", ""))
        assertEquals("image/gif", coverMediaType("cover.gif#download", "application/octet-stream"))
    }

    @Test
    fun coverMediaTypeFromBytesDetectsCommonImageHeaders() {
        assertEquals("image/jpeg", coverMediaTypeFromBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertEquals("image/png", coverMediaTypeFromBytes(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)))
        assertEquals("image/gif", coverMediaTypeFromBytes("GIF89a".toByteArray(StandardCharsets.US_ASCII)))
        assertEquals("image/webp", coverMediaTypeFromBytes("RIFFxxxxWEBP".toByteArray(StandardCharsets.US_ASCII)))
        assertNull(coverMediaTypeFromBytes("text".toByteArray(StandardCharsets.US_ASCII)))
    }

    @Test
    fun coverMediaTypeFromBytesRequiresFullKnownHeaders() {
        assertNull(coverMediaTypeFromBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertNull(coverMediaTypeFromBytes("RIFFxxxxWEPB".toByteArray(StandardCharsets.US_ASCII)))
        assertEquals("image/gif", coverMediaTypeFromBytes("GIF87a".toByteArray(StandardCharsets.US_ASCII)))
    }

    @Test
    fun coverFileNamesExtensionsAndValidationFollowMediaType() {
        assertEquals("png", coverExtension("image/png"))
        assertEquals("webp", coverExtension("image/webp"))
        assertEquals("gif", coverExtension("image/gif"))
        assertEquals("jpg", coverExtension("image/jpeg"))
        assertEquals("cover.png", coverFileNameForMediaType("image/png"))
        assertEquals("cover.webp", coverFileNameForMediaType("image/webp"))
        assertEquals("cover.gif", coverFileNameForMediaType("image/gif"))
        assertEquals("cover.jpg", coverFileNameForMediaType("image/jpeg"))
        assertEquals("cover.jpg", coverFileNameForMediaType("application/octet-stream"))
        validateInsertImageMediaType("image/webp")
        assertThrows(IllegalStateException::class.java) {
            validateInsertImageMediaType("image/svg+xml")
        }
    }

    @Test
    fun validateImageDimensionsRejectsOversizedImages() {
        validateImageDimensions(100 to 100, "图片")
        assertThrows(IllegalStateException::class.java) {
            validateImageDimensions(Int.MAX_VALUE to 2, "图片")
        }
        assertThrows(IllegalStateException::class.java) {
            validateImageDimensions(5000 to 5000, "图片")
        }
    }

    @Test
    fun nextCustomInsertImageFileNameSkipsExistingAndReservedStems() {
        val book = EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = linkedMapOf("OEBPS/Images/01.jpg" to byteArrayOf(1)),
            opfPath = "OEBPS/content.opf",
            tocPath = null,
            manifest = mutableMapOf(
                "02" to ManifestItem("02", "Images/02.jpg", "image/jpeg", "OEBPS/Images/02.jpg"),
                "cover-image" to ManifestItem("cover-image", "Images/cover.jpg", "image/jpeg", "OEBPS/Images/cover.jpg", properties = "cover-image")
            ),
            spineIds = mutableListOf(),
            chapters = mutableListOf()
        )
        val reserved = mutableSetOf("03")

        assertEquals("04.png", nextCustomInsertImageFileName(book, "png", reserved))
        assertEquals("05.png", nextCustomInsertImageFileName(book, "png", reserved))
        assertEquals(setOf("03", "04", "05"), reserved)
    }

    @Test
    fun nextCustomInsertImageFileNameUsesCoverDirectoryAndSkipsManifestIds() {
        val book = EpubBook(
            originalName = "book.epub",
            metadataTitle = "Book",
            metadataAuthor = "",
            entries = linkedMapOf("OPS/ART/01.JPG" to byteArrayOf(1)),
            opfPath = "OPS/content.opf",
            tocPath = null,
            manifest = mutableMapOf(
                "cover-image" to ManifestItem(
                    "cover-image",
                    "Art/cover.jpg",
                    "image/jpeg",
                    "OPS/Art/cover.jpg",
                    properties = "cover-image"
                ),
                "02" to ManifestItem("02", "Text/chapter1.xhtml", "application/xhtml+xml", "OPS/Text/chapter1.xhtml"),
                "03" to ManifestItem("03", "Text/chapter2.xhtml", "application/xhtml+xml", "OPS/Text/chapter2.xhtml")
            ),
            spineIds = mutableListOf(),
            chapters = mutableListOf()
        )
        val reserved = mutableSetOf<String>()

        assertEquals("04.webp", nextCustomInsertImageFileName(book, "webp", reserved))
        assertEquals(setOf("04"), reserved)
    }
}

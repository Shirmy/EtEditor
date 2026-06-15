package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverPreviewUtilsTest {
    @Test
    fun coverImageTypePrefersImageContentTypeAndStripsParameters() {
        assertEquals(
            "image/png",
            coverImageType("image/png; charset=binary", "https://example.com/cover.jpg")
        )
        assertEquals(
            "IMAGE/WEBP",
            coverImageType("IMAGE/WEBP", "https://example.com/cover.jpg")
        )
    }

    @Test
    fun coverImageTypeKeepsTrimmedImageContentTypeCasing() {
        assertEquals(
            "Image/JPEG",
            coverImageType(" Image/JPEG ; charset=binary", "https://example.com/cover.png")
        )
    }

    @Test
    fun coverImageTypeFallsBackToUrlExtensionBeforeUnknownType() {
        assertEquals(
            "image/jpeg",
            coverImageType("", "https://example.com/cover.jpeg?token=1#view")
        )
        assertEquals(
            "image/png",
            coverImageType("application/octet-stream", "https://example.com/cover.PNG")
        )
        assertEquals(
            "application/octet-stream",
            coverImageType("application/octet-stream", "https://example.com/cover.bin")
        )
        assertEquals(
            "未知类型",
            coverImageType("", "https://example.com/cover")
        )
    }

    @Test
    fun coverImageTypeIgnoresUrlFragmentWhenReadingExtension() {
        assertEquals(
            "image/gif",
            coverImageType("", "https://example.com/cover.gif#preview")
        )
        assertEquals(
            "image/webp",
            coverImageType("text/plain", "https://example.com/cover.webp?token=1#preview")
        )
    }

    @Test
    fun coverImageTypeMapsJpgExtensionAndKeepsNonImageContentTypeWithoutParameters() {
        assertEquals(
            "image/jpeg",
            coverImageType("", "https://example.com/COVER.JPG")
        )
        assertEquals(
            "text/plain",
            coverImageType(" text/plain ; charset=utf-8", "https://example.com/cover.bin")
        )
    }

    @Test
    fun formatCoverByteSizeUsesKbBelowOneMbAndMbAtOneMb() {
        assertEquals("0.0 KB", formatCoverByteSize(0))
        assertEquals("-0.0 KB", formatCoverByteSize(-1))
        assertEquals("0.5 KB", formatCoverByteSize(512))
        assertEquals("1023.0 KB", formatCoverByteSize(1023 * 1024))
        assertEquals("1.00 MB", formatCoverByteSize(1024 * 1024))
        assertEquals("2.50 MB", formatCoverByteSize(2_621_440))
    }
}

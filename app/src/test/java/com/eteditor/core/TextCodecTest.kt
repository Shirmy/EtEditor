package com.eteditor.core

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TextCodecTest {
    @Test
    fun decodeEmptyBytesAsUtf8EmptyText() {
        val decoded = TextCodec.decode(ByteArray(0))

        assertEquals("", decoded.first)
        assertEquals("UTF-8", decoded.second)
    }

    @Test
    fun decodeStripsUtf8BomAndKeepsUtf8Label() {
        val text = "\u6807\u9898\ncontent"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            text.toByteArray(StandardCharsets.UTF_8)

        val decoded = TextCodec.decode(bytes)

        assertEquals(text, decoded.first)
        assertEquals("UTF-8", decoded.second)
    }

    @Test
    fun decodeFallsBackToGbkForNonUtf8Text() {
        val text = "\u7b2c\u4e00\u7ae0"
        val bytes = text.toByteArray(Charset.forName("GBK"))

        val decoded = TextCodec.decode(bytes)

        assertEquals(text, decoded.first)
        assertEquals("GBK", decoded.second)
    }

    @Test
    fun decodeFallsBackToGb18030WhenGbkStrictDecodeFails() {
        val text = "\uD840\uDC00"
        val bytes = text.toByteArray(Charset.forName("GB18030"))

        val decoded = TextCodec.decode(bytes)

        assertEquals(text, decoded.first)
        assertEquals("GB18030", decoded.second)
    }

    @Test
    fun encodePreservesUtf16LeBomPreference() {
        val encoded = TextCodec.encode("AB", "UTF-16LE-BOM")

        assertEquals("UTF-16LE-BOM", encoded.second)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            encoded.first.copyOfRange(0, 2)
        )
        assertEquals("AB", TextCodec.decode(encoded.first).first)
    }

    @Test
    fun encodePreservesUtf16BeBomPreference() {
        val encoded = TextCodec.encode("AB", "UTF-16BE-BOM")

        assertEquals("UTF-16BE-BOM", encoded.second)
        assertArrayEquals(
            byteArrayOf(0xFE.toByte(), 0xFF.toByte()),
            encoded.first.copyOfRange(0, 2)
        )
        assertEquals("AB", TextCodec.decode(encoded.first).first)
    }

    @Test
    fun decodeDetectsUtf16WithBomAndWithoutBom() {
        val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "AB".toByteArray(StandardCharsets.UTF_16BE)
        val utf16LeNoBom = "ABCD".toByteArray(StandardCharsets.UTF_16LE)
        val utf16BeNoBom = "ABCD".toByteArray(StandardCharsets.UTF_16BE)
        val decodedBeBom = TextCodec.decode(utf16BeBom)
        val decodedLeNoBom = TextCodec.decode(utf16LeNoBom)
        val decodedBeNoBom = TextCodec.decode(utf16BeNoBom)

        assertEquals("AB", decodedBeBom.first)
        assertEquals("UTF-16BE-BOM", decodedBeBom.second)
        assertEquals("ABCD", decodedLeNoBom.first)
        assertEquals("UTF-16LE", decodedLeNoBom.second)
        assertEquals("ABCD", decodedBeNoBom.first)
        assertEquals("UTF-16BE", decodedBeNoBom.second)
    }

    @Test
    fun encodeFallsBackToUtf8WhenPreferredEncodingCannotRepresentText() {
        val text = "hello \uD83D\uDE42"

        val encoded = TextCodec.encode(text, "GBK")

        assertEquals("UTF-8", encoded.second)
        assertEquals(text, String(encoded.first, StandardCharsets.UTF_8))
    }

    @Test
    fun encodeUsesPreferredEncodingWhenTextIsRepresentable() {
        val text = "第一章"

        val encoded = TextCodec.encode(text, "GBK")

        assertEquals("GBK", encoded.second)
        assertEquals(text, String(encoded.first, Charset.forName("GBK")))
    }

    @Test
    fun encodeFallsBackToUtf8ForUnknownEncodingName() {
        val encoded = TextCodec.encode("标题", "not-a-charset")

        assertEquals("UTF-8", encoded.second)
        assertEquals("标题", String(encoded.first, StandardCharsets.UTF_8))
    }
}

package com.eteditor.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object TextCodec {
    private val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    private val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
    private val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    private const val UTF_16LE_BOM = "UTF-16LE-BOM"
    private const val UTF_16BE_BOM = "UTF-16BE-BOM"

    fun decode(bytes: ByteArray): Pair<String, String> {
        if (bytes.startsWith(utf16LeBom)) {
            val data = bytes.copyOfRange(utf16LeBom.size, bytes.size)
            decodeStrict(data, StandardCharsets.UTF_16LE)?.let { return it to UTF_16LE_BOM }
        }
        if (bytes.startsWith(utf16BeBom)) {
            val data = bytes.copyOfRange(utf16BeBom.size, bytes.size)
            decodeStrict(data, StandardCharsets.UTF_16BE)?.let { return it to UTF_16BE_BOM }
        }
        detectUtf16WithoutBom(bytes)?.let { return it }
        val data = bytes.dropUtf8Bom()
        decodeStrict(data, StandardCharsets.UTF_8)?.let { return it to "UTF-8" }
        decodeStrict(data, Charset.forName("GBK"))?.let { return it to "GBK" }
        decodeStrict(data, Charset.forName("GB18030"))?.let { return it to "GB18030" }
        return String(data, StandardCharsets.UTF_8) to "UTF-8"
    }

    fun encode(text: String, preferredEncoding: String): Pair<ByteArray, String> {
        if (preferredEncoding == UTF_16LE_BOM) {
            return utf16LeBom + text.toByteArray(StandardCharsets.UTF_16LE) to UTF_16LE_BOM
        }
        if (preferredEncoding == UTF_16BE_BOM) {
            return utf16BeBom + text.toByteArray(StandardCharsets.UTF_16BE) to UTF_16BE_BOM
        }
        val charset = runCatching { Charset.forName(preferredEncoding) }.getOrDefault(StandardCharsets.UTF_8)
        val encoded = runCatching {
            charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(text))
        }.getOrNull()

        return if (encoded != null) {
            val bytes = ByteArray(encoded.remaining())
            encoded.get(bytes)
            bytes to charset.name()
        } else {
            text.toByteArray(StandardCharsets.UTF_8) to "UTF-8"
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

    private fun detectUtf16WithoutBom(bytes: ByteArray): Pair<String, String>? {
        if (bytes.size < 8 || bytes.size % 2 != 0) return null
        val sampleSize = bytes.size.coerceAtMost(4096)
        var evenZero = 0
        var oddZero = 0
        var pairs = 0
        var index = 0
        while (index + 1 < sampleSize) {
            if (bytes[index].toInt() == 0) evenZero += 1
            if (bytes[index + 1].toInt() == 0) oddZero += 1
            pairs += 1
            index += 2
        }
        if (pairs == 0) return null
        return when {
            oddZero >= pairs / 4 && oddZero >= evenZero * 4 -> {
                decodeStrict(bytes, StandardCharsets.UTF_16LE)?.let { it to "UTF-16LE" }
            }
            evenZero >= pairs / 4 && evenZero >= oddZero * 4 -> {
                decodeStrict(bytes, StandardCharsets.UTF_16BE)?.let { it to "UTF-16BE" }
            }
            else -> null
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { this[it] == prefix[it] }
    }

    private fun ByteArray.dropUtf8Bom(): ByteArray {
        if (size < utf8Bom.size) return this
        return if (startsWith(utf8Bom)) {
            copyOfRange(utf8Bom.size, size)
        } else {
            this
        }
    }
}

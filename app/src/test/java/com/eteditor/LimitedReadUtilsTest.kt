package com.eteditor

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LimitedReadUtilsTest {
    @Test
    fun readBytesLimitedAllowsExactLimit() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        val result = ByteArrayInputStream(bytes).readBytesLimited(
            maxBytes = bytes.size.toLong(),
            label = "Test"
        )

        assertArrayEquals(bytes, result)
    }

    @Test
    fun readBytesLimitedRejectsDataOverLimit() {
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertThrows(IllegalStateException::class.java) {
            ByteArrayInputStream(bytes).readBytesLimited(
                maxBytes = 3,
                label = "Test"
            )
        }
    }

    @Test
    fun readBytesLimitedRejectsNegativeLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            ByteArrayInputStream(ByteArray(0)).readBytesLimited(
                maxBytes = -1,
                label = "Test"
            )
        }
    }

    @Test
    fun readBytesLimitedHandlesZeroByteLimit() {
        assertArrayEquals(
            ByteArray(0),
            ByteArrayInputStream(ByteArray(0)).readBytesLimited(maxBytes = 0, label = "Empty")
        )

        val error = assertThrows(IllegalStateException::class.java) {
            ByteArrayInputStream(byteArrayOf(1)).readBytesLimited(maxBytes = 0, label = "NonEmpty")
        }
        assertEquals("NonEmpty 过大，最多支持 0B", error.message)
    }

    @Test
    fun readBytesLimitedIgnoresZeroLengthReadsUntilDataArrives() {
        val stream = object : InputStream() {
            private var readCalls = 0
            private val data = byteArrayOf(7, 8)
            private var cursor = 0

            override fun read(): Int {
                return if (cursor < data.size) data[cursor++].toInt() and 0xff else -1
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                readCalls += 1
                if (readCalls == 1) return 0
                if (cursor >= data.size) return -1
                val count = minOf(length, data.size - cursor)
                data.copyInto(buffer, destinationOffset = offset, startIndex = cursor, endIndex = cursor + count)
                cursor += count
                return count
            }
        }

        assertArrayEquals(byteArrayOf(7, 8), stream.readBytesLimited(maxBytes = 2, label = "Test"))
    }

    @Test
    fun readBytesLimitedResetsZeroLengthReadCountAfterProgress() {
        val stream = object : InputStream() {
            private val reads = buildList {
                repeat(16) { add(0) }
                add(1)
                repeat(16) { add(0) }
                add(2)
                add(-1)
            }
            private var cursor = 0

            override fun read(): Int = -1

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val next = reads[cursor++]
                if (next <= 0) return next
                buffer[offset] = next.toByte()
                return 1
            }
        }

        assertArrayEquals(byteArrayOf(1, 2), stream.readBytesLimited(maxBytes = 2, label = "Test"))
    }

    @Test
    fun readBytesLimitedRejectsRepeatedZeroLengthReadsWithoutProgress() {
        val stream = object : InputStream() {
            override fun read(): Int = 0

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }

        val error = assertThrows(IllegalStateException::class.java) {
            stream.readBytesLimited(maxBytes = 2, label = "Test")
        }

        assertEquals("Test 读取没有进展，请重新选择文件或稍后重试", error.message)
    }

    @Test
    fun requireSizeWithinLimitAllowsBoundaryAndRejectsOverflow() {
        requireSizeWithinLimit(size = 10, maxBytes = 10, label = "Test")

        val error = assertThrows(IllegalStateException::class.java) {
            requireSizeWithinLimit(size = 11, maxBytes = 10, label = "Test")
        }
        assertEquals("Test 过大，最多支持 10B", error.message)
    }

    @Test
    fun fileReadBytesLimitedUsesFileSizeLimitAndReadsExactLimit() {
        val file = File.createTempFile("limited-read", ".bin")
        try {
            val bytes = byteArrayOf(1, 2, 3, 4)
            file.writeBytes(bytes)

            assertArrayEquals(bytes, file.readBytesLimited(maxBytes = 4, label = "File"))

            val error = assertThrows(IllegalStateException::class.java) {
                file.readBytesLimited(maxBytes = 3, label = "File")
            }
            assertEquals("File 过大，最多支持 3B", error.message)
        } finally {
            file.delete()
        }
    }
}

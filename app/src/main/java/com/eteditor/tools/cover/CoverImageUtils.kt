package com.eteditor

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

private const val INSERTED_COVER_MAX_EDGE = 1440
private const val INSERTED_COVER_JPEG_QUALITY = 85
private const val INSERTED_COVER_JPEG_MIN_QUALITY = 58
private const val INSERTED_COVER_JPEG_QUALITY_STEP = 4
private const val INSERTED_COVER_TARGET_MAX_BYTES = 350 * 1024
private const val GENERATED_COVER_JPEG_QUALITY = 85
private const val GENERATED_COVER_JPEG_MIN_QUALITY = 35
private const val GENERATED_COVER_JPEG_QUALITY_STEP = 5
private const val GENERATED_COVER_JPEG_FINE_STEP = 1
private const val GENERATED_COVER_JPEG_TARGET_MIN_BYTES = 20 * 1024
private const val GENERATED_COVER_JPEG_TARGET_MAX_BYTES = 45 * 1024

internal fun validateInsertImageMediaType(mediaType: String) {
    if (mediaType !in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) {
        error("仅支持 JPG、PNG、WebP、GIF 图片")
    }
}

internal fun coverExtension(mediaType: String): String {
    return when (mediaType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }
}

internal fun coverFileNameForMediaType(mediaType: String): String {
    return when (mediaType) {
        "image/png" -> "cover.png"
        "image/webp" -> "cover.webp"
        "image/gif" -> "cover.gif"
        else -> "cover.jpg"
    }
}

internal fun coverMediaTypeLabel(mediaType: String): String {
    return when (mediaType) {
        "image/jpeg" -> "JPG"
        "image/png" -> "PNG"
        "image/webp" -> "WebP"
        "image/gif" -> "GIF"
        else -> mediaType.substringAfter('/').uppercase().ifBlank { "图片" }
    }
}

internal fun buildInsertedCoverPreviewFromImageBytes(
    bytes: ByteArray,
    displayName: String,
    contentType: String,
    compress: Boolean
): GeneratedCoverPreview {
    if (bytes.isEmpty()) error("封面图片为空")
    val name = displayName.ifBlank { "cover" }
    val mediaType = coverMediaTypeFromBytes(bytes)
        ?: coverMediaType(name, contentType)
    if (mediaType !in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) {
        error("仅支持 JPG、PNG、WebP、GIF 图片")
    }
    val size = imageSize(bytes) ?: error("无法解析封面图片")
    validateImageDimensions(size, "封面图片")
    val prepared = if (compress) {
        compressInsertedCover(bytes, mediaType, size)
    } else {
        PreparedCoverImage(bytes, mediaType, size.first, size.second)
    }
    return GeneratedCoverPreview(
        title = name,
        bytes = prepared.bytes,
        mediaType = prepared.mediaType,
        width = prepared.width,
        height = prepared.height,
        convertedFromMediaType = if (prepared.mediaType != mediaType) mediaType else null
    )
}

internal fun coverImageInfoLabel(
    contentResolver: ContentResolver,
    uriText: String
): String {
    if (uriText.isBlank()) return ""
    return runCatching {
        val uri = Uri.parse(uriText)
        var displayName = displayName(contentResolver, uri)
        var byteSize: Long? = null
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex).orEmpty().ifBlank { displayName }
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    byteSize = cursor.getLong(sizeIndex).takeIf { it >= 0 }
                }
            }
        }
        val mediaType = coverMediaType(
            displayName.ifBlank { uri.lastPathSegment.orEmpty() },
            contentResolver.getType(uri).orEmpty()
        )
        val typeLabel = when (mediaType) {
            "image/jpeg" -> "JPG"
            "image/png" -> "PNG"
            "image/webp" -> "WebP"
            "image/gif" -> "GIF"
            else -> mediaType.substringAfter('/').uppercase().ifBlank { "图片" }
        }
        val sizeLabel = byteSize?.let { compactByteSize(it) } ?: "大小未知"
        "类型：$typeLabel | 大小：$sizeLabel"
    }.getOrDefault("")
}

internal fun imageSize(bytes: ByteArray): Pair<Int, Int>? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val width = options.outWidth
    val height = options.outHeight
    return if (width > 0 && height > 0) width to height else null
}

internal fun validateImageDimensions(size: Pair<Int, Int>, label: String) {
    val width = size.first
    val height = size.second
    val pixels = width.toLong() * height.toLong()
    if (width > IMAGE_MAX_EDGE || height > IMAGE_MAX_EDGE || pixels > IMAGE_MAX_PIXELS) {
        error("$label 像素过大，最多支持最大边 $IMAGE_MAX_EDGE 或 ${IMAGE_MAX_PIXELS / 10_000} 万像素")
    }
}

internal fun compressInsertedCover(
    bytes: ByteArray,
    mediaType: String,
    size: Pair<Int, Int>
): PreparedCoverImage {
    if (mediaType == "image/gif") {
        return PreparedCoverImage(bytes, mediaType, size.first, size.second)
    }
    val maxEdge = maxOf(size.first, size.second)
    if (bytes.size <= INSERTED_COVER_TARGET_MAX_BYTES && maxEdge <= INSERTED_COVER_MAX_EDGE) {
        return PreparedCoverImage(bytes, mediaType, size.first, size.second)
    }
    val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return PreparedCoverImage(bytes, mediaType, size.first, size.second)
    var working: Bitmap = source
    try {
        if (maxEdge > INSERTED_COVER_MAX_EDGE) {
            val scale = INSERTED_COVER_MAX_EDGE / maxEdge.toFloat()
            val width = maxOf(1, (size.first * scale).toInt())
            val height = maxOf(1, (size.second * scale).toInt())
            working = Bitmap.createScaledBitmap(source, width, height, true)
        }
        val targetMediaType = if (working.hasAlpha()) "image/png" else "image/jpeg"
        val compressedBytes = if (targetMediaType == "image/png") {
            ByteArrayOutputStream().use { output ->
                working.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } else {
            compressInsertedCoverJpeg(working)
        }
        if (compressedBytes.isEmpty() || compressedBytes.size >= bytes.size) {
            return PreparedCoverImage(bytes, mediaType, size.first, size.second)
        }
        return PreparedCoverImage(
            bytes = compressedBytes,
            mediaType = targetMediaType,
            width = working.width,
            height = working.height
        )
    } finally {
        if (working !== source) working.recycle()
        source.recycle()
    }
}

private fun compressInsertedCoverJpeg(bitmap: Bitmap): ByteArray {
    var bestBytes = ByteArray(0)
    var quality = INSERTED_COVER_JPEG_QUALITY
    while (quality >= INSERTED_COVER_JPEG_MIN_QUALITY) {
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
        bestBytes = bytes
        if (bytes.size <= INSERTED_COVER_TARGET_MAX_BYTES) break
        quality -= INSERTED_COVER_JPEG_QUALITY_STEP
    }
    return bestBytes
}

internal fun compressGeneratedCover(bitmap: Bitmap): ByteArray {
    var bestBytes = ByteArray(0)
    var bestQuality = GENERATED_COVER_JPEG_QUALITY
    var quality = GENERATED_COVER_JPEG_QUALITY
    while (quality >= GENERATED_COVER_JPEG_MIN_QUALITY) {
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
        bestBytes = bytes
        bestQuality = quality
        if (bytes.size <= GENERATED_COVER_JPEG_TARGET_MAX_BYTES) {
            break
        }
        quality -= GENERATED_COVER_JPEG_QUALITY_STEP
    }
    while (
        bestBytes.size < GENERATED_COVER_JPEG_TARGET_MIN_BYTES &&
        bestQuality < GENERATED_COVER_JPEG_QUALITY
    ) {
        val nextQuality = minOf(
            GENERATED_COVER_JPEG_QUALITY,
            bestQuality + GENERATED_COVER_JPEG_FINE_STEP
        )
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, nextQuality, output)
            output.toByteArray()
        }
        if (bytes.size > GENERATED_COVER_JPEG_TARGET_MAX_BYTES) break
        bestBytes = bytes
        bestQuality = nextQuality
    }
    return bestBytes
}

internal fun coverMediaType(coverUrl: String, contentType: String): String {
    val normalized = contentType.substringBefore(';').trim().lowercase()
    when (normalized) {
        "image/jpg", "image/pjpeg" -> return "image/jpeg"
        "image/x-png" -> return "image/png"
    }
    if (normalized in setOf("image/jpeg", "image/png", "image/webp", "image/gif")) return normalized
    val path = coverUrl.substringBefore('?').substringBefore('#')
    return when {
        path.endsWith(".jpg", ignoreCase = true) -> "image/jpeg"
        path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        path.endsWith(".jfif", ignoreCase = true) -> "image/jpeg"
        path.endsWith(".png", ignoreCase = true) -> "image/png"
        path.endsWith(".webp", ignoreCase = true) -> "image/webp"
        path.endsWith(".gif", ignoreCase = true) -> "image/gif"
        else -> "image/jpeg"
    }
}

internal fun coverMediaTypeFromBytes(bytes: ByteArray): String? {
    fun byteAt(index: Int): Int = bytes.getOrNull(index)?.toInt()?.and(0xFF) ?: -1
    fun asciiAt(index: Int, value: Char): Boolean = byteAt(index) == value.code
    return when {
        byteAt(0) == 0xFF && byteAt(1) == 0xD8 && byteAt(2) == 0xFF -> "image/jpeg"
        byteAt(0) == 0x89 &&
            asciiAt(1, 'P') &&
            asciiAt(2, 'N') &&
            asciiAt(3, 'G') &&
            byteAt(4) == 0x0D &&
            byteAt(5) == 0x0A &&
            byteAt(6) == 0x1A &&
            byteAt(7) == 0x0A -> "image/png"
        asciiAt(0, 'G') && asciiAt(1, 'I') && asciiAt(2, 'F') -> "image/gif"
        asciiAt(0, 'R') &&
            asciiAt(1, 'I') &&
            asciiAt(2, 'F') &&
            asciiAt(3, 'F') &&
            asciiAt(8, 'W') &&
            asciiAt(9, 'E') &&
            asciiAt(10, 'B') &&
            asciiAt(11, 'P') -> "image/webp"
        else -> null
    }
}

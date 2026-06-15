package com.eteditor

import android.content.ContentResolver
import android.content.res.AssetManager
import android.net.Uri
import com.eteditor.core.EpubBook

data class GeneratedCoverPreview(
    val title: String,
    val bytes: ByteArray,
    val mediaType: String,
    val width: Int,
    val height: Int
)

internal data class CoverTitleLayout(
    val left: Float,
    val y: Float,
    val fontSize: Float,
    val step: Float
)

internal data class PreparedCoverImage(
    val bytes: ByteArray,
    val mediaType: String,
    val width: Int,
    val height: Int
)

internal data class InsertImageResource(
    val fileName: String,
    val bytes: ByteArray,
    val mediaType: String
)

internal data class CoverToolPreviewResult(
    val success: Boolean,
    val preview: GeneratedCoverPreview? = null,
    val sourceLabel: String = "",
    val message: String = ""
)

internal data class CoverImageInsertResult(
    val success: Boolean,
    val fileName: String = "",
    val message: String = ""
)

internal fun buildInsertedCoverPreviewFromUri(
    contentResolver: ContentResolver,
    uri: Uri,
    compress: Boolean
): GeneratedCoverPreview {
    val bytes = contentResolver.readUriBytesLimited(
        uri = uri,
        maxBytes = IMAGE_FILE_MAX_BYTES,
        label = "封面图片",
        openError = "无法读取封面图片"
    )
    val name = displayName(contentResolver, uri).ifBlank {
        uri.lastPathSegment.orEmpty().ifBlank { "cover" }
    }
    return buildInsertedCoverPreviewFromImageBytes(
        bytes = bytes,
        displayName = name,
        contentType = contentResolver.getType(uri).orEmpty(),
        compress = compress
    )
}

internal fun buildCoverPreviewForTool(
    assets: AssetManager,
    contentResolver: ContentResolver,
    parameters: CoverParameters,
    defaultTitle: String
): CoverToolPreviewResult {
    return when (parameters.mode) {
        COVER_MODE_GENERATE -> buildGeneratedCoverPreviewForTitle(
            assets = assets,
            title = parameters.title.ifBlank { defaultTitle }
        )
        else -> {
            val imageUri = parameters.imageUri.trim()
            if (imageUri.isBlank()) {
                CoverToolPreviewResult(success = false, message = "请选择封面图片")
            } else {
                CoverToolPreviewResult(
                    success = true,
                    preview = buildInsertedCoverPreviewFromUri(
                        contentResolver = contentResolver,
                        uri = Uri.parse(imageUri),
                        compress = parameters.compress
                    ),
                    sourceLabel = "插入封面"
                )
            }
        }
    }
}

internal fun buildGeneratedCoverPreviewForTitle(
    assets: AssetManager,
    title: String
): CoverToolPreviewResult {
    val cleanTitle = title.trim()
    if (cleanTitle.isBlank()) {
        return CoverToolPreviewResult(success = false, message = "请输入封面标题")
    }
    if (coverTitleLength(cleanTitle) > GENERATED_COVER_MAX_CHARS) {
        return CoverToolPreviewResult(
            success = false,
            message = "封面标题超过 $GENERATED_COVER_MAX_CHARS 字，已跳过生成封面"
        )
    }
    return CoverToolPreviewResult(
        success = true,
        preview = buildGeneratedCover(assets, cleanTitle, generatedCoverTargetMediaType()),
        sourceLabel = "生成封面"
    )
}

internal fun insertImageResourceIntoEpubBook(
    book: EpubBook,
    parameters: CoverParameters,
    assets: AssetManager,
    contentResolver: ContentResolver,
    noteAssetPath: String,
    warningAssetPath: String
): CoverImageInsertResult {
    val image = when (parameters.imageInsertType) {
        COVER_IMAGE_INSERT_WARNING -> InsertImageResource(
            fileName = "wenli.png",
            bytes = assets.open(warningAssetPath).use { stream -> stream.readBytes() },
            mediaType = "image/png"
        )
        COVER_IMAGE_INSERT_CUSTOM -> {
            val imageUri = parameters.imageUri.trim()
            if (imageUri.isBlank()) {
                return CoverImageInsertResult(success = false, message = "请选择图片文件")
            }
            customInsertImageResource(
                book = book,
                contentResolver = contentResolver,
                uri = Uri.parse(imageUri)
            )
        }
        else -> InsertImageResource(
            fileName = "note.webp",
            bytes = assets.open(noteAssetPath).use { stream -> stream.readBytes() },
            mediaType = "image/webp"
        )
    }
    validateInsertImageMediaType(image.mediaType)
    if (image.bytes.isEmpty()) error("图片内容为空")
    writeImageResourceToEpub(book, image.fileName, image.bytes, image.mediaType)
    return CoverImageInsertResult(success = true, fileName = image.fileName)
}

private fun customInsertImageResource(
    book: EpubBook,
    contentResolver: ContentResolver,
    uri: Uri
): InsertImageResource {
    val bytes = contentResolver.readUriBytesLimited(
        uri = uri,
        maxBytes = IMAGE_FILE_MAX_BYTES,
        label = "图片文件",
        openError = "无法读取图片文件"
    )
    if (bytes.isEmpty()) error("图片内容为空")
    val name = displayName(contentResolver, uri).ifBlank {
        uri.lastPathSegment.orEmpty().ifBlank { "image" }
    }
    val mediaType = coverMediaTypeFromBytes(bytes)
        ?: coverMediaType(name, contentResolver.getType(uri).orEmpty())
    validateInsertImageMediaType(mediaType)
    val fileName = nextCustomInsertImageFileName(book, coverExtension(mediaType))
    return InsertImageResource(fileName = fileName, bytes = bytes, mediaType = mediaType)
}

package com.eteditor

import android.net.Uri
import com.eteditor.core.DocumentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun EditorController.coverImageInfoLabel(uriText: String): String {
    return coverImageInfoLabel(appContext.contentResolver, uriText)
}

fun EditorController.defaultCoverTitle(): String {
    return defaultCoverTitleForDocument(
        kind = kind,
        epubMetadataTitle = epub?.metadataTitle.orEmpty(),
        title = title
    )
}

fun EditorController.clearGeneratedCoverPreview() {
    generatedCoverPreview = null
}

suspend fun EditorController.generateCoverPreview(title: String): Boolean {
    if (kind != DocumentKind.Epub) {
        statusMessage = "\u751f\u6210\u5c01\u9762\u4ec5\u652f\u6301 EPUB"
        return false
    }
    return try {
        val result = withContext(Dispatchers.IO) {
            buildGeneratedCoverPreviewForTitle(appContext.assets, title)
        }
        if (!result.success) {
            if (result.message.isNotBlank()) statusMessage = result.message
            return false
        }
        generatedCoverPreview = result.preview
        statusMessage = "\u5c01\u9762\u5df2\u751f\u6210"
        true
    } catch (error: Throwable) {
        generatedCoverPreview = null
        statusMessage = coverFailureMessage("\u5c01\u9762\u751f\u6210\u5931\u8d25", error)
        false
    }
}

suspend fun EditorController.prepareInsertedCoverPreview(
    uri: Uri,
    compress: Boolean = coverParameters().compress
): Boolean {
    if (kind != DocumentKind.Epub) {
        statusMessage = "\u63d2\u5165\u5c01\u9762\u4ec5\u652f\u6301 EPUB"
        return false
    }
    return try {
        rememberReadableDocumentUri(appContext, uri)
        val preview = withContext(Dispatchers.IO) {
            buildInsertedCoverPreviewFromUri(appContext.contentResolver, uri, compress)
        }
        generatedCoverPreview = preview
        statusMessage = "\u5c01\u9762\u56fe\u7247\u5df2\u9009\u62e9"
        true
    } catch (error: Throwable) {
        generatedCoverPreview = null
        statusMessage = coverFailureMessage("\u5c01\u9762\u56fe\u7247\u8bfb\u53d6\u5931\u8d25", error)
        false
    }
}

fun EditorController.applyGeneratedCoverPreview(): Boolean {
    val preview = generatedCoverPreview ?: run {
        statusMessage = "\u8bf7\u5148\u51c6\u5907\u5c01\u9762\u9884\u89c8"
        return false
    }
    return writeCoverPreview(preview, "\u5c01\u9762\u5df2\u5199\u5165 EPUB")
}

internal fun EditorController.runCoverTool(tool: EditorTool): Boolean {
    if (kind != DocumentKind.Epub) {
        statusMessage = "\u56fe\u7247\u5904\u7406\u4ec5\u652f\u6301 EPUB"
        return false
    }
    val parameters = coverParameters(tool)
    if (parameters.mode == COVER_MODE_IMAGE_INSERT) {
        return insertImageResourceIntoEpub(parameters)
    }
    return try {
        val result = buildCoverPreviewForTool(
            assets = appContext.assets,
            contentResolver = appContext.contentResolver,
            parameters = parameters,
            defaultTitle = defaultCoverTitle()
        )
        if (!result.success) {
            if (result.message.isNotBlank()) statusMessage = result.message
            return false
        }
        val preview = result.preview ?: return false
        generatedCoverPreview = preview
        if (parameters.preview) {
            statusMessage = needsConfirmationMessage()
            return false
        }
        writeCoverPreview(preview, "${result.sourceLabel} \u5df2\u5199\u5165 EPUB")
    } catch (error: Throwable) {
        generatedCoverPreview = null
        statusMessage = coverFailureMessage("\u56fe\u7247\u5904\u7406\u5931\u8d25", error)
        false
    }
}

internal suspend fun EditorController.runCoverToolAsync(tool: EditorTool): Boolean {
    if (kind != DocumentKind.Epub) {
        statusMessage = "图片处理仅支持 EPUB"
        return false
    }
    val parameters = coverParameters(tool)
    if (parameters.mode == COVER_MODE_IMAGE_INSERT) {
        return insertImageResourceIntoEpubAsync(parameters)
    }
    return try {
        val result = withContext(Dispatchers.IO) {
            buildCoverPreviewForTool(
                assets = appContext.assets,
                contentResolver = appContext.contentResolver,
                parameters = parameters,
                defaultTitle = defaultCoverTitle()
            )
        }
        if (!result.success) {
            if (result.message.isNotBlank()) statusMessage = result.message
            return false
        }
        val preview = result.preview ?: return false
        generatedCoverPreview = preview
        if (parameters.preview) {
            statusMessage = needsConfirmationMessage()
            return false
        }
        writeCoverPreviewAsync(preview, "${result.sourceLabel} 已写入 EPUB")
    } catch (error: Throwable) {
        generatedCoverPreview = null
        statusMessage = coverFailureMessage("图片处理失败", error)
        false
    }
}

private fun EditorController.insertImageResourceIntoEpub(parameters: CoverParameters): Boolean {
    val book = epub ?: run {
        statusMessage = "\u63d2\u5165\u56fe\u7247\u4ec5\u652f\u6301 EPUB"
        return false
    }
    return try {
        if (parameters.imageInsertType == COVER_IMAGE_INSERT_CUSTOM && parameters.imageUri.isNotBlank()) {
            rememberReadableDocumentUri(appContext, Uri.parse(parameters.imageUri.trim()))
        }
        val result = insertImageResourceIntoEpubBook(
            book = book,
            parameters = parameters,
            assets = appContext.assets,
            contentResolver = appContext.contentResolver,
            noteAssetPath = insertImageNoteAssetPath(),
            warningAssetPath = insertImageWarningAssetPath()
        )
        if (!result.success) {
            if (result.message.isNotBlank()) statusMessage = result.message
            return false
        }
        generatedCoverPreview = null
        checkReport = null
        markDocumentChanged()
        statusMessage = "\u5df2\u63d2\u5165\u56fe\u7247\uff1a${result.fileName}"
        true
    } catch (error: Throwable) {
        statusMessage = coverFailureMessage("\u63d2\u5165\u56fe\u7247\u5931\u8d25", error)
        false
    }
}

private suspend fun EditorController.insertImageResourceIntoEpubAsync(parameters: CoverParameters): Boolean {
    val sourceBook = epub ?: run {
        statusMessage = "插入图片仅支持 EPUB"
        return false
    }
    return try {
        if (parameters.imageInsertType == COVER_IMAGE_INSERT_CUSTOM && parameters.imageUri.isNotBlank()) {
            rememberReadableDocumentUri(appContext, Uri.parse(parameters.imageUri.trim()))
        }
        val nextBook = sourceBook.mutableDeepCopy()
        val result = withContext(Dispatchers.IO) {
            insertImageResourceIntoEpubBook(
                book = nextBook,
                parameters = parameters,
                assets = appContext.assets,
                contentResolver = appContext.contentResolver,
                noteAssetPath = insertImageNoteAssetPath(),
                warningAssetPath = insertImageWarningAssetPath()
            )
        }
        if (!result.success) {
            if (result.message.isNotBlank()) statusMessage = result.message
            return false
        }
        epub = nextBook
        generatedCoverPreview = null
        checkReport = null
        markDocumentChanged()
        statusMessage = "已插入图片：${result.fileName}"
        true
    } catch (error: Throwable) {
        statusMessage = coverFailureMessage("插入图片失败", error)
        false
    }
}

private fun EditorController.writeCoverPreview(preview: GeneratedCoverPreview, successMessage: String): Boolean {
    val book = epub ?: run {
        statusMessage = "\u5c01\u9762\u5199\u5165\u4ec5\u652f\u6301 EPUB"
        return false
    }
    return try {
        val coverName = coverFileNameForMediaType(preview.mediaType)
        writeCoverToEpub(book, coverName, preview.bytes, preview.mediaType)
        checkReport = null
        markDocumentChanged()
        refreshChapters()
        statusMessage = successMessage
        true
    } catch (error: Throwable) {
        statusMessage = coverFailureMessage("\u5c01\u9762\u5199\u5165\u5931\u8d25", error)
        false
    }
}

private suspend fun EditorController.writeCoverPreviewAsync(preview: GeneratedCoverPreview, successMessage: String): Boolean {
    val sourceBook = epub ?: run {
        statusMessage = "封面写入仅支持 EPUB"
        return false
    }
    return try {
        val nextBook = sourceBook.mutableDeepCopy()
        val coverName = coverFileNameForMediaType(preview.mediaType)
        withContext(Dispatchers.Default) {
            writeCoverToEpub(nextBook, coverName, preview.bytes, preview.mediaType)
        }
        epub = nextBook
        checkReport = null
        markDocumentChanged()
        refreshChapters()
        statusMessage = successMessage
        true
    } catch (error: Throwable) {
        statusMessage = coverFailureMessage("封面写入失败", error)
        false
    }
}

private fun coverFailureMessage(prefix: String, error: Throwable): String {
    return "$prefix\uff1a${error.message ?: error.javaClass.simpleName}"
}

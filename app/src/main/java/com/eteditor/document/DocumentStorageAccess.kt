package com.eteditor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

internal suspend fun readDocumentBytes(
    context: Context,
    uri: Uri,
    maxBytes: Long,
    label: String
): ByteArray = withContext(Dispatchers.IO) {
    context.contentResolver.readUriBytesLimited(
        uri = uri,
        maxBytes = maxBytes,
        label = label,
        openError = "无法打开输入文件"
    )
}

internal fun rememberReadableDocumentUri(context: Context, rawUri: String) {
    if (rawUri.isBlank()) return
    runCatching { rememberReadableDocumentUri(context, Uri.parse(rawUri)) }
}

internal fun rememberReadableDocumentUri(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

internal fun rememberWritableDocumentUri(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }
}

internal suspend fun writeDocumentBytes(context: Context, uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
    var lastError: Throwable? = null
    val wroteWithDescriptor = listOf("rwt", "rw").any { mode ->
        runCatching {
            context.contentResolver.openFileDescriptor(uri, mode)?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { stream ->
                    stream.channel.truncate(0)
                    stream.channel.position(0)
                    stream.write(bytes)
                    stream.flush()
                }
            } ?: error("无法打开输出文件")
        }.onFailure { error ->
            lastError = error
        }.isSuccess
    }
    if (wroteWithDescriptor) return@withContext

    val wroteWithStream = listOf("rwt", "wt", "w", "").any { mode ->
        runCatching {
            val output = if (mode.isBlank()) {
                context.contentResolver.openOutputStream(uri)
            } else {
                context.contentResolver.openOutputStream(uri, mode)
            } ?: error("无法打开输出文件")
            output.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
        }.onFailure { error ->
            lastError = error
        }.isSuccess
    }
    if (wroteWithStream) return@withContext

    error(
        lastError?.let { error ->
            "无法打开输出文件：${writableFileErrorMessage(error)}"
        } ?: "无法打开输出文件：文件位置不允许写入或授权已失效，请重新从文件页打开原文件"
    )
}

internal suspend fun renameDocumentFile(
    context: Context,
    uri: Uri,
    targetFileName: String
): Result<Uri?> = withContext(Dispatchers.IO) {
    runCatching {
        DocumentsContract.renameDocument(context.contentResolver, uri, targetFileName)
    }
}

internal fun documentDisplayName(context: Context, uri: Uri): String {
    return displayName(context.contentResolver, uri)
}

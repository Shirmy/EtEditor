package com.eteditor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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

internal suspend fun writeDocumentBytes(context: Context, uri: Uri, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
    // 覆盖原文件前，先把原文件现有内容备份到应用私有缓存；写到一半出错时可还原，避免把原文件写坏
    val backup = backupExistingDocument(context, uri)
    try {
        replaceDocumentBytes(context, uri, bytes)
    } catch (writeError: Throwable) {
        val restored = backup != null && runCatching {
            replaceDocumentBytes(context, uri, backup.readBytes())
        }.isSuccess
        backup?.delete()
        val reason = writableFileErrorMessage(writeError)
        error(if (restored) "原文件已还原（$reason）" else reason)
    }
    backup?.delete()
}

// 把新内容完整写回原文件，并保证旧内容被彻底替换（不残留旧尾巴）；写不进去时抛出异常
private fun replaceDocumentBytes(context: Context, uri: Uri, bytes: ByteArray) {
    var lastError: Throwable? = null
    val wroteWithDescriptor = listOf("rwt", "rw").any { mode ->
        runCatching {
            context.contentResolver.openFileDescriptor(uri, mode)?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { stream ->
                    stream.channel.truncate(0)
                    stream.channel.position(0)
                    stream.write(bytes)
                    stream.flush()
                    // 强制把数据落到永久存储，避免写完即断电/崩溃丢失尾部内容
                    runCatching { stream.fd.sync() }
                }
            } ?: error("无法打开输出文件")
        }.onFailure { error ->
            lastError = error
        }.isSuccess
    }
    if (wroteWithDescriptor) return

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
                // 尽力强制落盘（兜底路径不一定拿得到落盘入口，拿不到就照常完成）
                runCatching { (stream as? FileOutputStream)?.fd?.sync() }
            }
            // 兜底写法在个别系统/位置上不会先清空旧内容，核对落盘长度，残留旧尾巴即判失败、触发还原
            val writtenLength = documentByteLength(context, uri)
            if (writtenLength >= 0 && writtenLength != bytes.size.toLong()) {
                error("写入未完整覆盖原文件，请重新从文件页打开原文件")
            }
        }.onFailure { error ->
            lastError = error
        }.isSuccess
    }
    if (wroteWithStream) return

    throw lastError ?: IllegalStateException("文件位置不允许写入或授权已失效，请重新从文件页打开原文件")
}

// 把原文件现有内容复制到应用私有缓存作为备份；没有可备份内容（如新文件）时返回 null
private fun backupExistingDocument(context: Context, uri: Uri): File? {
    return runCatching {
        val backup = File.createTempFile("save-backup-", ".bak", context.cacheDir)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(backup).use { output ->
                    input.copyTo(output)
                }
                true
            } ?: false
        }.getOrDefault(false)
        if (copied) {
            backup
        } else {
            backup.delete()
            null
        }
    }.getOrNull()
}

// 读取原文件当前实际字节长度；无法读取时返回 -1
private fun documentByteLength(context: Context, uri: Uri): Long {
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            var total = 0L
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
            }
            total
        } ?: -1L
    }.getOrDefault(-1L)
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

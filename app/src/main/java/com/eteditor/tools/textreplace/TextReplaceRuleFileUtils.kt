package com.eteditor

import android.content.ContentResolver
import android.net.Uri
import com.eteditor.core.TextCodec
import java.io.File

internal data class TextReplaceRuleFileReadResult(
    val text: String? = null,
    val message: String = ""
)

internal fun readTextReplaceRuleFileText(
    contentResolver: ContentResolver,
    pathOrUri: String
): TextReplaceRuleFileReadResult {
    val reference = pathOrUri.trim()
    if (reference.isBlank()) {
        return TextReplaceRuleFileReadResult(message = "请选择规则文件")
    }
    return runCatching {
        val uri = Uri.parse(reference)
        val fileName = when (uri.scheme?.lowercase()) {
            "content" -> displayName(contentResolver, uri)
            "file" -> java.io.File(uri.path.orEmpty()).name
            else -> java.io.File(reference).name.ifBlank { uri.lastPathSegment.orEmpty() }
        }
        if (!fileName.endsWith(".replacement", ignoreCase = true)) {
            return TextReplaceRuleFileReadResult(message = "规则文件只支持 .replacement 格式")
        }
        val bytes = when (uri.scheme?.lowercase()) {
            "content" -> contentResolver.readUriBytesLimited(
                uri = uri,
                maxBytes = REPLACEMENT_RULE_FILE_MAX_BYTES,
                label = "规则文件",
                openError = "无法打开规则文件"
            )
            "file" -> File(uri.path.orEmpty()).readBytesLimited(REPLACEMENT_RULE_FILE_MAX_BYTES, "规则文件")
            else -> File(reference).readBytesLimited(REPLACEMENT_RULE_FILE_MAX_BYTES, "规则文件")
        }
        TextReplaceRuleFileReadResult(text = TextCodec.decode(bytes).first)
    }.getOrElse { error ->
        TextReplaceRuleFileReadResult(
            message = error.message?.takeIf { it.contains("过大") } ?: "规则文件读取失败"
        )
    }
}

internal fun buildTextReplaceRulesForParameters(
    parameters: TextReplaceParameters
): TextReplaceRuleBuildResult {
    return buildTextReplaceRules(
        parameters = parameters,
        singleMode = TEXT_REPLACE_MODE_SINGLE,
        replacementMode = TEXT_REPLACE_MODE_REPLACEMENT,
        visibleTextTarget = TEXT_REPLACE_TARGET_VISIBLE
    )
}

internal fun readReplacementFileRules(
    contentResolver: ContentResolver,
    pathOrUri: String
): TextReplaceRuleBuildResult {
    val readResult = readTextReplaceRuleFileText(contentResolver, pathOrUri)
    if (readResult.text == null) {
        return TextReplaceRuleBuildResult(
            rules = null,
            message = readResult.message
        )
    }
    val buildResult = buildTextReplaceRulesFromReplacementFileText(readResult.text)
    return if (buildResult.message.isNotBlank()) {
        buildResult
    } else {
        buildResult.copy(message = readResult.message)
    }
}

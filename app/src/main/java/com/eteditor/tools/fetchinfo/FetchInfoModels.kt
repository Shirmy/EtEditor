package com.eteditor

data class FetchInfoParameters(
    val source: String,
    val searchMode: String,
    val query: String,
    val expectedAuthor: String = "",
    val content: String,
    val fetchCatalog: Boolean,
    val fetchIntro: Boolean,
    val fetchCover: Boolean,
    val authCookie: String,
    val bodyRangeStart: Int,
    val bodyRangeEnd: Int,
    val catalogFilter: String,
    val catalogFilterEnabled: Boolean = true,
    val autoTitleFormat: Boolean,
    val introFilter: String,
    val writeCatalog: Boolean,
    val writeIntro: Boolean,
    val introTargetPath: String,
    val writeCover: Boolean
)

data class FetchedCatalogItem(
    val index: Int,
    val title: String,
    val url: String = "",
    val sequence: String = "",
    val chapterTitle: String = "",
    val summary: String = "",
    val isVolume: Boolean = false
) {
    fun previewText(): String {
        if (isVolume) return title
        val parts = listOf(sequence, chapterTitle, summary).filter { it.isNotBlank() }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("|") ?: title
    }
}

data class FetchedSosadBody(
    val text: String,
    val blocks: List<FetchedSosadBodyBlock>
)

internal const val SOSAD_BODY_CSS_SYS = "sys"
internal const val SOSAD_BODY_CSS_AUTHOR_NOTE = "author-note"

data class FetchedSosadBodyBlock(
    val text: String = "",
    val imageUrl: String = "",
    val cssClass: String = ""
)

data class FetchedInfo(
    val source: String,
    val query: String,
    val resolvedUrl: String,
    val title: String,
    val author: String,
    val intro: String,
    val coverUrl: String,
    val catalog: List<FetchedCatalogItem>
)

data class FetchInfoFilterIssue(
    val lineNo: Int,
    val reason: String,
    val text: String
)

data class FetchInfoPreview(
    val toolId: String,
    val parameters: FetchInfoParameters,
    val raw: FetchedInfo,
    val filtered: FetchedInfo,
    val filterIssues: List<FetchInfoFilterIssue>
)

data class FetchInfoCatalogPreviewRow(
    val fileName: String,
    val originalTitle: String,
    val fetchedTitle: String,
    val isVolume: Boolean,
    val willCreateVolume: Boolean = false,
    val skipped: Boolean = false,
    val deleted: Boolean = false,
    val missingFetch: Boolean = false,
    val chapterPosition: Int = -1
)

internal data class FetchInfoWriteResult(
    val catalogChanged: Int = 0,
    val introWritten: Boolean = false,
    val coverWritten: Boolean = false,
    val coverError: String = ""
)

data class FetchInfoSearchChoice(
    val source: String,
    val title: String,
    val author: String,
    val detailUrl: String
)

data class FetchInfoSearchChoiceRequest(
    val toolId: String,
    val parameters: FetchInfoParameters,
    val choices: List<FetchInfoSearchChoice>
)

data class FetchInfoRetryRequest(
    val toolId: String,
    val parameters: FetchInfoParameters,
    val message: String
)

data class HttpBytes(
    val bytes: ByteArray,
    val contentType: String
)

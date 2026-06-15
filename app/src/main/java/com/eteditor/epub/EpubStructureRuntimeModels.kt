package com.eteditor

internal data class EpubChapterFileNameMatch(
    val directory: String,
    val prefix: String,
    val suffix: String,
    val extension: String,
    val numberWidth: Int,
    val number: Int
)

internal data class EpubChapterFileNameTemplate(
    val directory: String,
    val prefix: String,
    val suffix: String,
    val extension: String,
    val numberWidth: Int,
    val startNumber: Int
)

internal data class EpubNumberedTitleParts(
    val beforeNumber: String,
    val numberText: String,
    val afterNumber: String,
    val unit: String,
    val tail: String,
    val number: Int
)

internal data class EpubStructureResequenceResult(
    val renamedFiles: Int,
    val renamedTitles: Int
)

internal data class EpubChapterDeleteResult(
    val success: Boolean,
    val message: String = "",
    val deletedDisplayTitle: String = "",
    val nextPreviewIndex: Int = 0,
    val resequence: EpubStructureResequenceResult = EpubStructureResequenceResult(0, 0)
)

internal data class EpubChapterMoveResult(
    val success: Boolean,
    val movedDisplayTitle: String = "",
    val nextPreviewIndex: Int = 0
)

internal data class EpubChapterSplitResult(
    val success: Boolean,
    val message: String = "",
    val sourceDisplayTitle: String = "",
    val newTitle: String = "",
    val nextPreviewIndex: Int = 0,
    val resequence: EpubStructureResequenceResult = EpubStructureResequenceResult(0, 0)
)

internal data class EpubVolumeAddResult(
    val success: Boolean,
    val message: String = "",
    val nextPreviewIndex: Int = 0,
    val fileName: String = ""
)

internal data class EpubBodyLineDeleteResult(
    val success: Boolean,
    val message: String = ""
)

internal data class EpubBodyParagraphWrapResult(
    val success: Boolean,
    val nextBody: String = "",
    val message: String = ""
)

internal data class EpubBodyLineVolumeResult(
    val success: Boolean,
    val message: String = "",
    val nextPreviewIndex: Int = 0,
    val volumeDisplayTitle: String = ""
)

internal data class EpubTitleHeadingFormat(
    val tag: String,
    val classValue: String
)

package com.eteditor

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

private const val GENERATED_COVER_WIDTH = 900
private const val GENERATED_COVER_HEIGHT = 1200

// 标题总字数上限（每个汉字、每个散字母各算一个字，超过则不生成）
internal const val GENERATED_COVER_MAX_CHARS = 16
// 竖排时不超过该字数走单列，超过则走双列
private const val GENERATED_COVER_SINGLE_COLUMN_MAX = 9

private const val GENERATED_COVER_TITLE_RIGHT = 855f
private const val GENERATED_COVER_TOP_MARGIN = 50f
private const val GENERATED_COVER_BOTTOM_LIMIT = 1150f
private const val GENERATED_COVER_STEP_RATIO = 1.15f
private const val GENERATED_COVER_MIN_FONT = 20f

// 单列字号：字数越多字号越小，1 字最大、9 字最小，中间平滑过渡
private const val GENERATED_COVER_SINGLE_MAX_FONT = 300f
private const val GENERATED_COVER_SINGLE_MIN_FONT = 140f

// 双列：最右列先填满该字数，溢出进左列
private const val GENERATED_COVER_FIRST_COL_MAX = 8
private const val GENERATED_COVER_TWO_COL_START_FONT = 160f
private const val GENERATED_COVER_TWO_COL_GAP_RATIO = 0.2f
private const val GENERATED_COVER_TWO_COL_LEFT_MARGIN = 45f

// 横排：默认字号固定，一行约 4-5 个汉字宽；超宽词放不下时缩小
private const val GENERATED_COVER_H_SIDE_MARGIN = 50f
private const val GENERATED_COVER_H_TOP_MARGIN = 90f
private const val GENERATED_COVER_H_FONT_SIZE = 150f
private const val GENERATED_COVER_H_LINE_RATIO = 1.25f
private const val GENERATED_COVER_H_SPACE_RATIO = 0.15f

private const val GENERATED_COVER_FONT_ASSET = "cover_generator/SourceHanSerifCN-Heavy-4.otf"
private const val GENERATED_COVER_BACKGROUND_ASSET = "cover_generator/cover_background.jpg"

// 连续两个及以上的字母/数字算一串"单词"，出现即整个标题走横排
private val GENERATED_COVER_LETTER_RUN = Regex("[A-Za-z0-9]{2,}")
// 分词：连续字母/数字为一词，其余每个字符各自成词
private val GENERATED_COVER_WORD = Regex("[A-Za-z0-9]+|[^A-Za-z0-9]")
private val GENERATED_COVER_LETTERS = Regex("[A-Za-z0-9]+")

internal enum class GeneratedCoverTitleLayoutMode {
    SingleColumn,
    TwoColumn,
    Horizontal
}

internal fun generatedCoverTargetMediaType(): String {
    return "image/jpeg"
}

internal fun coverTitleLength(title: String): Int {
    return title.codePointCount(0, title.length)
}

internal fun generatedCoverTitleLayoutMode(title: String): GeneratedCoverTitleLayoutMode {
    return when {
        GENERATED_COVER_LETTER_RUN.containsMatchIn(title) ->
            GeneratedCoverTitleLayoutMode.Horizontal
        coverTitleLength(title) > GENERATED_COVER_SINGLE_COLUMN_MAX ->
            GeneratedCoverTitleLayoutMode.TwoColumn
        else ->
            GeneratedCoverTitleLayoutMode.SingleColumn
    }
}

internal fun buildGeneratedCover(
    assets: AssetManager,
    title: String,
    mediaType: String
): GeneratedCoverPreview {
    val background = assets.open(GENERATED_COVER_BACKGROUND_ASSET).use { stream ->
        BitmapFactory.decodeStream(stream) ?: error("无法读取封面底图")
    }
    val bitmap = Bitmap.createBitmap(
        GENERATED_COVER_WIDTH,
        GENERATED_COVER_HEIGHT,
        Bitmap.Config.ARGB_8888
    )
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(248, 246, 240))
        drawGeneratedCoverBackground(canvas, background)

        val chars = titleCharacters(title)
        if (chars.size > GENERATED_COVER_MAX_CHARS) {
            error("封面标题最多 ${GENERATED_COVER_MAX_CHARS} 字")
        }
        val font = Typeface.createFromAsset(assets, GENERATED_COVER_FONT_ASSET)
        val paint = generatedCoverPaint(font)

        when (generatedCoverTitleLayoutMode(title)) {
            GeneratedCoverTitleLayoutMode.Horizontal ->
                drawGeneratedCoverHorizontal(canvas, title, paint)
            GeneratedCoverTitleLayoutMode.TwoColumn ->
                drawGeneratedCoverTwoColumn(canvas, chars, paint)
            GeneratedCoverTitleLayoutMode.SingleColumn ->
                drawGeneratedCoverSingleColumn(canvas, chars, paint)
        }

        val bytes = compressGeneratedCover(bitmap)
        return GeneratedCoverPreview(
            title = title,
            bytes = bytes,
            mediaType = mediaType,
            width = GENERATED_COVER_WIDTH,
            height = GENERATED_COVER_HEIGHT
        )
    } finally {
        background.recycle()
        bitmap.recycle()
    }
}

private fun generatedCoverPaint(font: Typeface): Paint {
    return Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.rgb(0, 43, 92)
        typeface = font
        textAlign = Paint.Align.LEFT
    }
}

private fun drawGeneratedCoverBackground(canvas: Canvas, background: Bitmap) {
    val scale = maxOf(
        GENERATED_COVER_WIDTH / background.width.toFloat(),
        GENERATED_COVER_HEIGHT / background.height.toFloat()
    )
    val drawWidth = background.width * scale
    val drawHeight = background.height * scale
    val left = (GENERATED_COVER_WIDTH - drawWidth) / 2f
    val top = (GENERATED_COVER_HEIGHT - drawHeight) / 2f
    val dest = RectF(left, top, left + drawWidth, top + drawHeight)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(background, null, dest, paint)
}

// 单列字号：n 字时在 300→140 之间线性取值
internal fun generatedCoverSingleColumnFontSize(charCount: Int): Float {
    val n = charCount.coerceIn(1, GENERATED_COVER_SINGLE_COLUMN_MAX)
    if (GENERATED_COVER_SINGLE_COLUMN_MAX <= 1) return GENERATED_COVER_SINGLE_MAX_FONT
    val t = (n - 1).toFloat() / (GENERATED_COVER_SINGLE_COLUMN_MAX - 1)
    return GENERATED_COVER_SINGLE_MAX_FONT -
        (GENERATED_COVER_SINGLE_MAX_FONT - GENERATED_COVER_SINGLE_MIN_FONT) * t
}

private fun generatedCoverCharacterBounds(chars: List<String>, paint: Paint): List<Rect> {
    return chars.map { char ->
        Rect().also { rect -> paint.getTextBounds(char, 0, char.length, rect) }
    }
}

// 竖排单列：字号随字数平滑收敛，整列压在同一条中轴线上，靠右侧
private fun drawGeneratedCoverSingleColumn(canvas: Canvas, chars: List<String>, paint: Paint) {
    val n = chars.size
    val available = GENERATED_COVER_BOTTOM_LIMIT - GENERATED_COVER_TOP_MARGIN

    var fs = generatedCoverSingleColumnFontSize(n)
    var bounds: List<Rect>
    var minTop: Int
    var step: Float
    while (true) {
        paint.textSize = fs
        bounds = generatedCoverCharacterBounds(chars, paint)
        minTop = bounds.minOf { it.top }
        val maxBottom = bounds.maxOf { it.bottom }
        val glyphHeight = (maxBottom - minTop).toFloat()
        step = if (n <= 1) 0f else fs * GENERATED_COVER_STEP_RATIO
        val columnHeight = glyphHeight + (n - 1) * step
        if (columnHeight <= available || fs <= GENERATED_COVER_MIN_FONT) break
        fs -= 2f
    }

    val maxRight = bounds.maxOf { it.right }.toFloat()
    val centerX = GENERATED_COVER_TITLE_RIGHT - maxRight / 2f
    val y = GENERATED_COVER_TOP_MARGIN - minTop
    chars.forEachIndexed { index, char ->
        val b = bounds[index]
        val mid = (b.left + b.right) / 2f
        canvas.drawText(char, centerX - mid, y + index * step, paint)
    }
}

// 竖排双列：最右列先填满 8 个，溢出进左列；两列各按列宽中轴线对齐，都靠右侧
private fun drawGeneratedCoverTwoColumn(canvas: Canvas, chars: List<String>, paint: Paint) {
    val n = chars.size
    val rightCount = minOf(n, GENERATED_COVER_FIRST_COL_MAX)
    val rightChars = chars.subList(0, rightCount)
    val leftChars = chars.subList(rightCount, n)
    val tall = maxOf(rightCount, leftChars.size)

    val availableH = GENERATED_COVER_BOTTOM_LIMIT - GENERATED_COVER_TOP_MARGIN
    val availableW = GENERATED_COVER_TITLE_RIGHT - GENERATED_COVER_TWO_COL_LEFT_MARGIN

    var fs = GENERATED_COVER_TWO_COL_START_FONT
    var minTop = 0
    var glyphWidth = 0f
    var step = 0f
    var gap = 0f
    var bounds: List<Rect> = emptyList()
    while (true) {
        paint.textSize = fs
        bounds = generatedCoverCharacterBounds(chars, paint)
        minTop = bounds.minOf { it.top }
        val maxBottom = bounds.maxOf { it.bottom }
        val glyphHeight = (maxBottom - minTop).toFloat()
        glyphWidth = bounds.maxOf { it.right }.toFloat()
        step = fs * GENERATED_COVER_STEP_RATIO
        gap = glyphWidth * GENERATED_COVER_TWO_COL_GAP_RATIO
        val columnHeight = glyphHeight + (tall - 1) * step
        val totalWidth = (if (leftChars.isNotEmpty()) 2f else 1f) * glyphWidth +
            (if (leftChars.isNotEmpty()) gap else 0f)
        if ((columnHeight <= availableH && totalWidth <= availableW) ||
            fs <= GENERATED_COVER_MIN_FONT
        ) {
            break
        }
        fs -= 2f
    }

    paint.textSize = fs
    val y0 = GENERATED_COVER_TOP_MARGIN - minTop
    val rightColumnCenter = GENERATED_COVER_TITLE_RIGHT - glyphWidth / 2f
    val leftColumnCenter = GENERATED_COVER_TITLE_RIGHT - glyphWidth - gap - glyphWidth / 2f

    rightChars.forEachIndexed { index, char ->
        val b = bounds[index]
        canvas.drawText(char, rightColumnCenter - b.right / 2f, y0 + index * step, paint)
    }
    leftChars.forEachIndexed { index, char ->
        val b = bounds[rightCount + index]
        canvas.drawText(char, leftColumnCenter - b.right / 2f, y0 + index * step, paint)
    }
}

internal fun fitGeneratedCoverHorizontalFontSize(
    initialFontSize: Float,
    minFontSize: Float,
    availableWidth: Float,
    widestWordWidth: Float
): Float {
    if (availableWidth <= 0f || widestWordWidth <= availableWidth) return initialFontSize
    return (initialFontSize * availableWidth / widestWordWidth)
        .coerceIn(minFontSize, initialFontSize)
}

// 横排：默认固定字号，按词折行（连续字母串整体不断行），整体靠上、水平居中
private fun drawGeneratedCoverHorizontal(canvas: Canvas, title: String, paint: Paint) {
    paint.textSize = GENERATED_COVER_H_FONT_SIZE
    val words = GENERATED_COVER_WORD.findAll(title).map { it.value }.toList()
    val availableW = GENERATED_COVER_WIDTH - 2 * GENERATED_COVER_H_SIDE_MARGIN

    fun wordWidth(word: String): Float {
        val b = Rect()
        paint.getTextBounds(word, 0, word.length, b)
        return b.right.toFloat()
    }

    paint.textSize = fitGeneratedCoverHorizontalFontSize(
        initialFontSize = GENERATED_COVER_H_FONT_SIZE,
        minFontSize = GENERATED_COVER_MIN_FONT,
        availableWidth = availableW,
        widestWordWidth = words.maxOfOrNull { word -> wordWidth(word) } ?: 0f
    )
    val fontSize = paint.textSize
    val space = fontSize * GENERATED_COVER_H_SPACE_RATIO

    // 词间缝：仅当相邻两词有一方是字母串时才留缝，汉字之间贴排
    fun gapBefore(prev: String?, cur: String): Float {
        if (prev == null) return 0f
        return if (GENERATED_COVER_LETTERS.matches(prev) ||
            GENERATED_COVER_LETTERS.matches(cur)
        ) {
            space
        } else {
            0f
        }
    }

    val lines = mutableListOf<MutableList<String>>()
    var current = mutableListOf<String>()
    var currentWidth = 0f
    var prev: String? = null
    for (word in words) {
        val ww = wordWidth(word)
        val add = ww + (if (current.isNotEmpty()) gapBefore(prev, word) else 0f)
        if (current.isNotEmpty() && currentWidth + add > availableW) {
            lines.add(current)
            current = mutableListOf(word)
            currentWidth = ww
        } else {
            current.add(word)
            currentWidth += add
        }
        prev = word
    }
    if (current.isNotEmpty()) lines.add(current)

    fun lineWidth(lineWords: List<String>): Float {
        var total = 0f
        var p: String? = null
        for (word in lineWords) {
            total += gapBefore(p, word) + wordWidth(word)
            p = word
        }
        return total
    }

    val lineStep = fontSize * GENERATED_COVER_H_LINE_RATIO
    val titleChars = titleCharacters(title)
    val bounds = generatedCoverCharacterBounds(titleChars, paint)
    val minTop = bounds.minOf { it.top }
    val y0 = GENERATED_COVER_H_TOP_MARGIN - minTop

    lines.forEachIndexed { lineIndex, lineWords ->
        val lw = lineWidth(lineWords)
        var x = (GENERATED_COVER_WIDTH - lw) / 2f
        val y = y0 + lineIndex * lineStep
        var p: String? = null
        for (word in lineWords) {
            x += gapBefore(p, word)
            canvas.drawText(word, x, y, paint)
            x += wordWidth(word)
            p = word
        }
    }
}

private fun titleCharacters(title: String): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < title.length) {
        val codePoint = Character.codePointAt(title, index)
        result += String(Character.toChars(codePoint))
        index += Character.charCount(codePoint)
    }
    return result
}

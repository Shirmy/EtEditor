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
internal const val GENERATED_COVER_MAX_CHARS = 9
private const val GENERATED_COVER_TITLE_RIGHT = 855f
private const val GENERATED_COVER_TITLE_BOTTOM = 1186f
private const val GENERATED_COVER_FONT_ASSET = "cover_generator/SourceHanSerifCN-Heavy-4.otf"
private const val GENERATED_COVER_BACKGROUND_ASSET = "cover_generator/cover_background.jpg"

internal fun generatedCoverTargetMediaType(): String {
    return "image/jpeg"
}

internal fun coverTitleLength(title: String): Int {
    return title.codePointCount(0, title.length)
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
        val fontSize = generatedCoverTitleFontSize(chars.size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = Color.rgb(0, 43, 92)
            typeface = font
            textSize = fontSize
            textAlign = Paint.Align.LEFT
        }
        val layout = generatedCoverTitleLayout(chars, paint)
        paint.textSize = layout.fontSize
        chars.forEachIndexed { index, char ->
            canvas.drawText(char, layout.left, layout.y + index * layout.step, paint)
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

private fun generatedCoverTitleFontSize(charCount: Int): Float {
    val length = charCount.coerceAtLeast(1)
    return when (length) {
        1 -> 304f
        2 -> 286f
        3 -> 262f
        4 -> 236f
        5 -> 212f
        6 -> 190f
        7 -> 174f
        8 -> 156f
        else -> 144f
    }
}

private fun generatedCoverTitleLayout(
    chars: List<String>,
    paint: Paint
): CoverTitleLayout {
    val length = chars.size.coerceAtLeast(1)
    val fontSize = generatedCoverTitleFontSize(length)
    paint.textSize = fontSize
    val bounds = generatedCoverCharacterBounds(chars, paint)
    val maxRight = bounds.maxOfOrNull { it.right }?.toFloat() ?: fontSize
    val minTop = bounds.minOfOrNull { it.top }?.toFloat() ?: paint.fontMetrics.ascent
    val maxBottom = bounds.maxOfOrNull { it.bottom }?.toFloat() ?: paint.fontMetrics.descent
    val glyphHeight = maxBottom - minTop
    val top = generatedCoverTitleTop(length)
    val bottom = generatedCoverTitleBottom(length, top, glyphHeight)
    val naturalStep = if (length <= 1) {
        0f
    } else {
        (bottom - top - glyphHeight) / (length - 1)
    }
    val step = if (length <= 1) {
        0f
    } else {
        naturalStep.coerceIn(
            generatedCoverTitleMinStep(length, fontSize),
            generatedCoverTitleMaxStep(length, fontSize)
        )
    }
    return CoverTitleLayout(
        left = GENERATED_COVER_TITLE_RIGHT - maxRight,
        y = top - minTop,
        fontSize = fontSize,
        step = step
    )
}

private fun generatedCoverTitleMinStep(length: Int, fontSize: Float): Float {
    return when (length) {
        2 -> fontSize * 1.08f
        8 -> fontSize * 0.82f
        9 -> fontSize * 0.8f
        else -> fontSize * 0.68f
    }
}

private fun generatedCoverTitleMaxStep(length: Int, fontSize: Float): Float {
    return when (length) {
        2 -> fontSize * 1.45f
        8, 9 -> fontSize * 1.28f
        else -> fontSize * 1.22f
    }
}

private fun generatedCoverCharacterBounds(chars: List<String>, paint: Paint): List<Rect> {
    return chars.map { char ->
        Rect().also { rect ->
            paint.getTextBounds(char, 0, char.length, rect)
        }
    }
}

private fun generatedCoverTitleTop(length: Int): Float {
    return when (length) {
        1 -> 54f
        2 -> 44f
        3 -> 18f
        4 -> 8f
        5 -> 6f
        6 -> 8f
        7 -> 10f
        8 -> 7f
        else -> 4f
    }
}

private fun generatedCoverTitleBottom(length: Int, top: Float, glyphHeight: Float): Float {
    val preferred = when (length) {
        1 -> top + glyphHeight
        2 -> 620f
        3 -> 780f
        4 -> 950f
        5 -> 1010f
        6 -> 1100f
        7 -> 1130f
        8 -> 1180f
        else -> GENERATED_COVER_TITLE_BOTTOM
    }
    return preferred.coerceAtMost(GENERATED_COVER_TITLE_BOTTOM)
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

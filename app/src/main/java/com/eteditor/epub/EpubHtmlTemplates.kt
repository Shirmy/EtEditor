package com.eteditor

import com.eteditor.core.ChapterDetector

private const val JJWXC_CONTENT_TAG_LABEL = "\u5185\u5bb9\u6807\u7b7e"
private const val GONGZICP_TAG_LABEL = "\u6807\u7b7e"

internal fun introHtml(title: String, intro: String, source: String): String {
    val escapedTitle = title.escapeXmlText()
    val paragraphs = fetchedIntroBodyHtml(intro, source)
    return """
        |<?xml version="1.0" encoding="utf-8"?>
        |<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
        |  "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
        |
        |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        |<head>
        |  <link href="../Styles/main.css" type="text/css" rel="stylesheet"/>
        |  <title>$escapedTitle</title>
        |</head>
        |<body>
        |  <h1 class="centered-text_01">$escapedTitle</h1>
        |$paragraphs
        |</body>
        |</html>
    """.trimMargin()
}

internal fun fetchedIntroBodyHtml(intro: String, source: String): String {
    val lines = intro.lines()
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (lines.isEmpty()) return "  <p></p>"
    return buildList {
        lines.forEachIndexed { index, line ->
            when {
                source == FETCH_INFO_SOURCE_JJWXC && line.startsWith(JJWXC_CONTENT_TAG_LABEL) -> {
                    add("  <hr/>")
                    add(introParagraphHtml(line))
                }
                source == FETCH_INFO_SOURCE_GONGZICP && line.startsWith(GONGZICP_TAG_LABEL) -> {
                    add("  <hr/>")
                    add(introParagraphHtml(line))
                }
                source == FETCH_INFO_SOURCE_SOSAD && index == 0 && isSosadIntroTagLine(line) -> {
                    add(introParagraphHtml(line))
                    add("  <hr/>")
                }
                else -> add(introParagraphHtml(line))
            }
        }
    }.joinToString("\n")
}

internal fun volumeHtml(title: String): String {
    val escapedTitle = ChapterDetector.titleHeadingHtmlWithLineBreaks(title).ifBlank { title.escapeXmlText() }
    val documentTitle = ChapterDetector.cleanTitleLineBreaksAsSpace(title).escapeXmlText()
    return """
        |<?xml version="1.0" encoding="utf-8"?>
        |<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
        |  "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
        |
        |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        |<head>
        |  <link href="../Styles/main.css" type="text/css" rel="stylesheet"/>
        |<title>$documentTitle</title>
        |</head>
        |<body>
        |  <h1 class="centered-text_01">$escapedTitle</h1>
        |</body>
        |</html>
    """.trimMargin()
}

internal fun volumeHtml(title: String, paragraphs: List<String>): String {
    val escapedTitle = title.escapeXmlText()
    val documentTitle = ChapterDetector.cleanTitleLineBreaksAsSpace(title).escapeXmlText()
    val body = paragraphs
        .map { line -> line.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n") { line -> "  <p>${line.escapeXmlText()}</p>" }
    val bodySuffix = if (body.isBlank()) "" else "\n$body"
    return """
        |<?xml version="1.0" encoding="utf-8"?>
        |<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN"
        |  "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
        |
        |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        |<head>
        |  <link href="../Styles/main.css" type="text/css" rel="stylesheet"/>
        |<title>$documentTitle</title>
        |</head>
        |<body>
        |  <h1 class="centered-text_01">$escapedTitle</h1>$bodySuffix
        |</body>
        |</html>
    """.trimMargin()
}

internal fun chapterHtml(title: String, text: String): String {
    val escapedTitle = ChapterDetector.cleanTitle(title).ifBlank { "\u672a\u547d\u540d\u7ae0\u8282" }.escapeXmlText()
    val body = text
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n") { "  <p>${it.escapeXmlText()}</p>" }
        .ifBlank { "  <p></p>" }
    return """
        |<?xml version="1.0" encoding="utf-8"?>
        |<!DOCTYPE html>
        |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        |<head>
        |    <link href="../Styles/main.css" type="text/css" rel="stylesheet"/>
        |</head>
        |<body>
        |  <h2>$escapedTitle</h2>
        |$body
        |</body>
        |</html>
    """.trimMargin()
}

internal fun chapterHtml(
    title: String,
    blocks: List<InsertChapterBodyBlock>,
    imageHrefForFileName: (String) -> String
): String {
    val escapedTitle = ChapterDetector.cleanTitle(title).ifBlank { "\u672a\u547d\u540d\u7ae0\u8282" }.escapeXmlText()
    val body = chapterBodyBlocksHtml(blocks, imageHrefForFileName)
        .ifBlank { "  <p></p>" }
    return """
        |<?xml version="1.0" encoding="utf-8"?>
        |<!DOCTYPE html>
        |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
        |<head>
        |    <link href="../Styles/main.css" type="text/css" rel="stylesheet"/>
        |</head>
        |<body>
        |  <h2>$escapedTitle</h2>
        |$body
        |</body>
        |</html>
    """.trimMargin()
}

private fun isSosadIntroTagLine(line: String): Boolean {
    val tags = line.split('\u3001')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    return tags.size >= 2 &&
        tags.size <= 24 &&
        tags.all { it.length <= 16 } &&
        line.none { it in "\u3002\uff01\uff1f\uff1b\uff0c" }
}

private fun introParagraphHtml(line: String): String {
    return "  <p>${line.escapeXmlText()}</p>"
}

private fun chapterBodyBlocksHtml(
    blocks: List<InsertChapterBodyBlock>,
    imageHrefForFileName: (String) -> String
): String {
    val result = mutableListOf<String>()
    val sysParagraphs = mutableListOf<String>()

    fun flushSys() {
        if (sysParagraphs.isEmpty()) return
        result += buildString {
            append("  <div class=\"sys\">")
            sysParagraphs.forEach { paragraph ->
                append("\n    <p>")
                append(paragraph.escapeXmlText())
                append("</p>")
            }
            append("\n  </div>")
        }
        sysParagraphs.clear()
    }

    blocks.forEach { block ->
        when {
            block.cssClass == "sys" && block.text.isNotBlank() -> {
                sysParagraphs += block.text.cleanBlockParagraphs()
            }
            block.imageFileName.isNotBlank() -> {
                flushSys()
                val href = imageHrefForFileName(block.imageFileName).escapeXmlAttribute("\"")
                result += "  <div class=\"logo\">\n    <img alt=\"logo\" class=\"logo\" src=\"$href\"/>\n  </div>"
            }
            block.text.isNotBlank() -> {
                flushSys()
                block.text.cleanBlockParagraphs().forEach { paragraph ->
                    result += "  <p>${paragraph.escapeXmlText()}</p>"
                }
            }
        }
    }
    flushSys()
    return result.joinToString("\n")
}

private fun String.cleanBlockParagraphs(): List<String> {
    return lines()
        .map { line -> line.trim() }
        .filter { it.isNotBlank() }
}

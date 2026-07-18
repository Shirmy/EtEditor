package com.eteditor

import com.eteditor.core.DocumentKind
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyReadOnlyActionUtilsTest {
    @Test
    fun customActionOrderMatchesEpubAndTxtMenus() {
        assertEquals(
            listOf(
                BodyReadOnlyActionKind.Delete,
                BodyReadOnlyActionKind.Edit,
                BodyReadOnlyActionKind.Volume,
                BodyReadOnlyActionKind.Split,
                BodyReadOnlyActionKind.Wrap,
                BodyReadOnlyActionKind.Warning,
                BodyReadOnlyActionKind.AuthorNote,
                BodyReadOnlyActionKind.Annotation
            ),
            bodyReadOnlyCustomActionKinds(DocumentKind.Epub, splitAvailable = true)
        )
        assertEquals("补标签", BodyReadOnlyActionKind.Wrap.title)
        assertEquals("预警", BodyReadOnlyActionKind.Warning.title)
        assertEquals("作者有话说", BodyReadOnlyActionKind.AuthorNote.title)
        assertEquals("注解", BodyReadOnlyActionKind.Annotation.title)
        assertEquals("编辑", BodyReadOnlyActionKind.Edit.title)
        assertEquals(
            listOf(BodyReadOnlyActionKind.Delete, BodyReadOnlyActionKind.Split),
            bodyReadOnlyCustomActionKinds(DocumentKind.Txt, splitAvailable = true)
        )
        assertEquals(BodyReadOnlyActionIcon.Delete, BodyReadOnlyActionKind.Delete.icon)
        assertEquals(BodyReadOnlyActionIcon.Edit, BodyReadOnlyActionKind.Edit.icon)
    }

    @Test
    fun selectionLineUsesSelectionStartAndPreservesLineEndingShapes() {
        assertEquals(6, bodySelectionSourceLineIndex("一\n二\n三", selectionStart = 2, baseLineIndex = 5))
        assertEquals(6, bodySelectionSourceLineIndex("一\r\n二", selectionStart = 3, baseLineIndex = 5))
        assertEquals(6, bodySelectionSourceLineIndex("一\r二", selectionStart = 2, baseLineIndex = 5))
    }

    @Test
    fun selectionStartingWithCoveredLineBreakTargetsFollowingEmptyLine() {
        val text = "上一行\r\n\r\n下一行"
        val firstBreak = text.indexOf("\r\n")

        assertEquals(
            1,
            bodySelectionSourceLineIndex(
                text = text,
                selectionStart = firstBreak,
                selectionEnd = firstBreak + 2,
                baseLineIndex = 0
            )
        )
        assertEquals(
            0,
            bodySelectionSourceLineIndex(
                text = text,
                selectionStart = firstBreak,
                selectionEnd = firstBreak + 1,
                baseLineIndex = 0
            )
        )
        assertEquals(
            1,
            bodySelectionSourceLineIndex("上一行\n\n下一行", 3, 4, 0)
        )
        assertEquals(
            1,
            bodySelectionSourceLineIndex("上一行\r\r下一行", 3, 4, 0)
        )
    }
}

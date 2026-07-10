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
                BodyReadOnlyActionKind.Volume,
                BodyReadOnlyActionKind.Split,
                BodyReadOnlyActionKind.Wrap
            ),
            bodyReadOnlyCustomActionKinds(DocumentKind.Epub, splitAvailable = true)
        )
        assertEquals(
            listOf(BodyReadOnlyActionKind.Delete, BodyReadOnlyActionKind.Split),
            bodyReadOnlyCustomActionKinds(DocumentKind.Txt, splitAvailable = true)
        )
        assertEquals(BodyReadOnlyActionIcon.Delete, BodyReadOnlyActionKind.Delete.icon)
    }

    @Test
    fun selectionLineUsesSelectionStartAndPreservesLineEndingShapes() {
        assertEquals(6, bodySelectionSourceLineIndex("一\n二\n三", selectionStart = 2, baseLineIndex = 5))
        assertEquals(6, bodySelectionSourceLineIndex("一\r\n二", selectionStart = 3, baseLineIndex = 5))
        assertEquals(6, bodySelectionSourceLineIndex("一\r二", selectionStart = 2, baseLineIndex = 5))
    }
}

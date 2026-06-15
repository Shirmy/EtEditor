package com.eteditor.core

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubPathUtilsTest {
    @Test
    fun normalizePathRemovesCurrentAndParentSegments() {
        assertEquals(
            "OEBPS/Text/chapter.xhtml",
            normalizePath("OEBPS/Images/../Text/./chapter.xhtml")
        )
    }

    @Test
    fun normalizePathTreatsBackslashesAsSeparators() {
        assertEquals(
            "OEBPS/Text/chapter.xhtml",
            normalizePath("""OEBPS\Text\chapter.xhtml""")
        )
    }

    @Test
    fun normalizePathDoesNotClimbAboveRoot() {
        assertEquals(
            "chapter.xhtml",
            normalizePath("../../chapter.xhtml")
        )
    }

    @Test
    fun normalizePathCollapsesEmptySegmentsAndTrailingSlash() {
        assertEquals(
            "OEBPS/Images",
            normalizePath("/OEBPS//Text/../Images/")
        )
    }

    @Test
    fun normalizePathHandlesMixedSeparatorsAndInterleavedParents() {
        assertEquals(
            "OEBPS/Styles/main.css",
            normalizePath("""OEBPS\Text/../Images/../Styles/./main.css""")
        )
    }

    @Test
    fun normalizePathReturnsEmptyForBlankAndOnlyParentSegments() {
        assertEquals("", normalizePath(""))
        assertEquals("", normalizePath("./"))
        assertEquals("", normalizePath("../../"))
    }

    @Test
    fun relativeHrefBuildsPathBetweenEpubDirectories() {
        assertEquals(
            "../Text/chapter.xhtml",
            relativeHref("OEBPS/Nav", "OEBPS/Text/chapter.xhtml")
        )
        assertEquals(
            "chapter.xhtml",
            relativeHref("OEBPS/Text", "OEBPS/Text/chapter.xhtml")
        )
        assertEquals(
            "OEBPS/Text/chapter.xhtml",
            relativeHref("", "OEBPS/Text/chapter.xhtml")
        )
    }

    @Test
    fun relativeHrefBuildsPathFromNestedDirectoryToSiblingTree() {
        assertEquals(
            "../../Styles/main.css",
            relativeHref("OEBPS/Text/Parts", "OEBPS/Styles/main.css")
        )
        assertEquals(
            "../chapter.xhtml",
            relativeHref("OEBPS/Text/Parts", "OEBPS/Text/chapter.xhtml")
        )
    }

    @Test
    fun relativeHrefNormalizesFromAndTargetPathsBeforeComputing() {
        assertEquals(
            "../../Styles/main.css",
            relativeHref("""OEBPS\Text/Parts/./Extra/..""", "OEBPS/Images/../Styles/main.css")
        )
    }

    @Test
    fun decodeUrlKeepsInvalidEscapesUnchanged() {
        assertEquals("Text/Chapter 1.xhtml", "Text/Chapter%201.xhtml".decodeUrl())
        assertEquals("Text/%ZZ.xhtml", "Text/%ZZ.xhtml".decodeUrl())
    }

    @Test
    fun decodeUrlDecodesUtf8PercentSequences() {
        assertEquals("Text/第一章.xhtml", "Text/%E7%AC%AC%E4%B8%80%E7%AB%A0.xhtml".decodeUrl())
    }

    @Test
    fun decodeUrlTreatsPlusAsSpace() {
        assertEquals("Text/Chapter 1.xhtml", "Text/Chapter+1.xhtml".decodeUrl())
    }
}

package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class TxtRenameSaveTransitionTest {
    @Test
    fun renamedFileBecomesTheOnlyActiveSaveTarget() {
        val result = resolveTxtRenameSaveTransition(
            renamed = "content://new.txt",
            targetBaseName = "new"
        )

        assertEquals("content://new.txt", result.getOrThrow().activeSource)
        assertEquals("new", result.getOrThrow().baseName)
    }

    @Test
    fun repeatedRenamesAlwaysKeepOnlyTheLatestSaveTarget() {
        var activeSource = "content://first.txt"
        val saveTargets = mutableListOf(activeSource)
        val firstRename = resolveTxtRenameSaveTransition(
            renamed = "content://second.txt",
            targetBaseName = "second"
        ).getOrThrow()
        activeSource = firstRename.activeSource
        saveTargets += activeSource
        val secondRename = resolveTxtRenameSaveTransition(
            renamed = "content://third.txt",
            targetBaseName = "third"
        ).getOrThrow()
        activeSource = secondRename.activeSource
        saveTargets += activeSource

        assertEquals(
            listOf("content://first.txt", "content://second.txt", "content://third.txt"),
            saveTargets
        )
        assertEquals("third", secondRename.baseName)
    }
}

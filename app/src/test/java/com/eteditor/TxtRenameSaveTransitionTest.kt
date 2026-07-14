package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtRenameSaveTransitionTest {
    @Test
    fun renamedFileBecomesTheOnlyActiveSaveTarget() {
        val result = resolveTxtRenameSaveTransition(
            renamed = "content://new.txt",
            targetBaseName = "new",
            canContinueSaving = { it == "content://new.txt" }
        )

        assertEquals("content://new.txt", result.getOrThrow().activeSource)
        assertEquals("new", result.getOrThrow().baseName)
    }

    @Test
    fun inaccessibleRenamedFileDoesNotFallBackToDeletedOriginal() {
        val result = resolveTxtRenameSaveTransition(
            renamed = "content://new.txt",
            targetBaseName = "new",
            canContinueSaving = { false }
        )

        assertTrue(result.isFailure)
    }
}

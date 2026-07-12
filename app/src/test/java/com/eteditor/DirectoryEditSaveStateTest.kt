package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryEditSaveStateTest {
    @Test
    fun saveStartedClearsPreviousFailureAndMarksRequestInFlight() {
        val state = directoryEditSaveStarted()

        assertTrue(state.inFlight)
        assertFalse(state.showProgress)
        assertEquals("", state.message)
    }

    @Test
    fun saveProgressAppearsWithoutChangingFailureMessage() {
        val state = directoryEditSaveProgress()

        assertTrue(state.inFlight)
        assertTrue(state.showProgress)
        assertEquals("", state.message)
    }

    @Test
    fun saveFailureRestoresEditingAndKeepsReason() {
        val state = directoryEditSaveFailed("文件名已存在")

        assertFalse(state.inFlight)
        assertFalse(state.showProgress)
        assertEquals("文件名已存在", state.message)
    }
}

package com.eteditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPlanApplyResultTest {
    @Test
    fun zeroChangesRemainSuccessfulWhenExecutionCompleted() {
        assertTrue(ToolPlanApplyResult.completed(changed = 0).successful)
    }

    @Test
    fun rejectedExecutionIsNotSuccessful() {
        assertFalse(ToolPlanApplyResult.failed().successful)
    }
}

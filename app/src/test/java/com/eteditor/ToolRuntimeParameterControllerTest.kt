package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolRuntimeParameterControllerTest {
    @Test
    fun resetTextReplaceModeSelectionsRemovesOnlyTransientTextReplaceKeys() {
        val overrides = mutableMapOf(
            TEXT_REPLACE_PARAM_TARGET to TEXT_REPLACE_TARGET_VISIBLE,
            TEXT_REPLACE_PARAM_FIND to "needle",
            TEXT_REPLACE_PARAM_REPLACE to "replacement",
            TEXT_REPLACE_PARAM_FIND_REGEX to BOOL_TRUE,
            TEXT_REPLACE_PARAM_BATCH_SOURCE to TEXT_REPLACE_BATCH_FILE,
            TEXT_REPLACE_PARAM_SCOPE to TOOL_SCOPE_CURRENT,
            TEXT_REPLACE_PARAM_SELECTED_HTML to "1,2",
            TEXT_REPLACE_PARAM_BATCH_TEXT to "foo=>bar",
            TEXT_REPLACE_PARAM_BATCH_FILE to "rules.replacement",
            TEXT_REPLACE_PARAM_PREVIEW to BOOL_FALSE,
            "keep" to "value"
        )

        resetTextReplaceModeSelections(overrides)

        assertEquals(mapOf("keep" to "value"), overrides)
    }
}

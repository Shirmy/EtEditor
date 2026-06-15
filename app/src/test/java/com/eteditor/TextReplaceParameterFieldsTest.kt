package com.eteditor

import org.junit.Assert.assertEquals
import org.junit.Test

class TextReplaceParameterFieldsTest {
    @Test
    fun textReplaceModeForUiMapsSavedModesToDisplayedMode() {
        assertEquals(
            TEXT_REPLACE_MODE_REPLACEMENT,
            textReplaceModeForUi(TEXT_REPLACE_MODE_BATCH, TEXT_REPLACE_BATCH_FILE)
        )
        assertEquals(
            TEXT_REPLACE_MODE_BATCH,
            textReplaceModeForUi(TEXT_REPLACE_MODE_BATCH, "")
        )
        assertEquals(
            TEXT_REPLACE_MODE_REPLACEMENT,
            textReplaceModeForUi(TEXT_REPLACE_MODE_REPLACEMENT, "")
        )
        assertEquals(
            TEXT_REPLACE_MODE_SINGLE,
            textReplaceModeForUi("unknown", TEXT_REPLACE_BATCH_FILE)
        )
    }

    @Test
    fun cleanTxtTextReplaceScopeValueKeepsOnlyTxtPresetScopes() {
        assertEquals(TOOL_SCOPE_ALL, cleanTxtTextReplaceScopeValue(""))
        assertEquals(TOOL_SCOPE_ALL, cleanTxtTextReplaceScopeValue("unknown"))
        assertEquals(TOOL_SCOPE_ALL, cleanTxtTextReplaceScopeValue(TEXT_REPLACE_SCOPE_INTRO))
        assertEquals(TOOL_SCOPE_CURRENT, cleanTxtTextReplaceScopeValue(TOOL_SCOPE_CURRENT))
        assertEquals(TOOL_SCOPE_ALL, cleanTxtTextReplaceScopeValue(TOOL_SCOPE_ALL))
    }
}

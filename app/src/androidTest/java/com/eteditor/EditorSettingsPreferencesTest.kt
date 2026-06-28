@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.eteditor

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorSettingsPreferencesTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val prefsName = "editor_settings_preferences_test"

    @Before
    fun clearBefore() {
        clearPrefs()
    }

    @After
    fun clearAfter() {
        clearPrefs()
    }

    @Test
    fun savedSettingsAreLoadedByNewPreferencesInstance() {
        val writer = EditorSettingsPreferences(context, prefsName)
        writer.saveLeftRailExpanded(true)
        writer.saveEpubDoubleTapEdit(false)
        writer.saveEpubRightPanelMode("log")
        writer.saveTxtLeftPanelMode("tools")
        writer.saveTxtRightPanelMode("search")
        writer.saveTxtDoubleTapEdit(false)
        writer.saveTxtDoubleTapTitleEdit(false)
        writer.saveTxtChapterRules("chapter-rules")
        writer.saveTxtPurifyRules("purify-rules")
        writer.saveTxtBookTitleRules("book-title-rules")
        writer.saveTxtChapterHintSettings(
            wordCountHintsEnabled = false,
            shortHintEnabled = false,
            longHintEnabled = true,
            shortThreshold = 321,
            longThreshold = 654,
            hintMode = TXT_CHAPTER_HINT_MODE_MANUAL
        )
        writer.saveTxtAutoNumberOnSave(false)

        val reader = EditorSettingsPreferences(context, prefsName)
        val state = reader.loadForTest()

        assertTrue(state.leftRailExpanded)
        assertFalse(state.epubDoubleTapEdit)
        assertEquals("log", state.epubRightPanelMode)
        assertEquals("tools", state.txtLeftPanelMode)
        assertEquals("search", state.txtRightPanelMode)
        assertFalse(state.txtDoubleTapEdit)
        assertFalse(state.txtDoubleTapTitleEdit)
        assertEquals("chapter-rules", state.txtChapterRulesText)
        assertEquals("purify-rules", state.txtPurifyRulesText)
        assertEquals("book-title-rules", state.txtBookTitleRulesText)
        assertEquals(321, state.txtShortChapterThreshold)
        assertEquals(654, state.txtLongChapterThreshold)
        assertFalse(state.txtShortChapterHintEnabled)
        assertTrue(state.txtLongChapterHintEnabled)
        assertEquals(TXT_CHAPTER_HINT_MODE_MANUAL, state.txtChapterHintMode)
        assertFalse(state.txtAutoNumberOnSave)
    }

    @Test
    fun loadFallsBackForInvalidPersistedPanelModes() {
        val writer = EditorSettingsPreferences(context, prefsName)
        writer.saveEpubRightPanelMode("invalid")
        writer.saveTxtLeftPanelMode("invalid")
        writer.saveTxtRightPanelMode("automation")

        val state = EditorSettingsPreferences(context, prefsName).loadForTest()

        assertEquals("preview", state.epubRightPanelMode)
        assertEquals("catalog", state.txtLeftPanelMode)
        assertEquals("preview", state.txtRightPanelMode)
    }

    @Test
    fun applyTxtConfigRulesOnlyOverwritesProvidedValues() {
        val preferences = EditorSettingsPreferences(context, prefsName)
        preferences.saveTxtBookTitleRules("book-before")
        preferences.saveTxtChapterRules("chapter-before")
        preferences.saveTxtPurifyRules("purify-before")

        preferences.applyTxtConfigRules(
            bookTitleRules = null,
            chapterRules = "chapter-after",
            purifyRules = null
        )

        val state = EditorSettingsPreferences(context, prefsName).loadForTest()

        assertEquals("book-before", state.txtBookTitleRulesText)
        assertEquals("chapter-after", state.txtChapterRulesText)
        assertEquals("purify-before", state.txtPurifyRulesText)
    }

    private fun EditorSettingsPreferences.loadForTest(): EditorSettingsPreferenceState {
        return load(
            defaultTxtChapterRules = "default-chapter-rules",
            defaultEpubLeftPanelMode = "catalog",
            defaultEpubRightPanelMode = "preview",
            defaultTxtLeftPanelMode = "catalog",
            defaultTxtRightPanelMode = "preview",
            leftPanelModes = setOf("catalog", "tools"),
            rightPanelModes = setOf("preview", "log", "search"),
            txtRightPanelDisallowedMode = "automation"
        )
    }

    private fun clearPrefs() {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}

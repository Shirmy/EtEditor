package com.eteditor

import android.content.Context
import android.content.SharedPreferences

internal data class EditorSettingsPreferenceState(
    val leftRailExpanded: Boolean,
    val hideDirectoryFileNameByDefault: Boolean,
    val epubHideSection0001FromNcx: Boolean,
    val epubLongPressSplitChapter: Boolean,
    val epubDoubleTapEdit: Boolean,
    val epubLeftPanelMode: String,
    val epubRightPanelMode: String,
    val txtLeftPanelMode: String,
    val txtRightPanelMode: String,
    val txtChapterRulesText: String,
    val txtPurifyRulesText: String,
    val txtBookTitleRulesText: String,
    val txtShortChapterThreshold: Int,
    val txtLongChapterThreshold: Int,
    val txtShortChapterHintEnabled: Boolean,
    val txtLongChapterHintEnabled: Boolean,
    val txtChapterHintMode: String,
    val txtAutoNumberOnSave: Boolean,
    val txtChapterNumberStartAtOneOnSave: Boolean,
    val txtDoubleTapEdit: Boolean,
    val txtDoubleTapTitleEdit: Boolean,
    val txtSupplementLongPressMode: Boolean
)

internal class EditorSettingsPreferences(
    context: Context,
    prefsName: String
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun load(
        defaultTxtChapterRules: String,
        defaultEpubLeftPanelMode: String,
        defaultEpubRightPanelMode: String,
        defaultTxtLeftPanelMode: String,
        defaultTxtRightPanelMode: String,
        leftPanelModes: Set<String>,
        rightPanelModes: Set<String>,
        txtRightPanelDisallowedMode: String
    ): EditorSettingsPreferenceState {
        return EditorSettingsPreferenceState(
            leftRailExpanded = prefs.getBoolean(KEY_LEFT_RAIL_EXPANDED, false),
            hideDirectoryFileNameByDefault = true,
            epubHideSection0001FromNcx = true,
            epubLongPressSplitChapter = false,
            epubDoubleTapEdit = prefs.getBoolean(KEY_EPUB_DOUBLE_TAP_EDIT, true),
            epubLeftPanelMode = prefs.getString(KEY_EPUB_LEFT_PANEL, defaultEpubLeftPanelMode) ?: defaultEpubLeftPanelMode,
            epubRightPanelMode = prefs.getString(KEY_EPUB_RIGHT_PANEL, defaultEpubRightPanelMode)
                ?.takeIf { it in rightPanelModes }
                ?: defaultEpubRightPanelMode,
            txtLeftPanelMode = prefs.getString(KEY_TXT_LEFT_PANEL, defaultTxtLeftPanelMode)
                ?.takeIf { it in leftPanelModes }
                ?: defaultTxtLeftPanelMode,
            txtRightPanelMode = prefs.getString(KEY_TXT_RIGHT_PANEL, defaultTxtRightPanelMode)
                ?.takeIf { it != txtRightPanelDisallowedMode }
                ?: defaultTxtRightPanelMode,
            txtChapterRulesText = prefs.getString(KEY_TXT_CHAPTER_RULES, defaultTxtChapterRules) ?: defaultTxtChapterRules,
            txtPurifyRulesText = prefs.getString(KEY_TXT_PURIFY_RULES, "") ?: "",
            txtBookTitleRulesText = prefs.getString(KEY_TXT_BOOK_TITLE_RULES, "") ?: "",
            txtShortChapterThreshold = prefs.getInt(KEY_TXT_SHORT_CHAPTER_THRESHOLD, 1000),
            txtLongChapterThreshold = prefs.getInt(KEY_TXT_LONG_CHAPTER_THRESHOLD, 10000),
            txtShortChapterHintEnabled = prefs.getBoolean(KEY_TXT_SHORT_CHAPTER_HINT_ENABLED, true),
            txtLongChapterHintEnabled = prefs.getBoolean(KEY_TXT_LONG_CHAPTER_HINT_ENABLED, true),
            txtChapterHintMode = prefs.getString(KEY_TXT_CHAPTER_HINT_MODE, TXT_CHAPTER_HINT_MODE_AUTO)
                ?.takeIf { it in TXT_CHAPTER_HINT_MODES }
                ?: TXT_CHAPTER_HINT_MODE_AUTO,
            txtAutoNumberOnSave = prefs.getBoolean(KEY_TXT_AUTO_NUMBER_ON_SAVE, true),
            txtChapterNumberStartAtOneOnSave = prefs.getBoolean(KEY_TXT_CHAPTER_NUMBER_START_AT_ONE_ON_SAVE, true),
            txtDoubleTapEdit = prefs.getBoolean(KEY_TXT_DOUBLE_TAP_EDIT, true),
            txtDoubleTapTitleEdit = prefs.getBoolean(KEY_TXT_DOUBLE_TAP_TITLE_EDIT, true),
            txtSupplementLongPressMode = false
        )
    }

    // 常规设置改完即同步落盘（commit），避免进程被系统杀掉时异步写（apply）丢失尾部改动
    fun saveLeftRailExpanded(expanded: Boolean) {
        prefs.edit().putBoolean(KEY_LEFT_RAIL_EXPANDED, expanded).commit()
    }

    fun saveEpubDoubleTapEdit(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EPUB_DOUBLE_TAP_EDIT, enabled).commit()
    }

    fun saveEpubLeftPanelMode(mode: String) {
        prefs.edit().putString(KEY_EPUB_LEFT_PANEL, mode).commit()
    }

    fun saveEpubRightPanelMode(mode: String) {
        prefs.edit().putString(KEY_EPUB_RIGHT_PANEL, mode).commit()
    }

    fun saveTxtLeftPanelMode(mode: String) {
        prefs.edit().putString(KEY_TXT_LEFT_PANEL, mode).commit()
    }

    fun saveTxtRightPanelMode(mode: String) {
        prefs.edit().putString(KEY_TXT_RIGHT_PANEL, mode).commit()
    }

    fun saveTxtDoubleTapEdit(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TXT_DOUBLE_TAP_EDIT, enabled).commit()
    }

    fun saveTxtDoubleTapTitleEdit(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TXT_DOUBLE_TAP_TITLE_EDIT, enabled).commit()
    }

    fun saveTxtChapterRules(rulesText: String) {
        prefs.edit().putString(KEY_TXT_CHAPTER_RULES, rulesText).commit()
    }

    fun saveTxtPurifyRules(rulesText: String) {
        prefs.edit().putString(KEY_TXT_PURIFY_RULES, rulesText).commit()
    }

    fun saveTxtBookTitleRules(rulesText: String) {
        prefs.edit().putString(KEY_TXT_BOOK_TITLE_RULES, rulesText).commit()
    }

    fun saveTxtChapterThresholds(shortThreshold: Int, longThreshold: Int) {
        prefs.edit()
            .putInt(KEY_TXT_SHORT_CHAPTER_THRESHOLD, shortThreshold)
            .putInt(KEY_TXT_LONG_CHAPTER_THRESHOLD, longThreshold)
            .commit()
    }

    fun saveTxtChapterHintSettings(
        shortHintEnabled: Boolean,
        longHintEnabled: Boolean,
        shortThreshold: Int,
        longThreshold: Int,
        hintMode: String
    ) {
        prefs.edit()
            .putBoolean(KEY_TXT_SHORT_CHAPTER_HINT_ENABLED, shortHintEnabled)
            .putBoolean(KEY_TXT_LONG_CHAPTER_HINT_ENABLED, longHintEnabled)
            .putInt(KEY_TXT_SHORT_CHAPTER_THRESHOLD, shortThreshold)
            .putInt(KEY_TXT_LONG_CHAPTER_THRESHOLD, longThreshold)
            .putString(KEY_TXT_CHAPTER_HINT_MODE, hintMode.takeIf { it in TXT_CHAPTER_HINT_MODES } ?: TXT_CHAPTER_HINT_MODE_AUTO)
            .commit()
    }

    fun saveTxtShortChapterHintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TXT_SHORT_CHAPTER_HINT_ENABLED, enabled).commit()
    }

    fun saveTxtLongChapterHintEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TXT_LONG_CHAPTER_HINT_ENABLED, enabled).commit()
    }

    fun saveTxtAutoNumberOnSave(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TXT_AUTO_NUMBER_ON_SAVE, enabled).commit()
    }

    fun saveTxtChapterNumberStartAtOneOnSave(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TXT_CHAPTER_NUMBER_START_AT_ONE_ON_SAVE, enabled).commit()
    }

    fun applySettingsPreferenceValues(preferences: List<SettingsPreferenceValue>) {
        val editor = prefs.edit()
        preferences.forEach { preference ->
            when (preference) {
                is SettingsPreferenceValue.BooleanValue -> editor.putBoolean(preference.key, preference.value)
                is SettingsPreferenceValue.IntValue -> editor.putInt(preference.key, preference.value)
                is SettingsPreferenceValue.StringValue -> editor.putString(preference.key, preference.value)
            }
        }
        editor.commit()
    }

    fun applyTxtConfigRules(bookTitleRules: String?, chapterRules: String?, purifyRules: String?) {
        prefs.edit()
            .putStringIfNotNull(KEY_TXT_BOOK_TITLE_RULES, bookTitleRules)
            .putStringIfNotNull(KEY_TXT_CHAPTER_RULES, chapterRules)
            .putStringIfNotNull(KEY_TXT_PURIFY_RULES, purifyRules)
            .commit()
    }
}

internal fun editorSettingsConfigPreferenceKeys(): SettingsConfigPreferenceKeys {
    return SettingsConfigPreferenceKeys(
        leftRailExpanded = KEY_LEFT_RAIL_EXPANDED,
        epubDoubleTapEdit = KEY_EPUB_DOUBLE_TAP_EDIT,
        epubLeftPanelMode = KEY_EPUB_LEFT_PANEL,
        epubRightPanelMode = KEY_EPUB_RIGHT_PANEL,
        txtLeftPanelMode = KEY_TXT_LEFT_PANEL,
        txtRightPanelMode = KEY_TXT_RIGHT_PANEL,
        txtDoubleTapEdit = KEY_TXT_DOUBLE_TAP_EDIT,
        txtDoubleTapTitleEdit = KEY_TXT_DOUBLE_TAP_TITLE_EDIT,
        txtShortChapterThreshold = KEY_TXT_SHORT_CHAPTER_THRESHOLD,
        txtLongChapterThreshold = KEY_TXT_LONG_CHAPTER_THRESHOLD,
        txtShortChapterHintEnabled = KEY_TXT_SHORT_CHAPTER_HINT_ENABLED,
        txtLongChapterHintEnabled = KEY_TXT_LONG_CHAPTER_HINT_ENABLED,
        txtChapterHintMode = KEY_TXT_CHAPTER_HINT_MODE
    )
}

private fun SharedPreferences.Editor.putStringIfNotNull(
    key: String,
    value: String?
): SharedPreferences.Editor {
    return if (value == null) this else putString(key, value)
}

private const val KEY_LEFT_RAIL_EXPANDED = "left_rail_expanded"
private const val KEY_EPUB_DOUBLE_TAP_EDIT = "epub_double_tap_edit"
private const val KEY_EPUB_LEFT_PANEL = "epub_left_panel"
private const val KEY_EPUB_RIGHT_PANEL = "epub_right_panel"
private const val KEY_TXT_LEFT_PANEL = "txt_left_panel"
private const val KEY_TXT_RIGHT_PANEL = "txt_right_panel"
private const val KEY_TXT_CHAPTER_RULES = "txt_chapter_rules"
private const val KEY_TXT_PURIFY_RULES = "txt_purify_rules"
private const val KEY_TXT_BOOK_TITLE_RULES = "txt_book_title_rules"
private const val KEY_TXT_SHORT_CHAPTER_THRESHOLD = "txt_short_chapter_threshold"
private const val KEY_TXT_LONG_CHAPTER_THRESHOLD = "txt_long_chapter_threshold"
private const val KEY_TXT_SHORT_CHAPTER_HINT_ENABLED = "txt_short_chapter_hint_enabled"
private const val KEY_TXT_LONG_CHAPTER_HINT_ENABLED = "txt_long_chapter_hint_enabled"
private const val KEY_TXT_CHAPTER_HINT_MODE = "txt_chapter_hint_mode"
private const val KEY_TXT_AUTO_NUMBER_ON_SAVE = "txt_auto_number_on_save"
private const val KEY_TXT_CHAPTER_NUMBER_START_AT_ONE_ON_SAVE = "txt_chapter_number_start_at_one_on_save"
private const val KEY_TXT_DOUBLE_TAP_EDIT = "txt_double_tap_edit"
private const val KEY_TXT_DOUBLE_TAP_TITLE_EDIT = "txt_double_tap_title_edit"

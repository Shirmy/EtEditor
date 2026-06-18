package com.eteditor

import android.content.Context

internal data class PersistedEditorToolRawStores(
    val tools: String,
    val txtTextReplacePresets: String,
    val epubTextReplacePresets: String
)

internal class EditorJsonPreferences(
    context: Context,
    prefsName: String
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun readAutomationChains(): String {
        return prefs.getString(KEY_AUTOMATION_CHAINS, null).orEmpty()
    }

    fun writeAutomationChains(json: String) {
        prefs.edit()
            .putString(KEY_AUTOMATION_CHAINS, json)
            .apply()
    }

    fun readEditorToolStores(): PersistedEditorToolRawStores {
        return PersistedEditorToolRawStores(
            tools = prefs.getString(KEY_EDITOR_TOOLS, null).orEmpty(),
            txtTextReplacePresets = prefs.getString(KEY_TXT_TEXT_REPLACE_PRESETS, null).orEmpty(),
            epubTextReplacePresets = prefs.getString(KEY_EPUB_TEXT_REPLACE_PRESETS, null).orEmpty()
        )
    }

    fun writeEditorTools(json: String): Boolean {
        return writeStringCommit(KEY_EDITOR_TOOLS, json)
    }

    fun writeTxtTextReplacePresets(json: String): Boolean {
        return writeStringCommit(KEY_TXT_TEXT_REPLACE_PRESETS, json)
    }

    fun writeEpubTextReplacePresets(json: String): Boolean {
        return writeStringCommit(KEY_EPUB_TEXT_REPLACE_PRESETS, json)
    }

    fun readBuiltInToolDefaults(): String {
        return prefs.getString(KEY_BUILT_IN_TOOL_DEFAULTS, null).orEmpty()
    }

    fun writeBuiltInToolDefaults(json: String) {
        // 同步写盘：内置默认值（含目录规则）改完即落盘，避免进程被杀时异步写丢失。
        prefs.edit()
            .putString(KEY_BUILT_IN_TOOL_DEFAULTS, json)
            .commit()
    }

    private fun writeStringCommit(key: String, value: String): Boolean {
        return prefs.edit()
            .putString(key, value)
            .commit()
    }
}

private const val KEY_EDITOR_TOOLS = "editor_tools_v1_json"
private const val KEY_TXT_TEXT_REPLACE_PRESETS = "txt_text_replace_presets_v1_json"
private const val KEY_EPUB_TEXT_REPLACE_PRESETS = "epub_text_replace_presets_v1_json"
private const val KEY_BUILT_IN_TOOL_DEFAULTS = "built_in_tool_defaults_v1_json"
private const val KEY_AUTOMATION_CHAINS = "automation_chains_v1_json"

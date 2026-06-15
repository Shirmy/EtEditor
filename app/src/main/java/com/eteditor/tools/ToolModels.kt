package com.eteditor

data class ToolDefinition(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val implemented: Boolean,
    val parameters: List<ToolParameterDefinition> = emptyList()
)

data class ToolParameterDefinition(
    val key: String,
    val label: String,
    val defaultValue: String,
    val options: List<Pair<String, String>> = emptyList()
)

data class EditorTool(
    val id: String,
    val name: String,
    val group: String = "",
    val toolId: String,
    val parameterOverrides: Map<String, String> = emptyMap()
)

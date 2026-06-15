package com.eteditor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun InsertChapterParameterFields(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit,
    sourceFileContent: (@Composable (Modifier) -> Unit)? = null,
    useBottomActionForUpload: Boolean = false
) {
    val parameters = controller.toolParameterDefinitions(toolId)
    val parameterByKey = parameters.associateBy { it.key }

    fun effective(parameter: ToolParameterDefinition): String {
        return valueFor(parameter).ifBlank { parameter.defaultValue }
    }

    @Composable
    fun field(key: String) {
        parameterByKey[key]?.let { parameter ->
            ToolParameterField(
                parameter = parameter,
                value = effective(parameter),
                onValueChange = { value -> onValueChange(parameter, value) }
            )
        }
    }

    val sourceParameter = parameterByKey[INSERT_CHAPTER_PARAM_SOURCE_TYPE]
    val sourceType = sourceParameter
        ?.let { parameter ->
            effective(parameter).takeIf { value -> parameter.options.any { it.first == value } }
        }
        ?: INSERT_CHAPTER_SOURCE_UPLOAD
    val sosadQueryParameter = parameterByKey[INSERT_CHAPTER_PARAM_SOSAD_QUERY]
    val sosadAuthCookieParameter = parameterByKey[INSERT_CHAPTER_PARAM_SOSAD_AUTH_COOKIE]
    var showSosadLoginDialog by remember(toolId, sourceType) { mutableStateOf(false) }
    var sosadQueryMode by remember(toolId, sourceType) {
        mutableStateOf(
            if (sosadQueryParameter?.let(valueFor).orEmpty().isBlank()) "title" else "custom"
        )
    }

    @Composable
    fun sourceTypeField(modifier: Modifier = Modifier.fillMaxWidth()) {
        sourceParameter?.let { parameter ->
            ToolSegmentedChoiceField(
                label = parameter.label,
                value = sourceType,
                options = parameter.options,
                onSelect = { value -> onValueChange(parameter, value) },
                modifier = modifier
            )
        }
    }

    if (sourceType == INSERT_CHAPTER_SOURCE_SOSAD) {
        val queryModeOptions = listOf("title" to "按书名", "custom" to "自定义")
        sourceTypeField()
        sosadQueryParameter?.let { parameter ->
            ToolSegmentedChoiceField(
                label = "废文书名/网址",
                value = sosadQueryMode,
                options = queryModeOptions,
                onSelect = { value ->
                    sosadQueryMode = value
                    if (value == "title") {
                        onValueChange(parameter, "")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        sosadQueryParameter?.let { parameter ->
            if (sosadQueryMode == "custom") {
                ToolTextInputField(
                    label = "请输入书名/网站",
                    value = valueFor(parameter),
                    onValueChange = { value -> onValueChange(parameter, value) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (sosadAuthCookieParameter != null) {
            val sosadCookie = valueFor(sosadAuthCookieParameter).ifBlank { controller.sosadLoginCookie() }
            val hasSosadCookie = sosadCookie.isNotBlank()
            val showSosadLogin = controller.shouldShowSosadLogin(sosadCookie)
            if (showSosadLogin) {
                SosadLoginField(
                    hasCookie = hasSosadCookie,
                    loginInvalid = hasSosadCookie && controller.sosadLoginInvalid,
                    onLogin = { showSosadLoginDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showSosadLoginDialog) {
                SosadLoginDialog(
                    onDismiss = { showSosadLoginDialog = false },
                    onCookie = { cookie ->
                        onValueChange(sosadAuthCookieParameter, cookie)
                        showSosadLoginDialog = false
                    }
                )
            }
        }
    } else {
        sourceTypeField()
        if (!useBottomActionForUpload) {
            sourceFileContent?.invoke(Modifier.fillMaxWidth())
            field(INSERT_CHAPTER_PARAM_PREVIEW)
        }
    }
}

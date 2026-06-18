package com.eteditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FetchInfoParameterFields(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit
) {
    val parameters = controller.toolParameterDefinitions(toolId)
    val parameterByKey = parameters.associateBy { it.key }

    fun effective(parameter: ToolParameterDefinition): String {
        val value = valueFor(parameter)
        return value.ifBlank { parameter.defaultValue }
    }

    val contentParameter = parameterByKey[FETCH_INFO_PARAM_CONTENT]
    val authCookieParameter = parameterByKey[FETCH_INFO_PARAM_AUTH_COOKIE]
    val contentOptions = controller.fetchInfoContentOptions(FETCH_INFO_SOURCE_JJWXC)
    val selectedContent = contentParameter
        ?.let(::effective)
        ?.takeIf { value -> contentOptions.any { it.first == value } }
        ?: contentOptions.firstOrNull()?.first
        ?: FETCH_INFO_CONTENT_CATALOG

    var showSosadLoginDialog by remember(toolId, selectedContent) { mutableStateOf(false) }

    if (contentParameter != null) {
        FetchInfoContentSelector(
            options = contentOptions,
            selected = selectedContent,
            onSelect = { value -> onValueChange(contentParameter, value) },
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (authCookieParameter != null) {
        val sosadCookie = valueFor(authCookieParameter)
            .ifBlank { controller.sosadLoginCookie() }
        val hasSosadCookie = sosadCookie.isNotBlank()
        if (controller.shouldShowSosadLogin(sosadCookie)) {
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
                    onValueChange(authCookieParameter, cookie)
                    showSosadLoginDialog = false
                }
            )
        }
    }

    if (selectedContent == FETCH_INFO_CONTENT_CATALOG) {
        parameterByKey[FETCH_INFO_PARAM_AUTO_TITLE_FORMAT]?.let { parameter ->
            val effectiveValue = valueFor(parameter).ifBlank { parameter.defaultValue }
            ToolSwitchField(
                label = parameter.label,
                checked = effectiveValue == "true",
                onCheckedChange = { checked -> onValueChange(parameter, if (checked) "true" else "false") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    // 简介过滤已迁移到抓取预览界面右上角的「简介规则」面板（持久化保存），此处不再提供旧的过滤文本框。
}

@Composable
private fun FetchInfoContentSelector(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            if (active) {
                Button(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                OutlinedButton(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    shape = ControlShape,
                    contentPadding = CompactButtonPadding
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

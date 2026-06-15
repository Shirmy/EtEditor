package com.eteditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun CoverParameterFields(
    controller: EditorController,
    toolId: String,
    valueFor: (ToolParameterDefinition) -> String,
    onValueChange: (ToolParameterDefinition, String) -> Unit,
    imageFileContent: (@Composable () -> Unit)? = null
) {
    val parameters = controller.toolParameterDefinitions(toolId)
    val parameterByKey = parameters.associateBy { it.key }
    val modeParameter = parameterByKey[COVER_PARAM_MODE] ?: return
    val compressParameter = parameterByKey[COVER_PARAM_COMPRESS]
    val previewParameter = parameterByKey[COVER_PARAM_PREVIEW]
    val mode = valueFor(modeParameter)
        .takeIf { value -> modeParameter.options.any { it.first == value } }
        ?: modeParameter.defaultValue
    val titleParameter = parameterByKey[COVER_PARAM_TITLE]
    val titleValue = titleParameter?.let(valueFor).orEmpty()
    var coverTitleMode by remember(toolId, mode, titleValue) {
        mutableStateOf(if (titleValue.isBlank()) "default" else "custom")
    }
    val imageInsertTypeParameter = parameterByKey[COVER_PARAM_IMAGE_INSERT_TYPE]
    val imageInsertType = imageInsertTypeParameter
        ?.let(valueFor)
        ?.takeIf { value -> controller.coverImageInsertOptions().any { it.first == value } }
        ?: COVER_IMAGE_INSERT_NOTE
    val imageUri = parameterByKey[COVER_PARAM_IMAGE_URI]?.let(valueFor).orEmpty()
    val imageInfoLabel = remember(imageUri) {
        controller.coverImageInfoLabel(imageUri)
    }
    val imagePicker = rememberLauncherForActivityResult(OpenImageDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        parameterByKey[COVER_PARAM_IMAGE_URI]?.let { imageParameter ->
            onValueChange(imageParameter, uri.toString())
        }
    }

    @Composable
    fun modeField(modifier: Modifier = Modifier.fillMaxWidth()) {
        ToolSegmentedChoiceField(
            label = modeParameter.label,
            value = mode,
            options = modeParameter.options,
            onSelect = { value ->
                onValueChange(modeParameter, value)
                if (value != COVER_MODE_GENERATE) {
                    previewParameter?.let { parameter -> onValueChange(parameter, BOOL_FALSE) }
                }
            },
            modifier = modifier
        )
    }

    if (mode == COVER_MODE_INSERT &&
        parameterByKey[COVER_PARAM_IMAGE_URI] != null &&
        imageFileContent == null
    ) {
        parameterByKey[COVER_PARAM_IMAGE_URI]?.let { imageParameter ->
            modeField()
            ToolFileButtonField(
                label = null,
                value = imageUri,
                onPick = { imagePicker.launch(Unit) },
                onClear = { onValueChange(imageParameter, "") },
                modifier = Modifier.fillMaxWidth()
            )
            if (imageInfoLabel.isNotBlank()) {
                Text(
                    text = imageInfoLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else if (mode == COVER_MODE_GENERATE) {
        val titleModeOptions = listOf("default" to "默认", "custom" to "自定义")
        modeField()
        ToolSegmentedChoiceField(
            label = "封面标题",
            value = coverTitleMode,
            options = titleModeOptions,
            onSelect = { value ->
                coverTitleMode = value
                if (value == "default") {
                    titleParameter?.let { onValueChange(it, "") }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (coverTitleMode == "custom") {
            titleParameter?.let { titleParameter ->
                ToolTextInputField(
                    label = titleParameter.label,
                    value = titleValue,
                    onValueChange = { value -> onValueChange(titleParameter, value) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else if (mode == COVER_MODE_IMAGE_INSERT) {
        val imageInsertTypeOptions = controller.coverImageInsertOptions()
        modeField()
        imageInsertTypeParameter?.let { parameter ->
            ToolSegmentedChoiceField(
                label = parameter.label,
                value = imageInsertType,
                options = imageInsertTypeOptions,
                onSelect = { value ->
                    onValueChange(parameter, value)
                    if (value != COVER_IMAGE_INSERT_CUSTOM) {
                        parameterByKey[COVER_PARAM_IMAGE_URI]?.let { imageParameter ->
                            onValueChange(imageParameter, "")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (imageInsertType == COVER_IMAGE_INSERT_CUSTOM) {
            parameterByKey[COVER_PARAM_IMAGE_URI]?.let { imageParameter ->
                if (imageFileContent != null) {
                    imageFileContent()
                } else {
                    ToolFileButtonField(
                        label = null,
                        value = imageUri,
                        onPick = { imagePicker.launch(Unit) },
                        onClear = { onValueChange(imageParameter, "") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (imageInfoLabel.isNotBlank()) {
                        Text(
                            text = imageInfoLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    } else {
        modeField()
        parameterByKey[COVER_PARAM_IMAGE_URI]?.let { imageParameter ->
            if (imageFileContent != null) {
                imageFileContent()
            } else {
                ToolFileButtonField(
                    label = null,
                    value = imageUri,
                    onPick = { imagePicker.launch(Unit) },
                    onClear = { onValueChange(imageParameter, "") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (imageInfoLabel.isNotBlank()) {
                    Text(
                        text = imageInfoLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (mode == COVER_MODE_INSERT) compressParameter?.let { parameter ->
        val checked = valueFor(parameter).ifBlank { parameter.defaultValue } == "true"
        ToolSwitchField(
            label = parameter.label,
            checked = checked,
            onCheckedChange = { checkedValue ->
                onValueChange(parameter, if (checkedValue) "true" else "false")
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (mode == COVER_MODE_GENERATE) previewParameter?.let { parameter ->
        val checked = valueFor(parameter).ifBlank { parameter.defaultValue } == BOOL_TRUE
        ToolSwitchField(
            label = parameter.label,
            checked = checked,
            onCheckedChange = { checkedValue ->
                onValueChange(parameter, if (checkedValue) BOOL_TRUE else BOOL_FALSE)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

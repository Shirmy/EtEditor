package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ToolParameterField(
    parameter: ToolParameterDefinition,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    showLineBreakMarks: Boolean = false
) {
    val booleanOptionKeys = parameter.options.map { it.first }.toSet()
    if (booleanOptionKeys == setOf("true", "false")) {
        val effectiveValue = value.ifBlank { parameter.defaultValue }
        ToolSwitchField(
            label = parameter.label,
            checked = effectiveValue == "true",
            onCheckedChange = { checked -> onValueChange(if (checked) "true" else "false") },
            modifier = modifier
        )
    } else if (parameter.options.isEmpty()) {
        ToolTextInputField(
            value = value,
            onValueChange = onValueChange,
            label = parameter.label,
            modifier = modifier,
            showLineBreakMarks = showLineBreakMarks
        )
    } else {
        val effectiveValue = value
            .takeIf { current -> parameter.options.any { it.first == current } }
            ?: parameter.defaultValue.takeIf { default -> parameter.options.any { it.first == default } }
            ?: parameter.options.firstOrNull()?.first
            ?: value
        ToolDropdownField(
            value = parameter.options.firstOrNull { it.first == effectiveValue }?.second ?: effectiveValue,
            options = parameter.options,
            onSelect = onValueChange,
            label = parameter.label,
            modifier = modifier
        )
    }
}

@Composable
fun ToolSwitchField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Surface(
            onClick = { onCheckedChange(!checked) },
            shape = RoundedCornerShape(999.dp),
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.size(width = 46.dp, height = 24.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (checked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = if (checked) 0.dp else 1.dp,
                    modifier = Modifier
                        .offset(x = if (checked) 23.dp else 3.dp)
                        .size(18.dp)
                ) {}
            }
        }
    }
}

@Composable
fun FileRenameMatchRuleField(
    patternLabel: String,
    patternValue: String,
    patternPlaceholder: String = "",
    onPatternChange: (String) -> Unit,
    regexLabel: String,
    regexChecked: Boolean,
    onRegexChange: (Boolean) -> Unit,
    showRegexControl: Boolean = true,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember { mutableStateOf(TextFieldValue(patternValue)) }
    LaunchedEffect(patternValue) {
        if (patternValue != fieldValue.text) {
            fieldValue = TextFieldValue(patternValue, TextRange(patternValue.length))
        }
    }
    fun updateFieldValue(nextValue: TextFieldValue) {
        fieldValue = nextValue
        onPatternChange(nextValue.text)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = patternLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Surface(
            shape = ControlShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 9.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (fieldValue.text.isBlank() && patternPlaceholder.isNotBlank()) {
                    Text(
                        text = patternPlaceholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ScrollableSingleLineBasicTextField(
                    value = fieldValue,
                    onValueChange = ::updateFieldValue,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    textFieldModifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                )
            }
        }
        if (showRegexControl) {
            ToolSwitchField(
                label = regexLabel,
                checked = regexChecked,
                onCheckedChange = onRegexChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ToolActionButtonField(
    buttonText: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = containerColor,
            contentColor = contentColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ToolDropdownField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
    showLabel: Boolean = true,
    reserveLabelSpace: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showLabel || reserveLabelSpace) {
            Text(
                text = if (showLabel) label else "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = { expanded = true },
                shape = ControlShape,
                color = if (expanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
                    else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .onSizeChanged { fieldWidthPx = it.width }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 10.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                modifier = if (fieldWidthPx > 0) {
                    Modifier
                        .width(with(density) { fieldWidthPx.toDp() })
                        .dialogBorder()
                } else {
                    Modifier.dialogBorder()
                }
            ) {
                options.forEachIndexed { index, (key, title) ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            val selected = title == value || key == value
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) {
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelect(key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

package com.eteditor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun ScrollableSingleLineBasicTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    textStyle: TextStyle,
    cursorBrush: SolidColor,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit = { innerTextField ->
        innerTextField()
    }
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    var viewportWidthPx by remember { mutableStateOf(0) }
    var contentWidthPx by remember { mutableStateOf(0) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val fieldWidthPx = (contentWidthPx + with(density) { 18.dp.roundToPx() })
        .coerceAtLeast(viewportWidthPx)
        .coerceAtLeast(1)

    LaunchedEffect(value.text, value.selection, viewportWidthPx, contentWidthPx, scrollState.maxValue) {
        val layout = textLayoutResult ?: return@LaunchedEffect
        if (viewportWidthPx <= 0 || scrollState.maxValue <= 0) return@LaunchedEffect
        val cursor = value.selection.end.coerceIn(0, value.text.length)
        val cursorRect = layout.getCursorRect(cursor)
        val paddingPx = with(density) { 10.dp.roundToPx() }
        val left = cursorRect.left.roundToInt()
        val right = cursorRect.right.roundToInt()
        val visibleStart = scrollState.value
        val visibleEnd = visibleStart + viewportWidthPx
        val target = when {
            left - paddingPx < visibleStart -> left - paddingPx
            right + paddingPx > visibleEnd -> right + paddingPx - viewportWidthPx
            else -> null
        } ?: return@LaunchedEffect
        scrollState.scrollTo(target.coerceIn(0, scrollState.maxValue))
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { viewportWidthPx = it.width }
            .horizontalScroll(scrollState),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = textStyle,
            visualTransformation = visualTransformation,
            cursorBrush = cursorBrush,
            onTextLayout = { result ->
                textLayoutResult = result
                contentWidthPx = if (result.lineCount > 0) {
                    result.getLineRight(0).roundToInt()
                } else {
                    0
                }
            },
            decorationBox = decorationBox,
            modifier = Modifier
                .width(with(density) { fieldWidthPx.toDp() })
                .then(textFieldModifier)
        )
    }
}

@Composable
fun CompactDialogTextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    fun updateFieldValue(nextValue: TextFieldValue) {
        fieldValue = nextValue
        onValueChange(nextValue.text)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(36.dp)
            )
            Surface(
                shape = ControlShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    ScrollableSingleLineBasicTextField(
                        value = fieldValue,
                        onValueChange = ::updateFieldValue,
                        keyboardOptions = keyboardOptions,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        textFieldModifier = Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged { focused = it.isFocused }
                    )
                }
            }
        }
    }
}

@Composable
fun CompactDialogSwitchField(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 36.dp)
        )
        Surface(
            onClick = { onCheckedChange(!checked) },
            shape = RoundedCornerShape(999.dp),
            color = if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (checked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier.size(width = 38.dp, height = 20.dp)
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
                        .offset(x = if (checked) 20.dp else 2.dp)
                        .size(16.dp)
                ) {}
            }
        }
    }
}

@Composable
fun ToolTextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp,
    showLineBreakMarks: Boolean = false,
    autoFocus: Boolean = false,
    labelTrailingContent: @Composable RowScope.() -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val hostView = LocalView.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }
    LaunchedEffect(autoFocus) {
        if (!autoFocus) return@LaunchedEffect
        delay(120)
        runCatching { focusRequester.requestFocus() }
        hostView.showSoftKeyboard()
    }
    fun updateFieldValue(nextValue: TextFieldValue) {
        fieldValue = nextValue
        onValueChange(nextValue.text)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (label.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                labelTrailingContent()
            }
        }
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
                ScrollableSingleLineBasicTextField(
                    value = fieldValue,
                    onValueChange = ::updateFieldValue,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    visualTransformation = if (showLineBreakMarks) {
                        LineBreakMarkerVisualTransformation
                    } else {
                        VisualTransformation.None
                    },
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    textFieldModifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                )
            }
        }
    }
}

@Composable
fun ToolReadOnlyField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    height: Dp = 42.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Surface(
            shape = ControlShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private object LineBreakMarkerVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (!text.text.any { it == '\r' || it == '\n' }) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val marked = buildString(text.text.length) {
            text.text.forEach { char ->
                append(
                    when (char) {
                        '\r' -> '↵'
                        '\n' -> '↵'
                        else -> char
                    }
                )
            }
        }
        return TransformedText(AnnotatedString(marked), OffsetMapping.Identity)
    }
}

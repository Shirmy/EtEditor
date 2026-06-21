package com.eteditor

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.BuildEditorInfoEvent
import io.github.rosemoe.sora.lang.styling.HighlightTextContainer
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import kotlin.math.roundToInt

private val PLAIN_TEXT_EDITOR_INPUT_TYPE =
    InputType.TYPE_CLASS_TEXT or
        InputType.TYPE_TEXT_FLAG_MULTI_LINE or
        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

private const val PLAIN_TEXT_PRIVATE_IME_OPTIONS =
    "com.google.android.inputmethod.latin.noMicrophoneKey;com.google.android.inputmethod.latin.noPersonalizedLearning"

internal enum class TxtFullEditWindowEdge {
    Start,
    End
}

private const val TXT_FULL_EDIT_WINDOW_EDGE_TRIGGER_PX = 1800
private data class PreviewCodeEditorTag(
    val contentKey: Any,
    val configKey: Any
)

internal data class BodyReadOnlySelectionAction(
    val title: String,
    val onClick: (Int, Int) -> Unit
)

internal fun View.showSoftKeyboard() {
    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    inputMethodManager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

internal fun View.requestFocusAndShowKeyboard() {
    post {
        requestFocus()
        showSoftKeyboard()
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.previewContentKey(): Any? {
    return (tag as? PreviewCodeEditorTag)?.contentKey ?: tag
}

private fun io.github.rosemoe.sora.widget.CodeEditor.previewConfigKey(): Any? {
    return (tag as? PreviewCodeEditorTag)?.configKey
}

@Composable
internal fun LargeBodyCodeEditor(
    text: String,
    contentKey: Any,
    scrollTargetLineIndex: Int? = null,
    selectionTargetIndex: Int? = null,
    windowSourceStart: Int? = null,
    windowSourceEnd: Int? = null,
    windowSourceLength: Int? = null,
    onWindowEdgeReached: ((TxtFullEditWindowEdge, String, Int, Int) -> Unit)? = null,
    onEditorReady: (io.github.rosemoe.sora.widget.CodeEditor) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val textSizeSp = MaterialTheme.typography.bodyMedium.fontSize.value.takeIf { it > 0f } ?: 14f
    val padding = with(LocalDensity.current) { 8.dp.roundToPx() }
    val latestOnWindowEdgeReached by rememberUpdatedState(onWindowEdgeReached)
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .graphicsLayer { clip = true }
    ) {
        val expectedLayoutSizeKey = constraints.maxWidth to constraints.maxHeight
        val latestExpectedLayoutSizeKey by rememberUpdatedState(expectedLayoutSizeKey)
        var layoutSizeKey by remember { mutableStateOf(0 to 0) }
        val latestLayoutSizeKey by rememberUpdatedState(layoutSizeKey)
        var appliedLayoutSizeKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var appliedExpectedLayoutSizeKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        LaunchedEffect(contentKey) {
            appliedLayoutSizeKey = null
            appliedExpectedLayoutSizeKey = null
        }
        LaunchedEffect(expectedLayoutSizeKey) {
            appliedLayoutSizeKey = null
            appliedExpectedLayoutSizeKey = null
        }
        val contentReady = expectedLayoutSizeKey.first > 0 &&
            expectedLayoutSizeKey.second > 0 &&
            layoutSizeKey.first > 0 &&
            layoutSizeKey.second > 0 &&
            appliedLayoutSizeKey == layoutSizeKey &&
            appliedExpectedLayoutSizeKey == expectedLayoutSizeKey
        AndroidView(
            factory = { context ->
                io.github.rosemoe.sora.widget.CodeEditor(context).apply {
                    tag = contentKey
                    setText("")
                    setSelection(0, 0)
                    applyPlainTextEditorColors(textColor, backgroundColor)
                    setBackgroundColor(backgroundColor)
                    setTextSize(textSizeSp)
                    setTypefaceText(android.graphics.Typeface.MONOSPACE)
                    setEditable(true)
                    setInputType(PLAIN_TEXT_EDITOR_INPUT_TYPE)
                    subscribePlainTextImeOptions()
                    disablePlainTextInputAssists()
                    setPlainTextWordwrap()
                    setLineNumberEnabled(false)
                    setDisplayLnPanel(false)
                    setHorizontalScrollBarEnabled(false)
                    isVerticalScrollBarEnabled = true
                    setPadding(padding, padding, padding, 0)
                    setOnFocusChangeListener { _, hasFocus -> onFocusChanged(hasFocus) }
                    configureTxtFullEditWindowExpansion(
                        sourceStart = windowSourceStart,
                        sourceEnd = windowSourceEnd,
                        sourceLength = windowSourceLength,
                        onEdgeReached = null
                    )
                    setEditTextAfterStableLayout(
                        text = text,
                        contentKey = contentKey,
                        selectionTargetIndex = selectionTargetIndex,
                        scrollTargetLineIndex = scrollTargetLineIndex
                    ) {
                        appliedLayoutSizeKey = latestLayoutSizeKey
                        appliedExpectedLayoutSizeKey = latestExpectedLayoutSizeKey
                    }
                    onEditorReady(this)
                }
            },
            update = { editor ->
                if (editor.tag != contentKey) {
                    editor.tag = contentKey
                    appliedLayoutSizeKey = null
                    appliedExpectedLayoutSizeKey = null
                }
                editor.applyPlainTextEditorColors(textColor, backgroundColor)
                editor.setBackgroundColor(backgroundColor)
                editor.setTextSize(textSizeSp)
                editor.setTypefaceText(android.graphics.Typeface.MONOSPACE)
                editor.setEditable(true)
                editor.setInputType(PLAIN_TEXT_EDITOR_INPUT_TYPE)
                editor.disablePlainTextInputAssists()
                editor.setPlainTextWordwrap()
                editor.setLineNumberEnabled(false)
                editor.setDisplayLnPanel(false)
                editor.setHorizontalScrollBarEnabled(false)
                editor.setPadding(padding, padding, padding, 0)
                editor.configureTxtFullEditWindowExpansion(
                    sourceStart = windowSourceStart,
                    sourceEnd = windowSourceEnd,
                    sourceLength = windowSourceLength,
                    onEdgeReached = if (contentReady) latestOnWindowEdgeReached else null
                )
                if (
                    layoutSizeKey.first > 0 &&
                    layoutSizeKey.second > 0 &&
                    (
                        appliedLayoutSizeKey != layoutSizeKey ||
                            appliedExpectedLayoutSizeKey != expectedLayoutSizeKey
                    )
                ) {
                    // 布局尺寸变化（如输入法收起导致 imePadding 抬高的区域回缩）时，
                    // 编辑器可能已有用户输入但尚未保存。若仍用外部传入的原文 setText，
                    // 会把用户刚输入的内容覆盖掉。这里优先用编辑器当前内容，仅在其为空
                    // （首次布局完成前的边缘情形）时回退到传入文本。
                    val currentEditorText = editor.getText()?.toString().orEmpty()
                    editor.setEditTextAfterStableLayout(
                        text = currentEditorText.ifEmpty { text },
                        contentKey = contentKey,
                        selectionTargetIndex = selectionTargetIndex,
                        scrollTargetLineIndex = scrollTargetLineIndex
                    ) {
                        appliedLayoutSizeKey = latestLayoutSizeKey
                        appliedExpectedLayoutSizeKey = latestExpectedLayoutSizeKey
                    }
                }
                onEditorReady(editor)
            },
            onRelease = { editor ->
                editor.release()
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (contentReady) 1f else 0f)
                .clipToBounds()
                .onSizeChanged { layoutSizeKey = it.width to it.height }
        )
        if (!contentReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.configureTxtFullEditWindowExpansion(
    sourceStart: Int?,
    sourceEnd: Int?,
    sourceLength: Int?,
    onEdgeReached: ((TxtFullEditWindowEdge, String, Int, Int) -> Unit)?
) {
    if (
        sourceStart == null ||
        sourceEnd == null ||
        sourceLength == null ||
        onEdgeReached == null
    ) {
        setOnScrollChangeListener(null as android.view.View.OnScrollChangeListener?)
        return
    }
    setOnScrollChangeListener { _, _, _, _, _ ->
        val offsetY = getOffsetY()
        val maxY = getScrollMaxY()
        val edge = when {
            sourceStart > 0 && offsetY <= TXT_FULL_EDIT_WINDOW_EDGE_TRIGGER_PX -> TxtFullEditWindowEdge.Start
            sourceEnd < sourceLength && maxY - offsetY <= TXT_FULL_EDIT_WINDOW_EDGE_TRIGGER_PX -> TxtFullEditWindowEdge.End
            else -> null
        } ?: return@setOnScrollChangeListener
        onEdgeReached(
            edge,
            getText().toString(),
            currentTxtFullEditVisibleLine(),
            currentTxtFullEditCursorIndex()
        )
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.currentTxtFullEditVisibleLine(): Int {
    return runCatching {
        val packed = getPointPositionOnScreen(
            (paddingLeft + 1).toFloat(),
            (paddingTop + 1).toFloat()
        )
        io.github.rosemoe.sora.util.IntPair.getFirst(packed).coerceAtLeast(0)
    }.getOrDefault(0)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.currentTxtFullEditCursorIndex(): Int {
    return runCatching { getCursor().getLeft().coerceAtLeast(0) }.getOrDefault(0)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.configureTxtPreviewGestures(
    interactive: Boolean,
    onDoubleTap: ((Int) -> Unit)?,
    onLongPressLine: ((Int) -> Unit)?
) {
    val editor = this
    val gestureDetector = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(event: android.view.MotionEvent): Boolean {
                if (!interactive) return false
                val callback = onDoubleTap ?: return false
                val visibleOffset = runCatching {
                    val content = editor.getText()
                    if (content.length <= 0) return@runCatching 0
                    val packed = editor.getPointPositionOnScreen(event.x, event.y)
                    val line = io.github.rosemoe.sora.util.IntPair.getFirst(packed)
                        .coerceIn(0, (content.getLineCount() - 1).coerceAtLeast(0))
                    val column = io.github.rosemoe.sora.util.IntPair.getSecond(packed)
                        .coerceIn(0, content.getColumnCount(line))
                    content.getCharIndex(line, column).coerceIn(0, content.length)
                }.getOrDefault(0)
                callback(visibleOffset)
                return true
            }

            override fun onLongPress(event: android.view.MotionEvent) {
                if (!interactive) return
                val callback = onLongPressLine ?: return
                val packed = editor.getPointPositionOnScreen(event.x, event.y)
                val line = io.github.rosemoe.sora.util.IntPair.getFirst(packed)
                callback(line.coerceAtLeast(0))
            }
        }
    )
    setOnTouchListener { _, event ->
        if (!interactive) return@setOnTouchListener false
        gestureDetector.onTouchEvent(event)
        false
    }
}

private fun CodeEditor.configureReadOnlySelectionActions(
    actions: List<BodyReadOnlySelectionAction>,
    selectionMenuEnabled: Boolean
) {
    val current = getComponent(EditorTextActionWindow::class.java)
    if (!selectionMenuEnabled) {
        if (current !is BodyReadOnlyDisabledTextActionWindow) {
            current?.dismiss()
            replaceComponent(
                EditorTextActionWindow::class.java,
                BodyReadOnlyDisabledTextActionWindow(this)
            )
        }
        return
    }
    if (actions.isEmpty()) {
        if (current is BodyReadOnlyTextActionWindow) {
            replaceComponent(EditorTextActionWindow::class.java, EditorTextActionWindow(this))
        } else if (current is BodyReadOnlyDisabledTextActionWindow) {
            replaceComponent(EditorTextActionWindow::class.java, EditorTextActionWindow(this))
        }
        return
    }
    if (current is BodyReadOnlyTextActionWindow) {
        current.actions = actions
        current.rebuildActionButtons()
    } else {
        replaceComponent(
            EditorTextActionWindow::class.java,
            BodyReadOnlyTextActionWindow(this, actions)
        )
    }
}

private class BodyReadOnlyDisabledTextActionWindow(targetEditor: CodeEditor) : EditorTextActionWindow(targetEditor) {
    override fun show() {
        dismiss()
    }
}

private class BodyReadOnlyTextActionWindow(
    private val targetEditor: CodeEditor,
    var actions: List<BodyReadOnlySelectionAction>
) : EditorTextActionWindow(targetEditor) {
    private val actionButtons = mutableListOf<TextView>()

    init {
        rebuildActionButtons()
    }

    override fun show() {
        val visible = if (actions.isNotEmpty() && targetEditor.getCursor().isSelected()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        actionButtons.forEach { button ->
            button.setTextColor(
                targetEditor.getColorScheme()
                    .getColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR)
            )
            button.visibility = visible
        }
        super.show()
    }

    fun rebuildActionButtons() {
        val row = textActionButtonRow()
        actionButtons.forEach { row.removeView(it) }
        actionButtons.clear()
        val weighted = row is LinearLayout
        actions.forEachIndexed { index, action ->
            val button = TextView(targetEditor.context).apply {
                text = action.title
                gravity = android.view.Gravity.CENTER
                if (!weighted) {
                    minWidth = (targetEditor.getDpUnit() * 52).roundToInt()
                }
                setPadding(
                    (targetEditor.getDpUnit() * 12).roundToInt(),
                    0,
                    (targetEditor.getDpUnit() * 12).roundToInt(),
                    0
                )
                layoutParams = if (weighted) {
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                } else {
                    ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                setTextColor(
                    targetEditor.getColorScheme()
                        .getColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR)
                )
                setOnClickListener {
                    val cursor = targetEditor.getCursor()
                    val start = cursor.getLeft()
                    val end = cursor.getRight()
                    if (end > start) {
                        actions.getOrNull(index)?.onClick?.invoke(start, end)
                    }
                    dismiss()
                }
            }
            row.addView(button)
            actionButtons += button
        }
    }

    private fun textActionButtonRow(): ViewGroup {
        val scrollView = getView().getChildAt(0) as? HorizontalScrollView
        if (scrollView != null) {
            // 让按钮行填满视口宽度，配合按钮 weight 均分，
            // 三个按钮并排显示不被截断、无需横向滑动。
            scrollView.isFillViewport = true
            val row = scrollView.getChildAt(0) as? ViewGroup
            if (row != null) {
                row.layoutParams = android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                return row
            }
        }
        return getView()
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.applyEditSelectionTarget(index: Int?) {
    if (index == null) return
    val content = getText()
    if (content.length <= 0) return
    val safeIndex = index.coerceIn(0, content.length)
    val position = content.getIndexer().getCharPosition(safeIndex)
    setSelection(position.getLine(), position.getColumn(), false)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.scrollEditLineNearTop(line: Int?) {
    if (line == null) return
    val safeLine = line.coerceIn(0, (getLineCount() - 1).coerceAtLeast(0))
    val targetY = runCatching {
        (getOffsetY() + getCharOffsetY(safeLine, 0) - getRowHeight())
            .roundToInt()
            .coerceIn(0, getScrollMaxY())
    }.getOrDefault(0)
    val scroller = getScroller()
    scroller.forceFinished(true)
    scroller.startScroll(getOffsetX(), getOffsetY(), 0, targetY - getOffsetY(), 0)
    scroller.abortAnimation()
    invalidate()
}

private fun io.github.rosemoe.sora.widget.CodeEditor.setEditTextAfterStableLayout(
    text: String,
    contentKey: Any,
    selectionTargetIndex: Int?,
    scrollTargetLineIndex: Int?,
    onApplied: () -> Unit = {}
) {
    alpha = 0f
    requestLayout()
    postWhenPreviewMeasured(contentKey) {
        requestLayout()
        postPreviewFrames(contentKey, 1) {
            setText(text)
            applyEditSelectionTarget(selectionTargetIndex)
            scrollEditLineNearTop(scrollTargetLineIndex)
            requestLayout()
            invalidate()
            postPreviewFrames(contentKey, 6) {
                applyEditSelectionTarget(selectionTargetIndex)
                scrollEditLineNearTop(scrollTargetLineIndex)
                invalidate()
                postPreviewFrames(contentKey, 2) {
                    alpha = 1f
                    onApplied()
                }
            }
        }
    }
}

@Composable
internal fun LargeBodyReadOnlyPreview(
    text: String,
    contentKey: Any,
    positionKey: Any? = null,
    highlightRange: Pair<Int, Int>?,
    scrollTargetOffset: Int? = null,
    scrollTargetLineIndex: Int? = null,
    interactive: Boolean = true,
    showLoading: Boolean = true,
    onDoubleTap: ((Int) -> Unit)?,
    onLongPressLine: ((Int) -> Unit)?,
    selectionActions: List<BodyReadOnlySelectionAction> = emptyList(),
    onEditorReady: ((io.github.rosemoe.sora.widget.CodeEditor) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val textSizeSp = MaterialTheme.typography.bodyMedium.fontSize.value.takeIf { it > 0f } ?: 14f
    val padding = with(LocalDensity.current) { 8.dp.roundToPx() }
    val configKey = listOf(textColor, backgroundColor, textSizeSp, padding)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val latestOnLongPressLine by rememberUpdatedState(onLongPressLine)
    val latestSelectionActions by rememberUpdatedState(selectionActions)
    val latestInteractive by rememberUpdatedState(interactive)
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .graphicsLayer { clip = true }
    ) {
        val expectedLayoutSizeKey = constraints.maxWidth to constraints.maxHeight
        val latestExpectedLayoutSizeKey by rememberUpdatedState(expectedLayoutSizeKey)
        var appliedContentKey by remember { mutableStateOf<Any?>(null) }
        var appliedPositionKey by remember { mutableStateOf<Any?>(null) }
        var layoutSizeKey by remember { mutableStateOf(0 to 0) }
        val latestLayoutSizeKey by rememberUpdatedState(layoutSizeKey)
        var appliedLayoutSizeKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        var appliedExpectedLayoutSizeKey by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        LaunchedEffect(contentKey) {
            appliedContentKey = null
            appliedPositionKey = null
            appliedLayoutSizeKey = null
            appliedExpectedLayoutSizeKey = null
        }
        LaunchedEffect(expectedLayoutSizeKey) {
            appliedPositionKey = null
            appliedLayoutSizeKey = null
            appliedExpectedLayoutSizeKey = null
        }
        val layoutReady = !showLoading || (
            expectedLayoutSizeKey.first > 0 &&
                expectedLayoutSizeKey.second > 0 &&
                layoutSizeKey.first > 0 &&
                layoutSizeKey.second > 0 &&
                appliedLayoutSizeKey == layoutSizeKey &&
                appliedExpectedLayoutSizeKey == expectedLayoutSizeKey
        )
        val contentApplied = appliedContentKey == contentKey
        val contentReady = contentApplied && layoutReady
        AndroidView(
        factory = { context ->
            io.github.rosemoe.sora.widget.CodeEditor(context).apply {
                tag = PreviewCodeEditorTag(contentKey, configKey)
                isEnabled = interactive
                setText("")
                setSelection(0, 0, false)
                clipToOutline = true
                configurePlainTextCodeEditor(
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    textSizeSp = textSizeSp,
                    padding = padding,
                    editable = false
                )
                configureTxtPreviewGestures(
                    interactive = latestInteractive,
                    onDoubleTap = latestOnDoubleTap,
                    onLongPressLine = latestOnLongPressLine
                )
                configureReadOnlySelectionActions(
                    actions = if (latestInteractive) latestSelectionActions else emptyList(),
                    selectionMenuEnabled = latestInteractive && latestOnLongPressLine == null
                )
                setPreviewTextAfterStableLayout(text, contentKey, highlightRange, scrollTargetOffset, scrollTargetLineIndex) {
                    appliedContentKey = contentKey
                    appliedPositionKey = positionKey
                    appliedLayoutSizeKey = latestLayoutSizeKey
                    appliedExpectedLayoutSizeKey = latestExpectedLayoutSizeKey
                }
                onEditorReady?.invoke(this)
            }
        },
        update = { editor ->
            val contentChanged = editor.previewContentKey() != contentKey
            val configChanged = editor.previewConfigKey() != configKey
            if (contentChanged || configChanged) {
                editor.tag = PreviewCodeEditorTag(contentKey, configKey)
            }
            editor.isEnabled = interactive
            if (configChanged) {
                editor.configurePlainTextCodeEditor(
                    textColor = textColor,
                    backgroundColor = backgroundColor,
                    textSizeSp = textSizeSp,
                    padding = padding,
                    editable = false
                )
            }
            editor.configureTxtPreviewGestures(
                interactive = latestInteractive,
                onDoubleTap = latestOnDoubleTap,
                onLongPressLine = latestOnLongPressLine
            )
            editor.configureReadOnlySelectionActions(
                actions = if (latestInteractive) latestSelectionActions else emptyList(),
                selectionMenuEnabled = latestInteractive && latestOnLongPressLine == null
            )
            if (contentChanged || configChanged) {
                editor.setPreviewTextAfterStableLayout(text, contentKey, highlightRange, scrollTargetOffset, scrollTargetLineIndex) {
                    appliedContentKey = contentKey
                    appliedPositionKey = positionKey
                    appliedLayoutSizeKey = latestLayoutSizeKey
                    appliedExpectedLayoutSizeKey = latestExpectedLayoutSizeKey
                }
            } else if (
                interactive &&
                showLoading &&
                appliedContentKey == contentKey &&
                layoutSizeKey.first > 0 &&
                layoutSizeKey.second > 0 &&
                (
                    appliedLayoutSizeKey != layoutSizeKey ||
                        appliedExpectedLayoutSizeKey != expectedLayoutSizeKey
                )
            ) {
                editor.setPreviewTextAfterStableLayout(text, contentKey, highlightRange, scrollTargetOffset, scrollTargetLineIndex) {
                    appliedPositionKey = positionKey
                    appliedLayoutSizeKey = latestLayoutSizeKey
                    appliedExpectedLayoutSizeKey = latestExpectedLayoutSizeKey
                }
            } else if (interactive && appliedContentKey == contentKey && appliedPositionKey != positionKey) {
                editor.applyPreviewHighlightOrScroll(highlightRange, scrollTargetOffset, scrollTargetLineIndex)
                appliedPositionKey = positionKey
            }
            onEditorReady?.invoke(editor)
        },
        onRelease = { editor ->
            editor.release()
        },
        modifier = Modifier
            .fillMaxSize()
            .alpha(if (contentReady) 1f else 0f)
            .clipToBounds()
            .onSizeChanged { layoutSizeKey = it.width to it.height }
    )
        if (showLoading && !contentReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(contentKey) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!contentApplied) {
                    Text(
                    text = "加载中",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.setPreviewTextAfterStableLayout(
    text: String,
    contentKey: Any,
    highlightRange: Pair<Int, Int>?,
    scrollTargetOffset: Int?,
    scrollTargetLineIndex: Int?,
    onApplied: () -> Unit = {}
) {
    alpha = 0f
    requestLayout()
    postWhenPreviewMeasured(contentKey) {
        requestLayout()
        postPreviewFrames(contentKey, 1) {
            setText(text)
            rebuildPlainTextSoftWrap()
            setSelection(0, 0, false)
            applyPreviewHighlightOrScroll(highlightRange, scrollTargetOffset, scrollTargetLineIndex)
            requestLayout()
            invalidate()
            postPreviewFrames(contentKey, 6) {
                rebuildPlainTextSoftWrap()
                requestLayout()
                invalidate()
                applyPreviewHighlightOrScroll(highlightRange, scrollTargetOffset, scrollTargetLineIndex)
                postPreviewFrames(contentKey, 2) {
                    alpha = 1f
                    onApplied()
                }
            }
        }
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.postWhenPreviewMeasured(
    contentKey: Any,
    attemptsLeft: Int = 8,
    block: () -> Unit
) {
    postOnAnimation {
        if (previewContentKey() != contentKey) return@postOnAnimation
        if ((width <= 0 || height <= 0) && attemptsLeft > 0) {
            postWhenPreviewMeasured(contentKey, attemptsLeft - 1, block)
            return@postOnAnimation
        }
        block()
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.postPreviewFrames(
    contentKey: Any,
    frames: Int,
    block: () -> Unit
) {
    if (frames <= 0) {
        if (previewContentKey() == contentKey) block()
        return
    }
    postOnAnimation {
        if (previewContentKey() != contentKey) return@postOnAnimation
        postPreviewFrames(contentKey, frames - 1, block)
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.rebuildPlainTextSoftWrap() {
    setWordwrap(false, false, false)
    setPlainTextWordwrap()
    requestLayout()
    invalidate()
}

private fun io.github.rosemoe.sora.widget.CodeEditor.applyPlainTextEditorColors(
    textColor: Int,
    backgroundColor: Int
) {
    val scheme = getColorScheme()
    val highlightBackground = android.graphics.Color.rgb(255, 216, 77)
    val highlightContent = android.graphics.Color.rgb(17, 17, 17)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_NORMAL, textColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.WHOLE_BACKGROUND, backgroundColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER_BACKGROUND, backgroundColor)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_SELECTED, highlightContent)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SELECTED_TEXT_BACKGROUND, highlightBackground)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.SELECTED_TEXT_BORDER, highlightContent)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.MATCHED_TEXT_BACKGROUND, highlightBackground)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.MATCHED_TEXT_BORDER, highlightContent)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_HIGHLIGHT_BACKGROUND, highlightBackground)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_HIGHLIGHT_BORDER, highlightContent)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_HIGHLIGHT_STRONG_BACKGROUND, highlightBackground)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_HIGHLIGHT_STRONG_BORDER, highlightContent)
    scheme.setColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.UNDERLINE, highlightContent)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.configurePlainTextCodeEditor(
    textColor: Int,
    backgroundColor: Int,
    textSizeSp: Float,
    padding: Int,
    editable: Boolean
) {
    applyPlainTextEditorColors(textColor, backgroundColor)
    setBackgroundColor(backgroundColor)
    setTextSize(textSizeSp)
    setTypefaceText(android.graphics.Typeface.MONOSPACE)
    setEditable(editable)
    if (editable) {
        setInputType(PLAIN_TEXT_EDITOR_INPUT_TYPE)
    }
    disablePlainTextInputAssists()
    isFocusable = true
    isFocusableInTouchMode = true
    setPlainTextWordwrap()
    setLineNumberEnabled(false)
    setDisplayLnPanel(false)
    setHighlightCurrentLine(false)
    setHighlightCurrentBlock(false)
    setHighlightBracketPair(false)
    setCursorAnimationEnabled(editable)
    setHorizontalScrollBarEnabled(false)
    isSoundEffectsEnabled = editable
    isHapticFeedbackEnabled = true
    isVerticalScrollBarEnabled = true
    setPadding(padding, padding, padding, padding)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.disablePlainTextInputAssists() {
    getProps().disallowSuggestions = true
    getProps().disableTextExtracting = true
    getProps().autoCompletionOnComposing = false
    getProps().symbolPairAutoCompletion = false
    getComponent(io.github.rosemoe.sora.widget.component.EditorAutoCompletion::class.java).setEnabled(false)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.subscribePlainTextImeOptions() {
    subscribeEvent(BuildEditorInfoEvent::class.java) { event, _ ->
        event.editorInfo.imeOptions =
            (event.editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION.inv()) or
                EditorInfo.IME_ACTION_NONE or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        event.editorInfo.privateImeOptions =
            plainTextPrivateImeOptions(event.editorInfo.privateImeOptions)
    }
}

private fun plainTextPrivateImeOptions(current: String?): String {
    val value = current.orEmpty()
    if (value.isBlank()) return PLAIN_TEXT_PRIVATE_IME_OPTIONS
    if (value.contains(PLAIN_TEXT_PRIVATE_IME_OPTIONS)) return value
    return "$value;$PLAIN_TEXT_PRIVATE_IME_OPTIONS"
}

private fun io.github.rosemoe.sora.widget.CodeEditor.setPlainTextWordwrap() {
    setWordwrap(true, false, false)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.clearPreviewHighlightText() {
    if (getHighlightTexts() != null) {
        setHighlightTexts(null)
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.applyPreviewHighlight(range: Pair<Int, Int>?) {
    val content = getText()
    val length = content.length
    if (length <= 0) {
        clearPreviewHighlightText()
        return
    }
    if (range == null) {
        clearPreviewHighlightText()
        return
    }
    val start = range.first.coerceIn(0, length)
    val end = range.second.coerceIn(start, length)
    if (end <= start) {
        clearPreviewHighlightText()
        return
    }
    val startPosition = content.getIndexer().getCharPosition(start)
    val endPosition = content.getIndexer().getCharPosition(end)
    val highlights = HighlightTextContainer().apply {
        add(
            HighlightTextContainer.HighlightText(
                startPosition.getLine(),
                startPosition.getColumn(),
                endPosition.getLine(),
                endPosition.getColumn()
            )
        )
    }
    setHighlightTexts(highlights)
    setSelection(endPosition.getLine(), endPosition.getColumn(), false)
    getComponent(EditorTextActionWindow::class.java)?.dismiss()
    scrollPreviewPositionCentered(endPosition.getLine(), endPosition.getColumn())
    previewContentKey()?.let { contentKey ->
        postPreviewFrames(contentKey, 2) {
            scrollPreviewPositionCentered(endPosition.getLine(), endPosition.getColumn())
            getComponent(EditorTextActionWindow::class.java)?.dismiss()
        }
    }
}

private fun io.github.rosemoe.sora.widget.CodeEditor.applyPreviewHighlightOrScroll(
    range: Pair<Int, Int>?,
    scrollTargetOffset: Int?,
    scrollTargetLineIndex: Int?
) {
    if (range != null) {
        applyPreviewHighlight(range)
        return
    }
    clearPreviewHighlightText()
    val content = getText()
    val length = content.length
    if (length <= 0) return
    if (scrollTargetLineIndex != null) {
        scrollPreviewPositionNearTop(scrollTargetLineIndex, 0)
        return
    }
    val target = scrollTargetOffset?.coerceIn(0, length) ?: run {
        applyPreviewHighlight(null)
        return
    }
    val position = content.getIndexer().getCharPosition(target)
    scrollPreviewPositionNearTop(position.getLine(), 0)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.scrollPreviewPositionNearTop(
    line: Int,
    column: Int
) {
    val safeLine = line.coerceIn(0, (getLineCount() - 1).coerceAtLeast(0))
    val safeColumn = column.coerceIn(0, getText().getColumnCount(safeLine))
    setSelection(safeLine, safeColumn, false)
    val targetY = (getOffsetY() + getCharOffsetY(safeLine, safeColumn) - getRowHeight())
        .roundToInt()
    jumpPreviewScrollTo(targetY)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.scrollPreviewPositionCentered(
    line: Int,
    column: Int
) {
    val safeLine = line.coerceIn(0, (getLineCount() - 1).coerceAtLeast(0))
    val safeColumn = column.coerceIn(0, getText().getColumnCount(safeLine))
    val lineCenterY = getOffsetY() + getCharOffsetY(safeLine, safeColumn) - getRowHeight() / 2f
    val viewportCenterY = height.toFloat().coerceAtLeast(getRowHeight().toFloat()) / 2f
    val targetY = (lineCenterY - viewportCenterY)
        .roundToInt()
    jumpPreviewScrollTo(targetY)
}

private fun io.github.rosemoe.sora.widget.CodeEditor.jumpPreviewScrollTo(targetY: Int) {
    val safeTargetY = targetY.coerceIn(0, getScrollMaxY())
    val scroller = getScroller()
    scroller.forceFinished(true)
    scroller.startScroll(getOffsetX(), getOffsetY(), 0, safeTargetY - getOffsetY(), 0)
    scroller.abortAnimation()
    invalidate()
}

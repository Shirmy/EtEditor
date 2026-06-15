package com.eteditor

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

internal data class BodySelectionToolbarAction(
    val title: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
internal fun BodySelectionTextToolbarProvider(
    actions: () -> List<BodySelectionToolbarAction>,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val latestActions by rememberUpdatedState(actions)
    val toolbar = remember(view) {
        BodySelectionTextToolbar(view) { latestActions() }
    }
    DisposableEffect(toolbar) {
        onDispose { toolbar.hide() }
    }
    SideEffect {
        toolbar.invalidate()
    }
    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        content()
    }
}

private class BodySelectionTextToolbar(
    private val view: View,
    private val actions: () -> List<BodySelectionToolbarAction>
) : TextToolbar {
    private var actionMode: ActionMode? = null
    private var contentRect: Rect = Rect.Zero
    private var onCopyRequested: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?
    ) {
        contentRect = rect
        this.onCopyRequested = onCopyRequested
        val currentActionMode = actionMode
        if (currentActionMode == null) {
            status = TextToolbarStatus.Shown
            actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        } else {
            currentActionMode.invalidate()
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
            onAutofillRequested = null
        )
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        actionMode?.finish()
        actionMode = null
    }

    fun invalidate() {
        actionMode?.invalidate()
    }

    private val callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            populateMenu(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.clear()
            populateMenu(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                MENU_COPY -> onCopyRequested?.invoke()
                else -> {
                    val action = actions().getOrNull(item.itemId - MENU_EXTRA_START) ?: return false
                    action.onClick()
                }
            }
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            status = TextToolbarStatus.Hidden
            actionMode = null
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            outRect.set(
                contentRect.left.toInt(),
                contentRect.top.toInt(),
                contentRect.right.toInt(),
                contentRect.bottom.toInt()
            )
        }
    }

    private fun populateMenu(menu: Menu) {
        onCopyRequested?.let { addMenuItem(menu, MENU_COPY, 0, android.R.string.copy) }
        actions().forEachIndexed { index, action ->
            menu.add(0, MENU_EXTRA_START + index, 1 + index, action.title)
                .setEnabled(action.enabled)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    private fun addMenuItem(menu: Menu, id: Int, order: Int, titleRes: Int) {
        menu.add(0, id, order, titleRes)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private companion object {
        private const val MENU_COPY = 1
        private const val MENU_EXTRA_START = 100
    }
}

package com.eteditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eteditor.core.DocumentKind

enum class AppScreen {
    Files,
    Automation,
    Features,
    Settings
}

@Composable
fun VerticalDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
fun BottomNav(controller: EditorController) {
    val documentOpen = controller.kind != DocumentKind.None
    val featureItem = when (controller.kind) {
        DocumentKind.Txt -> "搜索" to Icons.Outlined.Search
        else -> "功能" to Icons.Outlined.Widgets
    }
    val items = buildList {
        add(AppScreen.Files to ("文件" to Icons.Outlined.FolderOpen))
        add(AppScreen.Features to featureItem)
        add(AppScreen.Settings to ("设置" to Icons.Outlined.Settings))
    }
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            items.forEach { (screen, meta) ->
                val enabled = documentOpen || screen == AppScreen.Files || screen == AppScreen.Settings
                NavigationBarItem(
                    selected = controller.selectedScreen == screen,
                    onClick = { controller.selectedScreen = screen },
                    enabled = enabled,
                    icon = { Icon(meta.second, contentDescription = meta.first) },
                    label = { Text(meta.first) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

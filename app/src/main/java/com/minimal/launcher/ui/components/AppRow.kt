package com.minimal.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.minimal.launcher.applications.AppInfo

/**
 * A titled strip of app icons (Frecuentes, Sugeridas). Horizontal on home/drawer, vertical in the
 * tablet side bar. Tap launches, long-press opens [menuFor].
 */
@Composable
fun AppRow(
    title: String,
    apps: List<AppInfo>,
    iconSize: Dp,
    labelStyle: TextStyle,
    showLabels: Boolean,
    onLaunch: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    testTag: String = "app_row",
    onTitleLongPress: (() -> Unit)? = null,
) {
    if (apps.isEmpty()) return
    val tiles: @Composable (Modifier) -> Unit = { m ->
        apps.forEach { app -> key(app.key) { RowTile(app, iconSize, labelStyle, showLabels, onLaunch, menuFor, m) } }
    }
    Column(modifier.testTag(testTag), horizontalAlignment = Alignment.CenterHorizontally) {
        RowTitle(title, labelStyle, onTitleLongPress)
        if (vertical) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                tiles(Modifier.width(iconSize + 16.dp))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { tiles(Modifier.weight(1f)) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowTitle(title: String, style: TextStyle, onLongPress: (() -> Unit)?) {
    val haptics = LocalHapticFeedback.current
    Text(
        title,
        style = style,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onLongPress != null) Modifier.combinedClickable(onClick = {}, onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress()
                }) else Modifier,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowTile(
    app: AppInfo,
    iconSize: Dp,
    labelStyle: TextStyle,
    showLabels: Boolean,
    onLaunch: (AppInfo) -> Unit,
    menuFor: (AppInfo) -> List<MenuAction>,
    modifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier.widthIn(max = iconSize + 32.dp), contentAlignment = Alignment.Center) {
        AppTile(
            icon = { size -> AppIconImage(app.key, size) },
            label = app.label,
            iconSize = iconSize,
            showLabel = showLabels,
            labelStyle = labelStyle,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { onLaunch(app) },
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                )
                .padding(4.dp)
                .testTag("row_app_${app.label}"),
        )
        LauncherMenu(expanded = menu, onDismiss = { menu = false }, actions = menuFor(app), app = app)
    }
}

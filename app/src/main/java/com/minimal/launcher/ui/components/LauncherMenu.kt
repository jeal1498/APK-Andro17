package com.minimal.launcher.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.minimal.launcher.applications.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/** Long-press popup used everywhere (home items, dock, drawer). Shows app shortcuts when available. */
@Composable
fun LauncherMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<MenuAction>,
    app: AppInfo? = null,
    onPinShortcut: ((id: String, label: String) -> Unit)? = null,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        if (app != null) AppShortcuts(app, onDismiss, onPinShortcut)
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(action.icon, contentDescription = null) },
                onClick = { onDismiss(); action.onClick() },
            )
        }
    }
}

private data class ShortcutRow(val id: String, val label: String, val icon: ImageBitmap?)

@Composable
private fun AppShortcuts(app: AppInfo, onDismiss: () -> Unit, onPin: ((String, String) -> Unit)?) {
    val actions = LocalAppActions.current
    val cache = LocalIconCache.current
    var rows by remember(app.key) { mutableStateOf<List<ShortcutRow>>(emptyList()) }
    LaunchedEffect(app.key) {
        rows = withContext(Dispatchers.IO) {
            actions.shortcutsFor(app.packageName, app.user).map {
                ShortcutRow(it.id, (it.shortLabel ?: it.longLabel ?: "").toString(), cache.shortcutIcon("shortcut:${app.packageName}:${it.id}", it))
            }
        }
    }
    if (rows.isEmpty()) return
    rows.forEach { row ->
        DropdownMenuItem(
            text = { Text(row.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { IconBitmap(row.icon, 24.dp) },
            trailingIcon = if (onPin != null) {
                {
                    IconButton(onClick = { onDismiss(); onPin(row.id, row.label) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.PushPin, contentDescription = "Añadir a inicio", modifier = Modifier.size(18.dp))
                    }
                }
            } else null,
            onClick = { onDismiss(); actions.startShortcut(app.packageName, row.id, app.user) },
        )
    }
    HorizontalDivider()
}

@Composable
fun MenuHeader(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

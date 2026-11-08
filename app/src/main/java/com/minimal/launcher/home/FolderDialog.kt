package com.minimal.launcher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.drawer.AppPickerDialog
import com.minimal.launcher.ui.DeviceProfile
import com.minimal.launcher.ui.components.AppTile
import com.minimal.launcher.ui.components.LauncherMenu
import com.minimal.launcher.ui.components.MenuAction
import com.minimal.launcher.ui.components.detectLauncherGestures

@Composable
fun FolderDialog(
    folder: HomeItemEntity,
    children: List<HomeItemEntity>,
    apps: Map<String, AppInfo>,
    allApps: List<AppInfo>,
    profile: DeviceProfile,
    onDismiss: () -> Unit,
    onOpen: (HomeItemEntity) -> Unit,
    onRename: (String) -> Unit,
    onRemoveFromFolder: (HomeItemEntity) -> Unit,
    onRemoveItem: (HomeItemEntity) -> Unit,
    onAddApps: (List<String>) -> Unit,
    onUngroup: () -> Unit,
    onAppInfo: (AppInfo) -> Unit,
) {
    var title by remember(folder.id) { mutableStateOf(folder.title ?: "Carpeta") }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    val columns = if (profile.isTablet) 5 else 4

    Dialog(onDismissRequest = { if (title != folder.title) onRename(title); onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = if (profile.isTablet) 520.dp else 400.dp).testTag("folder_dialog"),
        ) {
            Column(Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.padding(top = 16.dp).heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(children, key = { it.id }) { child ->
                        val app = child.appKey?.let { apps[it] }
                        Box(
                            Modifier.pointerInput(child.id) {
                                detectLauncherGestures(
                                    onTap = { onOpen(child) },
                                    onLongPress = { menuFor = child.id },
                                )
                            },
                        ) {
                            AppTile(
                                icon = { size -> ItemIcon(child, app, emptyList(), size) },
                                label = itemLabel(child, app),
                                iconSize = profile.drawerIconSize,
                                showLabel = true,
                                labelStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LauncherMenu(
                                expanded = menuFor == child.id,
                                onDismiss = { menuFor = null },
                                app = app,
                                actions = buildList {
                                    add(MenuAction("Sacar de la carpeta", Icons.Outlined.Logout) { onRemoveFromFolder(child) })
                                    if (app != null) add(MenuAction("Información de la app", Icons.Outlined.Info) { onAppInfo(app) })
                                    add(MenuAction("Quitar", Icons.Outlined.Close) { onRemoveItem(child) })
                                },
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onUngroup) { Text("Desagrupar") }
                    TextButton(onClick = { picking = true }) { Text("Añadir apps") }
                }
            }
        }
    }
    if (picking) {
        val existing = children.mapNotNull { it.appKey }.toSet()
        AppPickerDialog(
            title = "Añadir a ${title}",
            apps = allApps.filter { it.key !in existing },
            onDismiss = { picking = false },
            onConfirm = { picking = false; onAddApps(it.map { a -> a.key }) },
        )
    }
}

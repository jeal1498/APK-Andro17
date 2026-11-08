package com.minimal.launcher.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.minimal.launcher.AppContainer
import com.minimal.launcher.BuildConfigInfo
import com.minimal.launcher.categories.CategoryResolver
import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.data.prefs.AppSort
import com.minimal.launcher.data.prefs.DrawerTab
import com.minimal.launcher.data.prefs.LauncherSettings
import com.minimal.launcher.data.prefs.ThemeMode
import com.minimal.launcher.drawer.CategoryEditorDialog
import com.minimal.launcher.ui.components.AppIconImage
import com.minimal.launcher.ui.components.CategoryIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

object Routes {
    const val MAIN = "main"
    const val CATEGORIES = "categories"
    const val HIDDEN = "hidden"
    const val WIDGETS = "widgets"
    const val ABOUT = "about"
}

@Composable
fun SettingsNavHost(
    settings: LauncherSettings,
    container: AppContainer,
    scope: CoroutineScope,
    isDefaultLauncher: Boolean,
    onRequestDefault: () -> Unit,
    onClose: () -> Unit,
    nav: NavHostController = rememberNavController(),
) {
    NavHost(navController = nav, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainSettings(settings, container, scope, isDefaultLauncher, onRequestDefault, onBack = onClose, navigate = { nav.navigate(it) })
        }
        composable(Routes.CATEGORIES) { CategoriesSettings(container, scope, onBack = { nav.popBackStack() }) }
        composable(Routes.HIDDEN) { HiddenAppsSettings(container, scope, onBack = { nav.popBackStack() }) }
        composable(Routes.WIDGETS) { WidgetsSettings(container, scope, onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(isDefaultLauncher, onRequestDefault, onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun MainSettings(
    s: LauncherSettings,
    container: AppContainer,
    scope: CoroutineScope,
    isDefaultLauncher: Boolean,
    onRequestDefault: () -> Unit,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    val prefs = container.settings
    var gridDialog by remember { mutableStateOf(false) }
    var drawerColsDialog by remember { mutableStateOf(false) }
    var categoryColsDialog by remember { mutableStateOf(false) }
    var confirmClearUsage by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val context = LocalContext.current

    SettingsScaffold("Ajustes", onBack, "settings_main") {
        if (!isDefaultLauncher) item(key = "default") {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Minimal Launcher no es tu app de inicio", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Establécelo como predeterminado para usar accesos directos de apps y el botón de inicio.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    TextButton(onClick = onRequestDefault, modifier = Modifier.align(Alignment.End).testTag("set_default")) { Text("Establecer como predeterminado") }
                }
            }
        }

        section("Apariencia")
        item(key = "theme") {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tema", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.size(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("theme_selector")) {
                        listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro").forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { scope.launch { prefs.setThemeMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 3),
                            ) { Text(label) }
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 31) item(key = "dynamic") {
            SwitchRow("Colores del fondo de pantalla", "Dynamic Color (Material You)", s.dynamicColor, "dynamic_color") { scope.launch { prefs.setDynamicColor(it) } }
        }

        section("Pantalla de inicio")
        item(key = "grid") { SettingRow("Cuadrícula", "${s.homeColumns} × ${s.homeRows}", onClick = { gridDialog = true }, testTag = "grid_setting") }
        item(key = "homeLabels") { SwitchRow("Mostrar nombres en inicio", null, s.showHomeLabels, "home_labels") { scope.launch { prefs.setShowHomeLabels(it) } } }
        item(key = "search") { SwitchRow("Barra de búsqueda", "Debajo del dock", s.showSearchBar, "search_bar") { scope.launch { prefs.setShowSearchBar(it) } } }
        item(key = "rotation") { SwitchRow("Permitir rotación", "En tablets la rotación siempre está activa", s.allowRotation, "rotation") { scope.launch { prefs.setAllowRotation(it) } } }

        section("Cajón de aplicaciones")
        item(key = "drawerCols") { SettingRow("Columnas de apps", if (s.drawerColumns == 0) "Automático" else "${s.drawerColumns}", onClick = { drawerColsDialog = true }, testTag = "drawer_columns") }
        item(key = "catCols") { SettingRow("Columnas de categorías", if (s.categoryColumns == 0) "Automático" else "${s.categoryColumns}", onClick = { categoryColsDialog = true }, testTag = "category_columns") }
        item(key = "drawerLabels") { SwitchRow("Mostrar nombres en el cajón", null, s.showDrawerLabels, "drawer_labels") { scope.launch { prefs.setShowDrawerLabels(it) } } }
        item(key = "sort") {
            ChoiceRow("Orden de apps", AppSort.entries.map { SettingOption(it, sortLabel(it)) }, s.appSort, "sort") { scope.launch { prefs.setAppSort(it) } }
        }
        item(key = "tab") {
            SwitchRow("Abrir en categorías", "Si se desactiva, el cajón abre la lista completa", s.drawerTab == DrawerTab.CATEGORIES, "drawer_tab") {
                scope.launch { prefs.setDrawerTab(if (it) DrawerTab.CATEGORIES else DrawerTab.ALL) }
            }
        }

        section("Organización")
        item(key = "cats") { SettingRow("Categorías", "Crear, editar, ordenar y eliminar", onClick = { navigate(Routes.CATEGORIES) }, testTag = "manage_categories") }
        item(key = "hidden") { SettingRow("Apps ocultas", null, onClick = { navigate(Routes.HIDDEN) }, testTag = "hidden_apps") }
        item(key = "restore") { SettingRow("Restaurar clasificación automática", "Elimina todas las asignaciones manuales", onClick = { confirmRestore = true }, testTag = "restore_auto") }

        section("Uso de apps")
        item(key = "learnUsage") {
            SwitchRow("Aprender de las apps que abro", "Para Frecuentes y Sugeridas. Se guarda solo en este dispositivo.", s.learnUsage, "learn_usage") {
                scope.launch { prefs.setLearnUsage(it) }
            }
        }
        item(key = "clearUsage") { SettingRow("Borrar historial de uso", null, onClick = { confirmClearUsage = true }, testTag = "clear_usage") }

        section("Widgets")
        item(key = "widgets") { SettingRow("Widgets en la pantalla de inicio", "Ver y quitar widgets", onClick = { navigate(Routes.WIDGETS) }, testTag = "widgets_settings") }

        section("Sistema")
        item(key = "wallpaper") {
            SettingRow("Fondo de pantalla", null, onClick = {
                runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fondo de pantalla")) }
            })
        }
        item(key = "about") { SettingRow("Información", "Versión ${BuildConfigInfo.versionName(context)}", onClick = { navigate(Routes.ABOUT) }, testTag = "about") }
    }

    if (gridDialog) GridDialog(s.homeColumns, s.homeRows, onDismiss = { gridDialog = false }) { c, r ->
        gridDialog = false
        scope.launch { container.home.applyGrid(c, r) }
    }
    if (drawerColsDialog) ColumnsDialog("Columnas de apps", s.drawerColumns, 3..8, onDismiss = { drawerColsDialog = false }) {
        drawerColsDialog = false; scope.launch { prefs.setDrawerColumns(it) }
    }
    if (categoryColsDialog) ColumnsDialog("Columnas de categorías", s.categoryColumns, 2..6, onDismiss = { categoryColsDialog = false }) {
        categoryColsDialog = false; scope.launch { prefs.setCategoryColumns(it) }
    }
    if (confirmClearUsage) {
        ConfirmDialog("¿Borrar historial de uso?", "Frecuentes y Sugeridas empezarán de cero. Las apps fijadas se conservan.", "Borrar",
            onDismiss = { confirmClearUsage = false }) { scope.launch { container.usage.clear() } }
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("¿Restaurar clasificación automática?") },
            text = { Text("Se eliminarán todas las asignaciones manuales. Las categorías se conservan.") },
            confirmButton = { TextButton(onClick = { confirmRestore = false; scope.launch { container.categories.restoreAllAutomatic() } }) { Text("Restaurar") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancelar") } },
        )
    }
}

private fun sortLabel(sort: AppSort) = when (sort) {
    AppSort.ALPHABETICAL -> "Alfabético (A-Z)"
    AppSort.ALPHABETICAL_DESC -> "Alfabético (Z-A)"
    AppSort.RECENTLY_INSTALLED -> "Instaladas recientemente"
}

@Composable
private fun GridDialog(cols: Int, rows: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var c by remember { mutableFloatStateOf(cols.toFloat()) }
    var r by remember { mutableFloatStateOf(rows.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("grid_dialog"),
        title = { Text("Cuadrícula de inicio") },
        text = {
            Column {
                Text("Columnas: ${c.toInt()}")
                Slider(c, { c = it }, valueRange = 3f..8f, steps = 4)
                Text("Filas: ${r.toInt()}")
                Slider(r, { r = it }, valueRange = 3f..8f, steps = 4)
                Text("Los elementos que no quepan se moverán a otro espacio libre.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(c.toInt(), r.toInt()) }) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ColumnsDialog(title: String, current: Int, range: IntRange, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                (listOf(0) + range.toList()).forEach { v ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onConfirm(v) }.padding(12.dp)) {
                        Text((if (v == current) "● " else "○ ") + if (v == 0) "Automático (según pantalla)" else "$v")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun CategoriesSettings(container: AppContainer, scope: CoroutineScope, onBack: () -> Unit) {
    val categories by container.categories.categories.collectAsState(initial = emptyList())
    val grouped by remember {
        combine(container.apps.apps, container.categories.categories, container.categories.assignments, container.categories.manualApps) { a, c, s, m ->
            CategoryResolver.group(a, c, s, m.toSet()).associate { it.id to it.apps.size }
        }
    }.collectAsState(initial = emptyMap())
    var editing by remember { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }

    SettingsScaffold("Categorías", onBack, "settings_categories") {
        item(key = "add") { SettingRow("Nueva categoría", null, onClick = { creating = true }, trailing = { Icon(Icons.Outlined.Add, null) }, testTag = "add_category") }
        item(key = "restoreBuiltins") {
            SettingRow("Restaurar categorías predeterminadas", "Vuelve a crear las que se eliminaron", onClick = { scope.launch { container.categories.restoreMissingBuiltins() } }, trailing = { Icon(Icons.Outlined.Restore, null) })
        }
        item(key = "sp") { Spacer(Modifier.size(12.dp)) }
        itemsIndexed(categories, key = { _, c -> c.id }) { index, c ->
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(CategoryIcons.forKey(c.iconKey), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${grouped[c.id] ?: 0} apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(enabled = index > 0, onClick = {
                        val ids = categories.map { it.id }.toMutableList(); ids.add(index - 1, ids.removeAt(index))
                        scope.launch { container.categories.reorder(ids) }
                    }) { Icon(Icons.Outlined.ArrowUpward, "Subir") }
                    IconButton(enabled = index < categories.lastIndex, onClick = {
                        val ids = categories.map { it.id }.toMutableList(); ids.add(index + 1, ids.removeAt(index))
                        scope.launch { container.categories.reorder(ids) }
                    }) { Icon(Icons.Outlined.ArrowDownward, "Bajar") }
                    IconButton(onClick = { editing = c.id }) { Icon(Icons.Outlined.Edit, "Editar") }
                }
            }
        }
    }
    categories.firstOrNull { it.id == editing }?.let { c ->
        CategoryEditorDialog(
            title = "Editar categoría", initialName = c.name, initialIcon = c.iconKey, initialKeywords = c.keywords,
            onDismiss = { editing = null },
            onSave = { n, i, k -> editing = null; scope.launch { container.categories.update(c.copy(name = n, iconKey = i, keywords = k)) } },
            onDelete = { editing = null; scope.launch { container.categories.delete(c.id) } },
        )
    }
    if (creating) {
        CategoryEditorDialog(
            title = "Nueva categoría", initialName = "", initialIcon = "folder", initialKeywords = "",
            onDismiss = { creating = false },
            onSave = { n, i, k -> creating = false; scope.launch { container.categories.create(n, i, k) } },
        )
    }
}

@Composable
private fun HiddenAppsSettings(container: AppContainer, scope: CoroutineScope, onBack: () -> Unit) {
    val apps by container.apps.apps.collectAsState()
    val hidden by container.hiddenApps.observe().collectAsState(initial = emptyList())
    val hiddenSet = hidden.toSet()
    SettingsScaffold("Apps ocultas", onBack, "settings_hidden") {
        item(key = "info") {
            Text(
                "Las apps ocultas no aparecen en el cajón ni en la búsqueda. Siguen instaladas.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(apps, key = { it.key }) { app ->
            val isHidden = app.key in hiddenSet
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = { AppIconImage(app.key, 40.dp) },
                    headlineContent = { Text(app.label) },
                    trailingContent = {
                        Switch(checked = isHidden, onCheckedChange = { hide ->
                            scope.launch {
                                if (hide) container.hiddenApps.hide(com.minimal.launcher.data.db.HiddenAppEntity(app.key))
                                else container.hiddenApps.unhide(app.key)
                            }
                        })
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }
}

@Composable
private fun WidgetsSettings(container: AppContainer, scope: CoroutineScope, onBack: () -> Unit) {
    val items by container.home.items.collectAsState(initial = emptyList())
    val widgets = items.filter { it.type == HomeItemEntity.TYPE_WIDGET || it.type == HomeItemEntity.TYPE_CLOCK }
    val context = LocalContext.current
    SettingsScaffold("Widgets", onBack, "settings_widgets") {
        item(key = "info") {
            Text(
                "Para añadir un widget, mantén pulsado un espacio vacío de la pantalla de inicio y elige «Widgets». " +
                    "Mantén pulsado un widget para moverlo, redimensionarlo o configurarlo.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (widgets.isEmpty()) item(key = "empty") { SettingRow("No hay widgets en la pantalla de inicio") }
        items(widgets, key = { it.id }) { item ->
            val info = item.widgetId?.let { container.widgets.info(it) }
            val label = when {
                item.type == HomeItemEntity.TYPE_CLOCK -> "Reloj y fecha"
                info != null -> info.loadLabel(context.packageManager)
                else -> "Widget no disponible"
            }
            SettingRow(
                label,
                "Página ${item.containerId + 1} · ${item.spanX} × ${item.spanY}",
                trailing = {
                    IconButton(onClick = {
                        scope.launch { container.home.delete(item.id)?.let { container.widgets.deleteId(it) } }
                    }) { Icon(Icons.Outlined.Delete, "Quitar") }
                },
            )
        }
    }
}

@Composable
private fun AboutScreen(isDefaultLauncher: Boolean, onRequestDefault: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsScaffold("Información", onBack, "settings_about") {
        item { SettingRow("Minimal Launcher", "Versión ${BuildConfigInfo.versionName(context)}") }
        item { SettingRow("Launcher predeterminado", if (isDefaultLauncher) "Sí" else "No — toca para cambiarlo", onClick = onRequestDefault) }
        item { SettingRow("Android", "API ${Build.VERSION.SDK_INT} · ${Build.VERSION.RELEASE}") }
        item {
            SettingRow("Privacidad", "Sin conexión a Internet. La clasificación de apps se hace localmente en el dispositivo.")
        }
        item {
            SettingRow("Información de la app en el sistema", null, onClick = {
                runCatching {
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.fromParts("package", context.packageName, null)),
                    )
                }
            })
        }
    }
}

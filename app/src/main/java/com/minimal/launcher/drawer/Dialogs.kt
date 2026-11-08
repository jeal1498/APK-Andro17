package com.minimal.launcher.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.categories.AssignmentSource
import com.minimal.launcher.categories.CategoryWithApps
import com.minimal.launcher.categories.Resolution
import com.minimal.launcher.search.AppSearch
import com.minimal.launcher.ui.components.AppIconImage
import com.minimal.launcher.ui.components.CategoryIcons

/** Multi-category assignment for one app (manual classification). */
@Composable
fun AssignCategoriesDialog(
    app: AppInfo,
    categories: List<CategoryWithApps>,
    resolution: Resolution,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
    onRestoreAutomatic: () -> Unit,
) {
    var selected by remember(app.key, resolution) { mutableStateOf(resolution.categoryIds) }
    val real = categories.filterNot { it.isUnclassified }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("assign_dialog"),
        icon = { AppIconImage(app.key, 40.dp) },
        title = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                Text(
                    when (resolution.source) {
                        AssignmentSource.MANUAL -> "Clasificación manual"
                        AssignmentSource.AUTOMATIC -> "Clasificación automática"
                        AssignmentSource.UNCLASSIFIED -> "Sin clasificar"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(real, key = { it.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { selected = if (c.id in selected) selected - c.id else selected + c.id }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = c.id in selected, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Icon(CategoryIcons.forKey(c.iconKey), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(c.name)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected) }) { Text("Guardar") } },
        dismissButton = {
            Row {
                if (resolution.source != AssignmentSource.AUTOMATIC) TextButton(onClick = onRestoreAutomatic) { Text("Automática") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/** Create / edit a category: name, icon and local classification keywords. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    initialKeywords: String,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, keywords: String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    var keywords by remember { mutableStateOf(initialKeywords) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("category_editor"),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("category_name"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.all.forEach { (key, vector) ->
                        val sel = key == icon
                        Box(
                            Modifier.size(40.dp).clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(vector, key, tint = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                OutlinedTextField(
                    keywords, { keywords = it },
                    label = { Text("Palabras clave") },
                    supportingText = { Text("Separadas por comas. Las apps cuyo nombre o paquete coincida se clasificarán aquí automáticamente.") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), icon, keywords) }) { Text("Guardar") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/** Searchable multi-select list of installed apps. */
@Composable
fun AppPickerDialog(
    title: String,
    apps: List<AppInfo>,
    onDismiss: () -> Unit,
    onConfirm: (List<AppInfo>) -> Unit,
    maxSelection: Int = Int.MAX_VALUE,
    confirmLabel: String = "Añadir",
    subtitle: String? = null,
) {
    var query by remember { mutableStateOf("") }
    // Ordered: for pairs the selection order matters.
    var selected by remember { mutableStateOf(listOf<String>()) }
    val shown = remember(apps, query) { if (query.isBlank()) apps else AppSearch.search(apps, query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("app_picker"),
        title = { Text(title) },
        text = {
            Column {
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
                OutlinedTextField(
                    query, { query = it }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Buscar") }, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.key }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selected = when {
                                        app.key in selected -> selected - app.key
                                        selected.size >= maxSelection -> if (maxSelection == 1) listOf(app.key) else selected
                                        else -> selected + app.key
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = app.key in selected, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            AppIconImage(app.key, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selected.isNotEmpty(), onClick = {
                val byKey = apps.associateBy { it.key }
                onConfirm(selected.mapNotNull { byKey[it] })
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

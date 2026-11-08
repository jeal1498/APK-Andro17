package com.minimal.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.minimal.launcher.categories.Classifier
import com.minimal.launcher.container
import com.minimal.launcher.ui.DeviceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ProviderGroup(val appLabel: String, val appIcon: ImageBitmap?, val providers: List<AppWidgetProviderInfo>)

/** Lists the real widgets offered by installed apps (AppWidgetManager), grouped by app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    profile: DeviceProfile,
    onDismiss: () -> Unit,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onPickClock: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.container
    val version by container.widgets.providersVersion.collectAsState()
    var groups by remember { mutableStateOf<List<ProviderGroup>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(version) {
        groups = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val density = context.resources.displayMetrics.densityDpi
            container.widgets.providers(container.apps.profiles())
                .groupBy { it.provider.packageName to it.profile }
                .map { (key, list) ->
                    val appInfo = runCatching { pm.getApplicationInfo(key.first, 0) }.getOrNull()
                    val label = appInfo?.loadLabel(pm)?.toString() ?: key.first
                    val icon = runCatching { appInfo?.loadIcon(pm)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
                    ProviderGroup(label, icon, list.sortedBy { it.loadLabel(pm) })
                }
                .sortedBy { Classifier.normalize(it.appLabel) }
                .also { density.hashCode() }
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = Modifier.testTag("widget_picker")) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Widgets", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Buscar widgets") },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
        val list = groups
        val filtered = remember(list, query) {
            val q = Classifier.normalize(query.trim())
            if (q.isEmpty()) list.orEmpty() else list.orEmpty().filter { Classifier.normalize(it.appLabel).contains(q) }
        }
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            if (query.isBlank()) item {
                WidgetGroupHeader("Minimal Launcher", null)
                Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    WidgetCard(label = "Reloj y fecha", size = "4 × 1", preview = null, onClick = onPickClock)
                }
            }
            if (list == null) item {
                Text("Cargando…", modifier = Modifier.padding(20.dp))
            } else if (filtered.isEmpty() && query.isNotBlank()) item {
                Text("Sin resultados", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(filtered, key = { it.appLabel + it.providers.first().provider.flattenToString() }) { group ->
                WidgetGroupHeader(group.appLabel, group.appIcon)
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(group.providers, key = { it.provider.flattenToString() }) { info ->
                        val pm = context.packageManager
                        val preview by produceDrawable(info)
                        WidgetCard(
                            label = remember(info) { info.loadLabel(pm) },
                            size = sizeLabel(info, context.resources.displayMetrics.density),
                            preview = preview,
                            onClick = { onPick(info) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun produceDrawable(info: AppWidgetProviderInfo): androidx.compose.runtime.State<ImageBitmap?> {
    val context = LocalContext.current
    val state = remember(info) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(info) {
        state.value = withContext(Dispatchers.IO) {
            val density = context.resources.displayMetrics.densityDpi
            val d: Drawable? = runCatching { info.loadPreviewImage(context, density) }.getOrNull()
                ?: runCatching { info.loadIcon(context, density) }.getOrNull()
            d?.let { runCatching { val w = it.intrinsicWidth.coerceIn(48, 720); val h = it.intrinsicHeight.coerceIn(48, 720); it.toBitmap(w, h).asImageBitmap() }.getOrNull() }
        }
    }
    return state
}

private fun sizeLabel(info: AppWidgetProviderInfo, density: Float): String {
    if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) return "${info.targetCellWidth} × ${info.targetCellHeight}"
    val w = ((info.minWidth / density + 30) / 80).toInt().coerceAtLeast(1)
    val h = ((info.minHeight / density + 30) / 100).toInt().coerceAtLeast(1)
    return "$w × $h"
}

@Composable
private fun WidgetGroupHeader(label: String, icon: ImageBitmap?) {
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Image(BitmapPainter(icon), null, Modifier.size(28.dp))
        } else {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WidgetCard(label: String, size: String, preview: ImageBitmap?, onClick: () -> Unit) {
    Column(Modifier.width(168.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(BitmapPainter(preview), null, Modifier.padding(10.dp), contentScale = ContentScale.Fit)
            } else {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        Text(size, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
    }
}

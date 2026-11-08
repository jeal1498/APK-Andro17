package com.minimal.launcher

import android.appwidget.AppWidgetManager
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.lifecycleScope
import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.ui.theme.MinimalTheme
import kotlinx.coroutines.launch

/**
 * Handles apps asking to pin a shortcut or widget to the home screen
 * (ShortcutManager.requestPinShortcut / AppWidgetManager.requestPinAppWidget).
 */
class PinItemActivity : ComponentActivity() {

    private var request: LauncherApps.PinItemRequest? = null
    private var pendingWidgetId = -1

    private val bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) acceptWidget() else {
            container.widgets.deleteId(pendingWidgetId); finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launcherApps = getSystemService(LauncherApps::class.java)
        val req = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        if (req == null || !req.isValid) { finish(); return }
        request = req
        val label = when (req.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> req.shortcutInfo?.shortLabel?.toString() ?: "Acceso directo"
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> req.getAppWidgetProviderInfo(this)?.loadLabel(packageManager) ?: "Widget"
            else -> { finish(); return }
        }
        setContent {
            MinimalTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Añadir a la pantalla de inicio") },
                    text = { Text(label) },
                    confirmButton = { TextButton(onClick = ::accept) { Text("Añadir") } },
                    dismissButton = { TextButton(onClick = { finish() }) { Text("Cancelar") } },
                )
            }
        }
    }

    private fun accept() {
        val req = request ?: return finish()
        if (req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val info = req.shortcutInfo ?: return finish()
            if (!req.accept()) return finish()
            lifecycleScope.launch {
                container.home.addToDesktop(
                    HomeItemEntity(
                        container = HomeItemEntity.CONTAINER_DESKTOP, type = HomeItemEntity.TYPE_SHORTCUT,
                        packageName = info.`package`, shortcutId = info.id,
                        title = info.shortLabel?.toString(), userSerial = container.apps.serialOf(info.userHandle),
                    ),
                    0,
                )
                finish()
            }
        } else {
            val info = req.getAppWidgetProviderInfo(this) ?: return finish()
            pendingWidgetId = container.widgets.allocateId()
            if (container.widgets.bindIfAllowed(pendingWidgetId, info)) acceptWidget()
            else bindLauncher.launch(container.widgets.bindIntent(pendingWidgetId, info))
        }
    }

    private fun acceptWidget() {
        val req = request ?: return finish()
        val ok = req.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) })
        if (!ok) { container.widgets.deleteId(pendingWidgetId); return finish() }
        val info = req.getAppWidgetProviderInfo(this)
        lifecycleScope.launch {
            val s = container.settings.current()
            val (sx, sy) = if (info != null && Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0) {
                info.targetCellWidth.coerceAtMost(s.homeColumns) to info.targetCellHeight.coerceAtMost(s.homeRows)
            } else 2 to 2
            container.home.addToDesktop(
                HomeItemEntity(container = HomeItemEntity.CONTAINER_DESKTOP, type = HomeItemEntity.TYPE_WIDGET, widgetId = pendingWidgetId, spanX = sx, spanY = sy),
                0,
            )
            finish()
        }
    }
}

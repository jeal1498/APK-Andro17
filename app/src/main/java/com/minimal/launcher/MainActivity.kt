package com.minimal.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.minimal.launcher.home.HomeScreen
import com.minimal.launcher.home.HostActions
import com.minimal.launcher.settings.SettingsActivity
import com.minimal.launcher.ui.components.LocalAppActions
import com.minimal.launcher.ui.components.LocalIconCache
import com.minimal.launcher.ui.rememberDeviceProfile
import com.minimal.launcher.ui.theme.MinimalTheme
import kotlinx.coroutines.launch

/**
 * The HOME activity. Declares configChanges so rotation / resize / theme changes are handled by
 * Compose without recreating the activity (keeps widget views and state alive, like stock launchers).
 */
class MainActivity : ComponentActivity(), HostActions {

    private val vm: LauncherViewModel by viewModels()
    private val widgets get() = container.widgets

    private lateinit var bindLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        registerWidgetLaunchers()

        lifecycleScope.launch {
            vm.messages.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
        }

        setContent {
            val settings by vm.settings.collectAsState()
            val s = settings ?: return@setContent
            LaunchedEffect(s.allowRotation) { applyOrientation(s.allowRotation) }
            MinimalTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor) {
                CompositionLocalProvider(
                    LocalIconCache provides container.icons,
                    LocalAppActions provides container.actions,
                ) {
                    val profile = rememberDeviceProfile(s)
                    Box(Modifier.fillMaxSize()) {
                        if (s.homeColumns > 0 && s.homeRows > 0) HomeScreen(vm, s, profile, this@MainActivity)
                    }
                }
            }
        }
    }

    private fun applyOrientation(allowRotation: Boolean) {
        // Large screens always rotate (Android 16+ ignores orientation locks there anyway).
        val tablet = resources.configuration.smallestScreenWidthDp >= 600
        requestedOrientation = if (allowRotation || tablet) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgets.stopListening()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }

    // ---- Widget add flow: allocate → bind (permission) → configure (optional) → place ----------

    private fun registerWidgetLaunchers() {
        bindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) configureOrPlace() else vm.cancelPendingWidget()
        }
    }

    /*
     * AppWidgetHost.startAppWidgetConfigureActivityForResult is the only public API that can start a
     * provider's configuration activity for every profile, and it reports through onActivityResult.
     */
    @Deprecated("Required by AppWidgetHost.startAppWidgetConfigureActivityForResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) {
            if (resultCode == RESULT_OK) vm.placePendingWidget() else vm.cancelPendingWidget()
        }
    }

    override fun addWidget(info: AppWidgetProviderInfo, page: Int, cellWidthDp: Float, cellHeightDp: Float) {
        val s = vm.settings.value ?: return
        val id = widgets.allocateId()
        val (spanX, spanY) = widgets.defaultSpan(info, cellWidthDp, cellHeightDp, s.homeColumns, s.homeRows)
        vm.pendingWidget = LauncherViewModel.PendingWidget(id, info, page, spanX, spanY)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
        }
        if (widgets.bindIfAllowed(id, info, options)) {
            configureOrPlace()
        } else {
            try {
                bindLauncher.launch(widgets.bindIntent(id, info))
            } catch (e: ActivityNotFoundException) {
                vm.cancelPendingWidget()
                Toast.makeText(this, "No se puede añadir este widget", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureOrPlace() {
        val pending = vm.pendingWidget ?: return
        if (!widgets.needsConfiguration(pending.info)) {
            vm.placePendingWidget()
            return
        }
        try {
            widgets.startConfigure(this, pending.appWidgetId, REQUEST_CONFIGURE)
        } catch (e: Exception) {
            // Configuration activity not reachable: the widget cannot be set up correctly.
            vm.cancelPendingWidget()
            Toast.makeText(this, "No se pudo configurar el widget", Toast.LENGTH_SHORT).show()
        }
    }

    override fun reconfigureWidget(appWidgetId: Int) {
        runCatching { widgets.startConfigure(this, appWidgetId, REQUEST_RECONFIGURE) }
            .onFailure { Toast.makeText(this, "Este widget no se puede configurar", Toast.LENGTH_SHORT).show() }
    }

    private companion object {
        const val REQUEST_CONFIGURE = 41
        const val REQUEST_RECONFIGURE = 42
    }

    override fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun openWallpaperPicker() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Fondo de pantalla"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay selector de fondos disponible", Toast.LENGTH_SHORT).show()
        }
    }
}

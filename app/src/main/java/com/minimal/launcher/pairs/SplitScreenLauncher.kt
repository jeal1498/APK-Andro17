package com.minimal.launcher.pairs

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.minimal.launcher.applications.AppActions
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.categories.AppPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Opens a pair in split screen: app A → [LauncherAccessibilityService.toggleSplitScreen] → app B with
 * FLAG_ACTIVITY_LAUNCH_ADJACENT. A third app opens as a floating (freeform) window when the device
 * supports it. Timings are conservative because each step waits for a system animation.
 */
class SplitScreenLauncher(
    private val context: Context,
    private val actions: AppActions,
    private val scope: CoroutineScope,
    private val onLaunched: (AppInfo) -> Unit,
) {
    enum class Result { STARTED, NEEDS_PERMISSION, UNSUPPORTED_APP }

    fun launch(pair: AppPair): Result {
        val service = LauncherAccessibilityService.instance
            ?: return Result.NEEDS_PERMISSION
        val unsupported = pair.apps.take(2).firstOrNull { supportsMultiWindow(it) == false }
        if (unsupported != null) {
            toast("${unsupported.label} no admite pantalla dividida. Se abrirá solo ${pair.apps.first().label}.")
            actions.launch(pair.apps.first())
            return Result.UNSUPPORTED_APP
        }
        val (a, b) = pair.apps
        val third = pair.apps.getOrNull(2)
        scope.launch(Dispatchers.Main) {
            actions.launch(a)
            // The split action applies to the focused task: wait until app A (not this launcher) has focus.
            var waited = 0L
            while (waited < FOCUS_TIMEOUT_MS && service.focusedPackage() != a.packageName) {
                delay(POLL_MS)
                waited += POLL_MS
            }
            delay(POLL_MS)
            var active = service.isSplitScreenActive() == true
            var attempt = 0
            while (!active && attempt < 3) {
                attempt++
                service.toggleSplitScreen()
                delay(FOCUS_MS)
                active = service.isSplitScreenActive() == true
            }
            if (!active) Log.w(TAG, "split divider not detected after $attempt attempts; launching B adjacent anyway")
            startAdjacent(service, b)
            if (third != null) {
                delay(STEP_MS)
                if (supportsFreeform()) startFloating(service, third)
                else toast("Tu dispositivo no admite ventanas flotantes: ${third.label} no se abrió")
            }
            delay(CHECK_MS)
            if (service.isSplitScreenActive() == false) {
                toast("No se pudo dividir la pantalla. Puede que ${a.label} o ${b.label} no admita multiventana.")
            }
        }
        return Result.STARTED
    }

    private fun launchIntent(app: AppInfo, flags: Int) = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or flags)

    /** Started from the accessibility service so Android lets it open while the launcher is in the background. */
    private fun startAdjacent(service: Context, app: AppInfo) {
        if (app.user != Process.myUserHandle()) {
            // Work profile: only LauncherApps can start it (no adjacent flag available).
            actions.launch(app)
            return
        }
        runCatching {
            service.startActivity(launchIntent(app, Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
            onLaunched(app)
        }.onFailure {
            Log.w(TAG, "adjacent launch failed", it)
            actions.launch(app)
        }
    }

    private fun startFloating(service: Context, app: AppInfo) {
        val m = context.resources.displayMetrics
        val bounds = Rect((m.widthPixels * 0.45f).toInt(), (m.heightPixels * 0.30f).toInt(), (m.widthPixels * 0.95f).toInt(), (m.heightPixels * 0.70f).toInt())
        runCatching {
            val options = ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle()
            if (app.user == Process.myUserHandle()) {
                service.startActivity(launchIntent(app, Intent.FLAG_ACTIVITY_MULTIPLE_TASK), options)
                onLaunched(app)
            } else {
                actions.launch(app, bounds, options)
            }
        }.onFailure { toast("No se pudo abrir ${app.label} como ventana flotante") }
    }

    fun supportsFreeform(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT) ||
            runCatching { Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) != 0 }.getOrDefault(false)

    /**
     * Best effort: reads the activity's resize mode (not public API). null = unknown, then the result is
     * checked after launching. Android 12+ ignores the restriction on large screens, so only phones warn.
     */
    fun supportsMultiWindow(app: AppInfo): Boolean? {
        if (context.resources.configuration.smallestScreenWidthDp >= 600) return null
        return runCatching {
            val info = context.packageManager.getActivityInfo(app.component, 0)
            val mode = ActivityInfo::class.java.getField("resizeMode").getInt(info)
            mode != 0 // RESIZE_MODE_UNRESIZEABLE
        }.getOrNull()
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    private companion object {
        const val TAG = "SplitScreenLauncher"
        const val STEP_MS = 650L
        const val FOCUS_MS = 1000L
        const val POLL_MS = 150L
        const val FOCUS_TIMEOUT_MS = 4000L
        const val CHECK_MS = 1200L
    }
}

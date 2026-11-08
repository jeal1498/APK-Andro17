package com.minimal.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import android.util.SizeF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

/** Lifecycle and metadata of real Android widgets hosted by the launcher. */
class WidgetManager(private val context: Context) {

    val host = LauncherWidgetHost(context)
    val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    /** Incremented when providers change (installs, updates) so UI can refresh. */
    private val _providersVersion = MutableStateFlow(0)
    val providersVersion: StateFlow<Int> = _providersVersion.asStateFlow()

    init {
        host.onProvidersChangedListener = { _providersVersion.value++ }
    }

    private var listening = false

    fun startListening() {
        if (listening) return
        runCatching { host.startListening() }.onSuccess { listening = true }
            .onFailure { Log.w(TAG, "startListening failed", it) }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    fun providers(): List<AppWidgetProviderInfo> = runCatching { appWidgetManager.installedProviders }
        .getOrDefault(emptyList())

    /** Providers for every profile (personal + work). */
    fun providers(users: List<UserHandle>): List<AppWidgetProviderInfo> = users.flatMap { user ->
        runCatching { appWidgetManager.getInstalledProvidersForProfile(user) }.getOrDefault(emptyList())
    }.distinctBy { it.provider.flattenToString() + it.profile.hashCode() }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = runCatching { appWidgetManager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /** Binds directly when the user already granted the permission; otherwise the caller must launch [bindIntent]. */
    fun bindIfAllowed(appWidgetId: Int, info: AppWidgetProviderInfo, options: Bundle? = null): Boolean =
        runCatching {
            if (info.profile == android.os.Process.myUserHandle()) appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider, options)
            else appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, options)
        }.getOrDefault(false)

    fun bindIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)

    /** True when the provider declares a configuration activity that must run before placement. */
    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= 31) {
            val optional = info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
            if (optional) return false
        }
        return true
    }

    fun isReconfigurable(info: AppWidgetProviderInfo?): Boolean =
        info?.configure != null && Build.VERSION.SDK_INT >= 28 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

    /**
     * Official configuration entry point: the host starts the provider's configure activity
     * (works for any user profile and non-exported activities). The result arrives in
     * Activity.onActivityResult with [requestCode].
     */
    fun startConfigure(activity: android.app.Activity, appWidgetId: Int, requestCode: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, null)
    }

    /** Releases ids allocated by this host that are no longer placed (e.g. after a crash mid-flow). */
    fun cleanupOrphans(placedIds: Collection<Int>) {
        val allocated = runCatching { host.appWidgetIds }.getOrNull() ?: return
        allocated.filter { it !in placedIds }.forEach { deleteId(it) }
    }

    /** Initial span based on the provider's declared size and the current cell size. */
    fun defaultSpan(info: AppWidgetProviderInfo, cellWidthDp: Float, cellHeightDp: Float, cols: Int, rows: Int): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return info.targetCellWidth.coerceIn(1, cols) to info.targetCellHeight.coerceIn(1, rows)
        }
        val density = context.resources.displayMetrics.density
        return spanFor(info.minWidth / density, cellWidthDp, cols) to spanFor(info.minHeight / density, cellHeightDp, rows)
    }

    /** Minimum span allowed when resizing. */
    fun minSpan(info: AppWidgetProviderInfo?, cellWidthDp: Float, cellHeightDp: Float, cols: Int, rows: Int): Pair<Int, Int> {
        if (info == null) return 1 to 1
        val density = context.resources.displayMetrics.density
        val w = if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth
        val h = if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight
        return spanFor(w / density, cellWidthDp, cols) to spanFor(h / density, cellHeightDp, rows)
    }

    /** Maximum span allowed when resizing (API 31 maxResize*, else the grid). */
    fun maxSpan(info: AppWidgetProviderInfo?, cellWidthDp: Float, cellHeightDp: Float, cols: Int, rows: Int): Pair<Int, Int> {
        if (info == null || Build.VERSION.SDK_INT < 31) return cols to rows
        val density = context.resources.displayMetrics.density
        val mw = if (info.maxResizeWidth > 0) spanFor(info.maxResizeWidth / density, cellWidthDp, cols, floor = true) else cols
        val mh = if (info.maxResizeHeight > 0) spanFor(info.maxResizeHeight / density, cellHeightDp, rows, floor = true) else rows
        return mw to mh
    }

    private fun spanFor(sizeDp: Float, cellDp: Float, max: Int, floor: Boolean = false): Int {
        if (cellDp <= 0f) return 1
        val raw = sizeDp / cellDp
        val span = if (floor) raw.toInt() else ceil(raw - 0.15f).toInt()
        return span.coerceIn(1, max.coerceAtLeast(1))
    }

    /** Informs the provider about the size it is rendered at (needed for responsive widgets). */
    fun reportSize(view: LauncherAppWidgetHostView, widthDp: Int, heightDp: Int) {
        if (view.lastReportedSize == widthDp to heightDp) return
        view.lastReportedSize = widthDp to heightDp
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
            } else {
                @Suppress("DEPRECATION") // Only API for < 31.
                view.updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
            }
        }
    }

    fun canResize(info: AppWidgetProviderInfo?): Boolean = info != null && info.resizeMode != AppWidgetProviderInfo.RESIZE_NONE

    companion object {
        private const val TAG = "WidgetManager"
    }
}

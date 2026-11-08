package com.minimal.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

/** The launcher's [AppWidgetHost]. Lives as long as the process; listening follows the activity's start/stop. */
class LauncherWidgetHost(context: Context) : AppWidgetHost(context.applicationContext, HOST_ID) {

    var onProvidersChangedListener: (() -> Unit)? = null

    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LauncherAppWidgetHostView(context)

    override fun onProvidersChanged() {
        onProvidersChangedListener?.invoke()
    }

    companion object {
        const val HOST_ID = 0x4d4c // "ML"
    }
}

package com.minimal.launcher.widgets

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.minimal.launcher.container
import com.minimal.launcher.home.MissingWidget

/**
 * Hosts a real [android.appwidget.AppWidgetHostView] inside Compose. Long-press / drag are reported
 * by the view itself because the widget's RemoteViews consume touch events.
 */
@Composable
fun WidgetView(
    appWidgetId: Int,
    widthDp: Int,
    heightDp: Int,
    onLongPress: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: (cancelled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val widgets = context.container.widgets
    val version by widgets.providersVersion.collectAsState()
    val info = remember(appWidgetId, version) { widgets.info(appWidgetId) }
    if (info == null) {
        MissingWidget(modifier)
        return
    }
    val longPress by rememberUpdatedState(onLongPress)
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            (widgets.host.createView(ctx, appWidgetId, info) as LauncherAppWidgetHostView).apply {
                listener = object : LauncherAppWidgetHostView.Listener {
                    override fun onLongPress() = longPress()
                    override fun onDrag(dx: Float, dy: Float, rawX: Float, rawY: Float) = drag(dx, dy)
                    override fun onDragEnd(cancelled: Boolean) = dragEnd(cancelled)
                }
            }
        },
        update = { view -> widgets.reportSize(view, widthDp, heightDp) },
        onRelease = { view -> view.listener = null },
    )
}

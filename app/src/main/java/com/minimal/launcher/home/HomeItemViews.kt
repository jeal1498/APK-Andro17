package com.minimal.launcher.home

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.minimal.launcher.applications.AppInfo
import com.minimal.launcher.container
import com.minimal.launcher.data.db.HomeItemEntity
import com.minimal.launcher.ui.components.AppIconImage
import com.minimal.launcher.ui.components.IconBitmap
import com.minimal.launcher.ui.components.LocalAppActions
import com.minimal.launcher.ui.components.LocalIconCache
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Icon of a pinned shortcut, loaded through LauncherApps (requires being the default launcher). */
@Composable
fun ShortcutIcon(item: HomeItemEntity, size: Dp) {
    val context = LocalContext.current
    val actions = LocalAppActions.current
    val cache = LocalIconCache.current
    val key = "shortcut:${item.packageName}:${item.shortcutId}"
    var bitmap by remember(key) { mutableStateOf<ImageBitmap?>(cache.peek(key)) }
    LaunchedEffect(key) {
        if (bitmap != null) return@LaunchedEffect
        val user = context.container.apps.userForSerial(item.userSerial) ?: return@LaunchedEffect
        val info = actions.findShortcut(item.packageName ?: return@LaunchedEffect, item.shortcutId ?: return@LaunchedEffect, user)
        bitmap = info?.let { cache.shortcutIcon(key, it) }
    }
    if (bitmap == null) {
        // Fallback: icon of the owning app.
        val app = context.container.apps.findByPackage(item.packageName.orEmpty(), item.userSerial)
        if (app != null) AppIconImage(app.key, size) else IconBitmap(null, size)
    } else {
        IconBitmap(bitmap, size)
    }
}

/** Folder preview: rounded surface with up to four mini icons, like stock Android. */
@Composable
fun FolderIcon(children: List<HomeItemEntity>, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        val mini = size * 0.36f
        Column(verticalArrangement = Arrangement.spacedBy(size * 0.04f)) {
            children.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(size * 0.04f)) {
                    row.forEach { child ->
                        when (child.type) {
                            HomeItemEntity.TYPE_SHORTCUT -> ShortcutIcon(child, mini)
                            else -> child.appKey?.let { AppIconImage(it, mini) } ?: IconBitmap(null, mini)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemIcon(item: HomeItemEntity, app: AppInfo?, children: List<HomeItemEntity>, size: Dp) {
    when (item.type) {
        HomeItemEntity.TYPE_APP -> if (app != null) AppIconImage(app.key, size) else IconBitmap(null, size)
        HomeItemEntity.TYPE_SHORTCUT -> ShortcutIcon(item, size)
        HomeItemEntity.TYPE_FOLDER -> FolderIcon(children, size)
    }
}

fun itemLabel(item: HomeItemEntity, app: AppInfo?): String = when (item.type) {
    HomeItemEntity.TYPE_APP -> app?.label.orEmpty()
    HomeItemEntity.TYPE_FOLDER -> item.title ?: "Carpeta"
    else -> item.title.orEmpty()
}

/** Clock + date "glance" widget. Updated by the system TIME_TICK broadcast, no polling. */
@Composable
fun ClockWidget(textColor: Color, shadow: Boolean, compact: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) { now = System.currentTimeMillis() }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        now = System.currentTimeMillis()
        onDispose { context.unregisterReceiver(receiver) }
    }
    val locale: Locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val is24 = DateFormat.is24HourFormat(context)
    val time = remember(now, is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", locale).format(Date(now)) }
    val date = remember(now, locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(Date(now))
            .replaceFirstChar { it.titlecase(locale) }
    }
    val style = MaterialTheme.typography
    val textShadow = if (shadow) androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), blurRadius = 8f) else null
    Column(modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
        Text(
            time,
            style = (if (compact) style.displaySmall else style.displayMedium).copy(fontWeight = FontWeight.Normal, shadow = textShadow),
            color = textColor,
            maxLines = 1,
        )
        Text(
            date,
            style = style.titleMedium.copy(shadow = textShadow, fontSize = if (compact) 14.sp else 16.sp),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun clockIntent(): Intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
fun calendarIntent(): Intent = Intent(Intent.ACTION_VIEW).setData(
    CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(),
)

/** Placeholder for a widget whose provider is gone (uninstalled / unavailable). */
@Composable
fun MissingWidget(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .padding(6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Widget no disponible\nMantén pulsado para quitarlo",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp),
        )
    }
}

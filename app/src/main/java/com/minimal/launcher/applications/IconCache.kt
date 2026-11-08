package com.minimal.launcher.applications

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Memory-bounded icon cache. Icons are rasterised once at a fixed size off the main thread
 * and reused by every screen (home, dock, drawer, folders, search).
 */
class IconCache(private val context: Context, private val apps: AppRepository) {

    private val iconSizePx = (context.resources.displayMetrics.density * ICON_DP).toInt().coerceAtLeast(48)
    private val density = context.resources.displayMetrics.densityDpi

    private val cache = object : LruCache<String, ImageBitmap>(maxBytes()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private fun maxBytes(): Int = (Runtime.getRuntime().maxMemory() / 16).toInt().coerceIn(4 shl 20, 32 shl 20)

    fun peek(key: String): ImageBitmap? = cache.get(key)

    suspend fun appIcon(key: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val info = apps.activityInfo(key) ?: return@withContext null
            val drawable = runCatching { info.getBadgedIcon(density) }.getOrNull() ?: return@withContext null
            rasterize(drawable)?.also { cache.put(key, it) }
        }
    }

    suspend fun shortcutIcon(cacheKey: String, shortcut: ShortcutInfo): ImageBitmap? {
        cache.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val drawable = runCatching { launcherApps.getShortcutBadgedIconDrawable(shortcut, density) }.getOrNull()
                ?: return@withContext null
            rasterize(drawable)?.also { cache.put(cacheKey, it) }
        }
    }

    fun invalidatePackage(packageName: String) {
        cache.snapshot().keys.filter { it.startsWith("$packageName/") || it.startsWith("shortcut:$packageName:") }
            .forEach { cache.remove(it) }
    }

    private fun rasterize(drawable: Drawable): ImageBitmap? = runCatching {
        drawable.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
    }.getOrNull()

    companion object {
        const val ICON_DP = 64
    }
}

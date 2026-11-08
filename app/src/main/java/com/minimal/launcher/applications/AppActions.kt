package com.minimal.launcher.applications

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.util.Log
import android.widget.Toast

/** Thin wrapper over the official launcher APIs for launching apps / shortcuts and opening system screens. */
class AppActions(
    private val context: Context,
    /** Called after every successful app launch (usage learning). */
    private val onLaunched: (AppInfo) -> Unit = {},
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun launch(app: AppInfo, bounds: Rect? = null, options: Bundle? = null) {
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, options)
            onLaunched(app)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot launch ${app.key}", e)
            toast("No se pudo abrir ${app.label}")
        }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
            .onFailure { toast("No se pudo abrir la información de la app") }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("No se puede desinstalar esta app")
        }
    }

    /** App shortcuts are only available to the default launcher (official restriction). */
    fun hasShortcutPermission(): Boolean = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcutsFor(packageName: String, user: UserHandle): List<ShortcutInfo> {
        if (!hasShortcutPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST,
            )
        return runCatching { launcherApps.getShortcuts(query, user).orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.isEnabled }
            .sortedBy { it.rank }
            .take(5)
    }

    fun findShortcut(packageName: String, shortcutId: String, user: UserHandle): ShortcutInfo? {
        if (!hasShortcutPermission()) return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setShortcutIds(listOf(shortcutId))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        return runCatching { launcherApps.getShortcuts(query, user)?.firstOrNull() }.getOrNull()
    }

    fun startShortcut(packageName: String, shortcutId: String, user: UserHandle, bounds: Rect? = null) {
        try {
            launcherApps.startShortcut(packageName, shortcutId, bounds, null, user)
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start shortcut $packageName/$shortcutId", e)
            toast(if (hasShortcutPermission()) "Acceso directo no disponible" else "Establece Minimal Launcher como predeterminado para usar accesos directos")
        }
    }

    /** Keeps a shortcut pinned so it survives when the app removes it from its dynamic list. */
    fun pinShortcut(packageName: String, shortcutId: String, user: UserHandle, pin: Boolean) {
        if (!hasShortcutPermission()) return
        runCatching {
            val query = LauncherApps.ShortcutQuery().setPackage(packageName)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val pinned = launcherApps.getShortcuts(query, user).orEmpty().map { it.id }.toMutableSet()
            if (pin) pinned += shortcutId else pinned -= shortcutId
            launcherApps.pinShortcuts(packageName, pinned.toList(), user)
        }.onFailure { Log.w(TAG, "pinShortcuts failed", it) }
    }

    fun startIntent(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            toast("No hay ninguna app para esta acción")
        }
    }

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    companion object {
        private const val TAG = "AppActions"
        val myUser: UserHandle get() = Process.myUserHandle()
    }
}

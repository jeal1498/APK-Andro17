package com.minimal.launcher.pairs

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Global actions only (split screen, lock screen, notifications). It does not read screen content:
 * [isSplitScreenActive] just checks whether a split-screen divider window exists.
 */
class LauncherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    fun toggleSplitScreen(): Boolean = performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)

    fun lockScreen(): Boolean = Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)

    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    fun isSplitScreenActive(): Boolean? = runCatching {
        windows.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER } ||
            windows.count { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isSplitCandidate() } >= 2
    }.getOrNull()

    private fun AccessibilityWindowInfo.isSplitCandidate(): Boolean {
        val r = android.graphics.Rect().also { getBoundsInScreen(it) }
        val m = resources.displayMetrics
        return r.width() < m.widthPixels * 0.9f || r.height() < m.heightPixels * 0.9f
    }

    companion object {
        @Volatile
        var instance: LauncherAccessibilityService? = null
            private set

        fun component(context: Context) = ComponentName(context, LauncherAccessibilityService::class.java)

        /** True when the user enabled the service in the system settings (even if not bound yet). */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val me = component(context)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

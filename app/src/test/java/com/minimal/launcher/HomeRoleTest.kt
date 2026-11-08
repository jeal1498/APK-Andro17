package com.minimal.launcher

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeRoleTest {
    private val context = ApplicationProvider.getApplicationContext<LauncherApplication>()
    private val pm = context.packageManager

    @Test fun mainActivityIsAHomeActivity() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(context.packageName)
        val matches = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(listOf(MainActivity::class.java.name), matches.map { it.activityInfo.name })
        val info = pm.getActivityInfo(android.content.ComponentName(context, MainActivity::class.java), 0)
        assertEquals(android.content.pm.ActivityInfo.LAUNCH_SINGLE_TASK, info.launchMode)
        // Rotation/resize handled in-place so widgets are not recreated.
        val needed = android.content.pm.ActivityInfo.CONFIG_ORIENTATION or android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE or
            android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or android.content.pm.ActivityInfo.CONFIG_UI_MODE
        assertEquals(needed, info.configChanges and needed)
    }

    @Test fun pinRequestsAreHandled() {
        listOf("android.content.pm.action.CONFIRM_PIN_SHORTCUT", "android.content.pm.action.CONFIRM_PIN_APPWIDGET").forEach { action ->
            val r = pm.queryIntentActivities(Intent(action).setPackage(context.packageName), 0)
            assertEquals(action, PinItemActivity::class.java.name, r.single().activityInfo.name)
        }
    }

    @Test fun onlyMinimalPermissionsAreRequested() {
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertTrue("Unexpected permissions: $requested", requested.all {
            it == "android.permission.REQUEST_DELETE_PACKAGES" || it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        })
        assertTrue("QUERY_ALL_PACKAGES must not be used", "android.permission.QUERY_ALL_PACKAGES" !in requested)
    }
}

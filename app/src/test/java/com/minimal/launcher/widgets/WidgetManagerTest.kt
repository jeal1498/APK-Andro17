package com.minimal.launcher.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.minimal.launcher.LauncherApplication
import com.minimal.launcher.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
class WidgetManagerTest {
    private val context = ApplicationProvider.getApplicationContext<LauncherApplication>()

    private fun provider(minWdp: Int, minHdp: Int, configure: Boolean = false) = AppWidgetProviderInfo().apply {
        val d = context.resources.displayMetrics.density
        provider = ComponentName("com.example.weather", "com.example.weather.Widget")
        minWidth = (minWdp * d).toInt(); minHeight = (minHdp * d).toInt()
        minResizeWidth = (40 * d).toInt(); minResizeHeight = (40 * d).toInt()
        resizeMode = AppWidgetProviderInfo.RESIZE_BOTH
        // Hidden field set by the system service on devices.
        org.robolectric.util.ReflectionHelpers.setField(this, "providerInfo", android.content.pm.ActivityInfo().apply {
            packageName = "com.example.weather"; name = "com.example.weather.Widget"
            applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "com.example.weather"; uid = Process.myUid() }
        })
        if (configure) this.configure = ComponentName("com.example.weather", "com.example.weather.Config")
    }

    @Test fun bindFlowUsesRealAppWidgetApis() {
        val wm = WidgetManager(context)
        val info = provider(180, 110)
        val shadow = shadowOf(AppWidgetManager.getInstance(context))
        shadow.addInstalledProvider(info)
        shadow.addInstalledProvidersForProfile(Process.myUserHandle(), info)
        assertTrue(wm.providers(listOf(Process.myUserHandle())).any { it.provider == info.provider })

        val id = wm.allocateId()
        shadow.setAllowedToBindAppWidgets(false)
        assertFalse(wm.bindIfAllowed(id, info))
        val intent = wm.bindIntent(id, info)
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_BIND, intent.action)
        assertEquals(id, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))

        shadow.setAllowedToBindAppWidgets(true)
        assertTrue(wm.bindIfAllowed(id, info))
        assertEquals(info.provider, wm.info(id)?.provider)
        assertFalse(wm.needsConfiguration(info))
        assertTrue(wm.needsConfiguration(provider(100, 100, configure = true)))
        assertTrue(wm.canResize(info))
    }

    @Test fun spansDerivedFromProviderSizeAndCellSize() {
        val wm = WidgetManager(context)
        // 4x5 grid on a phone: ~100dp x ~120dp cells.
        assertEquals(2 to 1, wm.defaultSpan(provider(180, 110), 100f, 120f, 4, 5))
        assertEquals(4 to 2, wm.defaultSpan(provider(400, 230), 100f, 120f, 4, 5))
        assertEquals(4 to 5, wm.defaultSpan(provider(900, 900), 100f, 120f, 4, 5)) // clamped
        assertEquals(1 to 1, wm.minSpan(provider(180, 110), 100f, 120f, 4, 5))
    }

    @Test fun hostCreatesLauncherHostViewsInsideTheActivity() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val wm = WidgetManager(context)
        val info = provider(180, 110)
        shadowOf(AppWidgetManager.getInstance(context)).apply { addInstalledProvider(info); setAllowedToBindAppWidgets(true) }
        val id = wm.allocateId()
        wm.bindIfAllowed(id, info)
        val view = wm.host.createView(activity, id, info)
        assertTrue(view is LauncherAppWidgetHostView)
        wm.reportSize(view as LauncherAppWidgetHostView, 200, 120)
        assertEquals(200 to 120, view.lastReportedSize)
        wm.startListening(); wm.stopListening()
    }
}

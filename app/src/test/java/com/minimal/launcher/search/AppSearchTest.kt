package com.minimal.launcher.search

import android.content.ComponentName
import android.os.UserHandle
import com.minimal.launcher.applications.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSearchTest {
    private fun app(label: String, pkg: String) = AppInfo("$pkg/.M#0", label, ComponentName(pkg, "$pkg.M"), UserHandle.getUserHandleForUid(0), 0, -1, 0, false)
    private val apps = listOf(app("Cámara", "com.cam"), app("Google Maps", "com.google.maps"), app("Mapas Offline", "org.osm"), app("Calculadora", "com.calc"))

    @Test fun ranksPrefixFirstAndIgnoresAccents() {
        assertEquals(listOf("Mapas Offline", "Google Maps"), AppSearch.search(apps, "map").map { it.label })
        assertEquals(listOf("Cámara"), AppSearch.search(apps, "CAMA").map { it.label })
    }

    @Test fun emptyQueryReturnsNothingAndPackageMatchesNeedThreeChars() {
        assertTrue(AppSearch.search(apps, "  ").isEmpty())
        assertEquals(listOf("Mapas Offline"), AppSearch.search(apps, "osm").map { it.label })
    }
}

package com.minimal.launcher.applications

import android.content.pm.LauncherApps
import androidx.test.core.app.ApplicationProvider
import com.minimal.launcher.LauncherApplication
import com.minimal.launcher.TestApps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class AppDetectionTest {
    private val context = ApplicationProvider.getApplicationContext<LauncherApplication>()

    @Test fun detectsInstalledLaunchableAppsSortedByLabel() = runBlocking {
        TestApps.installSample(context)
        val repo = AppRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        repo.reloadAll()
        // Robolectric's LauncherActivityInfo resolves labels to the package name; on devices it is the app label.
        assertEquals(TestApps.sample.map { it.first }.toSet(), repo.apps.value.map { it.packageName }.toSet())
        assertTrue(repo.apps.value.all { it.label.isNotBlank() })
        assertEquals(repo.apps.value.map { it.label }.sortedBy { it.lowercase() }, repo.apps.value.map { it.label })
        val wa = repo.apps.value.first { it.packageName == "com.whatsapp" }
        assertEquals(AppInfo.keyOf(TestApps.component("com.whatsapp"), wa.userSerial), wa.key)
        assertTrue(repo.loaded.value)
    }

    @Test fun ownPackageIsNotListed() = runBlocking {
        TestApps.install(context, context.packageName, "Minimal Launcher")
        TestApps.install(context, "com.whatsapp", "WhatsApp")
        val repo = AppRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        repo.reloadAll()
        assertEquals(listOf("com.whatsapp"), repo.apps.value.map { it.packageName })
    }

    @Test fun reactsToInstallAndUninstallCallbacks() = runBlocking {
        TestApps.install(context, "com.whatsapp", "WhatsApp")
        val repo = AppRepository(context, CoroutineScope(SupervisorJob() + Dispatchers.IO))
        repo.start()
        withTimeout(5000) { repo.apps.first { it.size == 1 } }

        TestApps.install(context, "com.spotify.music", "Spotify")
        val launcherApps = shadowOf(context.getSystemService(LauncherApps::class.java))
        launcherApps.notifyPackageAdded("com.spotify.music")
        ShadowLooper.idleMainLooper()
        withTimeout(5000) { repo.apps.first { list -> list.any { it.packageName == "com.spotify.music" } } }

        val removed = async { withTimeout(5000) { repo.removedPackages.first() } }
        kotlinx.coroutines.delay(100) // let the collector subscribe
        launcherApps.notifyPackageRemoved("com.spotify.music")
        ShadowLooper.idleMainLooper()
        // The removal is propagated so home items / caches are cleaned (AppContainer).
        assertEquals("com.spotify.music", removed.await().first)
    }
}

package com.minimal.launcher.settings

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.minimal.launcher.container
import com.minimal.launcher.data.prefs.LauncherSettings
import com.minimal.launcher.ui.components.LocalAppActions
import com.minimal.launcher.ui.components.LocalIconCache
import com.minimal.launcher.ui.theme.MinimalTheme

class SettingsActivity : ComponentActivity() {

    val isDefaultLauncher = mutableStateOf(false)

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshDefault()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by container.settings.settings.collectAsState(initial = null)
            val s: LauncherSettings = settings ?: return@setContent
            MinimalTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor) {
                CompositionLocalProvider(LocalIconCache provides container.icons, LocalAppActions provides container.actions) {
                    SettingsNavHost(
                        settings = s,
                        container = container,
                        scope = lifecycleScope,
                        isDefaultLauncher = isDefaultLauncher.value,
                        onRequestDefault = ::requestDefaultLauncher,
                        onClose = ::finish,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDefault()
    }

    private fun refreshDefault() {
        isDefaultLauncher.value = isDefaultHome()
    }

    private fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            return rm.isRoleAvailable(RoleManager.ROLE_HOME) && rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName == packageName
    }

    /** Official flows: RoleManager (Android 10+) or the system "Default home app" screen. */
    private fun requestDefaultLauncher() {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                runCatching { roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)); return }
            }
        }
        runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
}

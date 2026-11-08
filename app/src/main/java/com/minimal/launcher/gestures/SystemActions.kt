package com.minimal.launcher.gestures

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.widget.Toast
import com.minimal.launcher.pairs.LauncherAccessibilityService

/** System actions available to gestures. Each returns false when it could not be done. */
class SystemActions(private val context: Context) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private var torchOn = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) torchOn = enabled
        }
    }

    private val torchId: String? by lazy {
        runCatching {
            camera?.cameraIdList?.firstOrNull { id ->
                camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    init {
        runCatching { camera?.registerTorchCallback(torchCallback, null) }
    }

    fun toggleFlashlight(): Boolean {
        val id = torchId ?: return false.also { toast("Este dispositivo no tiene linterna") }
        return runCatching { camera!!.setTorchMode(id, !torchOn); true }
            .getOrElse { Log.w(TAG, "torch", it); toast("No se pudo usar la linterna"); false }
    }

    /** Accessibility first; otherwise the status bar service (needs EXPAND_STATUS_BAR, a normal permission). */
    fun openNotifications(quickSettings: Boolean = false): Boolean {
        LauncherAccessibilityService.instance?.let { s ->
            if (if (quickSettings) s.openQuickSettings() else s.openNotifications()) return true
        }
        return expandStatusBar(if (quickSettings) "expandSettingsPanel" else "expandNotificationsPanel")
    }

    @SuppressLint("WrongConstant")
    private fun expandStatusBar(method: String): Boolean = runCatching {
        val service = context.getSystemService("statusbar") ?: return false
        service.javaClass.getMethod(method).invoke(service)
        true
    }.getOrElse { Log.w(TAG, "status bar", it); false }

    /** Needs the accessibility service (Android 9+). */
    fun screenOff(): Boolean = LauncherAccessibilityService.instance?.lockScreen() == true

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    private companion object { const val TAG = "SystemActions" }
}

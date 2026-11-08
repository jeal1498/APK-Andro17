package com.minimal.launcher.usage

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches headphones, charging and car mode with system callbacks (no polling, no permissions,
 * nothing leaves the device). "In the car" = car mode, or charging while a car-like Bluetooth audio
 * device is connected.
 */
class ContextMonitor(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val uiMode = context.getSystemService(UiModeManager::class.java)
    private val _state = MutableStateFlow(DeviceContext())
    val state: StateFlow<DeviceContext> = _state.asStateFlow()
    private var started = false

    private val audioCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        runCatching { audio?.registerAudioDeviceCallback(audioCallback, Handler(Looper.getMainLooper())) }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
            addAction(UiModeManager.ACTION_EXIT_CAR_MODE)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        runCatching { ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        refresh()
    }

    fun refresh() {
        val outputs = runCatching { audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList() }.getOrNull().orEmpty()
        val headphones = outputs.any { it.type in HEADPHONE_TYPES }
        val btNames = outputs.filter { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            .map { it.productName?.toString() }
        val carMode = uiMode?.currentModeType == Configuration.UI_MODE_TYPE_CAR
        val inCar = carMode || (isCharging() && btNames.any(Suggestions::looksLikeCar))
        // A car's Bluetooth is not "headphones".
        val realHeadphones = headphones && !(inCar && outputs.none { it.type in WIRED_TYPES })
        _state.value = DeviceContext(headphones = realHeadphones, inCar = inCar)
    }

    private fun isCharging(): Boolean {
        val battery = runCatching { ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED) }.getOrNull()
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return plugged != 0
    }

    private companion object {
        val WIRED_TYPES = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET)
        val HEADPHONE_TYPES = WIRED_TYPES + setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
}

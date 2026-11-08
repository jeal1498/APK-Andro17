package com.minimal.launcher.security

import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.UUID

/**
 * Unlocks hidden apps with the framework [BiometricPrompt] (no extra library): fingerprint / face,
 * with the screen lock (PIN, pattern, password) as fallback. Android 8.x uses the screen lock only.
 * If the device has no screen lock at all there is nothing to check against, so access is allowed
 * and the user is told how to protect it.
 */
object Authenticator {

    fun authenticate(context: Context, title: String, onSuccess: () -> Unit) {
        val activity = context.findActivity() ?: return onSuccess()
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            Toast.makeText(activity, "Configura un bloqueo de pantalla o huella para proteger las apps ocultas", Toast.LENGTH_LONG).show()
            onSuccess()
            return
        }
        if (Build.VERSION.SDK_INT >= 28) biometric(activity, title, onSuccess) else deviceCredential(activity, keyguard, title, onSuccess)
    }

    private fun biometric(activity: ComponentActivity, title: String, onSuccess: () -> Unit) {
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle("Usa tu huella o el bloqueo de pantalla")
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED && errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                    Toast.makeText(activity, errString ?: "No se pudo verificar", Toast.LENGTH_SHORT).show()
                }
            }
        }
        runCatching { builder.build().authenticate(CancellationSignal(), activity.mainExecutor, callback) }
            .onFailure { Toast.makeText(activity, "No se pudo pedir la verificación", Toast.LENGTH_SHORT).show() }
    }

    @Suppress("DEPRECATION")
    private fun deviceCredential(activity: ComponentActivity, keyguard: KeyguardManager, title: String, onSuccess: () -> Unit) {
        val intent = keyguard.createConfirmDeviceCredentialIntent(title, "Usa el bloqueo de pantalla") ?: return onSuccess()
        var launcher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>? = null
        launcher = activity.activityResultRegistry.register("auth-" + UUID.randomUUID(), ActivityResultContracts.StartActivityForResult()) { r ->
            launcher?.unregister()
            if (r.resultCode == android.app.Activity.RESULT_OK) onSuccess()
        }
        launcher.launch(intent)
    }

    private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

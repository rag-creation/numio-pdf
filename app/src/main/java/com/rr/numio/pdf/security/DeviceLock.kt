package com.rr.numio.pdf.security

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

/** The phone's own fingerprint, face or screen lock, used to guard things in the app. */
object DeviceLock {

    /** True when the phone has a screen lock (PIN, pattern, password) to check against. */
    fun available(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isDeviceSecure == true
    }

    /**
     * Asks for the fingerprint, face or screen lock. Phones with no screen lock
     * (or Android older than 9) call [onOk] straight away, since there's nothing
     * to check against; use [available] first where that matters.
     */
    fun ask(context: Context, title: String, subtitle: String, onOk: () -> Unit, onCancel: () -> Unit = {}) {
        if (!available(context)) {
            onOk()
            return
        }
        val builder = BiometricPrompt.Builder(context)
            .setTitle(title)
            .setSubtitle(subtitle)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                @Suppress("DEPRECATION")
                builder.setDeviceCredentialAllowed(true)
            else -> builder.setNegativeButton("Cancel", context.mainExecutor) { _, _ -> onCancel() }
        }
        try {
            builder.build().authenticate(
                CancellationSignal(),
                context.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onOk()
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onCancel()
                },
            )
        } catch (e: Exception) {
            // No fingerprint set up on an Android 9 phone with only a PIN: nothing to prompt with
            onOk()
        }
    }
}

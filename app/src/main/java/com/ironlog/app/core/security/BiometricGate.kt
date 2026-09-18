package com.ironlog.app.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    HARDWARE_UNAVAILABLE,
    NONE_ENROLLED,
    UNSUPPORTED
}

/**
 * Optional second factor in front of the app itself.
 *
 * A valid session token is enough to use IronLog, which means anyone holding an
 * unlocked phone can read the owner's bodyweight, body-fat figures and progress
 * photos. When the user turns the lock on, a biometric (or device credential)
 * check is required on every cold start and on every return from background.
 *
 * Device credential is allowed as a fallback so the feature still works on
 * handsets with no fingerprint reader.
 */
@Singleton
class BiometricGate @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val authenticators =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun availability(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NONE_ENROLLED
            else -> BiometricAvailability.UNSUPPORTED
        }

    fun isAvailable(): Boolean = availability() == BiometricAvailability.AVAILABLE

    fun prompt(
        activity: FragmentActivity,
        title: String = "Unlock IronLog",
        subtitle: String = "Your training log is locked",
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    when (code) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> onCancelled()
                        else -> onFailure(message.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // Fired on a non-matching finger; the prompt stays up and the
                    // user can retry, so this is not surfaced as an error.
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(info)
    }
}

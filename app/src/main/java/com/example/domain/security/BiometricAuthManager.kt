package com.example.domain.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAvailabilityStatus {
    AVAILABLE,
    NONE_ENROLLED,
    NO_HARDWARE,
    HW_UNAVAILABLE,
    SECURITY_UPDATE_REQUIRED,
    UNKNOWN
}

object BiometricAuthManager {

    /**
     * Checks if biometric authentication (fingerprint, face, etc.) is supported and enrolled on the device.
     */
    fun checkBiometricAvailability(context: Context): BiometricAvailabilityStatus {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        
        return when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailabilityStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailabilityStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailabilityStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailabilityStatus.HW_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricAvailabilityStatus.SECURITY_UPDATE_REQUIRED
            else -> BiometricAvailabilityStatus.UNKNOWN
        }
    }

    /**
     * Returns a user-friendly localized status message describing biometric capability.
     */
    fun getAvailabilityDescription(context: Context, isEnglish: Boolean): String {
        val status = checkBiometricAvailability(context)
        return when (status) {
            BiometricAvailabilityStatus.AVAILABLE -> {
                if (isEnglish) "Biometrics (Fingerprint / Face) supported and enrolled ✅"
                else "مستشعر البصمة والوجه متوفر ومسجل بنجاح ✅"
            }
            BiometricAvailabilityStatus.NONE_ENROLLED -> {
                if (isEnglish) "Biometrics supported, but no fingerprint/face enrolled in device settings ⚠️"
                else "الجهاز يدعم البصمة لكن لم يتم تسجيل أي بصمة في إعدادات الهاتف بعد ⚠️"
            }
            BiometricAvailabilityStatus.NO_HARDWARE -> {
                if (isEnglish) "Device does not have biometric hardware (Use PIN code) ℹ️"
                else "هذا الهاتف لا يحتوي على مستشعر بصمة أو وجه (استخدم رمز PIN) ℹ️"
            }
            BiometricAvailabilityStatus.HW_UNAVAILABLE -> {
                if (isEnglish) "Biometric sensor is currently busy or unavailable ⏳"
                else "مستشعر البصمة مشغول أو غير متاح حالياً ⏳"
            }
            BiometricAvailabilityStatus.SECURITY_UPDATE_REQUIRED -> {
                if (isEnglish) "Security update required for biometric hardware 🔒"
                else "يتطلب تحديث أمني لاستخدام البصمة 🔒"
            }
            BiometricAvailabilityStatus.UNKNOWN -> {
                if (isEnglish) "Biometric status unknown (PIN code supported) ℹ️"
                else "حالة مستشعر البصمة غير معروفة (رمز PIN متاح) ℹ️"
            }
        }
    }

    /**
     * Triggers the official Android BiometricPrompt authentication dialog.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String = "",
        negativeButtonText: String = "إلغاء",
        onSuccess: () -> Unit,
        onError: (errorCode: Int, errString: String) -> Unit = { _, _ -> },
        onFailed: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                if (subtitle.isNotBlank()) {
                    setSubtitle(subtitle)
                }
            }
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errorCode, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            onError(-1, e.message ?: "Authentication error")
        }
    }
}

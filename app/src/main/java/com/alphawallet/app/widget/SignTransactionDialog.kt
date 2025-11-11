package com.alphawallet.app.widget

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.alphawallet.app.R
import com.alphawallet.app.entity.AuthenticationCallback
import com.alphawallet.app.entity.AuthenticationFailType
import com.alphawallet.app.entity.Operation
import com.alphawallet.app.ui.BaseActivity
import java.security.ProviderException

/**
 * Handles biometric or device credential authentication for signing transactions.
 */
class SignTransactionDialog(context: Context) {

    private val hasStrongBiometric: Boolean
    private val hasDeviceCredential: Boolean
    private var biometricPrompt: BiometricPrompt? = null

    init {
        val biometricManager = BiometricManager.from(context)
        hasStrongBiometric =
            biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        hasDeviceCredential =
            biometricManager.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Requests authentication using available biometric or device credentials.
     */
    fun getAuthentication(
        authCallback: AuthenticationCallback,
        activity: Activity,
        callbackId: Operation
    ) {
        val fragmentActivity = activity as? FragmentActivity
            ?: run {
                authCallback.authenticateFail(
                    activity.getString(R.string.authentication_error),
                    AuthenticationFailType.BIOMETRIC_AUTHENTICATION_NOT_AVAILABLE,
                    callbackId
                )
                return
            }

        val executor = ContextCompat.getMainExecutor(activity)
        biometricPrompt = BiometricPrompt(
            fragmentActivity,
            executor,
            createPromptCallback(activity, authCallback, callbackId)
        )

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.unlock_private_key))

        if (!hasStrongBiometric && !hasDeviceCredential) {
            showAuthenticationScreen(activity, authCallback, callbackId)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = (if (hasStrongBiometric) BIOMETRIC_STRONG else 0) or
                (if (hasDeviceCredential) DEVICE_CREDENTIAL else 0)
            promptBuilder.setAllowedAuthenticators(authenticators)
        } else {
            if (!hasStrongBiometric) {
                showAuthenticationScreen(activity, authCallback, callbackId)
                return
            } else {
                promptBuilder.setAllowedAuthenticators(BIOMETRIC_STRONG)
                    .setNegativeButtonText(activity.getString(R.string.use_pin))
            }
        }

        if (!hasDeviceCredential) {
            promptBuilder.setNegativeButtonText(activity.getString(R.string.action_cancel))
        }

        try {
            val promptInfo = promptBuilder.build()
            biometricPrompt?.authenticate(promptInfo)
        } catch (e: ProviderException) {
            authCallback.authenticateFail(
                activity.getString(R.string.authentication_error),
                AuthenticationFailType.BIOMETRIC_AUTHENTICATION_NOT_AVAILABLE,
                callbackId
            )
        }
    }

    /**
     * Cancels any ongoing biometric prompts.
     */
    fun close() {
        runCatching { biometricPrompt?.cancelAuthentication() }
    }

    /**
     * Builds the biometric prompt callback for handling authentication events.
     */
    private fun createPromptCallback(
        activity: Activity,
        authCallback: AuthenticationCallback,
        callbackId: Operation
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        if (!TextUtils.isEmpty(errString) &&
                            errString == activity.getString(R.string.use_pin)
                        ) {
                            showAuthenticationScreen(activity, authCallback, callbackId)
                        } else {
                            authCallback.authenticateFail(
                                "Cancelled",
                                AuthenticationFailType.FINGERPRINT_ERROR_CANCELED,
                                callbackId
                            )
                        }
                    }

                    BiometricPrompt.ERROR_CANCELED -> authCallback.authenticateFail(
                        "Cancelled",
                        AuthenticationFailType.FINGERPRINT_ERROR_CANCELED,
                        callbackId
                    )

                    BiometricPrompt.ERROR_LOCKOUT,
                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> authCallback.authenticateFail(
                        activity.getString(R.string.too_many_fails),
                        AuthenticationFailType.FINGERPRINT_NOT_VALIDATED,
                        callbackId
                    )

                    BiometricPrompt.ERROR_USER_CANCELED -> authCallback.authenticateFail(
                        activity.getString(androidx.biometric.R.string.fingerprint_error_user_canceled),
                        AuthenticationFailType.AUTHENTICATION_DIALOG_CANCELLED,
                        callbackId
                    )

                    BiometricPrompt.ERROR_HW_NOT_PRESENT,
                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_BIOMETRICS,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    BiometricPrompt.ERROR_NO_SPACE,
                    BiometricPrompt.ERROR_TIMEOUT,
                    BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
                    BiometricPrompt.ERROR_VENDOR -> authCallback.authenticateFail(
                        activity.getString(R.string.fingerprint_authentication_failed),
                        AuthenticationFailType.FINGERPRINT_NOT_VALIDATED,
                        callbackId
                    )
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                authCallback.authenticatePass(callbackId)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                authCallback.authenticateFail(
                    activity.getString(R.string.fingerprint_authentication_failed),
                    AuthenticationFailType.FINGERPRINT_NOT_VALIDATED,
                    callbackId
                )
            }
        }
    }

    /**
     * Presents the system credential screen when biometrics are unavailable or user chooses PIN.
     */
    private fun showAuthenticationScreen(
        activity: Activity,
        authCallback: AuthenticationCallback,
        callBackId: Operation
    ) {
        val km = activity.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        when {
            km == null -> authCallback.authenticateFail(
                "Device unlocked",
                AuthenticationFailType.DEVICE_NOT_SECURE,
                callBackId
            )

            !km.isDeviceSecure -> authCallback.authenticatePass(callBackId)

            else -> {
                val intent =
                    km.createConfirmDeviceCredentialIntent(
                        activity.getString(R.string.unlock_private_key),
                        ""
                    )
                if (intent == null) {
                    authCallback.authenticateFail(
                        "Can not unlock",
                        AuthenticationFailType.BIOMETRIC_AUTHENTICATION_NOT_AVAILABLE,
                        callBackId
                    )
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    BaseActivity.authCallback = authCallback
                    activity.startActivityForResult(
                        intent,
                        REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS + callBackId.ordinal
                    )
                }
            }
        }
    }

    companion object {
        const val REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 123

        private const val BIOMETRIC_STRONG = BiometricManager.Authenticators.BIOMETRIC_STRONG
        private const val DEVICE_CREDENTIAL = BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}

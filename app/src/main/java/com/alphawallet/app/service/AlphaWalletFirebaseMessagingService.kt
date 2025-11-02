package com.alphawallet.app.service

import com.alphawallet.app.entity.notification.DataMessage
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles Firebase Cloud Messaging events such as new token registration and incoming messages.
 */
@AndroidEntryPoint
class AlphaWalletFirebaseMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var tokensService: TokensService

    @Inject
    lateinit var transactionsService: TransactionsService

    @Inject
    lateinit var preferenceRepository: PreferenceRepositoryType

    private val gson = Gson()

    /**
     * Invoked when a new FCM token is issued for this device/app combination.
     *
     * Updates the stored token so the backend can continue to deliver push notifications.
     */
    override fun onNewToken(token: String) {
        // sendRegistrationToServer(token)
        preferenceRepository.firebaseMessagingToken = token
    }

    /**
     * Placeholder for propagating the FCM token to a backend service if required.
     */
    @Suppress("SameParameterValue")
    private fun sendRegistrationToServer(token: String) {
        // TODO: Send token to app server if push registration needs to be mirrored remotely.
    }

    /**
     * Processes incoming FCM data payloads to trigger background transaction refreshes when needed.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val bodyJson = remoteMessage.data["body"] ?: return
        val body = runCatching {
            gson.fromJson(bodyJson, DataMessage.Body::class.java)
        }.getOrNull()

        if (body != null &&
            body.to.equals(preferenceRepository.currentWalletAddress, ignoreCase = true) &&
            !tokensService.isOnFocus()
        ) {
            transactionsService.fetchTransactionsFromBackground()
        }
    }
}

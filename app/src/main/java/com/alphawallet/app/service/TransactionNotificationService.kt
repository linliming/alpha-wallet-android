package com.alphawallet.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.TextUtils
import androidx.core.app.NotificationCompat
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.entity.Transaction
import com.alphawallet.app.entity.TransactionType
import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.app.entity.transactions.TransferEvent
import com.alphawallet.app.repository.PreferenceRepositoryType
import com.alphawallet.app.ui.TransactionDetailActivity
import com.alphawallet.app.util.BalanceUtils
import com.alphawallet.app.util.Utils
import java.util.Locale

/**
 * Builds and issues notifications for incoming transactions or transfer events.
 */
class TransactionNotificationService(
    private val context: Context,
    private val preferenceRepository: PreferenceRepositoryType,
) {

    /** Posts a notification summarising the supplied transaction/transfer. */
    fun showNotification(tx: Transaction, token: Token, transferEvent: TransferEvent?) {
        val id = tx.hash?.hashCode() ?: 0
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            buildIntent(tx, token),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
        )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = createNotification(pendingIntent, tx, token, transferEvent)
        notification?.let { manager.notify(id, it) }
    }

    /** Assembles the notification content, returning null when no alert should be shown. */
    fun createNotification(
        intent: PendingIntent,
        tx: Transaction,
        token: Token,
        transferEvent: TransferEvent?,
    ): Notification? {
        val walletAddress = preferenceRepository.currentWalletAddress
        val defaultCase =
            !preferenceRepository.isWatchOnly &&
                tx.timeStamp > preferenceRepository.getLoginTime(walletAddress) &&
                preferenceRepository.isTransactionNotificationsEnabled(walletAddress)

        if (transferEvent == null) {
            val txType = token.getTransactionType(tx)
            val receivedBaseToken =
                (txType == TransactionType.RECEIVED || txType == TransactionType.RECEIVE_FROM) &&
                    tx.to.equals(walletAddress, ignoreCase = true)

            if (defaultCase && receivedBaseToken) {
                return buildNotification(
                    intent,
                    context.getString(R.string.received),
                    token.getTransactionResultValue(tx),
                    tx.from,
                )
            }
        } else {
            if (defaultCase && isRecipient(walletAddress.orEmpty(), transferEvent.valueList)) {
                val isMintEvent = isMintEvent(transferEvent.valueList)
                val title = if (isMintEvent) context.getString(R.string.minted) else context.getString(R.string.received)
                val bodyValue = "${getReadableValue(token, transferEvent.tokenValue)} ${token.getSymbol()}"
                val from = if (isMintEvent) "" else tx.from
                return buildNotification(intent, title, bodyValue, from)
            }
        }

        return null
    }

    private fun buildIntent(tx: Transaction, token: Token): Intent =
        Intent(context, TransactionDetailActivity::class.java).apply {
            putExtra(C.EXTRA_TXHASH, tx.hash)
            putExtra(C.EXTRA_CHAIN_ID, token.tokenInfo.chainId)
            putExtra(C.EXTRA_ADDRESS, token.getAddress())
            putExtra(C.FROM_NOTIFICATION, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    private fun buildNotification(
        pendingIntent: PendingIntent,
        event: String,
        value: String,
        fromAddress: String,
    ): Notification {
        val body = getBody(value, fromAddress)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setContentTitle(getTitle(event, value))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()
    }

    private fun getTitle(event: String, value: String): String = "$event $value"

    private fun getBody(value: String, fromAddress: String): String {
        val walletAddress = preferenceRepository.currentWalletAddress
        return if (TextUtils.isEmpty(fromAddress)) {
            context.getString(
                R.string.notification_message_incoming_token,
                Utils.formatAddress(walletAddress),
                value,
            )
        } else {
            context.getString(
                R.string.notification_message_incoming_token_with_recipient,
                Utils.formatAddress(walletAddress),
                value,
                Utils.formatAddress(fromAddress),
            )
        }
    }

    private fun isMintEvent(input: String): Boolean {
        val searchText = "from,address,"
        val startIndex = input.indexOf(searchText) + searchText.length
        val endIndex = input.indexOf(',', startIndex)
        val from = input.substring(startIndex, endIndex)
        return from.equals(C.BURN_ADDRESS, ignoreCase = true)
    }

    private fun isRecipient(walletAddress: String, input: String): Boolean {
        val validationString = "to,address,${walletAddress.lowercase(Locale.ROOT)}"
        return input.lowercase(Locale.ROOT).contains(validationString)
    }

    private fun getReadableValue(token: Token, tokenValue: String): String {
        return if (token.isERC20()) {
            BalanceUtils.getScaledValue(tokenValue, token.tokenInfo.decimals.toLong())
        } else {
            "#$tokenValue"
        }
    }

    companion object {
        private const val CHANNEL_ID = "transactions_channel"
        private const val CHANNEL_NAME = "Transactions"
    }
}

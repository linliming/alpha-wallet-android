package com.alphawallet.app.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.alphawallet.app.C
import com.alphawallet.app.R
import com.alphawallet.app.ui.HomeActivity

/**
 * Created by James on 25/04/2019.
 * Stormbird in Sydney
 */
class NotificationService
    (private val context: Context) {
    private val CHANNEL_ID = "ALPHAWALLET CHANNEL"
    private val NOTIFICATION_ID = 314151024

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val attr = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val name: CharSequence = context.getString(R.string.app_name)
            val description = context.getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            channel.setSound(notification, attr)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = context.getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun DisplayNotification(title: String?, content: String, priority: Int) {
        checkNotificationPermission()
        val color = context.getColor(R.color.brand)

        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val openAppIntent = Intent(context, HomeActivity::class.java)
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        //openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        openAppIntent.setData(Uri.parse(AWSTARTUP + content))
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            openAppIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alpha_notification)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(content)
            .setSound(notification, AudioManager.STREAM_NOTIFICATION)
            .setAutoCancel(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(contentIntent)
            .setPriority(priority)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager != null) {
            try {
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun displayPriceAlertNotification(
        title: String?,
        content: String?,
        priority: Int,
        openAppIntent: Intent
    ) {
        checkNotificationPermission()
        val color = context.getColor(R.color.brand)

        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            openAppIntent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alpha_notification)
            .setColor(color)
            .setContentTitle(title)
            .setContentText(content)
            .setSound(notification, AudioManager.STREAM_NOTIFICATION)
            .setAutoCancel(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(contentIntent)
            .setPriority(priority)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager != null) {
            try {
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (!((ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                            != PackageManager.PERMISSION_DENIED))
        ) {
            val intent = Intent(C.REQUEST_NOTIFICATION_ACCESS)
            intent.setPackage("com.alphawallet.app.entity.HomeReceiver")
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        }
    }

    companion object {
        const val AWSTARTUP: String = "AW://"
    }
}

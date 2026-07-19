package com.vesper.flipper.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vesper.flipper.MainActivity
import com.vesper.flipper.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ralph campaign notifications. Fires when a running campaign transitions to
 * AWAITING_APPROVAL — either at a HIGH-risk action inside a phase, or at the
 * exploit gate itself. Tapping the notification deep-links to the Approval Inbox
 * so the operator can resolve the queued action(s).
 *
 * Channel importance is HIGH so notifications wake the device — an unattended
 * campaign that hits an approval gate is expected to interrupt.
 */
@Singleton
class CampaignNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_campaign_approval),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notification_channel_campaign_approval_desc)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun notifyAwaitingApproval(campaignId: String, campaignName: String, phase: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val deepLink = Uri.parse("vesper://approval-inbox?campaign=$campaignId")
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = deepLink
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            campaignId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Vesper: '$campaignName' needs approval")
            .setContentText("${phase.lowercase().replaceFirstChar { it.uppercase() }} phase has a queued action.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .build()

        NotificationManagerCompat.from(context).notify(
            campaignId.hashCode(),
            notification,
        )
    }

    fun cancel(campaignId: String) {
        NotificationManagerCompat.from(context).cancel(campaignId.hashCode())
    }

    companion object {
        const val CHANNEL_ID = "campaign_approval"
    }
}

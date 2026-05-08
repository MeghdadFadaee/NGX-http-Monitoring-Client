package net.rodakot.ngxhttpmonitoringclient.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import net.rodakot.ngxhttpmonitoringclient.model.AlertEvent
import net.rodakot.ngxhttpmonitoringclient.model.AlertSeverity
import net.rodakot.ngxhttpmonitoringclient.model.ServerProfile

object MonitorNotifications {
    private const val ChannelId = "ngx_monitor_alerts"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ChannelId,
            "Server alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "NGX Monitor alert notifications"
        }
        manager.createNotificationChannel(channel)
    }

    fun showAlert(context: Context, server: ServerProfile, alert: AlertEvent) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = builder(context)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("${alert.title}: ${server.name}")
            .setContentText(alert.message)
            .setStyle(Notification.BigTextStyle().bigText(alert.message))
            .setWhen(alert.timestampMillis)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setPriority(if (alert.severity == AlertSeverity.Critical) Notification.PRIORITY_HIGH else Notification.PRIORITY_DEFAULT)
            .build()
        manager.notify((server.id + alert.title).hashCode(), notification)
    }

    private fun builder(context: Context): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ChannelId)
        } else {
            Notification.Builder(context)
        }
    }
}

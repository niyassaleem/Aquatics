package com.example.aquatics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    private const val CHANNEL_ID = "aquatics_notifications"
    private const val CHANNEL_NAME = "Aquatics Alerts"
    private const val CHANNEL_DESC = "Notifications for task completion and critical errors"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showAlert(context: Context, title: String, message: String, isError: Boolean = false) {
        val sharedPref = context.getSharedPreferences("AquaticsSettings", Context.MODE_PRIVATE)
        
        val taskNotify = sharedPref.getBoolean("notify_task", true)
        val errorNotify = sharedPref.getBoolean("notify_error", true)

        // Filter based on settings
        if (!isError && !taskNotify) return
        if (isError && !errorNotify) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_water_drop)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (isError) {
            builder.setColor(context.getColor(android.R.color.holo_red_light))
        }

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            // Log error if notification permission is missing on Android 13+
            AppLogger.error(context, "NOTIFICATION", "Permission missing for notifications: ${e.message}")
        }
    }
}

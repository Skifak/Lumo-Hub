package com.lumo.hub.weather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lumo.hub.R
import com.lumo.hub.network.WeatherAlert

object WeatherNotifications {
    const val CHANNEL_ID = "weather_alerts"
    private const val CHANNEL_NAME = "Погодные предупреждения"

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Предупреждения о дожде и заморозках"
        })
    }

    fun post(context: Context, locationName: String, alerts: List<WeatherAlert>) {
        if (alerts.isEmpty() || !canNotify(context)) return
        val body = alerts.joinToString("\n") { "${it.title}: ${it.body}" }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Погода: $locationName")
            .setContentText(alerts.first().title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(locationName.hashCode(), notification)
    }

    fun canNotify(context: Context): Boolean = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

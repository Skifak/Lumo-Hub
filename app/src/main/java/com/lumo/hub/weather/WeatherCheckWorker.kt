package com.lumo.hub.weather

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lumo.hub.data.WeatherRepository
import java.util.concurrent.TimeUnit

class WeatherCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = WeatherRepository(applicationContext)
        if (!repository.notificationsEnabled() || repository.savedLocation() == null) return Result.success()
        return runCatching {
            val forecast = repository.forecast() ?: return Result.success()
            WeatherNotifications.createChannel(applicationContext)
            WeatherNotifications.post(applicationContext, repository.savedLocation()!!.name, repository.alerts(forecast))
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val UNIQUE_NAME = "weather_periodic_check"
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeatherCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

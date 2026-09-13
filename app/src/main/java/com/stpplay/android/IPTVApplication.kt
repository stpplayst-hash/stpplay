package com.stpplay.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.imageLoader
import coil.memory.MemoryCache
import com.stpplay.android.worker.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class IPTVApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15) // Reduzido de 0.25 para 0.15 para dar mais RAM ao sistema em TVs
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleReminder()
        scheduleSubscriptionAlert()
        schedulePlaylistUpdate()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        imageLoader.memoryCache?.clear()
        System.gc()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= 60 /* TRIM_MEMORY_MODERATE */) {
            imageLoader.memoryCache?.clear()
        }
    }

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            val channels = listOf(
                android.app.NotificationChannel(
                    "epg_reminders",
                    "Lembretes de Programação",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos para programas que você quer assistir"
                },
                android.app.NotificationChannel(
                    "continue_watching",
                    "Continuar Assistindo",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Lembretes para terminar seus filmes e séries"
                },
                android.app.NotificationChannel(
                    "subscription_alerts",
                    "Avisos de Assinatura",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alertas sobre o vencimento da sua conta"
                }
            )
            notificationManager.createNotificationChannels(channels)
        }
    }

    private fun scheduleReminder() {
        val workRequest = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reminder_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleSubscriptionAlert() {
        val workRequest = PeriodicWorkRequestBuilder<com.stpplay.android.worker.SubscriptionWorker>(12, TimeUnit.HOURS)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "subscription_alert_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun schedulePlaylistUpdate() {
        val workRequest = PeriodicWorkRequestBuilder<com.stpplay.android.worker.PlaylistUpdateWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "playlist_update_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

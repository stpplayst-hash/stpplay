package com.stpplay.android.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stpplay.android.MainActivity
import com.stpplay.android.R
import com.stpplay.android.database.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("ReminderWorker", "Verificando conteúdos pendentes...")
        val lastContent = db.playbackPositionDao().getLastUnfinishedContent()
        
        if (lastContent != null) {
            Log.d("ReminderWorker", "Conteúdo encontrado: ${lastContent.name}")
            showNotification(lastContent.name)
        } else {
            Log.d("ReminderWorker", "Nenhum conteúdo pendente encontrado.")
        }
        
        return Result.success()
    }

    private fun showNotification(title: String) {
        val channelId = "continue_watching"
        val notificationId = 1001

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("STP Play")
            .setContentText("Não perca o resto! Continue a assistir: $title")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(applicationContext)) {
                notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("ReminderWorker", "Sem permissão para enviar notificações: ${e.message}")
        }
    }
}

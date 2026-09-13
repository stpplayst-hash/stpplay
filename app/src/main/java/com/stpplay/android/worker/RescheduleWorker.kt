package com.stpplay.android.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stpplay.android.database.AppDatabase
import com.stpplay.android.database.ReminderEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("RescheduleWorker", "Iniciando re-agendamento de lembretes...")
        
        try {
            // Pegamos todos os lembretes do banco (todos os perfis)
            val allReminders = db.reminderDao().getAllRemindersForReschedule().first()
            val now = System.currentTimeMillis()
            
            var count = 0
            allReminders.forEach { reminder ->
                // Só re-agendamos lembretes futuros (com margem de 5 min)
                if (reminder.startTimestamp > now + 300000) {
                    scheduleAlarm(reminder)
                    count++
                }
            }
            Log.d("RescheduleWorker", "$count lembretes re-agendados com sucesso.")
        } catch (e: Exception) {
            Log.e("RescheduleWorker", "Erro ao re-agendar lembretes: ${e.message}")
            return Result.retry()
        }
        
        return Result.success()
    }

    private fun scheduleAlarm(reminder: ReminderEntity) {
        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(applicationContext, ReminderReceiver::class.java).apply {
            putExtra("title", reminder.title)
            putExtra("channelName", "Canal") // Nome real precisaria de um join no banco
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext, 
            (reminder.streamId + reminder.startTimestamp).hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = reminder.startTimestamp - 300000 
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}

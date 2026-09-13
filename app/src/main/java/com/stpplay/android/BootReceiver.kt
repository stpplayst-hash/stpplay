package com.stpplay.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stpplay.android.data.SettingsManager
import com.stpplay.android.worker.RescheduleWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsManager: SettingsManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            // 1. Re-agendar lembretes via WorkManager
            val rescheduleRequest = OneTimeWorkRequestBuilder<RescheduleWorker>().build()
            WorkManager.getInstance(context).enqueue(rescheduleRequest)

            // 2. Auto-start se configurado
            if (settingsManager.startOnBoot.value) {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}

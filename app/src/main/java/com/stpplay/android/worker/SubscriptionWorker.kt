package com.stpplay.android.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stpplay.android.MainActivity
import com.stpplay.android.R
import com.stpplay.android.data.AuthManager
import com.stpplay.android.data.UserInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class SubscriptionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authManager: AuthManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("SubscriptionWorker", "Verificando expiração da assinatura...")
        
        // 1. Tentar atualizar dados online primeiro (se houver credenciais)
        val loginType = authManager.getLoginType()
        val (user, pass) = authManager.getCredentials()
        if (loginType == "XTREAM" && !user.isNullOrBlank() && !pass.isNullOrBlank()) {
            val savedUrl = authManager.getXtreamUrl() ?: "http://flashplay.top"
            val serversToTry = mutableListOf<String>()
            serversToTry.add(savedUrl)
            val fallbacks = listOf("http://flashplay.top", "http://titanplayprincipal.com", "http://nobre.lat")
            fallbacks.forEach { if (!serversToTry.contains(it)) serversToTry.add(it) }

            for (baseUrl in serversToTry) {
                try {
                    val apiUrl = "$baseUrl/player_api.php?username=$user&password=$pass"
                    val connection = URL(apiUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    if (connection.responseCode == 200) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val json = Json { ignoreUnknownKeys = true }
                        val jsonElement = json.parseToJsonElement(response)
                        val userObj = jsonElement.jsonObject["user_info"]?.jsonObject
                        if (userObj != null) {
                            val expiry = userObj["exp_date"]?.jsonPrimitive?.content
                            val status = userObj["status"]?.jsonPrimitive?.content ?: "Ativo"
                            val createdAt = userObj["created_at"]?.jsonPrimitive?.content
                            
                            fun formatTimestamp(ts: String?): String {
                                if (ts == null || ts == "null" || ts == "0") return "Vitalício"
                                return try {
                                    val date = Date(ts.toLong() * 1000)
                                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(date)
                                } catch (e: Exception) { "N/A" }
                            }

                            fun getRawTimestamp(ts: String?): Long? {
                                if (ts == null || ts == "null" || ts == "0") return null
                                return try { ts.toLong() * 1000 } catch (e: Exception) { null }
                            }

                            val updatedInfo = UserInfo(
                                username = user,
                                status = status,
                                expiryDate = formatTimestamp(expiry),
                                createdAt = formatTimestamp(createdAt),
                                activeConnections = userObj["active_cons"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                                maxConnections = userObj["max_connections"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
                                expiryTimestamp = getRawTimestamp(expiry)
                            )
                            authManager.saveUserInfo(updatedInfo)
                            authManager.saveXtreamUrl(baseUrl)
                            Log.d("SubscriptionWorker", "Dados da conta atualizados via $baseUrl")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w("SubscriptionWorker", "Não foi possível atualizar via $baseUrl: ${e.message}")
                }
            }
        }

        val userInfo = authManager.getUserInfo() ?: return Result.success()

        if (userInfo.expiryDate == "Vitalício" || userInfo.expiryDate == "N/A") {
            return Result.success()
        }

        try {
            val now = System.currentTimeMillis()
            val expiryTs = userInfo.expiryTimestamp ?: run {
                val format = if (userInfo.expiryDate.contains(":")) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy"
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.parse(userInfo.expiryDate)?.time
            } ?: return Result.success()
            
            val diff = expiryTs - now
            val daysRemaining = TimeUnit.MILLISECONDS.toDays(diff)

            Log.d("SubscriptionWorker", "Dias restantes: $daysRemaining")

            // Notificar se faltarem entre 1 e 3 dias
            if (daysRemaining in 1L..3L) {
                val lastNotif = authManager.getLastExpiryNotificationTime()
                val isSameDay = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(lastNotif)) == 
                               SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

                // Só notifica se ainda não notificou hoje
                if (!isSameDay) {
                    showNotification(userInfo.expiryDate, daysRemaining.toInt())
                    authManager.saveLastExpiryNotificationTime(System.currentTimeMillis())
                }
            }
        } catch (e: Exception) {
            Log.e("SubscriptionWorker", "Erro ao processar data: ${e.message}")
        }

        return Result.success()
    }

    private fun showNotification(expiryDate: String, daysRemaining: Int) {
        val channelId = "subscription_alerts"
        val notificationId = 2001

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (daysRemaining == 1) "Sua assinatura vence amanhã!" else "Sua assinatura está terminando!"
        val body = "Sua conta STP Play vence em $daysRemaining dia(s) ($expiryDate). Renove agora para não ficar sem sinal!"

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(applicationContext)) {
                notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            Log.e("SubscriptionWorker", "Sem permissão de notificação")
        }
    }
}

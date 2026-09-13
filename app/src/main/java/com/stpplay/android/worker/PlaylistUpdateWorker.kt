package com.stpplay.android.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stpplay.android.data.AuthManager
import com.stpplay.android.data.PlaylistCredentials
import com.stpplay.android.repository.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class PlaylistUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: PlaylistRepository,
    private val authManager: AuthManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val loginType = authManager.getLoginType()
        if (loginType == "NONE") return@withContext Result.success()

        val intervalHours = authManager.getUpdateInterval()
        if (intervalHours == 0) return@withContext Result.success()

        val lastUpdate = authManager.getLastUpdateTime()
        val currentTime = System.currentTimeMillis()
        val intervalMs = intervalHours * 3600000L

        if (currentTime - lastUpdate < intervalMs) {
            Log.d("PlaylistUpdateWorker", "Ainda não é hora de atualizar. Restam ${(intervalMs - (currentTime - lastUpdate)) / 60000} min.")
            return@withContext Result.success()
        }

        Log.d("PlaylistUpdateWorker", "Iniciando atualização automática da lista ($loginType)...")

        try {
            when (loginType) {
                "XTREAM" -> {
                    val (user, pass) = authManager.getCredentials()
                    if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                        // Lista Dinâmica de Backup
                        val savedUrl = authManager.getXtreamUrl() ?: "http://flashplay.top"
                        val serversToTry = mutableListOf<String>()
                        serversToTry.add(savedUrl)
                        val fallbacks = listOf("http://flashplay.top", "http://titanplayprincipal.com", "http://nobre.lat")
                        fallbacks.forEach { if (!serversToTry.contains(it)) serversToTry.add(it) }

                        var updateSuccess = false
                        for (apiUrl in serversToTry) {
                            try {
                                repository.loadXtreamPlaylist(PlaylistCredentials("API", user, pass, apiUrl))
                                authManager.saveXtreamUrl(apiUrl)
                                authManager.updateLastUpdateTime()
                                updateSuccess = true
                                break
                            } catch (e: Exception) {
                                Log.w("PlaylistUpdateWorker", "Falha ao atualizar via $apiUrl: ${e.message}")
                            }
                        }
                        if (!updateSuccess) return@withContext Result.retry()
                    }
                }
                "M3U" -> {
                    val m3uUrl = authManager.getM3uUrl()
                    if (!m3uUrl.isNullOrBlank()) {
                        repository.loadM3uPlaylist(m3uUrl)
                        authManager.updateLastUpdateTime()
                    }
                }
            }
            Log.d("PlaylistUpdateWorker", "Playlist atualizada com sucesso via background.")
            Result.success()
        } catch (e: Exception) {
            Log.e("PlaylistUpdateWorker", "Erro na atualização automática: ${e.message}")
            Result.retry()
        }
    }
}

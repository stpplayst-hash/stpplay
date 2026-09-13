package com.stpplay.android.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPrefs = context.getSharedPreferences("stp_play_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveCredentials(user: String, pass: String) {
        sharedPrefs.edit {
            putString("username", user)
            putString("password", pass)
            putString("login_type", "XTREAM")
            putLong("last_update_time", System.currentTimeMillis())
        }
    }

    fun saveXtreamUrl(url: String) {
        sharedPrefs.edit { putString("xtream_url", url) }
    }

    fun getXtreamUrl(): String? {
        return sharedPrefs.getString("xtream_url", null)
    }

    fun saveM3uData(name: String, url: String) {
        sharedPrefs.edit {
            putString("m3u_name", name)
            putString("m3u_url", url)
            putString("login_type", "M3U")
            putLong("last_update_time", System.currentTimeMillis())
        }
    }

    fun getM3uName(): String? {
        return sharedPrefs.getString("m3u_name", null)
    }

    fun getLoginType(): String {
        return sharedPrefs.getString("login_type", "NONE") ?: "NONE"
    }

    fun getM3uUrl(): String? {
        return sharedPrefs.getString("m3u_url", null)
    }

    fun saveUserInfo(info: UserInfo) {
        try {
            val jsonString = json.encodeToString(info)
            sharedPrefs.edit { putString("user_info_json", jsonString) }
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "Erro ao salvar perfil: ${e.message}")
        }
    }

    fun getUserInfo(): UserInfo? {
        val jsonString = sharedPrefs.getString("user_info_json", null) ?: return null
        return try {
            json.decodeFromString<UserInfo>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    fun updateLastUpdateTime() {
        sharedPrefs.edit {
            putLong("last_update_time", System.currentTimeMillis())
        }
    }

    fun getLastUpdateTime(): Long {
        return sharedPrefs.getLong("last_update_time", 0L)
    }

    fun setUpdateInterval(hours: Int) {
        sharedPrefs.edit {
            putInt("update_interval", hours)
        }
    }

    fun getUpdateInterval(): Int {
        return sharedPrefs.getInt("update_interval", 24) // Default 24h
    }

    fun getCredentials(): Pair<String?, String?> {
        return sharedPrefs.getString("username", null) to sharedPrefs.getString("password", null)
    }

    fun clearCredentials() {
        sharedPrefs.edit {
            remove("username")
            remove("password")
            remove("user_info_json")
            remove("last_expiry_notif")
        }
    }

    fun saveLastExpiryNotificationTime(time: Long) {
        sharedPrefs.edit { putLong("last_expiry_notif", time) }
    }

    fun getLastExpiryNotificationTime(): Long {
        return sharedPrefs.getLong("last_expiry_notif", 0L)
    }

    // DAILY SELECTION PERSISTENCE
    fun saveDailySelection(ids: List<String>, isTop10: Boolean) {
        val key = if (isTop10) "daily_top10_ids" else "daily_rec_ids"
        sharedPrefs.edit { putString(key, ids.joinToString(",")) }
    }

    fun getDailySelection(isTop10: Boolean): List<String> {
        val key = if (isTop10) "daily_top10_ids" else "daily_rec_ids"
        val raw = sharedPrefs.getString(key, null) ?: return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun getLastDailyUpdateTimestamp(): Long {
        return sharedPrefs.getLong("last_daily_update", 0L)
    }

    fun saveLastDailyUpdateTimestamp(time: Long) {
        sharedPrefs.edit { putLong("last_daily_update", time) }
    }
    
    fun clearAll() {
        sharedPrefs.edit { clear() }
    }
}

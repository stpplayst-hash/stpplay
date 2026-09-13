package com.stpplay.android.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

    // RESIZE MODE (Default: FIT = 0)
    private val _defaultResizeMode = MutableStateFlow(prefs.getInt("default_resize_mode", 0))
    val defaultResizeMode: StateFlow<Int> = _defaultResizeMode.asStateFlow()

    // SHOW CLOCK (Default: true)
    private val _showClock = MutableStateFlow(prefs.getBoolean("show_clock", true))
    val showClock: StateFlow<Boolean> = _showClock.asStateFlow()

    // AUTO PLAY NEXT (Default: true)
    private val _autoPlayNext = MutableStateFlow(prefs.getBoolean("auto_play_next", true))
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext.asStateFlow()

    // BUFFER SIZE (0: Short, 1: Long - Default: 1 para maior estabilidade)
    private val _bufferSize = MutableStateFlow(prefs.getInt("buffer_size", 1))
    val bufferSize: StateFlow<Int> = _bufferSize.asStateFlow()

    // DECODING MODE (0: Hardware, 1: Software - Default: 0)
    private val _decodingMode = MutableStateFlow(prefs.getInt("decoding_mode", 0))
    val decodingMode: StateFlow<Int> = _decodingMode.asStateFlow()

    // POSTER SIZE (0.8: Small, 1.0: Medium, 1.2: Large - Default: 1.0)
    private val _posterSize = MutableStateFlow(prefs.getFloat("poster_size", 1.0f))
    val posterSize: StateFlow<Float> = _posterSize.asStateFlow()

    // THEME HIGHLIGHT COLOR (Default: StpPrimary 0xFFFFD60A)
    private val _themeColor = MutableStateFlow(prefs.getLong("theme_color", 0xFFFFD60A))
    val themeColor: StateFlow<Long> = _themeColor.asStateFlow()

    // START ON BOOT
    private val _startOnBoot = MutableStateFlow(prefs.getBoolean("start_on_boot", false))
    val startOnBoot: StateFlow<Boolean> = _startOnBoot.asStateFlow()

    // RESUME LAST CHANNEL
    private val _resumeLastChannel = MutableStateFlow(prefs.getBoolean("resume_last_channel", false))
    val resumeLastChannel: StateFlow<Boolean> = _resumeLastChannel.asStateFlow()

    // USER AGENT
    private val _customUserAgent = MutableStateFlow(prefs.getString("custom_user_agent", ""))
    val customUserAgent: StateFlow<String?> = _customUserAgent.asStateFlow()

    // LANGUAGE (Default: "pt")
    private val _language = MutableStateFlow(prefs.getString("language", "pt") ?: "pt")
    val language: StateFlow<String> = _language.asStateFlow()

    // LARGE TEXT MODE (Default: false)
    private val _largeTextMode = MutableStateFlow(prefs.getBoolean("large_text_mode", false))
    val largeTextMode: StateFlow<Boolean> = _largeTextMode.asStateFlow()

    fun setDefaultResizeMode(mode: Int) {
        prefs.edit { putInt("default_resize_mode", mode) }
        _defaultResizeMode.value = mode
    }

    fun setShowClock(show: Boolean) {
        prefs.edit { putBoolean("show_clock", show) }
        _showClock.value = show
    }

    fun setAutoPlayNext(auto: Boolean) {
        prefs.edit { putBoolean("auto_play_next", auto) }
        _autoPlayNext.value = auto
    }

    fun setBufferSize(size: Int) {
        prefs.edit { putInt("buffer_size", size) }
        _bufferSize.value = size
    }

    fun setDecodingMode(mode: Int) {
        prefs.edit { putInt("decoding_mode", mode) }
        _decodingMode.value = mode
    }

    fun setPosterSize(size: Float) {
        prefs.edit { putFloat("poster_size", size) }
        _posterSize.value = size
    }

    fun setThemeColor(color: Long) {
        prefs.edit { putLong("theme_color", color) }
        _themeColor.value = color
    }

    fun setStartOnBoot(start: Boolean) {
        prefs.edit { putBoolean("start_on_boot", start) }
        _startOnBoot.value = start
    }

    fun setResumeLastChannel(resume: Boolean) {
        prefs.edit { putBoolean("resume_last_channel", resume) }
        _resumeLastChannel.value = resume
    }

    fun setCustomUserAgent(ua: String) {
        prefs.edit { putString("custom_user_agent", ua) }
        _customUserAgent.value = ua
    }

    fun setLanguage(code: String) {
        prefs.edit { putString("language", code) }
        _language.value = code
    }

    fun setLargeTextMode(enabled: Boolean) {
        prefs.edit { putBoolean("large_text_mode", enabled) }
        _largeTextMode.value = enabled
    }

    // Ultimo canal assistido (interno)
    fun saveLastChannel(url: String) {
        prefs.edit { putString("last_channel_url", url) }
    }

    fun getLastChannel(): String? = prefs.getString("last_channel_url", null)
}

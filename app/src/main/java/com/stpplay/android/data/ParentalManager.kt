package com.stpplay.android.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentalManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("parental_prefs", Context.MODE_PRIVATE)

    private val _lockedCategories = MutableStateFlow<Set<String>>(getLockedCategoriesFromPrefs())
    val lockedCategories: StateFlow<Set<String>> = _lockedCategories.asStateFlow()

    private val _manuallyUnlockedCategories = MutableStateFlow<Set<String>>(getManuallyUnlockedFromPrefs())

    private val _shouldHideLockedCategories = MutableStateFlow(prefs.getBoolean("hide_locked_categories", false))
    val shouldHideLockedCategories: StateFlow<Boolean> = _shouldHideLockedCategories.asStateFlow()

    private val DEFAULT_KEYWORDS = setOf(
        "XXX", "ADULTOS", "18+", "+18", "ADULTO", "SEXO", "PORNO", "EROTICO", "PREMIUM", "HOT", "18", "SEX"
    )

    private val _keywordFilters = MutableStateFlow(getInitialKeywords())
    val keywordFilters: StateFlow<Set<String>> = _keywordFilters.asStateFlow()

    private val _hasPin = MutableStateFlow(getPin() != null)
    val hasPinState: StateFlow<Boolean> = _hasPin.asStateFlow()

    private fun getInitialKeywords(): Set<String> {
        val saved = prefs.getStringSet("keyword_filters", null)
        return saved ?: DEFAULT_KEYWORDS
    }

    fun setPin(pin: String) {
        prefs.edit().putString("parental_pin", pin).apply()
        _hasPin.value = true
    }

    fun getPin(): String? {
        return prefs.getString("parental_pin", null)
    }

    fun hasPin(): Boolean {
        return getPin() != null
    }

    fun verifyPin(input: String): Boolean {
        return getPin() == input
    }

    fun setHideLockedCategories(hide: Boolean) {
        prefs.edit().putBoolean("hide_locked_categories", hide).apply()
        _shouldHideLockedCategories.value = hide
    }

    fun setKeywordFilters(keywords: Set<String>) {
        prefs.edit().putStringSet("keyword_filters", keywords).apply()
        _keywordFilters.value = keywords
    }

    fun addKeywordFilter(keyword: String) {
        val current = _keywordFilters.value.toMutableSet()
        val normalized = keyword.trim().uppercase()
        if (normalized.isNotBlank() && current.add(normalized)) {
            setKeywordFilters(current)
        }
    }

    fun removeKeywordFilter(keyword: String) {
        val current = _keywordFilters.value.toMutableSet()
        if (current.remove(keyword)) {
            setKeywordFilters(current)
        }
    }

    fun setCategoryLock(categoryId: String, isLocked: Boolean) {
        val currentLocked = getLockedCategoriesFromPrefs().toMutableSet()
        val currentUnlockedManual = getManuallyUnlockedFromPrefs().toMutableSet()
        val normalizedId = categoryId.trim()

        if (isLocked) {
            currentLocked.add(normalizedId)
            currentUnlockedManual.remove(normalizedId)
        } else {
            currentLocked.remove(normalizedId)
            currentUnlockedManual.add(normalizedId)
        }
        
        prefs.edit().apply {
            putStringSet("locked_categories", currentLocked)
            putStringSet("unlocked_categories_manual", currentUnlockedManual)
            apply()
        }
        
        _lockedCategories.value = currentLocked.toSet()
        _manuallyUnlockedCategories.value = currentUnlockedManual.toSet()
        android.util.Log.d("ParentalManager", "Categoria '$normalizedId' agora está bloqueada? $isLocked")
    }

    private fun getLockedCategoriesFromPrefs(): Set<String> {
        return prefs.getStringSet("locked_categories", emptySet())?.map { it.trim() }?.toSet() ?: emptySet()
    }

    private fun getManuallyUnlockedFromPrefs(): Set<String> {
        return prefs.getStringSet("unlocked_categories_manual", emptySet())?.map { it.trim() }?.toSet() ?: emptySet()
    }

    fun isManuallyUnlocked(categoryId: String): Boolean {
        return _manuallyUnlockedCategories.value.contains(categoryId.trim())
    }

    fun getLockedCategories(): Set<String> {
        return _lockedCategories.value
    }

    fun isCategoryLocked(categoryId: String?): Boolean {
        if (categoryId == null) return false
        val normalizedId = categoryId.trim()
        return _lockedCategories.value.contains(normalizedId)
    }

    fun clearManualUnlocks() {
        prefs.edit().remove("unlocked_categories_manual").apply()
        _manuallyUnlockedCategories.value = emptySet()
        android.util.Log.d("ParentalManager", "Desbloqueios manuais resetados.")
    }
}

package com.stpplay.android.data

import android.content.Context
import androidx.core.content.edit
import com.stpplay.android.database.ProfileDao
import com.stpplay.android.database.ProfileEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profileDao: ProfileDao
) {
    private val prefs = context.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)

    private val _currentProfile = MutableStateFlow<ProfileEntity?>(null)
    val currentProfile: StateFlow<ProfileEntity?> = _currentProfile.asStateFlow()

    val profiles: Flow<List<ProfileEntity>> = profileDao.getAllProfiles()

    suspend fun selectProfile(profile: ProfileEntity) {
        prefs.edit { putString("current_profile_id", profile.id) }
        _currentProfile.value = profile
    }

    suspend fun createProfile(name: String, isKids: Boolean, iconResId: Int) {
        val newProfile = ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            iconResId = iconResId,
            isKids = isKids
        )
        profileDao.insertProfile(newProfile)
        
        // Se for o primeiro perfil, seleciona automaticamente
        if (_currentProfile.value == null) {
            selectProfile(newProfile)
        }
    }

    suspend fun deleteProfile(profile: ProfileEntity) {
        if (_currentProfile.value?.id == profile.id) {
            _currentProfile.value = null
            prefs.edit { remove("current_profile_id") }
        }
        profileDao.deleteProfile(profile)
    }

    suspend fun loadSavedProfile(): Boolean {
        val savedProfileId = prefs.getString("current_profile_id", null)
        if (savedProfileId != null) {
            val profile = profileDao.getProfileById(savedProfileId)
            if (profile != null) {
                _currentProfile.value = profile
                return true
            }
        }
        return false
    }
    
    fun logoutProfile() {
        _currentProfile.value = null
        prefs.edit { remove("current_profile_id") }
    }
}

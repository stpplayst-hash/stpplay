package com.stpplay.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stpplay.android.data.AuthManager
import com.stpplay.android.data.Channel
import com.stpplay.android.data.ParentalManager
import com.stpplay.android.data.PlaylistCredentials
import com.stpplay.android.data.ProfileManager
import com.stpplay.android.data.SettingsManager
import com.stpplay.android.data.UserInfo
import com.stpplay.android.database.EpgProgramEntity
import com.stpplay.android.database.ProfileEntity
import com.stpplay.android.database.ReminderEntity
import com.stpplay.android.database.SearchHistoryEntity
import com.stpplay.android.repository.PlaylistRepository
import com.stpplay.android.worker.ReminderReceiver
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import coil.imageLoader
import coil.annotation.ExperimentalCoilApi
import kotlin.OptIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: PlaylistRepository,
    private val authManager: AuthManager,
    private val parentalManager: ParentalManager,
    private val settingsManager: SettingsManager,
    private val profileManager: ProfileManager,
    @param:ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val xtreamServers = listOf(
        "http://flashplay.top",
        "http://titanplayprincipal.com",
        "http://nobre.lat"
    )

    private val defaultServerUrl = xtreamServers[0]

    private val sensitiveKeywords = listOf(
        "XXX", "ADULT", "SEX", "PORN", "FOR MEN", "PLAYBOY", "VENUS", 
        "SEXTREME", "PRIVATE", "HOT", "LUST", "REDLIGHT", "HARDCORE", "MAN'S"
    )

    private fun isSafeContent(channel: Channel): Boolean {
        val name = channel.name.uppercase()
        val group = (channel.group ?: "").uppercase()
        return sensitiveKeywords.none { name.contains(it) || group.contains(it) }
    }

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    // Agora o Flow principal de canais é limitado para não estourar memória
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    // Fluxos para a Home (Limitados para economizar memória e CPU em TVs)
    val homeMovies: StateFlow<List<Channel>> = repository.getChannelsByTypeLimited(com.stpplay.android.data.ContentType.MOVIE, 100)
        .map { list -> list.filter { isSafeContent(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val homeSeries: StateFlow<List<Channel>> = repository.getChannelsByTypeLimited(com.stpplay.android.data.ContentType.SERIES, 100)
        .map { list -> list.filter { isSafeContent(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<Channel>>(emptyList())
    val searchResults: StateFlow<List<Channel>> = _searchResults.asStateFlow()

    fun searchPaging(query: String): Flow<PagingData<Channel>> {
        return repository.searchChannelsPaging(query).cachedIn(viewModelScope)
    }

    private val _lastFocusedStreamId = MutableStateFlow<String?>(null)
    val lastFocusedStreamId: StateFlow<String?> = _lastFocusedStreamId.asStateFlow()

    fun setLastFocusedStreamId(id: String?) {
        _lastFocusedStreamId.value = id
    }

    fun getEpgProgramsInRange(startTime: Long, endTime: Long): Flow<List<EpgProgramEntity>> =
        repository.getEpgProgramsInRange(startTime, endTime)

    fun search(query: String) {
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            repository.searchChannels(query).collect { _searchResults.value = it }
        }
    }

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel.asStateFlow()

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo.asStateFlow()

    private val _episodes = MutableStateFlow<List<Channel>>(emptyList())

    private val _currentTrailerUrl = MutableStateFlow<String?>(null)
    val currentTrailerUrl: StateFlow<String?> = _currentTrailerUrl.asStateFlow()

    private val _currentDuration = MutableStateFlow<String?>(null)
    val currentDuration: StateFlow<String?> = _currentDuration.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isAutoLoggingIn = MutableStateFlow(false)
    val isAutoLoggingIn: StateFlow<Boolean> = _isAutoLoggingIn.asStateFlow()

    private val _isInitializing = MutableStateFlow(authManager.getLoginType() != "NONE")
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    val isSubscriptionValid: StateFlow<Boolean> = _userInfo.map { info ->
        info == null || !isSubscriptionExpired(info)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isDataEmpty: StateFlow<Boolean> = repository.getChannelsCount()
        .map { it == 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private fun isSubscriptionExpired(info: UserInfo): Boolean {
        // 1. Verificação por Status (mais rápido)
        val status = info.status.lowercase()
        if (status.contains("expir") || status.contains("inativ") || status.contains("disabl")) return true
        
        // 2. Verificação por Timestamp Direto (Mais preciso: inclui hora e minuto)
        info.expiryTimestamp?.let {
            return System.currentTimeMillis() > it
        }

        // 3. Verificação por Data (Fallback para dados antigos em cache)
        val expiryDate = info.expiryDate
        if (expiryDate == "Vitalício" || expiryDate == "N/A") return false
        
        return try {
            // Suporta formatos com ou sem hora
            val format = if (expiryDate.contains(":")) "dd/MM/yyyy HH:mm" else "dd/MM/yyyy"
            val sdf = java.text.SimpleDateFormat(format, java.util.Locale.getDefault())
            val date = sdf.parse(expiryDate) ?: return false
            date.time < System.currentTimeMillis()
        } catch (e: Exception) {
            false
        }
    }

    // PROFILES
    val currentProfile: StateFlow<ProfileEntity?> = profileManager.currentProfile
    val profiles: Flow<List<ProfileEntity>> = profileManager.profiles

    val favoriteChannels: StateFlow<List<Channel>> = currentProfile
        .filterNotNull()
        .flatMapLatest { profile -> repository.getFavoriteChannels(profile.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveCategories: StateFlow<List<String>> = repository.getChannelsCount()
        .map { repository.getDistinctCategoriesByType(com.stpplay.android.data.ContentType.LIVE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val episodesWithProgress: StateFlow<List<com.stpplay.android.data.ChannelWithProgress>> = combine(
        _episodes,
        currentProfile.filterNotNull()
            .flatMapLatest { profile -> repository.getContinueWatchingWithProgress(profile.id) }
            .onStart { emit(emptyList()) }
    ) { epList, progressList ->
        epList.map { ep ->
            val progressItem = progressList.find { it.channel.streamId == ep.streamId }
            com.stpplay.android.data.ChannelWithProgress(ep, progressItem?.progress ?: 0f)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _updateInterval = MutableStateFlow(authManager.getUpdateInterval())
    val updateInterval: StateFlow<Int> = _updateInterval.asStateFlow()

    private val _currentProgram = MutableStateFlow<EpgProgramEntity?>(null)
    val currentProgram: StateFlow<EpgProgramEntity?> = _currentProgram.asStateFlow()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _isPlaybackActive = MutableStateFlow(false)
    val isPlaybackActive: StateFlow<Boolean> = _isPlaybackActive.asStateFlow()

    val lockedCategories: StateFlow<Set<String>> = parentalManager.lockedCategories
    val shouldHideLockedCategories: StateFlow<Boolean> = parentalManager.shouldHideLockedCategories
    val keywordFilters: StateFlow<Set<String>> = parentalManager.keywordFilters
    val hasParentalPin: StateFlow<Boolean> = parentalManager.hasPinState

    val defaultResizeMode: StateFlow<Int> = settingsManager.defaultResizeMode
    val showClock: StateFlow<Boolean> = settingsManager.showClock
    val autoPlayNext: StateFlow<Boolean> = settingsManager.autoPlayNext
    val bufferSize: StateFlow<Int> = settingsManager.bufferSize
    val decodingMode: StateFlow<Int> = settingsManager.decodingMode
    val posterSize: StateFlow<Float> = settingsManager.posterSize
    val themeColor: StateFlow<Long> = settingsManager.themeColor
    val startOnBoot: StateFlow<Boolean> = settingsManager.startOnBoot
    val resumeLastChannel: StateFlow<Boolean> = settingsManager.resumeLastChannel
    val customUserAgent: StateFlow<String?> = settingsManager.customUserAgent
    val language: StateFlow<String> = settingsManager.language
    val largeTextMode: StateFlow<Boolean> = settingsManager.largeTextMode

    private val parentalState = combine(
        lockedCategories,
        keywordFilters,
        shouldHideLockedCategories
    ) { locked, keywords, hide ->
        Triple(locked, keywords, hide)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Triple(emptySet(), emptySet(), false))

    private val _sleepTimerMinutes = MutableStateFlow(0)
    val sleepTimerMinutes: StateFlow<Int> = _sleepTimerMinutes.asStateFlow()

    private val _downloadSpeed = MutableStateFlow(0f)
    val downloadSpeed: StateFlow<Float> = _downloadSpeed.asStateFlow()

    // INTELLIGENT FEATURES
    val recentSearches: Flow<List<SearchHistoryEntity>> = currentProfile
        .filterNotNull()
        .flatMapLatest { repository.getRecentSearches(it.id) }

    val activeReminders: StateFlow<List<ReminderEntity>> = currentProfile
        .filterNotNull()
        .flatMapLatest { profile -> repository.getAllReminders(profile.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dailyRecommendationIds = MutableStateFlow<List<String>>(emptyList())
    private val _dailyTop10Ids = MutableStateFlow<List<String>>(emptyList())

    val recommendations: StateFlow<List<Channel>> = combine(
        _dailyRecommendationIds,
        parentalState,
        currentProfile
    ) { dailyIds, parental, profile ->
        if (dailyIds.isEmpty()) return@combine emptyList()
        val (locked, keywords, hideLocked) = parental
        val isKids = profile?.isKids == true
        
        val baseList = repository.getChannelsByStreamIds(dailyIds)

        baseList.filter { channel ->
            if (channel.type == com.stpplay.android.data.ContentType.LIVE) return@filter false

            // Filtragem estrita para Kids
            if (isKids && !Channel.isKidsContent(channel)) return@filter false

            val group = channel.group?.trim() ?: ""
            if (hideLocked && locked.contains(group)) return@filter false
            val name = channel.name.uppercase()
            if (keywords.any { k -> group.contains(k, ignoreCase = true) || name.contains(k, ignoreCase = true) }) return@filter false
            true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val top10: StateFlow<List<Channel>> = combine(
        _dailyTop10Ids,
        parentalState,
        currentProfile
    ) { dailyIds, parental, profile ->
        if (dailyIds.isEmpty()) return@combine emptyList()
        val (locked, keywords, hideLocked) = parental
        val isKids = profile?.isKids == true
        
        val baseList = repository.getChannelsByStreamIds(dailyIds)

        baseList.filter { channel ->
            if (channel.type == com.stpplay.android.data.ContentType.LIVE) return@filter false
            
            // Filtragem estrita para Kids
            if (isKids && !Channel.isKidsContent(channel)) return@filter false

            val group = channel.group?.trim() ?: ""
            if (hideLocked && locked.contains(group)) return@filter false
            val name = channel.name.uppercase()
            if (keywords.any { k -> group.contains(k, ignoreCase = true) || name.contains(k, ignoreCase = true) }) return@filter false
            true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatchingWithProgress: StateFlow<List<com.stpplay.android.data.ChannelWithProgress>> = combine(
        currentProfile.filterNotNull().flatMapLatest { profile -> repository.getContinueWatchingWithProgress(profile.id) },
        lockedCategories,
        keywordFilters,
        shouldHideLockedCategories
    ) { list, locked, keywords, hideLocked ->
        list.filter { item ->
            val group = item.channel.group?.trim() ?: ""
            if (hideLocked && locked.contains(group)) return@filter false
            val name = item.channel.name.uppercase()
            if (keywords.any { keyword -> group.contains(keyword, ignoreCase = true) || name.contains(keyword, ignoreCase = true) }) return@filter false
            true
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentLiveChannels: StateFlow<List<Channel>> = currentProfile
        .filterNotNull()
        .flatMapLatest { profile -> repository.getRecentLiveChannels(profile.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isHomeDataReady: StateFlow<Boolean> = combine(
        homeMovies, 
        homeSeries, 
        recentLiveChannels,
        repository.getChannelsCount()
    ) { movies, series, recent, count ->
        movies.isNotEmpty() || series.isNotEmpty() || recent.isNotEmpty() || count > 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun getChannelsByCategoryPaging(
        type: com.stpplay.android.data.ContentType, 
        groupName: String?, 
        isFavorites: Boolean = false,
        sortBy: String = "Adicionado"
    ): Flow<PagingData<Channel>> {
        val profileId = currentProfile.value?.id
        return repository.getChannelsByCategoryPaging(type, groupName, isFavorites, profileId, sortBy)
            .cachedIn(viewModelScope)
    }

    fun getChannelsByTypePaging(type: com.stpplay.android.data.ContentType): Flow<PagingData<Channel>> {
        return repository.getChannelsByTypePaging(type).cachedIn(viewModelScope)
    }

    val becauseYouWatched: StateFlow<Pair<Channel, List<Channel>>?> = currentProfile
        .filterNotNull()
        .flatMapLatest { profile -> repository.getLastWatchedContent(profile.id) }
        .filterNotNull()
        .flatMapLatest { last -> 
            repository.getSimilarContent(last).map { list -> last to list }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val carouselItems: StateFlow<List<Channel>> = combine(homeMovies, homeSeries) { movies, series ->
        (movies.take(15) + series.take(15))
            .filter { !it.logo.isNullOrBlank() }
            .shuffled()
            .take(12)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private var currentCredentials: PlaylistCredentials? = null

    private val _quickList = MutableStateFlow<List<Channel>>(emptyList())
    val quickList: StateFlow<List<Channel>> = _quickList.asStateFlow()

    fun loadQuickList(channel: Channel) {
        if (channel.type == com.stpplay.android.data.ContentType.SERIES) {
            _quickList.value = _episodes.value
        } else {
            viewModelScope.launch {
                repository.getChannelsByCategory(channel.categoryId, channel.group)
                    .collect { _quickList.value = it }
            }
        }
    }

    init {
        repository.getChannelsCount()
            .distinctUntilChanged()
            .onEach { count -> 
                if (count > 0) {
                    _isInitializing.value = false
                }
                if (count == 0) return@onEach
                
                // Atualiza a seleção diária de forma assíncrona
                viewModelScope.launch { refreshDailySelection() }
                
                // Aplica bloqueio automático por palavras-chave
                viewModelScope.launch { applyAutomaticKeywordLock() }
                
                // Tenta retomar último canal
                // tryResumeLastChannel() // Removido daqui
            }
            .launchIn(viewModelScope)

        // Carrega a lista completa em background para funcionalidades que dependem dela
        repository.getChannels()
            .onEach { list -> 
                _channels.value = list
                if (list.isNotEmpty()) tryResumeLastChannel()
            }
            .launchIn(viewModelScope)

        currentProfile
            .filterNotNull()
            .flatMapLatest { profile -> repository.getFavorites(profile.id) }
            .onEach { _favorites.value = it }
            .launchIn(viewModelScope)

        _userInfo.value = authManager.getUserInfo()
        checkSavedCredentials()
        startAutomaticUpdateChecker()
        viewModelScope.launch { profileManager.loadSavedProfile() }
    }

    private fun startAutomaticUpdateChecker() {
        viewModelScope.launch {
            while (true) {
                delay(3600000L) // Verifica a cada 1 hora
                if (!_isPlaybackActive.value && !_isLoading.value && !_isInitializing.value) {
                    android.util.Log.d("PlayerViewModel", "Checker: Verificando se precisa atualizar playlist...")
                    checkSavedCredentials()
                } else {
                    android.util.Log.d("PlayerViewModel", "Checker: Atualização adiada (Player ativo ou carregando)")
                }
            }
        }
    }

    private suspend fun applyAutomaticKeywordLock() {
        val keywords = keywordFilters.value
        if (keywords.isEmpty()) return

        val allCategories = mutableSetOf<String>()
        allCategories.addAll(repository.getDistinctCategoriesByType(com.stpplay.android.data.ContentType.LIVE))
        allCategories.addAll(repository.getDistinctCategoriesByType(com.stpplay.android.data.ContentType.MOVIE))
        allCategories.addAll(repository.getDistinctCategoriesByType(com.stpplay.android.data.ContentType.SERIES))

        val categoriesToLock = allCategories
            .map { it.trim() }
            .filter { groupName ->
                val matchesKeyword = keywords.any { groupName.contains(it, ignoreCase = true) }
                matchesKeyword && !parentalManager.isManuallyUnlocked(groupName)
            }

        categoriesToLock.forEach { category ->
            if (!parentalManager.isCategoryLocked(category)) {
                parentalManager.setCategoryLock(category, true)
            }
        }
    }

    private suspend fun refreshDailySelection() {
        val lastUpdate = authManager.getLastDailyUpdateTimestamp()
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        val existingRecs = authManager.getDailySelection(false)
        val existingTop10 = authManager.getDailySelection(true)

        if (now - lastUpdate > oneDayMs || existingRecs.isEmpty() || existingTop10.isEmpty()) {
            android.util.Log.d("PlayerViewModel", "Gerando nova seleção diária (24h passadas ou lista vazia)")
            
            // 1. Gerar Recomendações
            val safeAll = repository.getRandomSafeChannels(30)
            val newRecs = safeAll.take(15).mapNotNull { it.streamId }

            // 2. Gerar Top 10
            // Nota: Simplificado para pegar randômico para performance, 
            // mas idealmente seria uma query SQL filtrando por rating
            val newTop10 = safeAll.takeLast(10).mapNotNull { it.streamId }

            authManager.saveDailySelection(newRecs, false)
            authManager.saveDailySelection(newTop10, true)
            authManager.saveLastDailyUpdateTimestamp(now)

            _dailyRecommendationIds.value = newRecs
            _dailyTop10Ids.value = newTop10
        } else {
            _dailyRecommendationIds.value = existingRecs
            _dailyTop10Ids.value = existingTop10
        }
    }

    fun checkSavedCredentials(force: Boolean = false) {
        val loginType = authManager.getLoginType()
        if (loginType == "NONE") {
            _isInitializing.value = false
            return
        }
        
        val lastUpdate = authManager.getLastUpdateTime()
        val intervalHours = authManager.getUpdateInterval()
        val currentTime = System.currentTimeMillis()
        
        val needsUpdate = if (force) true 
                         else if (intervalHours == 0) false 
                         else (currentTime - lastUpdate) > (intervalHours * 3600000L)

        if (loginType == "M3U") {
            val m3uUrl = authManager.getM3uUrl()
            if (!m3uUrl.isNullOrBlank() && needsUpdate) {
                _isAutoLoggingIn.value = !force
                viewModelScope.launch {
                    try {
                        repository.loadM3uPlaylist(m3uUrl)
                        authManager.updateLastUpdateTime()
                    } finally {
                        _isAutoLoggingIn.value = false
                        _isInitializing.value = false
                    }
                }
            } else {
                _isInitializing.value = false
            }
            return
        }

        val (user, pass) = authManager.getCredentials()
        if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
            // Verificação de Expiração no cache antes de tentar carregar
            val cachedInfo = authManager.getUserInfo()
            if (cachedInfo != null && isSubscriptionExpired(cachedInfo)) {
                _errorMessage.value = "A sua assinatura expirou, por favor entre em contato com o teu provedor"
                viewModelScope.launch { 
                    repository.clearPlaylist()
                    _isInitializing.value = false
                }
                return
            }

            // Tenta usar o servidor que funcionou antes ou o padrão
            val savedUrl = authManager.getXtreamUrl() ?: defaultServerUrl

            if (needsUpdate) {
                if (force) android.util.Log.d("PlayerViewModel", "Atualização manual solicitada")
                else android.util.Log.d("PlayerViewModel", "Atualização automática necessária ($intervalHours h)")
                
                _isAutoLoggingIn.value = !force // Só mostra splash se for auto login
                loadXtreamPlaylist(PlaylistCredentials("API", user, pass, savedUrl))
            } else {
                android.util.Log.d("PlayerViewModel", "Usando cache local (última atualização: ${((currentTime - lastUpdate)/3600000)} h atrás)")
                // Se não precisa de update, apenas carregamos as credenciais para uso futuro se necessário
                currentCredentials = PlaylistCredentials("API", user, pass, savedUrl)
            }
        }
    }

    fun toggleFavorite(streamId: String?) {
        val profileId = currentProfile.value?.id ?: return
        if (streamId == null) return
        viewModelScope.launch {
            repository.toggleFavorite(streamId, profileId)
        }
    }

    fun savePlaybackPosition(streamId: String?, position: Long, parentId: String? = null) {
        val profileId = currentProfile.value?.id ?: return
        if (streamId == null) return
        viewModelScope.launch {
            repository.savePlaybackPosition(streamId, profileId, position, parentId)
        }
    }

    fun deletePlaybackPosition(streamId: String?) {
        val profileId = currentProfile.value?.id ?: return
        if (streamId == null) return
        viewModelScope.launch {
            repository.deletePlaybackPosition(streamId, profileId)
        }
    }

    suspend fun getPlaybackPosition(streamId: String?): Long {
        val profileId = currentProfile.value?.id ?: return 0L
        if (streamId == null) return 0L
        return repository.getPlaybackPosition(streamId, profileId)
    }

    fun logout() {
        viewModelScope.launch {
            authManager.clearAll()
            repository.clearPlaylist()
            _userInfo.value = null
            _selectedChannel.value = null
        }
    }

    fun loginXtream(user: String, pass: String) {
        if (user.isBlank() || pass.isBlank()) {
            _errorMessage.value = "Preencha usuário e senha."
            return
        }
        loadXtreamPlaylist(PlaylistCredentials("API", user, pass, defaultServerUrl), save = true)
    }

    fun loginM3u(name: String, url: String) {
        if (name.isBlank() || url.isBlank()) {
            _errorMessage.value = "Preencha o nome e a URL da lista."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            var attempt = 0
            var success = false
            
            while (attempt < 3 && !success) {
                try {
                    val list = repository.loadM3uPlaylist(url)
                    if (list.isNotEmpty()) {
                        authManager.saveM3uData(name, url)
                        authManager.updateLastUpdateTime()
                        
                        // Cria um perfil de usuário "fake" para listas M3U
                        val fakeInfo = UserInfo(
                            username = name,
                            status = "Ativo (M3U)",
                            expiryDate = "Vitalício",
                            createdAt = "N/A"
                        )
                        authManager.saveUserInfo(fakeInfo)
                        _userInfo.value = fakeInfo
                        
                        success = true
                    } else {
                        _errorMessage.value = "URL inválida ou lista vazia."
                        break 
                    }
                } catch (e: Exception) {
                    attempt++
                    if (attempt < 3) {
                        _errorMessage.value = "Falha na ligação. Tentando novamente (${attempt}/3)..."
                        delay(2000L * attempt)
                    } else {
                        _errorMessage.value = "Erro ao carregar lista M3U após várias tentativas."
                    }
                }
            }
            _isLoading.value = false
            _isInitializing.value = false
        }
    }

    fun loadXtreamPlaylist(creds: PlaylistCredentials, save: Boolean = false) {
        viewModelScope.launch {
            if (!_isAutoLoggingIn.value) _isLoading.value = true
            _errorMessage.value = null
            
            // Lista Dinâmica de Tentativas: 
            // Começa com a URL fornecida e anexa os backups oficiais se não estiverem lá.
            val urlsToTry = mutableListOf<String>()
            urlsToTry.add(creds.url)
            xtreamServers.forEach { if (!urlsToTry.contains(it)) urlsToTry.add(it) }

            var success = false
            var authError = false
            
            for (apiUrl in urlsToTry) {
                if (success || authError) break
                
                val currentCreds = creds.copy(url = apiUrl)
                currentCredentials = currentCreds
                
                // Tentamos cada servidor em caso de falha técnica para failover rápido
                try {
                    android.util.Log.d("PlayerViewModel", "Tentando conexão: $apiUrl")
                    val (list, info) = repository.loadXtreamPlaylist(currentCreds)
                    
                    if (info == null) {
                        // Resposta com sucesso mas credenciais erradas (auth=0)
                        if (!_isAutoLoggingIn.value) _errorMessage.value = "Usuário ou senha incorretos."
                        authError = true
                        break 
                    }
                    
                    // Validação de Expiração
                    if (isSubscriptionExpired(info)) {
                        _errorMessage.value = "A sua assinatura expirou, por favor entre em contato com o teu provedor"
                        repository.clearPlaylist()
                        _isLoading.value = false
                        _isAutoLoggingIn.value = false
                        _isInitializing.value = false
                        return@launch
                    }

                    if (list.isNotEmpty()) {
                        authManager.updateLastUpdateTime()
                        authManager.saveXtreamUrl(apiUrl) // Salva o servidor "vencedor"
                        _userInfo.value = info
                        info.let { authManager.saveUserInfo(it) }
                        if (save) authManager.saveCredentials(creds.user, creds.pass)
                        
                        success = true
                        
                        // Carrega EPG em segundo plano
                        launch {
                            try {
                                repository.loadAllEpg(currentCreds)
                            } catch (e: Exception) {
                                android.util.Log.e("PlayerViewModel", "Erro no EPG: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PlayerViewModel", "Falha técnica em $apiUrl: ${e.message}")
                    // Se for o último servidor da lista, mostra erro técnico
                    if (apiUrl == urlsToTry.last()) {
                        _errorMessage.value = "Falha na ligação ao servidor. Verifique sua internet."
                    }
                    // Continua o loop para o próximo servidor
                }
            }
            
            _isLoading.value = false
            _isAutoLoggingIn.value = false
            _isInitializing.value = false
        }
    }

    fun loadSeriesDetails(series: Channel) {
        val creds = currentCredentials ?: return
        val seriesId = series.streamId ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _episodes.value = emptyList()
            try {
                val list = repository.fetchSeriesEpisodes(creds, seriesId)
                _episodes.value = list
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao carregar episódios."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadTrailer(channel: Channel) {
        val creds = currentCredentials ?: return
        val streamId = channel.streamId ?: return
        _currentTrailerUrl.value = null
        _currentDuration.value = null
        viewModelScope.launch {
            try {
                val result = repository.fetchVODInfo(creds, streamId, channel.type)
                _currentTrailerUrl.value = result.first
                _currentDuration.value = result.second
            } catch (e: Exception) {
                android.util.Log.e("PlayerViewModel", "Erro ao carregar info VOD: ${e.message}")
            }
        }
    }

    fun playNextEpisode(): Channel? {
        val current = _selectedChannel.value ?: return null
        
        if (current.type == com.stpplay.android.data.ContentType.SERIES) {
            val list = _episodes.value
            if (list.isEmpty()) return null
            val currentIndex = list.indexOfFirst { it.streamId == current.streamId }
            return if (currentIndex != -1 && currentIndex < list.size - 1) {
                val next = list[currentIndex + 1]
                _selectedChannel.value = next
                next
            } else null
        } else {
            val list = _channels.value.filter { it.type == current.type && it.group == current.group }
            if (list.isEmpty()) return null
            val currentIndex = list.indexOfFirst { it.streamId == current.streamId }
            return if (currentIndex != -1 && currentIndex < list.size - 1) {
                val next = list[currentIndex + 1]
                _selectedChannel.value = next
                next
            } else if (currentIndex != -1 && list.isNotEmpty()) {
                val next = list[0]
                _selectedChannel.value = next
                next
            } else null
        }
    }

    fun playPreviousEpisode(): Channel? {
        val current = _selectedChannel.value ?: return null
        
        if (current.type == com.stpplay.android.data.ContentType.SERIES) {
            val list = _episodes.value
            if (list.isEmpty()) return null
            val currentIndex = list.indexOfFirst { it.streamId == current.streamId }
            return if (currentIndex > 0) {
                val prev = list[currentIndex - 1]
                _selectedChannel.value = prev
                prev
            } else null
        } else {
            val list = _channels.value.filter { it.type == current.type && it.group == current.group }
            if (list.isEmpty()) return null
            val currentIndex = list.indexOfFirst { it.streamId == current.streamId }
            return if (currentIndex > 0) {
                val prev = list[currentIndex - 1]
                _selectedChannel.value = prev
                prev
            } else if (currentIndex != -1 && list.isNotEmpty()) {
                val prev = list.last()
                _selectedChannel.value = prev
                prev
            } else null
        }
    }

    fun selectChannel(channel: Channel) {
        _selectedChannel.value = channel
        saveLastChannel(channel)
        
        // Se for canal de TV, salvamos em playback_positions para rastrear "Vistos Recentemente"
        if (channel.type == com.stpplay.android.data.ContentType.LIVE) {
            savePlaybackPosition(channel.streamId ?: channel.url, 0L)
        }
    }

    private var lastPrefetchedId: String? = null

    fun onItemFocused(channel: Channel) {
        if (channel.streamId == lastPrefetchedId) return
        
        // Memória de foco para retorno de telas
        setLastFocusedStreamId(channel.streamId)
        prefetchChannelData(channel)
    }

    fun prefetchChannelData(channel: Channel) {
        if (channel.streamId == lastPrefetchedId) return
        lastPrefetchedId = channel.streamId

        viewModelScope.launch {
            // Pequeno delay para evitar prefetch durante scroll rápido
            delay(400)
            if (lastPrefetchedId != channel.streamId) return@launch

            if (channel.type == com.stpplay.android.data.ContentType.SERIES) {
                loadSeriesDetails(channel)
            }
            if (channel.type != com.stpplay.android.data.ContentType.LIVE) {
                loadTrailer(channel)
            }

            // Zero-Wait Play: Pré-aquecimento da conexão do stream
            if (channel.url.isNotBlank()) {
                prewarmStreamConnection(channel.url)
            }
        }
    }

    private fun prewarmStreamConnection(streamUrl: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val connection = URL(streamUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 2000
                connection.connect()
                connection.disconnect()
                android.util.Log.d("STP_PREWARM", "Conexão pré-aquecida para: $streamUrl")
            } catch (e: Exception) {
                // Silencioso
            }
        }
    }

    fun clearSelected() {
        _selectedChannel.value = null
    }

    // Profile Management
    fun selectProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            profileManager.selectProfile(profile)
        }
    }

    fun createProfile(name: String, isKids: Boolean, iconResId: Int) {
        viewModelScope.launch {
            profileManager.createProfile(name, isKids, iconResId)
        }
    }

    fun logoutProfile() {
        parentalManager.clearManualUnlocks()
        profileManager.logoutProfile()
    }

    fun loadEpg(streamId: String) {
        val creds = currentCredentials ?: return
        val channel = _channels.value.find { it.streamId == streamId }
        val epgChannelId = channel?.epgChannelId
        
        viewModelScope.launch {
            try {
                android.util.Log.d("EPG_DEBUG", "Solicitando EPG para: $streamId (EPG ID: $epgChannelId)")
                repository.loadEpg(creds, streamId, epgChannelId)
                updateCurrentProgram(streamId, epgChannelId)
                
                // Forçar log de contagem após carga
                repository.getUpcomingPrograms(streamId, epgChannelId).take(1).collect { list ->
                    android.util.Log.d("EPG_DEBUG", "Programas encontrados após carga: ${list.size}")
                }
            } catch (e: Exception) {
                android.util.Log.e("EPG_DEBUG", "Falha ao carregar EPG ($streamId): ${e.message}")
            }
        }
    }

    private suspend fun updateCurrentProgram(streamId: String, epgId: String?) {
        _currentProgram.value = repository.getCurrentProgram(streamId, epgId)
    }

    fun getCurrentProgramForChannel(streamId: String, epgId: String? = null): Flow<EpgProgramEntity?> {
        return repository.getCurrentProgramFlow(streamId, epgId)
    }

    fun getUpcomingPrograms(streamId: String, epgId: String? = null): Flow<List<EpgProgramEntity>> {
        return repository.getUpcomingPrograms(streamId, epgId)
    }

    fun setPipMode(inPip: Boolean) {
        _isInPipMode.value = inPip
    }

    fun setPlaybackActive(active: Boolean) {
        _isPlaybackActive.value = active
    }

    // Parental Control
    fun verifyParentalPin(pin: String) = parentalManager.verifyPin(pin)
    fun setParentalPin(pin: String) = parentalManager.setPin(pin)
    fun toggleCategoryLock(categoryId: String, locked: Boolean) = parentalManager.setCategoryLock(categoryId, locked)
    
    fun setHideLockedCategories(hide: Boolean) {
        parentalManager.setHideLockedCategories(hide)
    }
    
    fun addKeywordFilter(keyword: String) {
        parentalManager.addKeywordFilter(keyword)
        // Reaplica o bloqueio após adicionar palavra-chave
        viewModelScope.launch { applyAutomaticKeywordLock() }
    }
    
    fun removeKeywordFilter(keyword: String) {
        parentalManager.removeKeywordFilter(keyword)
    }

    fun setUpdateInterval(hours: Int) {
        authManager.setUpdateInterval(hours)
        _updateInterval.value = hours
    }

    fun setDefaultResizeMode(mode: Int) {
        settingsManager.setDefaultResizeMode(mode)
    }

    fun setShowClock(show: Boolean) {
        settingsManager.setShowClock(show)
    }

    fun setAutoPlayNext(auto: Boolean) {
        settingsManager.setAutoPlayNext(auto)
    }

    fun setBufferSize(size: Int) {
        settingsManager.setBufferSize(size)
    }

    fun setDecodingMode(mode: Int) {
        settingsManager.setDecodingMode(mode)
    }

    fun setPosterSize(size: Float) {
        settingsManager.setPosterSize(size)
    }

    fun setThemeColor(color: Long) {
        settingsManager.setThemeColor(color)
    }

    fun setStartOnBoot(start: Boolean) {
        settingsManager.setStartOnBoot(start)
    }

    fun setResumeLastChannel(resume: Boolean) {
        settingsManager.setResumeLastChannel(resume)
    }

    fun setCustomUserAgent(ua: String) {
        settingsManager.setCustomUserAgent(ua)
    }

    fun setLanguage(code: String) {
        settingsManager.setLanguage(code)
    }

    fun setLargeTextMode(enabled: Boolean) {
        settingsManager.setLargeTextMode(enabled)
    }

    // INTELLIGENT METHODS
    fun toggleReminder(program: EpgProgramEntity, channelName: String) {
        val profileId = currentProfile.value?.id ?: return
        val reminder = ReminderEntity(
            streamId = program.streamId,
            title = program.title,
            startTimestamp = program.startTimestamp,
            profileId = profileId
        )
        
        viewModelScope.launch {
            repository.toggleReminder(reminder)
            if (repository.hasReminder(reminder.streamId, reminder.startTimestamp, profileId)) {
                scheduleAlarm(reminder, channelName)
            } else {
                cancelAlarm(reminder)
            }
        }
    }

    private fun scheduleAlarm(reminder: ReminderEntity, channelName: String) {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("title", reminder.title)
            putExtra("channelName", channelName)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            (reminder.streamId + reminder.startTimestamp).hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Alerta 5 minutos antes
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
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    private fun cancelAlarm(reminder: ReminderEntity) {
        val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            (reminder.streamId + reminder.startTimestamp).hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun saveSearch(query: String) {
        val profileId = currentProfile.value?.id ?: return
        viewModelScope.launch {
            repository.saveSearch(query, profileId)
        }
    }

    fun clearSearchHistory() {
        val profileId = currentProfile.value?.id ?: return
        viewModelScope.launch {
            repository.clearSearchHistory(profileId)
        }
    }

    fun startSleepTimer(minutes: Int) {
        _sleepTimerMinutes.value = minutes
        if (minutes > 0) {
            viewModelScope.launch {
                while (_sleepTimerMinutes.value > 0) {
                    delay(60000)
                    _sleepTimerMinutes.value -= 1
                }
                _selectedChannel.value = null // Para a reprodução
            }
        }
    }

    fun runSpeedTest() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoading.value = true
            _downloadSpeed.value = 0f
            try {
                // Lista de URLs para tentar caso uma falhe
                val testUrls = listOf(
                    "http://ipv4.download.thinkbroadband.com/10MB.zip",
                    "http://speedtest.tele2.net/10MB.zip",
                    "https://speed.hetzner.de/10MB.bin"
                )
                
                var success = false
                for (urlStr in testUrls) {
                    try {
                        val startTime = System.currentTimeMillis()
                        val url = URL(urlStr)
                        val connection = url.openConnection() as java.net.HttpURLConnection
                        connection.connectTimeout = 8000
                        connection.readTimeout = 8000
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                        connection.connect()
                        
                        if (connection.responseCode !in 200..299) continue

                        val inputStream = connection.getInputStream()
                        var totalBytesRead = 0L
                        val buffer = ByteArray(16384)
                        val testLimitTime = 5000 
                        val maxTestData = 8 * 1024 * 1024L 
                        
                        while (totalBytesRead < maxTestData && (System.currentTimeMillis() - startTime) < testLimitTime) {
                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break
                            totalBytesRead += bytesRead
                        }
                        
                        inputStream.close()
                        connection.disconnect()
                        
                        val endTime = System.currentTimeMillis()
                        val durationSecs = (endTime - startTime) / 1000f
                        
                        if (durationSecs > 0.5f && totalBytesRead > 100000) {
                            val speedMbps = (totalBytesRead * 8) / (durationSecs * 1000000f)
                            _downloadSpeed.value = speedMbps
                            success = true
                            android.util.Log.d("SpeedTest", "Sucesso com $urlStr: $speedMbps Mbps")
                            break
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SpeedTest", "Falha com $urlStr: ${e.message}")
                    }
                }
                
                if (!success) _downloadSpeed.value = -1f
                
            } catch (e: Exception) {
                android.util.Log.e("SpeedTest", "Erro crítico: ${e.message}")
                _downloadSpeed.value = -1f
            } finally {
                _isLoading.value = false
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun clearImageCache() {
        context.imageLoader.diskCache?.clear()
        context.imageLoader.memoryCache?.clear()
    }

    fun saveLastChannel(channel: Channel) {
        settingsManager.saveLastChannel(channel.url)
    }

    fun tryResumeLastChannel() {
        if (resumeLastChannel.value) {
            val url = settingsManager.getLastChannel()
            if (url != null) {
                val channel = _channels.value.find { it.url == url }
                if (channel != null) {
                    _selectedChannel.value = channel
                }
            }
        }
    }

    fun getSavedUser(): String? = authManager.getCredentials().first

    fun refreshSubscriptionStatus() {
        val (user, pass) = authManager.getCredentials()
        if (user.isNullOrBlank() || pass.isNullOrBlank()) return
        
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val (list, info) = repository.loadXtreamPlaylist(PlaylistCredentials("API", user, pass, defaultServerUrl))
                if (info != null) {
                    authManager.saveUserInfo(info)
                    _userInfo.value = info
                    if (isSubscriptionExpired(info)) {
                        _errorMessage.value = "A sua assinatura (vencida em ${info.expiryDate}) ainda não foi renovada no servidor."
                    } else {
                        // Sucesso: Assinatura renovada
                        checkSavedCredentials(force = true)
                    }
                } else {
                    _errorMessage.value = "Não foi possível conectar ao servidor. Tente novamente."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erro na verificação: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

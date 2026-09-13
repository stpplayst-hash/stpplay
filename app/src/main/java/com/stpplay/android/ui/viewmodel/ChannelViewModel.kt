package com.stpplay.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stpplay.android.data.Channel
import com.stpplay.android.data.ChannelRepository
import com.stpplay.android.data.ContentType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val repository: ChannelRepository
) : ViewModel() {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadChannels()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun loadChannels() {
        viewModelScope.launch {
            repository.getAllChannels().collectLatest {
                _channels.value = it
            }
        }
    }

    fun refreshPlaylist(url: String) {
        viewModelScope.launch {
            _isLoading.value = true
            repository.refreshChannels(url)
            _isLoading.value = false
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            repository.toggleFavorite(channel)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun filterByType(type: ContentType) {
        viewModelScope.launch {
            repository.getChannelsByType(type).collectLatest {
                _channels.value = it
            }
        }
    }
}

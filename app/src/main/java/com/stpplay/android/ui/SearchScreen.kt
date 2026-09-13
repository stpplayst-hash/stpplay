package com.stpplay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.stpplay.android.R
import com.stpplay.android.data.Channel
import com.stpplay.android.data.ContentType
import com.stpplay.android.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    viewModel: PlayerViewModel,
    favorites: Set<String>,
    onNavigate: (Screen) -> Unit,
    onShowDetail: (Channel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val channels by viewModel.channels.collectAsState()
    val lockedCategories by viewModel.lockedCategories.collectAsState()
    val shouldHideLocked by viewModel.shouldHideLockedCategories.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState(initial = emptyList())
    val focusRequester = remember { FocusRequester() }

    var showParentalDialog by remember { mutableStateOf(false) }
    var pendingChannel by remember { mutableStateOf<Channel?>(null) }
    var parentalAction by remember { mutableStateOf<((Channel) -> Unit)?>(null) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp > 720
    val isTVLayout = isLandscape || isWideScreen

    val filteredResults = remember(channels, searchQuery, lockedCategories, shouldHideLocked) {
        if (searchQuery.isBlank()) emptyList()
        else channels.filter { channel ->
            val matchesSearch = channel.name.contains(searchQuery, ignoreCase = true)
            val isHidden = shouldHideLocked && lockedCategories.contains(channel.group?.trim())
            matchesSearch && !isHidden
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            delay(2000)
            viewModel.saveSearch(searchQuery)
        }
    }

    val liveResults = filteredResults.filter { it.type == ContentType.LIVE }
    val movieResults = filteredResults.filter { it.type == ContentType.MOVIE }
    val seriesResults = filteredResults.filter { it.type == ContentType.SERIES }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun checkParentalAndExecute(channel: Channel, action: (Channel) -> Unit) {
        if (lockedCategories.contains(channel.group?.trim())) {
            pendingChannel = channel
            parentalAction = action
            showParentalDialog = true
        } else {
            action(channel)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().background(StpBackground)) {
            if (isTVLayout) {
                PremiumNavigationRail(currentScreen = Screen.Search, onNavigate = onNavigate)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = StpOnSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = StpSurface,
                        unfocusedContainerColor = StpSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = StpOutline
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (searchQuery.isBlank()) {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        if (recentSearches.isNotEmpty()) {
                            Text(stringResource(R.string.recent_searches), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            recentSearches.forEach { search ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { searchQuery = search.query }.padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.History, null, tint = StpOnSurfaceVariant, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(search.query, color = Color.White, fontSize = 15.sp)
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            }
                            TextButton(onClick = { viewModel.clearSearchHistory() }, modifier = Modifier.align(Alignment.End)) {
                                Text(stringResource(R.string.clear_history), color = StpOnSurfaceVariant, fontSize = 12.sp)
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Search, null, modifier = Modifier.size(64.dp), tint = StpSurfaceHigh)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(stringResource(R.string.search_empty_hint), color = StpOnSurfaceVariant, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                } else if (filteredResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_results_found, searchQuery), color = StpOnSurfaceVariant)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
                        if (liveResults.isNotEmpty()) {
                            item {
                                SearchSectionHeader(stringResource(R.string.live_channels))
                                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(liveResults) { channel ->
                                        Box(modifier = Modifier.width(200.dp)) {
                                            ChannelListRow(
                                                channel = channel, 
                                                isFavorite = favorites.contains(channel.streamId), 
                                                viewModel = viewModel, 
                                                onClick = { 
                                                    checkParentalAndExecute(channel) { viewModel.selectChannel(it) }
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        if (movieResults.isNotEmpty()) {
                            item {
                                SearchSectionHeader(stringResource(R.string.movies_title))
                                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(movieResults) { movie ->
                                        PosterItemDesign(
                                            channel = movie, 
                                            isFavorite = favorites.contains(movie.streamId), 
                                            onFavClick = { viewModel.toggleFavorite(movie.streamId) }, 
                                            onClick = { 
                                                checkParentalAndExecute(movie) { onShowDetail(it) }
                                            }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }

                        if (seriesResults.isNotEmpty()) {
                            item {
                                SearchSectionHeader(stringResource(R.string.series_title))
                                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(seriesResults) { series ->
                                        PosterItemDesign(
                                            channel = series, 
                                            isFavorite = favorites.contains(series.streamId), 
                                            onFavClick = { viewModel.toggleFavorite(series.streamId) }, 
                                            onClick = { 
                                                checkParentalAndExecute(series) { onShowDetail(it) }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showParentalDialog) {
            ParentalPinDialog(
                onDismiss = { showParentalDialog = false },
                onVerify = { viewModel.verifyParentalPin(it) },
                onSuccess = {
                    showParentalDialog = false
                    pendingChannel?.let { parentalAction?.invoke(it) }
                },
                title = stringResource(R.string.restricted_access),
                description = stringResource(R.string.enter_pin_to_access, pendingChannel?.group ?: "")
            )
        }
    }
}

@Composable
fun SearchSectionHeader(title: String) {
    Text(text = title.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
}

package com.stpplay.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stpplay.android.R
import com.stpplay.android.data.Channel
import com.stpplay.android.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    favorites: Set<String>,
    onNavigate: (Screen) -> Unit,
    onShowDetail: (Channel) -> Unit,
    onProfileClick: () -> Unit
) {
    var globalQuery by remember { mutableStateOf("") }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp > 720
    val isTVLayout = isLandscape || isWideScreen
    val isSmallHeight = configuration.screenHeightDp < 500

    val recommendations by viewModel.recommendations.collectAsState()
    val becauseYouWatched by viewModel.becauseYouWatched.collectAsState()
    val continueWatching by viewModel.continueWatchingWithProgress.collectAsState()
    val top10 by viewModel.top10.collectAsState()
    val lockedCategories by viewModel.lockedCategories.collectAsState()

    val homeMovies by viewModel.homeMovies.collectAsState()
    val homeSeries by viewModel.homeSeries.collectAsState()
    val carouselItems by viewModel.carouselItems.collectAsState()
    val posterSizeMultiplier by viewModel.posterSize.collectAsState()
    val isHomeDataReady by viewModel.isHomeDataReady.collectAsState()
    val favItems by viewModel.favoriteChannels.collectAsState()

    var showParentalDialog by remember { mutableStateOf(false) }
    var pendingChannel by remember { mutableStateOf<Channel?>(null) }
    var parentalAction by remember { mutableStateOf<((Channel) -> Unit)?>(null) }

    val featuredFocusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()

    // Requisitar foco inicial na Home para Android TV
    LaunchedEffect(Unit) {
        if (isTVLayout) {
            delay(800) 
            try { 
                if (viewModel.lastFocusedStreamId.value == null) {
                    featuredFocusRequester.requestFocus() 
                }
            } catch (e: Exception) {}
        }
    }

    var carouselIndex by remember { mutableIntStateOf(0) }
    var focusedItem by remember { mutableStateOf<Channel?>(null) }
    
    // Rotação Automática do Banner (apenas se nada estiver focado manualmente)
    LaunchedEffect(carouselItems, focusedItem) {
        if (focusedItem == null && carouselItems.isNotEmpty()) {
            while(true) {
                delay(8000)
                carouselIndex = (carouselIndex + 1) % carouselItems.size
            }
        }
    }
    
    val currentBannerImage = remember(focusedItem, carouselItems, carouselIndex) {
        focusedItem?.logo ?: if (carouselItems.isNotEmpty()) carouselItems[carouselIndex % carouselItems.size].logo else null
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

    LaunchedEffect(carouselIndex, carouselItems, focusedItem) {
        if (focusedItem == null && carouselItems.isNotEmpty()) {
            val currentCarouselItem = carouselItems[carouselIndex % carouselItems.size]
            viewModel.prefetchChannelData(currentCarouselItem)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(StpBackground)) {
        KenBurnsBackdrop(imageUrl = currentBannerImage)

        if (!isHomeDataReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isTVLayout) {
                    PremiumNavigationRail(
                        currentScreen = Screen.Home,
                        onNavigate = onNavigate
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (isTVLayout) 12.dp else 0.dp)
                    ) {
                        // 1. BANNER FIXO NO TOPO
                        if (carouselItems.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(top = if (isTVLayout) 24.dp else 100.dp)
                                    .padding(bottom = 16.dp)
                            ) {
                                focusedItem?.let {
                                    FeaturedInfo(
                                        item = it,
                                        isExpanded = true,
                                        isSmallHeight = isSmallHeight,
                                        onShowDetail = { onShowDetail(it) },
                                        focusRequester = featuredFocusRequester
                                    )
                                } ?: run {
                                    val current = carouselItems[carouselIndex % carouselItems.size]
                                    FeaturedInfo(
                                        item = current,
                                        isExpanded = true,
                                        isSmallHeight = isSmallHeight,
                                        onShowDetail = { onShowDetail(current) },
                                        focusRequester = featuredFocusRequester
                                    )
                                }
                            }
                        }

                        // 2. CATEGORIAS DESLIZANTES
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            // 2. CONTINUAR ASSISTINDO
                            if (continueWatching.isNotEmpty()) {
                                item {
                                    NetflixCategoryRowWithProgress(
                                        title = stringResource(R.string.continue_watching),
                                        items = continueWatching,
                                        posterSizeMultiplier = posterSizeMultiplier,
                                        onShowDetail = { checkParentalAndExecute(it, onShowDetail) },
                                        onFocus = { focusedItem = it; viewModel.onItemFocused(it) }
                                    )
                                }
                            }

                            // 3. FAVORITOS
                            if (favItems.isNotEmpty()) {
                                item {
                                    NetflixCategoryRow(
                                        title = stringResource(R.string.favorites),
                                        items = favItems,
                                        posterSizeMultiplier = posterSizeMultiplier,
                                        onShowDetail = { checkParentalAndExecute(it, onShowDetail) },
                                        onFocus = { focusedItem = it; viewModel.onItemFocused(it) }
                                    )
                                }
                            }

                            // 4. TOP 10
                            if (top10.isNotEmpty()) {
                                item {
                                    Top10CategoryRow(
                                        title = stringResource(R.string.top_10_today),
                                        items = top10,
                                        posterSizeMultiplier = posterSizeMultiplier,
                                        onShowDetail = { checkParentalAndExecute(it, onShowDetail) },
                                        onFocus = { focusedItem = it; viewModel.onItemFocused(it) }
                                    )
                                }
                            }

                            // 5. RECOMENDAÇÕES PARA VOCÊ
                            if (recommendations.isNotEmpty()) {
                                item {
                                    NetflixCategoryRow(
                                        title = stringResource(R.string.for_you),
                                        items = recommendations,
                                        posterSizeMultiplier = posterSizeMultiplier,
                                        onShowDetail = { checkParentalAndExecute(it, onShowDetail) },
                                        onFocus = { focusedItem = it; viewModel.onItemFocused(it) }
                                    )
                                }
                            }

                            // 6. PORQUE VOCÊ ASSISTIU
                            becauseYouWatched?.let { (last, list) ->
                                if (list.isNotEmpty()) {
                                    item {
                                        NetflixCategoryRow(
                                            title = stringResource(R.string.because_you_watched, last.name),
                                            items = list,
                                            posterSizeMultiplier = posterSizeMultiplier,
                                            onShowDetail = { checkParentalAndExecute(it, onShowDetail) },
                                            onFocus = { focusedItem = it; viewModel.onItemFocused(it) }
                                        )
                                    }
                                }
                            }

                            // 7. FILMES RECENTES
                            if (homeMovies.isNotEmpty()) {
                                item {
                                    NetflixCategoryRow(
                                        title = stringResource(R.string.featured_movies),
                                        items = homeMovies,
                                        posterSizeMultiplier = posterSizeMultiplier,
                                        onShowDetail = { checkParentalAndExecute(it, onShowDetail) },
                                        onFocus = { focusedItem = it; viewModel.onItemFocused(it) }
                                    )
                                }
                            }

                            // 8. SÉRIES RECENTES
                            if (homeSeries.isNotEmpty()) {
                                item {
                                    NetflixCategoryRow(
                                        title = stringResource(R.string.featured_series),
                                        items = homeSeries,
                                        posterSizeMultiplier = posterSizeMultiplier,
                                        onShowDetail = { checkParentalAndExecute(it, onShowDetail) },
                                        onFocus = { focusedItem = it; viewModel.onItemFocused(it) }
                                    )
                                }
                            }
                        }
                    }

                    if (isTVLayout) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.TopEnd
                        ) {
                            TvClock()
                        }
                    }
                }
            }

            // Top Bar (Sempre visível se não for TV)
            if (!isTVLayout) {
                NetflixTopBar(
                    onSearchClick = { onNavigate(Screen.Search) },
                    onProfileClick = onProfileClick
                )
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
            title = stringResource(R.string.unlock_category),
            description = stringResource(R.string.enter_pin_to_access, pendingChannel?.group ?: "")
        )
    }
}

package com.stpplay.android.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.stpplay.android.R
import com.stpplay.android.data.Channel
import com.stpplay.android.data.ContentType
import com.stpplay.android.data.ChannelWithProgress
import com.stpplay.android.database.EpgProgramEntity
import com.stpplay.android.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TvVodScreen(
    favorites: Set<String>, 
    viewModel: PlayerViewModel, 
    screenTitle: String, 
    onNavigate: (Screen) -> Unit,
    onShowDetail: (Channel) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 500
    
    val channels by viewModel.channels.collectAsState()
    val items = remember(channels, screenTitle) {
        val type = if (screenTitle.contains("Filmes", true)) ContentType.MOVIE else ContentType.SERIES
        channels.filter { it.type == type }
    }
    
    val lockedCategories by viewModel.lockedCategories.collectAsState()
    val shouldHideLocked by viewModel.shouldHideLockedCategories.collectAsState()
    val keywords by viewModel.keywordFilters.collectAsState()
    val posterSizeMultiplier by viewModel.posterSize.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val isKids = currentProfile?.isKids == true

    val allLabel = stringResource(R.string.all)
    val favoritesLabel = stringResource(R.string.favorites_label)

    val safeItems = remember(items, keywords, isKids, lockedCategories, shouldHideLocked) {
        val filtered = if (isKids) {
            items.filter { Channel.isKidsContent(it) }
        } else {
            // Se a opção de ocultar está desligada, mostramos tudo (o PIN protege o acesso)
            if (shouldHideLocked) {
                items.filter { channel ->
                    val group = channel.group?.trim() ?: ""
                    keywords.none { group.contains(it, ignoreCase = true) }
                }
            } else {
                items
            }
        }

        if (shouldHideLocked) {
            filtered.filter { !lockedCategories.contains(it.group?.trim()) }
        } else {
            filtered
        }
    }

    val groups = remember(items, lockedCategories, shouldHideLocked, screenTitle, allLabel, favoritesLabel) { 
        val list = mutableListOf(allLabel, favoritesLabel)
        
        val priorityKeywords = if (screenTitle.contains("Filmes", true)) {
            listOf("lançamentos", "recém adicionados", "q.cinema", "4k")
        } else {
            listOf("lançamentos", "recém adicionados", "netflix", "globoplay", "hbo")
        }

        val contentGroups = items.asSequence()
            .mapNotNull { it.group }
            .distinct()
            .filter { !shouldHideLocked || !lockedCategories.contains(it.trim()) }
            .sortedWith(compareBy<String> { group ->
                val normalized = group.lowercase()
                val index = priorityKeywords.indexOfFirst { keyword -> normalized.contains(keyword) }
                if (index != -1) index else Int.MAX_VALUE
            }.thenBy { it })
            .toList()

        list.addAll(contentGroups)
        list
    }
    
    var selectedGroup by remember(allLabel) { mutableStateOf(allLabel) }
    var sortOrder by remember { mutableStateOf("Adicionado") } 

    var userFocusedItem by remember { mutableStateOf<Channel?>(null) }
    var autoRotationIndex by remember { mutableIntStateOf(0) }

    val pagedItems = remember(selectedGroup, screenTitle, sortOrder) {
        val type = if (screenTitle.contains("Filmes", true)) ContentType.MOVIE else ContentType.SERIES
        val isFavs = selectedGroup == favoritesLabel
        val group = if (selectedGroup == allLabel || isFavs) null else selectedGroup
        viewModel.getChannelsByCategoryPaging(type, group, isFavs, sortOrder)
    }.collectAsLazyPagingItems()

    // Itens para o banner (usamos a lista em memória para garantir rotação imediata e estável)
    val bannerPool = remember(safeItems, selectedGroup, favoritesLabel, allLabel, favorites) {
        val pool = when (selectedGroup) {
            allLabel -> {
                // Inteligência: Prioriza Lançamentos quando estiver em "Tudo"
                val releases = safeItems.filter { channel ->
                    val group = (channel.group ?: "").uppercase()
                    group.contains("LANÇAMENTO") || group.contains("ESTREIA") || group.contains("NEW")
                }
                if (releases.isNotEmpty()) releases else safeItems
            }
            favoritesLabel -> safeItems.filter { favorites.contains(it.streamId) }
            else -> safeItems.filter { it.group == selectedGroup }
        }
        pool.filter { !it.logo.isNullOrBlank() }.take(15)
    }

    val currentBannerPool by rememberUpdatedState(bannerPool)

    // Lógica de Rotação Automática do Banner (Independente de atualizações da lista para estabilidade)
    LaunchedEffect(userFocusedItem) {
        if (userFocusedItem == null) {
            while(true) {
                delay(8000)
                val pool = currentBannerPool
                if (pool.isNotEmpty()) {
                    autoRotationIndex = (autoRotationIndex + 1) % pool.size
                }
            }
        }
    }

    val bannerItem = remember(userFocusedItem, autoRotationIndex, bannerPool) {
        if (userFocusedItem != null) userFocusedItem
        else if (bannerPool.isNotEmpty()) bannerPool[autoRotationIndex % bannerPool.size]
        else null
    }

    // Prefetch silencioso para o item do banner
    LaunchedEffect(bannerItem) {
        bannerItem?.let { viewModel.prefetchChannelData(it) }
    }

    var showUnlockDialog by remember { mutableStateOf(false) }
    var groupToUnlock by remember { mutableStateOf<String?>(null) }

    val featuredFocusRequester = remember { FocusRequester() }
    val sidebarFocusRequester = remember { FocusRequester() }
    val sortRowFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(800)
        try { sidebarFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize().background(StpBackground)) {
        KenBurnsBackdrop(imageUrl = bannerItem?.logo)

        // Relógio flutuante no topo direito (não empurra o conteúdo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, end = 24.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            TvClock()
        }

        Row(modifier = Modifier.fillMaxSize()) {
            PremiumNavigationRail(
                currentScreen = if(screenTitle.contains("Filmes", true) || screenTitle.contains("Movies", true)) Screen.Movies else Screen.Series,
                onNavigate = onNavigate
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, top = 24.dp, end = 24.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 16.dp else 24.dp)) {
                    // 1. SIDEBAR DE CATEGORIAS
                    Column(modifier = Modifier.weight(0.28f)) {
                        Text(
                            text = screenTitle.uppercase(), 
                            color = Color.White, 
                            fontSize = if (isCompactHeight) 22.sp else 28.sp, 
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            itemsIndexed(groups) { index, group ->
                                val isSelected = selectedGroup == group
                                var isFocused by remember { mutableStateOf(false) }
                                
                                // PREFETCH LOGIC
                                LaunchedEffect(isFocused) {
                                    if (isFocused && !isSelected) {
                                        delay(300)
                                        selectedGroup = group
                                    }
                                }

                                val isLocked = lockedCategories.contains(group.trim())
                                val count = remember(safeItems, group) {
                                    when(group) {
                                        allLabel -> safeItems.size
                                        favoritesLabel -> safeItems.count { favorites.contains(it.streamId) }
                                        else -> safeItems.count { it.group == group }
                                    }
                                }

                                Surface(
                                    onClick = { 
                                        if (isLocked) {
                                            groupToUnlock = group
                                            showUnlockDialog = true
                                        } else {
                                            selectedGroup = group 
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (index == 0) Modifier.focusRequester(sidebarFocusRequester) else Modifier)
                                        .onFocusChanged { 
                                            isFocused = it.isFocused
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isFocused) MaterialTheme.colorScheme.primary else if (isSelected) StpSurfaceHigh else Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = group,
                                            color = if (isFocused) Color.Black else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE),
                                            maxLines = 1
                                        )
                                        if (count > 0) {
                                            Text(
                                                text = count.toString(),
                                                color = if (isFocused) Color.Black.copy(0.6f) else StpOnSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. ÁREA DE CONTEÚDO (DESTAQUE + GRID)
                    Column(modifier = Modifier.weight(0.72f)) {
                        // INFO DO DESTAQUE
                        Box(modifier = Modifier.height(if (isCompactHeight) 200.dp else 250.dp).fillMaxWidth()) {
                            bannerItem?.let { item ->
                                FeaturedInfo(
                                    item = item,
                                    isExpanded = true,
                                    isSmallHeight = isCompactHeight,
                                    onShowDetail = onShowDetail,
                                    focusRequester = featuredFocusRequester
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Chips de Ordenação
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp), 
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .focusRequester(sortRowFocusRequester),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.sort_by),
                                color = Color.White.copy(0.4f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            val sortOptions = listOf(
                                "Adicionado" to R.string.sort_added,
                                "Nome" to R.string.sort_name,
                                "IMDb" to R.string.sort_imdb,
                                "Ano" to R.string.sort_year
                            )
                            sortOptions.forEachIndexed { index, (internalOrder, resId) ->
                                val isSelected = sortOrder == internalOrder
                                var isChipFocused by remember { mutableStateOf(false) }
                                
                                Surface(
                                    onClick = { sortOrder = internalOrder },
                                    modifier = Modifier
                                        .then(if (index == 0) Modifier.focusRequester(sortRowFocusRequester) else Modifier)
                                        .onFocusChanged { isChipFocused = it.isFocused },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isChipFocused) Color.White else if (isSelected) MaterialTheme.colorScheme.primary.copy(0.2f) else Color.Transparent,
                                    border = if (isChipFocused) null else BorderStroke(1.dp, if(isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.1f))
                                ) {
                                    Text(
                                        text = stringResource(resId),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        color = if (isChipFocused) Color.Black else if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.6f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (isCompactHeight) (90 * posterSizeMultiplier).dp else (120 * posterSizeMultiplier).dp),
                            modifier = Modifier.focusProperties { 
                                up = sortRowFocusRequester
                            },
                            contentPadding = PaddingValues(bottom = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            items(
                                count = pagedItems.itemCount,
                                key = pagedItems.itemKey { it.streamId ?: it.url }
                            ) { index ->
                                val item = pagedItems[index]
                                
                                // SMART PRELOAD: Carregar as próximas 10 imagens em background
                                val context = LocalContext.current
                                LaunchedEffect(index) {
                                    for (i in 1..10) {
                                        if (index + i < pagedItems.itemCount) {
                                            val nextItem = pagedItems[index + i]
                                            if (nextItem?.logo != null) {
                                                val request = coil.request.ImageRequest.Builder(context)
                                                    .data(nextItem.logo)
                                                    .size(coil.size.Size.ORIGINAL)
                                                    .build()
                                                coil.Coil.imageLoader(context).enqueue(request)
                                            }
                                        }
                                    }
                                }

                                if (item != null) {
                                    NetflixPosterItem(
                                        channel = item,
                                        posterSizeMultiplier = posterSizeMultiplier,
                                        modifier = Modifier.fillMaxWidth(),
                                        onFocus = { 
                                            userFocusedItem = item
                                            viewModel.onItemFocused(item)
                                        },
                                        onClick = { 
                                            if (lockedCategories.contains(item.group?.trim())) {
                                                groupToUnlock = item.group
                                                showUnlockDialog = true
                                            } else {
                                                onShowDetail(item) 
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showUnlockDialog) {
            ParentalPinDialog(
                onDismiss = { showUnlockDialog = false },
                onVerify = { viewModel.verifyParentalPin(it) },
                onSuccess = {
                    showUnlockDialog = false
                    selectedGroup = groupToUnlock ?: allLabel
                },
                title = stringResource(R.string.unlock_category),
                description = stringResource(R.string.enter_pin_to_access, groupToUnlock ?: "")
            )
        }
    }
}

@Composable
fun ContentDetailScreen(
    channel: Channel, 
    viewModel: PlayerViewModel, 
    onBack: () -> Unit, 
    onPlay: (Channel) -> Unit,
    onShowDetail: (Channel) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp > 720
    val isTVLayout = isLandscape || isWideScreen
    
    val allChannels by viewModel.channels.collectAsState()
    val episodesWithProgress by viewModel.episodesWithProgress.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val posterSizeMultiplier by viewModel.posterSize.collectAsState()
    val currentTrailerUrl by viewModel.currentTrailerUrl.collectAsState()
    val fetchedDuration by viewModel.currentDuration.collectAsState()
    val context = LocalContext.current
    val isFavorite = favorites.contains(channel.streamId)
    
    val similarContent = remember(allChannels, channel) {
        allChannels.filter { 
            (it.categoryId == channel.categoryId || it.group == channel.group) && 
            it.type == channel.type && 
            it.streamId != channel.streamId 
        }.shuffled().take(15)
    }

    val upcomingPrograms by remember(channel) {
        if (channel.type == ContentType.LIVE && channel.streamId != null) {
            viewModel.getUpcomingPrograms(channel.streamId!!, channel.epgChannelId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val cleanName = remember(channel.name) { channel.name.replace(Regex("\\(\\d{4}\\)"), "").trim() }
    val sidePadding = if (isTVLayout) 48.dp else 20.dp

    LaunchedEffect(channel) { 
        if (channel.type == ContentType.SERIES) viewModel.loadSeriesDetails(channel)
        channel.streamId?.let {
            if (channel.type == ContentType.LIVE) viewModel.loadEpg(it)
            viewModel.loadTrailer(channel)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(StpBackground)) {
        // BACKDROP FULLSCREEN
        KenBurnsBackdrop(imageUrl = channel.logo)
        
        // SCRIM PARA LEGIBILIDADE
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.8f),
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.8f),
                            StpBackground
                        )
                    )
                )
        )

        // CONTEÚDO PRINCIPAL
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isTVLayout) 24.dp else 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (channel.type == ContentType.SERIES) stringResource(R.string.screen_series) else stringResource(R.string.screen_movies),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = if (isTVLayout) 20.sp else 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (isTVLayout) TvClock()
            }

            // ROLAGEM DO CONTEÚDO
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = sidePadding)
            ) {
                // 2. HERO SECTION ADAPTATIVO
                if (isTVLayout) {
                    // MODO SMART TV / PAISAGEM (SPLIT-VIEW)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Card(
                            modifier = Modifier.width(180.dp).aspectRatio(2 / 3f),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(16.dp)
                        ) {
                            AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, error = painterResource(id = R.drawable.logo))
                        }
                        Spacer(modifier = Modifier.width(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            InfoContent(channel, cleanName, episodesWithProgress, isTVLayout = true, fetchedDuration = fetchedDuration)
                        }
                    }
                } else {
                    // MODO TELEMÓVEL RETRATO (STACKED)
                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(
                            modifier = Modifier.width(160.dp).aspectRatio(2 / 3f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, error = painterResource(id = R.drawable.logo))
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        InfoContent(channel, cleanName, episodesWithProgress, isTVLayout = false, fetchedDuration = fetchedDuration)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 3. BOTÕES DE AÇÃO HORIZONTAIS
                Row(
                    modifier = Modifier.fillMaxWidth(if (isTVLayout) 0.5f else 1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val lastWatched = episodesWithProgress.lastOrNull { it.progress > 0.05f }
                    val nextToWatch = if (lastWatched != null) {
                        val idx = episodesWithProgress.indexOf(lastWatched)
                        if (idx < episodesWithProgress.size - 1) episodesWithProgress[idx + 1] else lastWatched
                    } else episodesWithProgress.firstOrNull()

                    SugarActionButton(
                        text = stringResource(R.string.watch),
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.weight(1f),
                        onClick = { 
                            if (channel.type == ContentType.SERIES) nextToWatch?.let { onPlay(it.channel) }
                            else onPlay(channel)
                        }
                    )
                    
                    SugarActionButton(
                        text = stringResource(R.string.favorites_label),
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.toggleFavorite(channel.streamId) }
                    )

                    if (!currentTrailerUrl.isNullOrBlank()) {
                        SugarActionButton(
                            text = "Trailer",
                            icon = Icons.Default.Movie,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(currentTrailerUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.could_not_open_link), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                // 4. ÁREA DE EPISÓDIOS
                if (channel.type == ContentType.SERIES) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        val seasons = remember(episodesWithProgress) { episodesWithProgress.groupBy { it.channel.group ?: "Temporada 1" } }
                        var selectedSeasonName by remember(seasons) { mutableStateOf(seasons.keys.firstOrNull()) }
                        
                        if (seasons.isNotEmpty()) {
                            // SELETOR DE TEMPORADAS
                            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                items(seasons.keys.toList()) { season ->
                                    val isSelected = selectedSeasonName == season
                                    var isFocused by remember { mutableStateOf(false) }
                                    Column(modifier = Modifier.onFocusChanged { isFocused = it.isFocused }.clickable { selectedSeasonName = season }) {
                                        Text(text = season, color = if (isFocused || isSelected) Color.White else Color.White.copy(alpha = 0.5f), fontSize = if (isTVLayout) 18.sp else 16.sp, fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                                        Box(modifier = Modifier.width(40.dp).height(2.dp).background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            // LISTA DE EPISÓDIOS
                            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                                seasons[selectedSeasonName]?.let { seasonEpisodes ->
                                    items(seasonEpisodes) { ep ->
                                        val seasonNumber = remember(selectedSeasonName) { seasonEpisodes.firstOrNull()?.channel?.group?.filter { it.isDigit() } ?: "1" }
                                        SugarEpisodeCard(episode = ep.channel, seriesLogo = channel.logo, progress = ep.progress, seasonPrefix = "S$seasonNumber", episodeIndex = (seasonEpisodes.indexOf(ep) + 1).toString(), isTVLayout = isTVLayout, onClick = { onPlay(ep.channel) })
                                    }
                                }
                            }
                        }
                    }
                } else if (channel.type == ContentType.LIVE) {
                    LiveContentArea(upcomingPrograms, viewModel, channel.name)
                }

                // 5. CONTEÚDOS SEMELHANTES (Apenas para Filmes e Séries)
                if (channel.type != ContentType.LIVE && similarContent.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (channel.type == ContentType.MOVIE) stringResource(R.string.similar_movies) else stringResource(R.string.similar_series),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 40.dp)
                    ) {
                        items(similarContent) { item ->
                            NetflixPosterItem(
                                channel = item,
                                posterSizeMultiplier = posterSizeMultiplier,
                                onClick = { onShowDetail(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoContent(channel: Channel, cleanName: String, episodesWithProgress: List<ChannelWithProgress>, isTVLayout: Boolean, fetchedDuration: String? = null) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val isLargeText by viewModel.largeTextMode.collectAsState()
    
    Column {
        var isTitleFocused by remember { mutableStateOf(false) }
        Text(
            text = cleanName, 
            color = if (isTitleFocused) MaterialTheme.colorScheme.primary else Color.White, 
            fontSize = if (isTVLayout) (if(isLargeText) 50.sp else 42.sp) else 28.sp, 
            fontWeight = FontWeight.Black, 
            lineHeight = if (isTVLayout) (if(isLargeText) 56.sp else 48.sp) else 34.sp,
            modifier = Modifier
                .focusable()
                .onFocusChanged { isTitleFocused = it.isFocused }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = channel.year ?: "2024", color = Color.White.copy(alpha = 0.6f), fontSize = if(isLargeText) 18.sp else 14.sp)
            
            val durationToShow = channel.duration ?: fetchedDuration
            formatDuration(durationToShow)?.let {
                Text(text = it, color = Color.White.copy(alpha = 0.6f), fontSize = if(isLargeText) 18.sp else 14.sp, fontWeight = FontWeight.Bold)
            }

            if (channel.type == ContentType.SERIES) {
                val seasonCount = remember(episodesWithProgress) { episodesWithProgress.map { it.channel.group }.distinct().size.coerceAtLeast(1) }
                val seasonLabel = if (seasonCount == 1) stringResource(R.string.seasons).removeSuffix("S") else stringResource(R.string.seasons)
                Text(text = "$seasonCount $seasonLabel", color = Color.White.copy(alpha = 0.6f), fontSize = if(isLargeText) 18.sp else 14.sp)
            }
            val rating = channel.rating
            if (!rating.isNullOrBlank() && rating != "0" && rating != "0.0") {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                    Text(text = "IMDb $rating", color = Color.Black, fontSize = if(isLargeText) 15.sp else 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        var isDescFocused by remember { mutableStateOf(false) }
        Text(
            text = channel.description ?: stringResource(R.string.no_description), 
            color = if (isDescFocused) Color.White else Color.White.copy(alpha = 0.9f), 
            fontSize = if(isLargeText) 20.sp else 15.sp, 
            lineHeight = if(isLargeText) 28.sp else 22.sp, 
            maxLines = if (isTVLayout) (if(isLargeText) 3 else 4) else 10, 
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .focusable()
                .onFocusChanged { isDescFocused = it.isFocused }
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (!channel.genre.isNullOrEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Movie, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = channel.genre!!, color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!channel.cast.isNullOrEmpty()) {
            Text(text = channel.cast!!, color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun SugarActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp).onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) Color.White else MaterialTheme.colorScheme.primary,
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), 
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = text.uppercase(), 
                color = Color.Black, 
                fontSize = 13.sp, 
                fontWeight = FontWeight.Black, 
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun SugarEpisodeCard(
    episode: Channel,
    seriesLogo: String?,
    progress: Float,
    seasonPrefix: String,
    episodeIndex: String,
    isTVLayout: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.width(if (isTVLayout) 220.dp else 180.dp).onFocusChanged { isFocused = it.isFocused }.clickable { onClick() }) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(RoundedCornerShape(8.dp)).border(width = if (isFocused) 3.dp else 0.dp, color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)).background(StpSurfaceHigh)) {
            val fallbackPainter = rememberAsyncImagePainter(model = seriesLogo, error = painterResource(id = R.drawable.logo))
            AsyncImage(model = episode.logo ?: seriesLogo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, error = fallbackPainter, fallback = fallbackPainter)
            Surface(color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(topEnd = 8.dp), modifier = Modifier.align(Alignment.BottomStart)) {
                Text(text = "$seasonPrefix.E$episodeIndex", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = if (isFocused) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(14.dp))
            if (progress > 0.01f) {
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomStart).background(Color.Black.copy(0.5f))) {
                    Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = episode.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun LiveContentArea(
    programs: List<EpgProgramEntity>,
    viewModel: PlayerViewModel,
    channelName: String
) {
    Column {
        Text(stringResource(R.string.epg_guide), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(16.dp))
        if (programs.isNotEmpty()) {
            programs.forEach { program ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .background(StpSurfaceHigh.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(16.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val time = remember(program) {
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        "${sdf.format(Date(program.startTimestamp))} - ${sdf.format(Date(program.stopTimestamp))}"
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(program.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(time, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { viewModel.toggleReminder(program, channelName) }) {
                        Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        } else {
            Text(stringResource(R.string.no_epg_available), color = Color.Gray, fontSize = 14.sp)
        }
    }
}

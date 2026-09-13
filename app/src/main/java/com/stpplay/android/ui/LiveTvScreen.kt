package com.stpplay.android.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.stpplay.android.R
import com.stpplay.android.data.Channel
import com.stpplay.android.database.EpgProgramEntity
import com.stpplay.android.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TvLiveTvScreen(
    favorites: Set<String>,
    viewModel: PlayerViewModel,
    onNavigate: (Screen) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isCompactHeight = configuration.screenHeightDp < 500
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var categorySearch by remember { mutableStateOf("") }
    var channelSearch by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(false) }
    val lockedCategories by viewModel.lockedCategories.collectAsState()
    val shouldHideLocked by viewModel.shouldHideLockedCategories.collectAsState()
    val activeReminders by viewModel.activeReminders.collectAsState()
    val recentLiveChannels by viewModel.recentLiveChannels.collectAsState()
    val liveCategories by viewModel.liveCategories.collectAsState()
    val favoriteChannels by viewModel.favoriteChannels.collectAsState()
    
    val allGroups = remember(liveCategories, lockedCategories, shouldHideLocked, recentLiveChannels, favoriteChannels) { 
        val priorityKeywords = listOf("filmes e series", "jogos do dia", "infantil", "globo sudeste", "record", "portugal", "portugal esportes", "hbo")
        val list = mutableListOf<String>()
        if (recentLiveChannels.isNotEmpty()) list.add("Recentes")
        if (favoriteChannels.isNotEmpty()) list.add("Favoritos")

        val contentGroups = liveCategories.asSequence()
            .filter { !shouldHideLocked || !lockedCategories.contains(it.trim()) }
            .sortedWith(
                compareBy<String> { group ->
                    val normalized = group.trim().lowercase().replace("é", "e").replace("á", "a").replace("ã", "a")
                    val index = priorityKeywords.indexOfFirst { normalized.contains(it) }
                    if (index != -1) index else Int.MAX_VALUE
                }.thenBy { it }
            )
            .toList()
        
        list.addAll(contentGroups)
        list
    }
    val filteredGroups = remember(allGroups, categorySearch) {
        allGroups.filter { it.contains(categorySearch, ignoreCase = true) }
    }
    
    var selectedGroup by remember { mutableStateOf("") }
    var showUnlockDialog by remember { mutableStateOf(value = false) }
    var groupToUnlock by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(allGroups) {
        if (selectedGroup.isEmpty() || !allGroups.contains(selectedGroup)) {
            selectedGroup = allGroups.firstOrNull() ?: ""
        }
    }
    
    val channels by viewModel.channels.collectAsState()
    val groupChannels = remember(channels, selectedGroup, recentLiveChannels, favoriteChannels) { 
        when (selectedGroup) {
            "Recentes" -> recentLiveChannels
            "Favoritos" -> favoriteChannels
            else -> channels.filter { it.group == selectedGroup } 
        }
    }
    val filteredChannels = remember(groupChannels, channelSearch) {
        groupChannels.filter { it.name.contains(channelSearch, ignoreCase = true) }
    }
    
    var focusedChannel by remember { mutableStateOf(filteredChannels.firstOrNull()) }
    var selectedEpgProgram by remember { mutableStateOf<EpgProgramEntity?>(null) }
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(1000)
        try { initialFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    val upcomingPrograms by remember(focusedChannel) {
        selectedEpgProgram = null
        if (focusedChannel != null && focusedChannel!!.streamId != null) {
            viewModel.getUpcomingPrograms(focusedChannel!!.streamId!!, focusedChannel!!.epgChannelId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsState(initial = emptyList())

    val previewPlayer = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_ONE; volume = 0f } }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> 
            if (event == Lifecycle.Event.ON_PAUSE) previewPlayer.pause()
            else if (event == Lifecycle.Event.ON_RESUME) previewPlayer.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { 
            lifecycleOwner.lifecycle.removeObserver(observer)
            previewPlayer.release()
        }
    }

    LaunchedEffect(filteredChannels) {
        if (filteredChannels.isNotEmpty() && (focusedChannel == null || !filteredChannels.contains(focusedChannel))) {
            focusedChannel = filteredChannels.first()
        }
    }

    LaunchedEffect(focusedChannel) {
        focusedChannel?.let { channel ->
            viewModel.loadEpg(channel.streamId ?: "")
            previewPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(channel.url)))
            previewPlayer.prepare()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(StpBackground)) {
        // Backdrop
        AnimatedContent(targetState = focusedChannel, label = "bg") { channel ->
            channel?.let {
                AsyncImage(model = it.logo, contentDescription = null, modifier = Modifier.fillMaxSize().blur(40.dp), contentScale = ContentScale.Crop, alpha = 0.2f)
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, StpBackground.copy(alpha = 0.5f), StpBackground))))

        Row(modifier = Modifier.fillMaxSize()) {
            PremiumNavigationRail(currentScreen = Screen.Live, onNavigate = onNavigate)

            Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp, start = 12.dp, end = if (isCompactHeight) 16.dp else 32.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = if (isCompactHeight) 8.dp else 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.canais), color = Color.White, fontSize = if (isCompactHeight) 22.sp else 28.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(24.dp))
                        
                        // BOTÃO DE ALTERNÂNCIA (LISTA / GRADE)
                        var isToggleFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.height(36.dp).onFocusChanged { isToggleFocused = it.isFocused },
                            shape = RoundedCornerShape(18.dp),
                            color = if (isToggleFocused) Color.White else Color.White.copy(alpha = 0.1f),
                            border = if (isToggleFocused) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Icon(
                                    if (isGridView) Icons.AutoMirrored.Filled.List else Icons.Default.GridView, 
                                    null, 
                                    tint = if (isToggleFocused) Color.Black else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (isGridView) stringResource(R.string.mode_list) else stringResource(R.string.mode_grid), 
                                    color = if (isToggleFocused) Color.Black else Color.White, 
                                    fontSize = 11.sp, 
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                    TvClock()
                }

                if (isGridView) {
                    TvEpgGrid(
                        channels = filteredChannels,
                        viewModel = viewModel,
                        onChannelClick = { viewModel.selectChannel(it) }
                    )
                } else {
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (isCompactHeight) 12.dp else 20.dp)) {
                        // CATEGORIAS
                        Column(modifier = Modifier.weight(0.40f)) {
                            OutlinedTextField(
                                value = categorySearch,
                                onValueChange = { categorySearch = it },
                                placeholder = { Text(stringResource(R.string.search_category), color = StpOnSurfaceVariant, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).height(if (isCompactHeight) 44.dp else 56.dp),
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = StpSurface, unfocusedContainerColor = StpSurface, focusedBorderColor = MaterialTheme.colorScheme.primary)
                            )
                            
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                items(filteredGroups) { group ->
                                    var isFocused by remember { mutableStateOf(false) }
                                    val isSelected = selectedGroup == group
                                    val isLocked = lockedCategories.contains(group.trim())
                                    val animatedBg by animateColorAsState(if (isFocused) MaterialTheme.colorScheme.primary else if (isSelected) StpSurfaceHigh else Color.Transparent, label = "bg")
                                    
                                    Surface(
                                        onClick = { if (isLocked) { groupToUnlock = group; showUnlockDialog = true } else { selectedGroup = group } },
                                        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused; if (it.isFocused && !isLocked) selectedGroup = group },
                                        shape = RoundedCornerShape(12.dp),
                                        color = animatedBg
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (isLocked) {
                                                Icon(Icons.Default.Lock, null, tint = if (isFocused) Color.Black else StpRed, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                            }
                                            val icon = when (group) {
                                                "Recentes" -> Icons.Default.History
                                                "Favoritos" -> Icons.Default.Favorite
                                                else -> null
                                            }
                                            if (icon != null) {
                                                Icon(icon, null, tint = if (group == "Favoritos") StpRed else if (isFocused) Color.Black else MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                            }

                                            Text(text = group, color = if (isFocused) Color.Black else Color.White, fontSize = 13.sp, fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f).basicMarquee(iterations = Int.MAX_VALUE))
                                            if (!isLocked && group != "Recentes" && group != "Favoritos") Text(text = channels.count { it.group == group }.toString(), color = if (isFocused) Color.Black.copy(0.6f) else StpOnSurfaceVariant, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // CANAIS
                        Column(modifier = Modifier.weight(0.60f)) {
                            OutlinedTextField(
                                value = channelSearch,
                                onValueChange = { channelSearch = it },
                                placeholder = { Text(stringResource(R.string.search_channels), color = StpOnSurfaceVariant, fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).height(if (isCompactHeight) 44.dp else 56.dp),
                                shape = RoundedCornerShape(24.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = StpSurface, unfocusedContainerColor = StpSurface, focusedBorderColor = MaterialTheme.colorScheme.primary)
                            )

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredChannels) { channel ->
                                    var isFocused by remember { mutableStateOf(false) }
                                    val isSelected = focusedChannel?.streamId == channel.streamId
                                    Surface(
                                        onClick = { viewModel.selectChannel(channel) },
                                        modifier = Modifier.fillMaxWidth().then(if (filteredChannels.indexOf(channel) == 0) Modifier.focusRequester(initialFocusRequester) else Modifier).onFocusChanged { isFocused = it.isFocused; if (it.isFocused) focusedChannel = channel },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isFocused) Color.White.copy(0.1f) else if (isSelected) MaterialTheme.colorScheme.primary.copy(0.1f) else Color.Transparent,
                                        border = if (isFocused) BorderStroke(2.dp, Color.White) else null
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(StpSurfaceHigh), contentScale = ContentScale.Crop, error = painterResource(R.drawable.logo))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = channel.name, color = if (isFocused) Color.White else if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.9f), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                                val currentProg by viewModel.getCurrentProgramForChannel(channel.streamId ?: "").collectAsState(initial = null)
                                                if (currentProg != null) Text(text = currentProg!!.title, color = if (isFocused) Color.White.copy(0.7f) else StpOnSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                                            }
                                            
                                            val isFav = favorites.contains(channel.streamId)
                                            var isHeartFocused by remember { mutableStateOf(false) }
                                            IconButton(
                                                onClick = { viewModel.toggleFavorite(channel.streamId) },
                                                modifier = Modifier.size(36.dp).onFocusChanged { isHeartFocused = it.isFocused }.background(if (isHeartFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                                            ) {
                                                Icon(imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (isHeartFocused) Color.White else if (isFav) StpRed else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // PREVIEW & EPG
                        Column(modifier = Modifier.weight(0.45f)) {
                            Card(modifier = Modifier.fillMaxWidth().aspectRatio(16/9f), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color.White.copy(0.1f))) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                                    AndroidView(factory = { PlayerView(it).apply { player = previewPlayer; useController = false } }, modifier = Modifier.fillMaxSize())
                                    
                                    // ZAPPING INSTANTÂNEO: Mostrar Logo HD enquanto o vídeo carrega
                                    var isVideoReady by remember { mutableStateOf(false) }
                                    LaunchedEffect(focusedChannel) {
                                        isVideoReady = false
                                        delay(1500) // Simular tempo de buffer para garantir transição suave
                                        isVideoReady = true
                                    }

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = !isVideoReady,
                                        enter = fadeIn(),
                                        exit = fadeOut(tween(800))
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                                            AsyncImage(
                                                model = focusedChannel?.logo,
                                                contentDescription = null,
                                                modifier = Modifier.size(100.dp).blur(if(!isVideoReady) 0.dp else 20.dp),
                                                contentScale = ContentScale.Fit,
                                                alpha = 0.6f
                                            )
                                        }
                                    }

                                    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f)))))
                                    
                                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(focusedChannel?.name ?: "", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                            focusedChannel?.let { channel ->
                                                val isFav = favorites.contains(channel.streamId)
                                                var isFavFocused by remember { mutableStateOf(false) }
                                                Surface(
                                                    onClick = { viewModel.toggleFavorite(channel.streamId) },
                                                    modifier = Modifier.size(40.dp).onFocusChanged { isFavFocused = it.isFocused },
                                                    shape = CircleShape,
                                                    color = if (isFavFocused) Color.White else Color.Black.copy(alpha = 0.5f),
                                                    border = if (isFavFocused) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (isFavFocused) Color.Black else if (isFav) StpRed else Color.White, modifier = Modifier.size(20.dp))
                                                    }
                                                }
                                            }
                                        }
                                        
                                        val currentProg by if (focusedChannel?.streamId != null) viewModel.getCurrentProgramForChannel(focusedChannel!!.streamId!!).collectAsState(initial = null) else remember { mutableStateOf(null) }
                                        if (currentProg != null) {
                                            val progress = remember(currentProg) { val now = System.currentTimeMillis(); if (currentProg!!.stopTimestamp > currentProg!!.startTimestamp) (now - currentProg!!.startTimestamp).toFloat() / (currentProg!!.stopTimestamp - currentProg!!.startTimestamp) else 0f }
                                            Text(currentProg!!.title, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.6f).height(2.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = Color.White.copy(0.1f))
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.programming_label), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                                items(upcomingPrograms) { program ->
                                    var isFocused by remember { mutableStateOf(false) }
                                    val isSelected = selectedEpgProgram == program
                                    val hasReminder = activeReminders.any { it.streamId == program.streamId && it.startTimestamp == program.startTimestamp }
                                    val time = remember(program) { val sdf = SimpleDateFormat("HH:mm", Locale.getDefault()); "${sdf.format(Date(program.startTimestamp))} - ${sdf.format(Date(program.stopTimestamp))}" }
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected && !isFocused) StpSurfaceHigh else Color.Transparent)
                                    ) {
                                        Surface(
                                            onClick = { selectedEpgProgram = if (isSelected) null else program },
                                            modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isFocused) Color.White.copy(0.1f) else Color.Transparent
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = time, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(text = program.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                                                if (hasReminder) Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        
                                        if (isSelected) {
                                            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                                Text(
                                                    text = program.description ?: stringResource(R.string.no_description), 
                                                    color = Color.White.copy(0.6f), 
                                                    fontSize = 12.sp, 
                                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                                )
                                                
                                                var isBtnFocused by remember { mutableStateOf(false) }
                                                Button(
                                                    onClick = { viewModel.toggleReminder(program, focusedChannel?.name ?: "") }, 
                                                    modifier = Modifier
                                                        .onFocusChanged { isBtnFocused = it.isFocused }
                                                        .height(32.dp), 
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isBtnFocused) Color.White else (if (hasReminder) Color.White.copy(0.1f) else MaterialTheme.colorScheme.primary)
                                                    ), 
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                    border = if (hasReminder || isBtnFocused) BorderStroke(1.dp, if (isBtnFocused) MaterialTheme.colorScheme.primary else Color.White.copy(0.3f)) else null
                                                ) {
                                                    Icon(
                                                        if (hasReminder) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive, 
                                                        null, 
                                                        tint = if (isBtnFocused) Color.Black else (if (hasReminder) Color.White else Color.Black), 
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(
                                                        if (hasReminder) stringResource(R.string.remove_caps) else stringResource(R.string.reminder_label), 
                                                        color = if (isBtnFocused) Color.Black else (if (hasReminder) Color.White else Color.Black), 
                                                        fontSize = 10.sp, 
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
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
                selectedGroup = groupToUnlock ?: ""
            },
            title = stringResource(R.string.unlock_category),
            description = stringResource(R.string.enter_pin_to_access, groupToUnlock ?: "")
        )
    }
}

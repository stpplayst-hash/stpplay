package com.stpplay.android.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.stpplay.android.data.Channel
import com.stpplay.android.data.ContentType
import com.stpplay.android.database.EpgProgramEntity
import com.stpplay.android.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.nativeCanvas


sealed class Screen(val icon: ImageVector, val label: String) {
    object Search : Screen(Icons.Default.Search, "Busca Inteligente")
    object Home : Screen(Icons.Default.Home, "Home")
    object Live : Screen(Icons.Default.Tv, "Live TV")
    object Movies : Screen(Icons.Default.Movie, "Filmes")
    object Series : Screen(Icons.Default.VideoLibrary, "Séries")
    object Settings : Screen(Icons.Default.Settings, "Definições")
    object Profile : Screen(Icons.Default.Person, "Perfil")
}

@Composable
fun PlayerApp() {
    val viewModel: PlayerViewModel = hiltViewModel()
    val channels by viewModel.channels.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val isAutoLoggingIn by viewModel.isAutoLoggingIn.collectAsState()
    val isInitializing by viewModel.isInitializing.collectAsState()
    val isDataEmpty by viewModel.isDataEmpty.collectAsState()
    val isSubscriptionValid by viewModel.isSubscriptionValid.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val profiles by viewModel.profiles.collectAsState(initial = emptyList())
    val hasParentalPin by viewModel.hasParentalPin.collectAsState()

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var detailChannel by remember { mutableStateOf<Channel?>(null) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var showExitConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context.findActivity()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp > 720
    val isTVLayout = isLandscape || isWideScreen

    LaunchedEffect(currentScreen) { selectedGroup = null }

    val backHandlerEnabled = selectedChannel == null && (detailChannel != null || selectedGroup != null || currentScreen != Screen.Home)
    
    BackHandler(enabled = backHandlerEnabled) {
        when {
            detailChannel != null -> detailChannel = null
            selectedGroup != null -> {
                // Se estiver no modo TV e o grupo for limpo, pode haver necessidade de reajustar foco
                selectedGroup = null
            }
            currentScreen != Screen.Home -> currentScreen = Screen.Home
        }
    }

    // BackHandler para fechar o app
    BackHandler(enabled = !backHandlerEnabled && selectedChannel == null) {
        showExitConfirmDialog = true
    }

    if (showExitConfirmDialog) {
        AnimatedExitDialog(
            onDismiss = { showExitConfirmDialog = false },
            onConfirm = { activity?.finish() }
        )
    }

    if (selectedChannel != null) {
        PremiumPlayerScreen(
            channel = selectedChannel!!,
            viewModel = viewModel,
            onBack = { viewModel.clearSelected() }
        )
    } else if (detailChannel != null) {
        ContentDetailScreen(
            channel = detailChannel!!,
            viewModel = viewModel,
            onBack = { detailChannel = null },
            onPlay = { 
                viewModel.selectChannel(it)
                // detailChannel = null // Comentado para permitir voltar aos detalhes após fechar o player
            },
            onShowDetail = { detailChannel = it }
        )
    } else if (isInitializing || isAutoLoggingIn) {
        SplashScreen()
    } else if (isDataEmpty || !isSubscriptionValid) {
        SetupScreen(viewModel, isLoading, error)
    } else if (currentProfile == null) {
        ProfileSelectionScreen(
            profiles = profiles,
            hasParentalPin = hasParentalPin,
            onVerifyPin = { viewModel.verifyParentalPin(it) },
            onSelect = { viewModel.selectProfile(it) },
            onCreate = { name, isKids -> viewModel.createProfile(name, isKids, 0) }
        )
    } else {
        if (isTVLayout) {
            // LAYOUT PARA SMART TV (Menu Lateral Integrado nas Telas)
            Box(modifier = Modifier.fillMaxSize().background(StpBackground)) {
                // Conteúdo Principal com Transição Animada
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400, easing = LinearOutSlowInEasing)) + 
                         slideInHorizontally(animationSpec = tween(400, easing = LinearOutSlowInEasing), initialOffsetX = { 20 }))
                        .togetherWith(
                            fadeOut(animationSpec = tween(300)) + 
                            slideOutHorizontally(animationSpec = tween(300), targetOffsetX = { -20 })
                        )
                    },
                    label = "screen_transition"
                ) { targetScreen ->
                    when (targetScreen) {
                        Screen.Search -> SearchScreen(viewModel, favorites, onNavigate = { currentScreen = it }, onShowDetail = { detailChannel = it })
                        Screen.Home -> HomeScreen(
                            favorites = favorites,
                            viewModel = viewModel,
                            onNavigate = { currentScreen = it },
                            onShowDetail = { detailChannel = it },
                            onProfileClick = { currentScreen = Screen.Profile }
                        )
                        Screen.Live -> {
                            TvLiveTvScreen(favorites, viewModel, onNavigate = { currentScreen = it })
                        }
                        Screen.Movies -> {
                            TvVodScreen(favorites, viewModel, "Filmes", onNavigate = { currentScreen = it }, onShowDetail = { detailChannel = it })
                        }
                        Screen.Series -> {
                            TvVodScreen(favorites, viewModel, "Séries", onNavigate = { currentScreen = it }, onShowDetail = { detailChannel = it })
                        }
                        Screen.Settings -> SettingsScreen(viewModel, onNavigate = { currentScreen = it })
                        Screen.Profile -> ProfileScreen(viewModel, onNavigate = { currentScreen = it })
                    }
                }
            }
        } else {
            // LAYOUT PARA SMARTPHONE (Menu Inferior Imersivo)
            Scaffold(
                containerColor = Color.Transparent, // Essencial para o efeito vidro funcionar
                bottomBar = {
                    PremiumBottomNavigation(
                        currentScreen = currentScreen,
                        onScreenSelected = { currentScreen = it }
                    )
                }
            ) { paddingValues ->
                // O conteúdo agora preenche toda a tela, ignorando os paddings do Scaffold.
                // Usamos o padding bottom apenas para o container de conteúdo das telas
                // para que a imagem do background flua por trás de tudo.
                Box(modifier = Modifier.fillMaxSize()) {
                    val contentModifier = Modifier.padding(bottom = paddingValues.calculateBottomPadding())
                    
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(250))
                        },
                        label = "mobile_transition"
                    ) { targetScreen ->
                        when (targetScreen) {
                            Screen.Search -> Box(modifier = contentModifier) { SearchScreen(viewModel, favorites, onNavigate = { currentScreen = it }, onShowDetail = { detailChannel = it }) }
                            Screen.Home -> HomeScreen(
                                favorites = favorites,
                                viewModel = viewModel,
                                onNavigate = { currentScreen = it },
                                onShowDetail = { detailChannel = it },
                                onProfileClick = { currentScreen = Screen.Profile }
                            )
                            Screen.Live -> {
                                CategorizedFlowScreen("CANAIS AO VIVO", channels.filter { it.type == ContentType.LIVE }, favorites, viewModel, selectedGroup, { selectedGroup = it }, onShowDetail = { detailChannel = it })
                            }
                            Screen.Movies -> {
                                val movies = channels.filter { it.type == ContentType.MOVIE }
                                CategorizedFlowScreen("FILMES", movies, favorites, viewModel, selectedGroup, { selectedGroup = it }, onShowDetail = { detailChannel = it })
                            }
                            Screen.Series -> {
                                val series = channels.filter { it.type == ContentType.SERIES }
                                CategorizedFlowScreen("SÉRIES", series, favorites, viewModel, selectedGroup, { selectedGroup = it }, onShowDetail = { detailChannel = it })
                            }
                            Screen.Settings -> Box(modifier = contentModifier) { SettingsScreen(viewModel, onNavigate = { currentScreen = it }) }
                            Screen.Profile -> Box(modifier = contentModifier) { ProfileScreen(viewModel, onNavigate = { currentScreen = it }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumBottomNavigation(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    GlassySurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            // Desativamos os insets automáticos para controlar manualmente com o padding
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier
                .navigationBarsPadding() // Garante que o conteúdo fique ACIMA dos botões do sistema
                .height(76.dp) // Altura fixa generosa apenas para os ícones e labels
        ) {
            val screens = listOf(Screen.Home, Screen.Live, Screen.Movies, Screen.Series, Screen.Settings)
            screens.forEach { screen ->
                NavigationBarItem(
                    selected = currentScreen == screen,
                    onClick = { onScreenSelected(screen) },
                    icon = { Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(24.dp)) },
                    label = { Text(screen.label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = StpOnSurfaceVariant,
                        unselectedTextColor = StpOnSurfaceVariant,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PremiumPlayerScreen(channel: Channel, viewModel: PlayerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()
    
    // Garantir modo imersivo total durante a reprodução
    LaunchedEffect(Unit) {
        activity?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val currentProgram by viewModel.currentProgram.collectAsState()
    val isInPipMode by viewModel.isInPipMode.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp > 720
    val isTVLayout = isLandscape || isWideScreen
    val isCompactHeight = configuration.screenHeightDp < 500

    val bufferSize by viewModel.bufferSize.collectAsState()
    val decodingMode by viewModel.decodingMode.collectAsState()
    val customUA by viewModel.customUserAgent.collectAsState()

    val player = remember(bufferSize, decodingMode, customUA) {
        val userAgent = if (customUA.isNullOrBlank()) "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36" else customUA
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setUserAgent(userAgent!!)
            
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpDataSourceFactory)
        
        // BUFFER CONFIG OTIMIZADA PARA REPRODUÇÃO INSTANTÂNEA
        val minBuffer = if (bufferSize == 0) 15000 else 35000
        val maxBuffer = if (bufferSize == 0) 50000 else 80000
        val bufferForPlayback = if (bufferSize == 0) 1000 else 2500
        val bufferForPlaybackAfterRebuffer = if (bufferSize == 0) 2500 else 5000
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBuffer, 
                maxBuffer, 
                bufferForPlayback, 
                bufferForPlaybackAfterRebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
            
        // DECODING CONFIG
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(if (decodingMode == 0) 
                androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON 
                else androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
        }
            
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build()
    }

    val displayList by viewModel.quickList.collectAsState()

    LaunchedEffect(player, channel.streamId) {
        viewModel.loadQuickList(channel)
        if (channel.type == ContentType.LIVE) channel.streamId?.let { viewModel.loadEpg(it) }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(channel.url)))
        player.prepare()
        player.playWhenReady = true
    }

    var currentTime by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(value = true) }
    var showControls by remember { mutableStateOf(value = true) }
    var showExitDialog by remember { mutableStateOf(value = false) }
    var showResumeDialog by remember { mutableStateOf(value = false) }
    var savedPosition by remember { mutableStateOf(0L) }
    var lastInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    val defaultResizeMode by viewModel.defaultResizeMode.collectAsState()
    var resizeMode by remember(defaultResizeMode) { mutableStateOf(defaultResizeMode) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var trackTypeToSelect by remember { mutableStateOf(androidx.media3.common.C.TRACK_TYPE_TEXT) }
    var showQuickMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var showBingeOverlay by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    val playPauseFocusRequester = remember { FocusRequester() }
    val progressFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    // Gesture States
    var showGestureFeedback by remember { mutableStateOf(false) }
    var gestureIcon by remember { mutableStateOf(Icons.AutoMirrored.Filled.VolumeUp) }
    var gestureText by remember { mutableStateOf("") }
    var gestureProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(lastInteraction) { 
        showControls = true
        delay(4500)
        if (!showQuickMenu && !showTrackDialog) showControls = false 
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            if (!showQuickMenu && !showTrackDialog) {
                delay(100)
                try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
            }
        } else {
            try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Initial focus
    LaunchedEffect(Unit) {
        delay(500)
        try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
    }
    
    LaunchedEffect(player) { 
        if (channel.type != ContentType.LIVE) { 
            savedPosition = viewModel.getPlaybackPosition(channel.streamId)
            if (savedPosition > 5000) showResumeDialog = true 
        } 
    }

    // Gerenciamento robusto do botão voltar no Player
    BackHandler(enabled = !isInPipMode) {
        when {
            showQuickMenu -> showQuickMenu = false
            showTrackDialog -> showTrackDialog = false
            showControls -> showControls = false
            showExitDialog -> showExitDialog = false
            else -> showExitDialog = true
        }
    }

    // Use rememberUpdatedState para evitar problemas de captura no observer do ciclo de vida
    val currentIsInPipMode by rememberUpdatedState(isInPipMode)

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event -> 
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                val act = context.findActivity()
                val isActivityInPip = act?.isInPictureInPictureMode ?: false
                
                // Só pausa se não estiver em PiP (tanto detectado pelo sistema quanto pelo ViewModel)
                if (!isActivityInPip && !currentIsInPipMode) {
                    player.pause() 
                }
            }
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { 
                if (state == Player.STATE_READY) {
                    duration = player.duration
                    playbackError = null
                }
                if (state == Player.STATE_BUFFERING) {
                    playbackError = null
                }
                if (state == Player.STATE_ENDED) { 
                    viewModel.deletePlaybackPosition(channel.streamId)
                    if (channel.type == ContentType.SERIES) { 
                        val next = viewModel.playNextEpisode()
                        if (next != null) { 
                            player.setMediaItem(MediaItem.fromUri(Uri.parse(next.url)))
                            player.prepare()
                            player.play() 
                        } else onBack() 
                    } else onBack() 
                } 
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlayerError", "Erro ExoPlayer: ${error.errorCodeName} (${error.errorCode})")
                playbackError = when (error.errorCode) {
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Sem conexão com a internet."
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "O link do canal está offline ou é inválido."
                    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED -> "Este formato de vídeo não é suportado pelo seu hardware."
                    else -> "Não foi possível reproduzir este conteúdo. Tente novamente mais tarde."
                }
            }
            override fun onPlayWhenReadyChanged(p: Boolean, r: Int) { 
                isPlaying = p 
                viewModel.setPlaybackActive(p)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        player.addListener(listener)
        onDispose { 
            if (channel.type != ContentType.LIVE) {
                val pos = player.currentPosition
                val dur = player.duration
                if (dur > 0 && pos > dur * 0.95) {
                    viewModel.deletePlaybackPosition(channel.streamId)
                } else {
                    viewModel.savePlaybackPosition(channel.streamId, pos, channel.parentId)
                }
            }
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release() 
            viewModel.setPlaybackActive(false)
        }
    }

    LaunchedEffect(isPlaying) { 
        while (isPlaying) { 
            currentTime = player.currentPosition
            
            // Lógica Binge-Watch: Aparece 20s antes do fim em Séries/Filmes
            if (channel.type != ContentType.LIVE && duration > 60000) {
                val remaining = duration - currentTime
                showBingeOverlay = remaining in 1000..20000
            }
            
            delay(1000) 
        } 
    }
    LaunchedEffect(playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }

    fun formatTime(ms: Long): String { 
        val totalSecs = ms / 1000
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        return if (h > 0) String.format(Locale.US, "%02d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s) 
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (keyEvent.key == Key.Back) return@onKeyEvent false

                lastInteraction = System.currentTimeMillis()
                
                // Se controles estiverem ocultos, qualquer tecla (exceto Back) deve mostrá-los
                if (!showControls && !showQuickMenu && !showTrackDialog) {
                    showControls = true
                    
                    when (keyEvent.key) {
                        Key.DirectionCenter, Key.Enter -> { 
                            if (player.isPlaying) player.pause() else player.play()
                        }
                        Key.DirectionUp -> viewModel.playPreviousEpisode()
                        Key.DirectionDown -> viewModel.playNextEpisode()
                    }
                    return@onKeyEvent true
                }

                // Se controles estiverem visíveis, lidar APENAS com teclas de escape/atalhos globais que NÃO sejam navegação
                if (keyEvent.key == Key.Escape) {
                    showQuickMenu = false
                    showTrackDialog = false
                    showControls = false
                    return@onKeyEvent true
                }
                
                false
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (!isInPipMode) {
                            if (showQuickMenu) showQuickMenu = false
                            else if (showTrackDialog) showTrackDialog = false
                            else lastInteraction = System.currentTimeMillis()
                        }
                    },
                    onDoubleTap = { offset ->
                        val isRightSide = offset.x > size.width / 2
                        if (isRightSide) {
                            player.seekTo(player.currentPosition + 10000)
                        } else {
                            player.seekTo(player.currentPosition - 10000)
                        }
                        lastInteraction = System.currentTimeMillis()
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { showGestureFeedback = true },
                    onDragEnd = { showGestureFeedback = false },
                    onVerticalDrag = { change, dragAmount ->
                        val isRightSide = change.position.x > size.width / 2
                        if (isRightSide) {
                            // VOLUME
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val delta = -(dragAmount / 50).toInt()
                            val newVol = (currentVol + delta).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            
                            gestureIcon = if (newVol == 0) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                            gestureText = "Volume"
                            gestureProgress = newVol.toFloat() / maxVol
                        } else {
                            // BRIGHTNESS
                            activity?.let { act ->
                                val params = act.window.attributes
                                val currentBrightness = if (params.screenBrightness < 0) 0.5f else params.screenBrightness
                                val delta = -dragAmount / 1000f
                                val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                params.screenBrightness = newBrightness
                                act.window.attributes = params
                                
                                gestureIcon = Icons.Default.Brightness6
                                gestureText = "Brilho"
                                gestureProgress = newBrightness
                            }
                        }
                        lastInteraction = System.currentTimeMillis()
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx -> 
                PlayerView(ctx).apply { 
                    this.player = player
                    useController = false
                    keepScreenOn = true
                    this.resizeMode = resizeMode
                    // Impedir que o PlayerView roube o foco do Compose no Android TV
                    isFocusable = false
                    isFocusableInTouchMode = false
                } 
            }, 
            update = { view -> view.resizeMode = resizeMode }, 
            modifier = Modifier.fillMaxSize()
        )
        
        // Gesture Feedback Overlay
        AnimatedVisibility(
            visible = showGestureFeedback,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(120.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(gestureIcon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(gestureText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { gestureProgress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                    )
                }
            }
        }

        // Main Controls Overlay
        AnimatedVisibility(
            visible = showControls && !isInPipMode, 
            enter = fadeIn(tween(250)), 
            exit = fadeOut(tween(250))
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))) {
                // Top Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = if (isCompactHeight) 8.dp else 16.dp), 
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isBackFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showExitDialog = true }, 
                        modifier = Modifier
                            .size(if (isCompactHeight) 36.dp else 44.dp)
                            .onFocusChanged { isBackFocused = it.isFocused }
                            .background(if (isBackFocused) Color.White.copy(0.3f) else Color.White.copy(0.1f), CircleShape)
                            .border(if (isBackFocused) 2.dp else 0.dp, Color.White, CircleShape)
                    ) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(if (isCompactHeight) 20.dp else 24.dp)) 
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            channel.name, 
                            color = Color.White, 
                            fontWeight = FontWeight.Bold, 
                            fontSize = if (isCompactHeight) 16.sp else 18.sp, 
                            maxLines = 1, 
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (channel.type == ContentType.LIVE && currentProgram != null) (currentProgram?.title ?: "Ao Vivo") else (channel.group ?: "Reproduzindo"), 
                            color = MaterialTheme.colorScheme.primary, 
                            fontSize = if (isCompactHeight) 11.sp else 12.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                    var isFullscreenFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { 
                            resizeMode = when(resizeMode) { 
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT 
                            }
                            lastInteraction = System.currentTimeMillis() 
                        },
                        modifier = Modifier
                            .onFocusChanged { isFullscreenFocused = it.isFocused }
                            .background(if (isFullscreenFocused) Color.White.copy(0.2f) else Color.Transparent, CircleShape)
                            .border(if (isFullscreenFocused) 2.dp else 0.dp, Color.White, CircleShape)
                    ) { 
                        Icon(if (resizeMode == androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) Icons.Default.Fullscreen else Icons.Default.FullscreenExit, null, tint = Color.White) 
                    }
                }
                
                // Central Controls
                Row(
                    modifier = Modifier.align(Alignment.Center), 
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(if (isTVLayout) 16.dp else 12.dp)
                ) {
                    // Função auxiliar para garantir simetria absoluta em todos os controles
                    @Composable
                    fun ControlButton(icon: ImageVector, isLarge: Boolean = false, requester: FocusRequester? = null, onClick: () -> Unit) {
                        var isFocused by remember { mutableStateOf(false) }
                        val size = if (isTVLayout) {
                            if (isLarge) 52.dp else 44.dp
                        } else {
                            if (isLarge) 52.dp else 44.dp
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(size)
                                .clip(CircleShape)
                                .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
                                .onFocusChanged { 
                                    isFocused = it.isFocused 
                                    if (it.isFocused) lastInteraction = System.currentTimeMillis()
                                }
                                .background(if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                                .border(if (isFocused) 2.dp else 0.dp, Color.White, CircleShape)
                                .clickable { onClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(if (isTVLayout) (if (isLarge) 32.dp else 24.dp) else (if (isLarge) 32.dp else 24.dp))
                            )
                        }
                    }

                    // 1. Anterior (Grande)
                    ControlButton(
                        icon = if (channel.type == ContentType.LIVE) Icons.Default.KeyboardArrowUp else Icons.Default.SkipPrevious,
                        isLarge = true,
                        onClick = { lastInteraction = System.currentTimeMillis(); viewModel.playPreviousEpisode() }
                    )
                    
                    if (channel.type != ContentType.LIVE) {
                        // 2. Retroceder 10s
                        ControlButton(
                            icon = Icons.Default.Replay10,
                            onClick = { lastInteraction = System.currentTimeMillis(); player.seekTo(player.currentPosition - 10000) }
                        )
                    }
                    
                    // 3. Play / Pause (Central - Maior de todos)
                    var isPlayPauseFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { lastInteraction = System.currentTimeMillis(); if (player.isPlaying) player.pause() else player.play() }, 
                        modifier = Modifier
                            .size(72.dp)
                            .focusRequester(playPauseFocusRequester)
                            .onFocusChanged { 
                                isPlayPauseFocused = it.isFocused 
                                if (it.isFocused) lastInteraction = System.currentTimeMillis()
                            }, 
                        shape = CircleShape, 
                        color = if (isPlayPauseFocused) Color.White else MaterialTheme.colorScheme.primary,
                        tonalElevation = 8.dp,
                        border = if (isPlayPauseFocused) BorderStroke(4.dp, MaterialTheme.colorScheme.primary) else null
                    ) { 
                        Box(contentAlignment = Alignment.Center) { 
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                                contentDescription = null, 
                                tint = Color.Black, 
                                modifier = Modifier.size(42.dp)
                            ) 
                        } 
                    }
                    
                    if (channel.type != ContentType.LIVE) {
                        // 4. Avançar 10s
                        ControlButton(
                            icon = Icons.Default.Forward10,
                            onClick = { lastInteraction = System.currentTimeMillis(); player.seekTo(player.currentPosition + 10000) }
                        )
                    }
                    
                    // 5. Próximo (Grande - Idêntico ao Anterior)
                    ControlButton(
                        icon = if (channel.type == ContentType.LIVE) Icons.Default.KeyboardArrowDown else Icons.Default.SkipNext,
                        isLarge = true,
                        onClick = { lastInteraction = System.currentTimeMillis(); viewModel.playNextEpisode() }
                    )
                }

                // Bottom Panel
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = if (isCompactHeight) 8.dp else 16.dp)
                ) {
                    // Progress Bar
                    if (channel.type != ContentType.LIVE) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(formatTime(currentTime), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            
                            var isProgressFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .focusRequester(progressFocusRequester)
                                    .onFocusChanged { 
                                        isProgressFocused = it.isFocused 
                                        if (it.isFocused) lastInteraction = System.currentTimeMillis()
                                    }
                                    .focusable()
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            when (keyEvent.key) {
                                                Key.DirectionLeft -> {
                                                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                                    lastInteraction = System.currentTimeMillis()
                                                    true
                                                }
                                                Key.DirectionRight -> {
                                                    player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                                                    lastInteraction = System.currentTimeMillis()
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val progress = if (duration > 0) currentTime.toFloat() / duration else 0f
                                
                                // Trilho de Foco (Apenas visível na TV quando focado)
                                if (isProgressFocused) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .background(Color.White.copy(0.1f), CircleShape)
                                            .border(1.dp, Color.White.copy(0.3f), CircleShape)
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { progress }, 
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isProgressFocused) 4.dp else 3.dp)
                                        .clip(CircleShape), 
                                    color = if (isProgressFocused) Color.White else MaterialTheme.colorScheme.primary, 
                                    trackColor = Color.White.copy(0.2f)
                                )
                                
                                // Slider invisível para suporte a touch (mantendo compatibilidade)
                                Slider(
                                    value = progress, 
                                    onValueChange = { 
                                        lastInteraction = System.currentTimeMillis()
                                        player.seekTo((it * duration).toLong()) 
                                    }, 
                                    modifier = Modifier.fillMaxWidth().alpha(0f), 
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Transparent, 
                                        activeTrackColor = Color.Transparent, 
                                        inactiveTrackColor = Color.Transparent
                                    )
                                )
                                
                                // Indicador de Foco (Ponto de busca)
                                if (isProgressFocused) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .fillMaxWidth(progress)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .size(12.dp)
                                                .background(Color.White, CircleShape)
                                                .shadow(4.dp, CircleShape)
                                        )
                                    }
                                }
                            }
                            val totalDuration = if (duration > 0) formatTime(duration) else "--:--"
                            Text(totalDuration, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val progress = remember(currentProgram) { 
                            val now = System.currentTimeMillis()
                            if (currentProgram != null && currentProgram!!.stopTimestamp > currentProgram!!.startTimestamp) 
                                (now - currentProgram!!.startTimestamp).toFloat() / (currentProgram!!.stopTimestamp - currentProgram!!.startTimestamp) 
                            else 1f 
                        }
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) }, 
                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), 
                            color = StpRed, 
                            trackColor = Color.White.copy(0.2f)
                        )
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 16.dp))

                    // Secondary Functions
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = if (isTVLayout) 80.dp else 12.dp), 
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PlayerFunctionButton("ÁUDIO", Icons.Default.AudioFile, modifier = Modifier.weight(1f)) { trackTypeToSelect = androidx.media3.common.C.TRACK_TYPE_AUDIO; showTrackDialog = true }
                        
                        val isSubDisabled = player.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_TEXT)
                        PlayerFunctionButton(
                            "LEGENDA", 
                            Icons.Default.Subtitles, 
                            color = if (isSubDisabled) Color.White.copy(0.4f) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        ) { trackTypeToSelect = androidx.media3.common.C.TRACK_TYPE_TEXT; showTrackDialog = true }
                        
                        PlayerFunctionButton("QUALIDADE", Icons.Default.SettingsInputComponent, modifier = Modifier.weight(1f)) { 
                            trackTypeToSelect = androidx.media3.common.C.TRACK_TYPE_VIDEO
                            showTrackDialog = true 
                        }

                        PlayerFunctionButton("${playbackSpeed}X", Icons.Default.Speed, color = if (playbackSpeed > 1.0f) MaterialTheme.colorScheme.primary else Color.White, modifier = Modifier.weight(1f)) { 
                            playbackSpeed = when(playbackSpeed) { 1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2.0f; else -> 1.0f }
                        }
                        
                        PlayerFunctionButton(if (channel.type == ContentType.SERIES) "EPISÓDIOS" else "LISTA", Icons.AutoMirrored.Filled.List, modifier = Modifier.weight(1f)) { showQuickMenu = !showQuickMenu }
                    }
                }
            }
        }

        // Quick Channel Menu (Side Drawer)
        AnimatedVisibility(
            visible = showQuickMenu && !isInPipMode,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                modifier = Modifier.fillMaxHeight().width(if (isCompactHeight) 240.dp else 280.dp),
                color = Color.Black.copy(alpha = 0.9f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Column {
                    Text(
                        text = if (channel.type == ContentType.SERIES) "Episódios" else (channel.group ?: "Canais"),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(16.dp)
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(displayList) { item ->
                            val isSelected = item.streamId == channel.streamId
                            var isItemFocused by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isItemFocused = it.isFocused }
                                    .clickable { 
                                        viewModel.selectChannel(item) 
                                        showQuickMenu = false
                                    }
                                    .background(if (isItemFocused) Color.White.copy(0.2f) else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                    .border(if (isItemFocused) 1.dp else 0.dp, Color.White)
                                    .padding(horizontal = 16.dp, vertical = if (isCompactHeight) 8.dp else 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.logo,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isCompactHeight) 28.dp else 32.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = com.stpplay.android.R.drawable.logo)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = item.name,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                    fontSize = if (isCompactHeight) 12.sp else 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }

        if (showResumeDialog) AlertDialog(onDismissRequest = { showResumeDialog = false }, title = { Text("Continuar assistindo?") }, text = { Text("Deseja retomar de onde você parou?") }, confirmButton = { Button(onClick = { player.seekTo(savedPosition); showResumeDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Sim", color = Color.Black) } }, dismissButton = { TextButton(onClick = { showResumeDialog = false }) { Text("Não", color = Color.White) } }, containerColor = StpSurface, titleContentColor = Color.White,textContentColor = StpOnSurfaceVariant)
        if (showExitDialog) AlertDialog(onDismissRequest = { showExitDialog = false }, title = { Text("Interromper?") }, text = { Text("Deseja parar a reprodução agora?") }, confirmButton = { Button(onClick = { onBack() }, colors = ButtonDefaults.buttonColors(containerColor = StpRed)) { Text("Sim") } }, dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Não", color = Color.White) } }, containerColor = StpSurface, titleContentColor = Color.White, textContentColor = StpOnSurfaceVariant)
        if (showTrackDialog) TrackSelectionDialog(player = player, trackType = trackTypeToSelect, onDismiss = { showTrackDialog = false })

        // ERROR OVERLAY
        AnimatedVisibility(
            visible = playbackError != null && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ErrorOutline, 
                        null, 
                        tint = StpRed, 
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "ERRO DE REPRODUÇÃO",
                        color = Color.White, 
                        fontWeight = FontWeight.Black, 
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playbackError ?: "Erro desconhecido", 
                        color = StpOnSurfaceVariant, 
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { 
                                playbackError = null
                                player.prepare()
                                player.play()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("TENTAR NOVAMENTE", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = onBack) {
                            Text("VOLTAR", color = Color.White)
                        }
                    }
                }
            }
        }

        // BINGE-WATCH OVERLAY
        AnimatedVisibility(
            visible = showBingeOverlay && !showControls && !isInPipMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomEnd).padding(32.dp)
        ) {
            BingeNextOverlay(
                onPlayNext = {
                    showBingeOverlay = false
                    viewModel.playNextEpisode()
                },
                onCancel = { showBingeOverlay = false }
            )
        }
    }
}

@Composable
fun BingeNextOverlay(onPlayNext: () -> Unit, onCancel: () -> Unit) {
    var isPlayFocused by remember { mutableStateOf(false) }
    
    Surface(
        color = Color.Black.copy(alpha = 0.8f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(0.2f)),
        modifier = Modifier.width(300.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("PRÓXIMO CONTEÚDO", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Text("O próximo vídeo começará em instantes.", color = Color.White, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPlayNext,
                    modifier = Modifier.weight(1f).onFocusChanged { isPlayFocused = it.isFocused },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isPlayFocused) Color.White else MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("ASSISTIR AGORA", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun PlayerFunctionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTV = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp > 720
    
    var isFocused by remember { mutableStateOf(false) }
    
    // Design ultra-compacto para TV e Modo Paisagem
    val containerHeight = if (isTV) 34.dp else 44.dp
    val iconSize = if (isTV) 14.dp else 18.dp
    val fontSize = if (isTV) 8.sp else 10.sp
    val spacerHeight = if (isTV) 1.dp else 2.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(containerHeight)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .background(if (isFocused) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
            .border(
                width = if (isFocused) 1.5.dp else 0.5.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 4.dp)
    ) {
        Icon(
            imageVector = icon, 
            contentDescription = null, 
            tint = if (isFocused) MaterialTheme.colorScheme.primary else color, 
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.height(spacerHeight))
        Text(
            text = label, 
            color = if (isFocused) MaterialTheme.colorScheme.primary else color, 
            fontSize = fontSize, 
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TrackSelectionDialog(player: Player, trackType: Int, onDismiss: () -> Unit) {
    val tracks = player.currentTracks
    val groups = tracks.groups.filter { it.type == trackType }
    
    val title = when(trackType) {
        androidx.media3.common.C.TRACK_TYPE_TEXT -> "Legendas"
        androidx.media3.common.C.TRACK_TYPE_AUDIO -> "Idioma do Áudio"
        androidx.media3.common.C.TRACK_TYPE_VIDEO -> "Qualidade do Vídeo"
        else -> "Seleção de Trilhas"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Opção "Desativar" ou "Automático"
                val isAutoSelected = player.trackSelectionParameters.disabledTrackTypes.contains(trackType) || 
                                    (trackType == androidx.media3.common.C.TRACK_TYPE_VIDEO && !player.trackSelectionParameters.overrides.containsKey(groups.firstOrNull()?.mediaTrackGroup))
                
                var isAutoFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isAutoFocused = it.isFocused }
                        .background(if (isAutoFocused) Color.White.copy(0.1f) else Color.Transparent)
                        .clickable {
                            if (trackType == androidx.media3.common.C.TRACK_TYPE_VIDEO) {
                                // Para vídeo, "Automático" significa remover overrides
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .clearOverridesOfType(trackType)
                                    .setTrackTypeDisabled(trackType, false)
                                    .build()
                            } else {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .setTrackTypeDisabled(trackType, true)
                                    .build()
                            }
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isAutoSelected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        if (trackType == androidx.media3.common.C.TRACK_TYPE_VIDEO) "Automático (Recomendado)" else "Desativar",
                        color = Color.White
                    )
                }

                groups.forEach { group ->
                    for (i in 0 until group.length) {
                        val isSelected = group.isTrackSelected(i)
                        val format = group.getTrackFormat(i)
                        
                        val label = when(trackType) {
                            androidx.media3.common.C.TRACK_TYPE_VIDEO -> {
                                val res = if (format.width > 0 && format.height > 0) "${format.height}p" else ""
                                val bitrate = if (format.bitrate > 0) " (${format.bitrate / 1000} kbps)" else ""
                                if (res.isEmpty()) "Qualidade ${i + 1}$bitrate" else "$res$bitrate"
                            }
                            else -> format.label ?: format.language ?: "Trilha ${i + 1}"
                        }

                        var isItemFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .background(if (isItemFocused) Color.White.copy(0.1f) else Color.Transparent)
                                .clickable {
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(trackType, false)
                                        .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, i))
                                        .build()
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected && !player.trackSelectionParameters.disabledTrackTypes.contains(trackType),
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label.uppercase(), color = Color.White)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("FECHAR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = StpSurface
    )
}

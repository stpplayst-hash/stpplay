package com.stpplay.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import coil.compose.AsyncImage
import com.stpplay.android.R
import com.stpplay.android.data.Channel
import com.stpplay.android.data.ContentType
import com.stpplay.android.ui.theme.*

@Composable
fun CategorizedFlowScreen(
    title: String, 
    channels: List<Channel>, 
    favorites: Set<String>, 
    viewModel: PlayerViewModel, 
    selectedGroup: String?, 
    onGroupSelected: (String?) -> Unit, 
    onShowDetail: (Channel) -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    
    val lockedCategories by viewModel.lockedCategories.collectAsState()
    val shouldHideLocked by viewModel.shouldHideLockedCategories.collectAsState()
    val keywords by viewModel.keywordFilters.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val isKids = currentProfile?.isKids == true
    val kidsWhitelist = listOf("Animação", "Infantil", "Kids", "Criança", "Desenho", "Biblic", "Bíblic", "Caminho da Fé", "Gospel")
    
    val safeChannels = remember(channels, keywords, isKids, lockedCategories, shouldHideLocked) {
        channels.filter { channel ->
            if (isKids) {
                kidsWhitelist.any { (channel.group ?: "").contains(it, ignoreCase = true) }
            } else {
                if (shouldHideLocked) {
                    val group = channel.group?.trim() ?: ""
                    keywords.none { group.contains(it, ignoreCase = true) }
                } else true
            }
        }.filter { 
            if (shouldHideLocked) !lockedCategories.contains(it.group?.trim())
            else true
        }
    }
    
    var pinValue by remember { mutableStateOf("") }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var categoryToUnlockTemporarily by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val allLabel = stringResource(R.string.all)

    Box(modifier = Modifier.fillMaxSize().background(StpBackground)) {
        // Banner de Fundo (Backdrop) para Mobile/Paisagem
        val backdropUrl = remember(safeChannels) { safeChannels.firstOrNull { !it.logo.isNullOrBlank() }?.logo }
        KenBurnsBackdrop(imageUrl = backdropUrl)

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            if (selectedGroup == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = getGutter(), vertical = if (isLandscape) 4.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title, 
                        color = MaterialTheme.colorScheme.primary, 
                        fontWeight = FontWeight.Black, 
                        fontSize = if (isLandscape) 22.sp else 28.sp
                    )
                }
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = getGutter()),
                    placeholder = { Text(stringResource(R.string.search_category_placeholder), color = StpOnSurfaceVariant, fontSize = if (isLandscape) 13.sp else 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = StpSurface, unfocusedContainerColor = StpSurface, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = StpOutline)
                )

                Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 16.dp))

                if (channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Movie, null, modifier = Modifier.size(64.dp), tint = StpSurfaceHigh)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(stringResource(R.string.no_episodes_found), color = StpOnSurfaceVariant)
                        }
                    }
                } else {
                    val groups = remember(safeChannels, searchQuery, lockedCategories, shouldHideLocked, isKids, title, allLabel) { 
                        val priorityKeywords = if (title.contains("FILMES", true)) {
                            listOf(
                                "lançamentos",
                                "recém adicionados",
                                "q.cinema",
                                "4k"
                            )
                        } else {
                            listOf(
                                "lançamentos",
                                "recém adicionados",
                                "netflix",
                                "globoplay",
                                "hbo"
                            )
                        } + listOf(
                            "filmes e series",
                            "jogos do dia",
                            "infantil",
                            "globo sudeste",
                            "record",
                            "portugal",
                            "portugal esportes",
                            "hbo"
                        )

                        val contentGroups = safeChannels.map { it.group ?: "Outros" }
                            .distinct()
                            .filter { it.isNotBlank() && it.contains(searchQuery, ignoreCase = true) }
                            .sortedWith(compareBy<String> { group ->
                                val normalized = group.lowercase()
                                val index = priorityKeywords.indexOfFirst { keyword -> 
                                    normalized.contains(keyword) 
                                }
                                if (index != -1) index else Int.MAX_VALUE
                            }.thenBy { it })

                        val list = mutableListOf<String>()
                        if (searchQuery.isBlank()) {
                            list.add(allLabel)
                        }
                        list.addAll(contentGroups)
                        list
                    }
                    
                    if (isLandscape) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp, start = getGutter(), end = getGutter(), top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (searchQuery.isBlank()) {
                                val groupFavorites = safeChannels.filter { favorites.contains(it.streamId) }
                                if (groupFavorites.isNotEmpty()) {
                                    item(span = { GridItemSpan(2) }) {
                                        Column {
                                            Text(stringResource(R.string.favorites), color = StpSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = getGutter(), vertical = 4.dp))
                                            LazyRow(contentPadding = PaddingValues(horizontal = getGutter()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                items(groupFavorites) { channel ->
                                                    PosterItemDesign(channel, favorites.contains(channel.streamId), { viewModel.toggleFavorite(channel.streamId) }) { onShowDetail(channel) }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }
                                }
                            }

                            items(groups) { group ->
                                val isLocked = lockedCategories.contains(group.trim())
                                var isFocused by remember { mutableStateOf(false) }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .graphicsLayer(scaleX = if (isFocused) 1.02f else 1f, scaleY = if (isFocused) 1.02f else 1f)
                                        .clickable { 
                                            if (isLocked) {
                                                categoryToUnlockTemporarily = group
                                                pinValue = ""
                                                showUnlockDialog = true
                                            } else {
                                                searchQuery = "" 
                                                onGroupSelected(group) 
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else StpSurface
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = if (isFocused) MaterialTheme.colorScheme.primary else StpOutline.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (isLocked) {
                                            Icon(
                                                Icons.Default.Lock, 
                                                null, 
                                                tint = StpRed, 
                                                modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(if (isLandscape) 12.dp else 16.dp))
                                        }
                                        Text(
                                            text = group, 
                                            color = Color.White, 
                                            fontSize = if (isLandscape) 14.sp else 16.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isLocked) {
                                            Icon(Icons.Default.Lock, null, tint = StpRed, modifier = Modifier.size(14.dp))
                                        } else {
                                            val count = if (group == allLabel) safeChannels.size
                                            else safeChannels.count { (it.group ?: "Outros") == group }
                                            Text(text = count.toString(), color = StpOnSurfaceVariant, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                            if (searchQuery.isBlank()) {
                                val groupFavorites = safeChannels.filter { favorites.contains(it.streamId) }
                                if (groupFavorites.isNotEmpty()) {
                                    item {
                                        Text(stringResource(R.string.favorites), color = StpSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = getGutter(), vertical = 8.dp))
                                        LazyRow(contentPadding = PaddingValues(horizontal = getGutter()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(groupFavorites) { channel ->
                                                PosterItemDesign(channel, favorites.contains(channel.streamId), { viewModel.toggleFavorite(channel.streamId) }) { onShowDetail(channel) }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }

                            items(groups) { group ->
                                val isLocked = lockedCategories.contains(group.trim())
                                var isFocused by remember { mutableStateOf(false) }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = getGutter(), vertical = 6.dp)
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .graphicsLayer(scaleX = if (isFocused) 1.02f else 1f, scaleY = if (isFocused) 1.02f else 1f)
                                        .clickable { 
                                            if (isLocked) {
                                                categoryToUnlockTemporarily = group
                                                pinValue = ""
                                                showUnlockDialog = true
                                            } else {
                                                searchQuery = "" 
                                                onGroupSelected(group) 
                                            }
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else StpSurface
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = if (isFocused) MaterialTheme.colorScheme.primary else StpOutline.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (isLocked) {
                                            Icon(
                                                Icons.Default.Lock, 
                                                null, 
                                                tint = StpRed, 
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }
                                        Text(text = group, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        if (isLocked) {
                                            Text(text = stringResource(R.string.locked), color = StpRed, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                        } else {
                                            val count = if (group == allLabel) safeChannels.size
                                            else safeChannels.count { (it.group ?: "Outros") == group }
                                            Text(text = stringResource(R.string.items_count, count), color = StpOnSurfaceVariant, fontSize = 12.sp)
                                        }
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = StpOnSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val groupChannels by remember(safeChannels, selectedGroup, searchQuery) { 
                    derivedStateOf {
                        safeChannels.asSequence()
                            .filter { 
                                (selectedGroup == allLabel || (it.group ?: "Outros") == selectedGroup) && 
                                it.name.contains(searchQuery, ignoreCase = true) 
                            }
                            .toList()
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(getGutter()), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { 
                        searchQuery = ""
                        onGroupSelected(null) 
                    }, modifier = Modifier.background(StpSurfaceHigh, CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = selectedGroup ?: "", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = title, color = StpOnSurfaceVariant, fontSize = 12.sp)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = getGutter()),
                    placeholder = { Text(stringResource(R.string.search_in_group), color = StpOnSurfaceVariant) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = StpSurface, unfocusedContainerColor = StpSurface, focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = StpOutline)
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLandscape && channels.firstOrNull()?.type == ContentType.LIVE) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = getGutter(), end = getGutter(), top = 0.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(groupChannels) { channel ->
                            ChannelListRow(channel, favorites.contains(channel.streamId), viewModel) { onShowDetail(channel) }
                        }
                    }
                } else if (LocalConfiguration.current.screenWidthDp > 600 && channels.firstOrNull()?.type != ContentType.LIVE) {
                    LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = getGutter(), end = getGutter(), top = 0.dp, bottom = 80.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(groupChannels) { item ->
                            PosterItemDesign(item, favorites.contains(item.streamId), { viewModel.toggleFavorite(item.streamId) }) { onShowDetail(item) }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                        items(groupChannels) { channel ->
                            ChannelListRow(channel, favorites.contains(channel.streamId), viewModel) { onShowDetail(channel) }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = getGutter()), color = StpOutline.copy(alpha = 0.5f))
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
                onGroupSelected(categoryToUnlockTemporarily)
            },
            title = stringResource(R.string.unlock_category),
            description = stringResource(R.string.enter_pin_to_access, categoryToUnlockTemporarily ?: "")
        )
    }
}

@Composable
fun ChannelListRow(channel: Channel, isFavorite: Boolean, viewModel: PlayerViewModel, onClick: () -> Unit) {
    val currentProgram by if (channel.type == ContentType.LIVE && channel.streamId != null) viewModel.getCurrentProgramForChannel(channel.streamId).collectAsState(initial = null) else remember { mutableStateOf(null) }
    val appLogo = painterResource(id = R.drawable.logo)
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .background(if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
            .clickable { onClick() }
            .padding(getGutter()), 
        verticalAlignment = Alignment.CenterVertically
    ) { 
        AsyncImage(
            model = channel.logo, 
            contentDescription = null, 
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                .background(StpSurfaceHighest), 
            contentScale = ContentScale.Crop, 
            error = appLogo, 
            placeholder = appLogo, 
            fallback = appLogo
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) { 
            Text(channel.name, color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1); 
            if (channel.type == ContentType.LIVE && currentProgram != null) { 
                Column { 
                    Text(currentProgram?.title ?: "", color = if (isFocused) Color.White else MaterialTheme.colorScheme.primary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); 
                    val progress = remember(currentProgram) { val now = System.currentTimeMillis(); if (currentProgram!!.stopTimestamp > currentProgram!!.startTimestamp) (now - currentProgram!!.startTimestamp).toFloat() / (currentProgram!!.stopTimestamp - currentProgram!!.startTimestamp) else 0f }; 
                    if (progress > 0f) { LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.padding(top = 4.dp).width(80.dp).height(2.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = Color.White.copy(alpha = 0.1f)) } 
                } 
            } else { 
                Text(channel.year ?: "2024", color = StpOnSurfaceVariant, fontSize = 12.sp) 
            } 
        }
        IconButton(onClick = { viewModel.toggleFavorite(channel.streamId) }) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFavorite) StpRed else StpOnSurfaceVariant) }; 
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = if (isFocused) MaterialTheme.colorScheme.primary else StpOnSurfaceVariant, modifier = Modifier.size(20.dp)) 
    }
}

@Composable
fun PosterItemDesign(channel: Channel, isFavorite: Boolean, onFavClick: () -> Unit, onClick: () -> Unit) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val posterSizeMultiplier by viewModel.posterSize.collectAsState()
    
    val appLogo = painterResource(id = R.drawable.logo)
    var isFocused by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .width((110 * posterSizeMultiplier).dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer(scaleX = if (isFocused) 1.05f else 1f, scaleY = if (isFocused) 1.05f else 1f)
            .clickable { onClick() }
    ) { 
        Box(
            modifier = Modifier
                .aspectRatio(2/3f)
                .clip(RoundedCornerShape(12.dp))
                .border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                .background(StpSurfaceHighest)
        ) { 
            AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, error = appLogo, placeholder = appLogo, fallback = appLogo)
            IconButton(onClick = onFavClick, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.Black.copy(0.3f), CircleShape).size(24.dp)) { Icon(if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (isFavorite) StpRed else Color.White, modifier = Modifier.size(16.dp)) } 
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(channel.name, color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White, fontSize = (12 * posterSizeMultiplier).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) 
    }
}

@Composable
fun ParentalPinDialog(
    onDismiss: () -> Unit,
    onVerify: (String) -> Boolean,
    onSuccess: () -> Unit,
    title: String = "Área Protegida",
    description: String? = null
) {
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (description != null) {
                    Text(description, color = StpOnSurfaceVariant, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { input -> 
                        if (input.length <= 4 && input.all { char -> char.isDigit() }) {
                            pinValue = input
                            if (pinError) pinError = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Digite o PIN de 4 dígitos") },
                    isError = pinError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                if (pinError) {
                    Text(
                        text = "PIN incorreto. Tente novamente.",
                        color = StpRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (onVerify(pinValue)) {
                        pinError = false
                        onSuccess()
                    } else {
                        pinError = true
                        pinValue = ""
                    }
                }, 
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("DESBLOQUEAR", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCELAR", color = Color.White) }
        },
        containerColor = StpSurface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun SectionHeader(
    title: String, 
    modifier: Modifier = Modifier,
    showIndicator: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = getGutter(), vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = title, 
            color = Color.White, 
            style = MaterialTheme.typography.titleMedium, 
            fontWeight = FontWeight.Black, 
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun AnimatedExitDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sad_emoji")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "😢",
                    fontSize = 60.sp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Deseja fechar o app?",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StpRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(100.dp)
            ) {
                Text("SIM", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.width(100.dp)
            ) {
                Text("NÃO", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = StpSurface,
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun ShortcutTile(
    title: String, 
    icon: ImageVector, 
    color: Color, 
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f, 
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, 
            stiffness = Spring.StiffnessLow
        ),
        label = "shortcut_scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(160.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16 / 9f),
            shape = RoundedCornerShape(12.dp),
            color = if (isFocused) color else StpSurface,
            border = if (isFocused) BorderStroke(3.dp, Color.White) else BorderStroke(1.dp, Color.White.copy(0.1f)),
            tonalElevation = if (isFocused) 15.dp else 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = if (isFocused) Color.Black else color, 
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title, 
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(0.7f), 
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold
        )
    }
}

@Composable
fun CarouselIndicators(size: Int, currentIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(size) { i ->
            val isSelected = currentIndex == i
            val width by animateDpAsState(if (isSelected) 18.dp else 6.dp, label = "w")
            Box(
                modifier = Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.3f))
            )
        }
    }
}

@Composable
fun PremiumNavigationRail(
    currentScreen: Screen? = null,
    onNavigate: (Screen) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (isExpanded) 240.dp else 80.dp, label = "w")
    val scrollState = rememberScrollState()
    
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .onFocusChanged { isExpanded = it.hasFocus }
            // Efeito Cristalino (Gota d'Água): Borda com brilho e transparência
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.Black.copy(alpha = 0.15f)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp, 
                    Brush.verticalGradient(
                        listOf(Color.White.copy(0.25f), Color.Transparent, Color.White.copy(0.1f))
                    )
                ), 
                RoundedCornerShape(0.dp)
            ),
        color = Color.Transparent, // Fundo transparente para o efeito de vidro
        tonalElevation = 0.dp // Removido para não escurecer o cristal
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, 
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            androidx.compose.foundation.Image(
                painter = painterResource(id = R.drawable.logo), 
                contentDescription = "STP Play Logo", 
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onNavigate(Screen.Home) }
                    .focusProperties { canFocus = false }
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            RailItem(
                emoji = "🔍", 
                label = Screen.Search.label, 
                isSelected = currentScreen == Screen.Search,
                isExpanded = isExpanded
            ) { onNavigate(Screen.Search) }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val menuItems = listOf(
                Screen.Home to "🏠",
                Screen.Live to "📺",
                Screen.Movies to "🎬",
                Screen.Series to "🍿",
                Screen.Profile to "👤",
                Screen.Settings to "⚙️"
            )

            menuItems.forEach { (screen, emoji) ->
                RailItem(
                    emoji = emoji, 
                    label = screen.label, 
                    isSelected = currentScreen == screen,
                    isExpanded = isExpanded
                ) { onNavigate(screen) }
                
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun RailItem(
    emoji: String, 
    label: String, 
    isSelected: Boolean = false,
    isExpanded: Boolean, 
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "emoji_scale"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .background(if (isFocused) Color.White.copy(0.1f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center
    ) {
        Box(modifier = Modifier.width(80.dp), contentAlignment = Alignment.Center) {
            Text(
                text = emoji,
                fontSize = 24.sp,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        if (isExpanded) {
            Text(
                text = label, 
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White, 
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isFocused) FontWeight.Black else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun formatDuration(duration: String?): String? {
    if (duration.isNullOrBlank() || duration == "0" || duration == "00:00:00" || duration == "00:00") return null
    
    if (duration.contains(":")) {
        val parts = duration.split(":")
        return when (parts.size) {
            3 -> {
                val h = parts[0].toIntOrNull() ?: 0
                val m = parts[1].toIntOrNull() ?: 0
                if (h == 0 && m == 0) null
                else if (h > 0) "${h}h ${m}min" else "$m min"
            }
            2 -> {
                val m = parts[0].toIntOrNull() ?: 0
                if (m == 0) null else "$m min"
            }
            else -> duration
        }
    }
    
    val num = duration.toLongOrNull() ?: return null
    if (num <= 0) return null
    
    val totalMinutes = if (num < 600) num else num / 60
    
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes}min"
        else -> "$minutes min"
    }
}

@Composable
fun FeaturedInfo(
    item: Channel, 
    isExpanded: Boolean, 
    isSmallHeight: Boolean, 
    onShowDetail: (Channel) -> Unit, 
    focusRequester: FocusRequester
) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val fetchedDuration by viewModel.currentDuration.collectAsState()
    val isLargeText by viewModel.largeTextMode.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(if (isExpanded) 0.9f else 1f)
    ) {
        Text(
            text = item.name.uppercase(), 
            style = if (isSmallHeight) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium, 
            fontSize = if (!isExpanded) 28.sp else (if (isSmallHeight) 22.sp else (if(isLargeText) 42.sp else 35.sp)),
            fontWeight = FontWeight.Black, 
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = if (!isExpanded) 32.sp else (if (isSmallHeight) 26.sp else (if(isLargeText) 48.sp else 40.sp))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.spacedBy(12.dp), 
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            if (!item.rating.isNullOrEmpty() && item.rating != "0") QualityBadge("IMDb ${item.rating}")
            Text(
                text = item.year ?: "2024", 
                color = Color.White.copy(0.7f), 
                fontSize = if(isLargeText) 18.sp else 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            val durationToShow = item.duration ?: fetchedDuration
            formatDuration(durationToShow)?.let {
                Text(
                    text = it,
                    color = Color.White.copy(0.7f),
                    fontSize = if(isLargeText) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = item.genre ?: "Ação", 
                color = Color(0xFF4CAF50),
                fontSize = if(isLargeText) 16.sp else 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = item.description ?: "", 
            color = Color.White.copy(0.85f), 
            style = MaterialTheme.typography.bodyMedium, 
            fontSize = if(isLargeText) 18.sp else 14.sp,
            maxLines = if(isLargeText) 2 else 3, 
            lineHeight = if(isLargeText) 28.sp else 24.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = if (isExpanded) 16.dp else 8.dp)
        )

        var isFocused by remember { mutableStateOf(false) }
        Surface(
            onClick = { onShowDetail(item) },
            modifier = Modifier
                .focusRequester(focusRequester)
                .width(if (isExpanded) 180.dp else 150.dp)
                .height(if (isExpanded) 48.dp else 42.dp)
                .onFocusChanged { isFocused = it.isFocused },
            shape = RoundedCornerShape(24.dp),
            color = if (isFocused) Color.White else MaterialTheme.colorScheme.primary,
            border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow, 
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(if (isExpanded) 24.dp else 20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.watch), 
                    color = Color.Black, 
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isExpanded) 14.sp else 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun NetflixCategoryRow(
    title: String, 
    items: List<Channel>, 
    posterSizeMultiplier: Float = 1.0f,
    onShowDetail: (Channel) -> Unit, 
    onFocus: (Channel) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        SectionHeader(title = title)
        LazyRow(contentPadding = PaddingValues(horizontal = getGutter()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.streamId ?: it.url }) { channel -> 
                NetflixPosterItem(
                    channel = channel, 
                    posterSizeMultiplier = posterSizeMultiplier,
                    onFocus = { onFocus(channel) },
                    onClick = { onShowDetail(channel) }
                ) 
            }
        }
    }
}

@Composable
fun NetflixCategoryRowWithProgress(
    title: String, 
    items: List<com.stpplay.android.data.ChannelWithProgress>, 
    posterSizeMultiplier: Float = 1.0f,
    onShowDetail: (Channel) -> Unit, 
    onFocus: (Channel) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        SectionHeader(title = title)
        LazyRow(contentPadding = PaddingValues(horizontal = getGutter()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.channel.streamId ?: it.channel.url }) { item -> 
                NetflixPosterItem(
                    channel = item.channel, 
                    posterSizeMultiplier = posterSizeMultiplier,
                    progress = item.progress,
                    onFocus = { onFocus(item.channel) },
                    onClick = { onShowDetail(item.channel) }
                ) 
            }
        }
    }
}

@Composable
fun Top10CategoryRow(
    title: String, 
    items: List<Channel>, 
    posterSizeMultiplier: Float = 1.0f,
    onShowDetail: (Channel) -> Unit, 
    onFocus: (Channel) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        SectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = getGutter()), 
            horizontalArrangement = Arrangement.spacedBy(32.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(items.take(10), key = { _, item -> item.streamId ?: item.url }) { index, channel ->
                Box(contentAlignment = Alignment.BottomStart) {
                    Text(
                        text = "${index + 1}", 
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 100.sp, 
                            fontWeight = FontWeight.Black, 
                            color = Color.DarkGray.copy(0.5f)
                        ), 
                        modifier = Modifier.offset(x = (-16).dp, y = 20.dp)
                    )
                    NetflixPosterItem(
                        channel = channel, 
                        posterSizeMultiplier = posterSizeMultiplier,
                        modifier = Modifier.padding(start = 24.dp), 
                        onFocus = { onFocus(channel) },
                        onClick = { onShowDetail(channel) }
                    )
                }
            }
        }
    }
}

@Composable
fun NetflixPosterItem(
    channel: Channel, 
    modifier: Modifier = Modifier,
    posterSizeMultiplier: Float = 1.0f,
    progress: Float? = null,
    onFocus: () -> Unit = {}, 
    onClick: () -> Unit
) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val lastFocusedId by viewModel.lastFocusedStreamId.collectAsState()
    val focusRequester = remember { FocusRequester() }
    
    var isFocused by remember { mutableStateOf(false) }
    
    LaunchedEffect(lastFocusedId) {
        if (lastFocusedId == channel.streamId) {
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, 
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Card(
        modifier = modifier
            .width((110 * posterSizeMultiplier).dp)
            .focusRequester(focusRequester)
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) onFocus() 
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (isFocused) {
                    shadowElevation = 30.dp.toPx()
                }
            }
            .clickable { onClick() }
            .aspectRatio(2/3f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp, 
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f)
        ),
        elevation = CardDefaults.cardElevation(if (isFocused) 25.dp else 2.dp)
    ) {
        Box {
            if (channel.logo.isNullOrBlank()) {
                DynamicLogoPlaceholder(channel.name, Modifier.fillMaxSize())
            } else {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(channel.logo)
                        .crossfade(true)
                        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .size(coil.size.Size.ORIGINAL)
                        .build(),
                    contentDescription = null, 
                    modifier = Modifier.fillMaxSize(), 
                    contentScale = ContentScale.FillBounds,
                    placeholder = painterResource(id = R.drawable.logo),
                    error = painterResource(id = R.drawable.logo)
                )
            }
            
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f))
                            )
                        )
                )
            }

            if (progress != null && progress > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}

@Composable
fun NetflixTopBar(onSearchClick: () -> Unit, onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.95f),
                0.6f to Color.Black.copy(alpha = 0.7f),
                1.0f to Color.Transparent
            ))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp), 
        horizontalArrangement = Arrangement.SpaceBetween, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.logo), 
            contentDescription = null, 
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { onProfileClick() }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = onSearchClick) { Icon(Icons.Default.Search, null, tint = Color.White) }
            IconButton(onClick = onProfileClick) { Icon(Icons.Default.Person, null, tint = Color.White) }
        }
    }
}

@Composable
fun NetflixSearchOverlay(
    query: String, 
    onQueryChange: (String) -> Unit, 
    onClose: () -> Unit, 
    results: List<Channel>, 
    onShowDetail: (Channel) -> Unit
) {
    val viewModel: PlayerViewModel = hiltViewModel()
    val posterSizeMultiplier by viewModel.posterSize.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        val focusRequester = remember { FocusRequester() }
        
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                OutlinedTextField(
                    value = query, 
                    onValueChange = onQueryChange, 
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.search_placeholder), color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent, 
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ), 
                    singleLine = true
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3), 
                modifier = Modifier.fillMaxSize(), 
                contentPadding = PaddingValues(16.dp), 
                horizontalArrangement = Arrangement.spacedBy(8.dp), 
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { channel -> 
                    NetflixPosterItem(
                        channel = channel,
                        posterSizeMultiplier = posterSizeMultiplier,
                        onClick = { onShowDetail(channel) }
                    ) 
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

package com.stpplay.android.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stpplay.android.R
import com.stpplay.android.ui.theme.*

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StpBackground),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(R.drawable.fundo_login)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(radius = 2.4.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.8f
        )
        // Overlay Gradiente para Profundidade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        center = androidx.compose.ui.geometry.Offset.Unspecified,
                        radius = Float.POSITIVE_INFINITY
                    )
                )
        )
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedLogo(size = 150.dp)
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SetupScreen(viewModel: PlayerViewModel, isLoading: Boolean, errorMessage: String?) {
    val context = LocalContext.current
    val macAddress = remember { getMacAddress(context) }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 720
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isTabletOrTV = isWideScreen || isLandscape
    
    var loginMode by remember { mutableIntStateOf(0) } // 0: Xtream, 1: M3U
    val passwordFocusRequester = remember { FocusRequester() }

    Box(modifier = Modifier.fillMaxSize().background(StpBackground)) {
        AsyncImage(
            model = coil.request.ImageRequest.Builder(LocalContext.current)
                .data(R.drawable.fundo_login)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().blur(radius = 2.4.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.8f
        )
        // Overlay Gradiente para Profundidade
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                        center = androidx.compose.ui.geometry.Offset.Unspecified,
                        radius = Float.POSITIVE_INFINITY
                    )
                )
        )

        if (isTabletOrTV) {
            // LAYOUT PARA SMART TV E MODO PAISAGEM (Duas Colunas)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isWideScreen) 48.dp else 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Coluna Esquerda: Branding
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedLogo(size = if (isWideScreen) 120.dp else 100.dp)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.welcome_to).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            letterSpacing = 2.sp
                        )
                    }
                    
                    Text(
                        text = stringResource(R.string.tv_premium),
                        color = Color.White,
                        fontSize = if (isWideScreen) 44.sp else 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                        lineHeight = if (isWideScreen) 48.sp else 40.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    DisclaimerSection()
                    
                    Box(
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .width(80.dp)
                            .height(3.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, MaterialTheme.colorScheme.primary, Color.Transparent)
                                )
                            )
                    )
                    
                    Text(
                        text = stringResource(R.string.mac_address_label, macAddress),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                // Coluna Direita: Formulário
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    LoginFormContainer(
                        loginMode = loginMode,
                        onLoginModeChange = { loginMode = it },
                        viewModel = viewModel,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        passwordFocusRequester = passwordFocusRequester,
                        isWide = true
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    ContactSupportSection()
                }
            }
        } else {
            // LAYOUT PARA TELEMÓVEL (Retrato - Vertical)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(48.dp))
                AnimatedLogo(size = 100.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = stringResource(R.string.welcome_to), color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
                Text(text = stringResource(R.string.tv_premium), color = MaterialTheme.colorScheme.primary, fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                DisclaimerSection()
                Spacer(modifier = Modifier.height(16.dp))
                
                LoginFormContainer(
                    loginMode = loginMode,
                    onLoginModeChange = { loginMode = it },
                    viewModel = viewModel,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    passwordFocusRequester = passwordFocusRequester,
                    isWide = false
                )

                Spacer(modifier = Modifier.height(32.dp))
                ContactSupportSection()
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun DisclaimerSection() {
    var showDialog by remember { mutableStateOf(false) }

    Text(
        text = stringResource(R.string.disclaimer_link),
        color = Color.White.copy(alpha = 0.3f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable { showDialog = true }
            .padding(8.dp)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { 
                Text(
                    text = stringResource(R.string.disclaimer_title),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Text(
                    text = stringResource(R.string.disclaimer_body),
                    color = StpOnSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = StpSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun LoginFormContainer(
    loginMode: Int,
    onLoginModeChange: (Int) -> Unit,
    viewModel: PlayerViewModel,
    isLoading: Boolean,
    errorMessage: String?,
    passwordFocusRequester: FocusRequester,
    isWide: Boolean
) {
    Box(
        modifier = Modifier
            .widthIn(max = 400.dp)
            .padding(horizontal = if (isWide) 0.dp else 24.dp)
            .shadow(
                elevation = 40.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color.Black,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                ),
                RoundedCornerShape(32.dp)
            )
            .padding(if (isWide) 32.dp else 24.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Input,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.access_account),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LoginModeTab(text = stringResource(R.string.xtream_api), isSelected = loginMode == 0, modifier = Modifier.weight(1f)) { onLoginModeChange(0) }
                LoginModeTab(text = stringResource(R.string.m3u_list), isSelected = loginMode == 1, modifier = Modifier.weight(1f)) { onLoginModeChange(1) }
            }

            AnimatedContent(
                targetState = loginMode,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                },
                label = "login_form"
            ) { mode ->
                if (mode == 0) {
                    XtreamLoginForm(viewModel, isLoading, errorMessage, passwordFocusRequester)
                } else {
                    M3uLoginForm(viewModel, isLoading, errorMessage)
                }
            }
        }
    }
}

@Composable
fun LoginModeTab(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (isSelected) Color.Black else Color.White.copy(alpha = 0.6f)

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused && !isSelected) Color.White.copy(alpha = 0.1f) else backgroundColor,
        border = if (isFocused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (isFocused && !isSelected) Color.White else contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun XtreamLoginForm(
    viewModel: PlayerViewModel,
    isLoading: Boolean,
    errorMessage: String?,
    passwordFocusRequester: FocusRequester
) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LoginTextField(
            value = user,
            onValueChange = { user = it },
            label = stringResource(R.string.username),
            icon = Icons.Default.Person,
            enabled = !isLoading
        )

        LoginTextField(
            value = pass,
            onValueChange = { pass = it },
            label = stringResource(R.string.password),
            icon = Icons.Default.Lock,
            enabled = !isLoading,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        null,
                        tint = StpOnSurfaceVariant
                    )
                }
            },
            focusRequester = passwordFocusRequester
        )

        if (errorMessage != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = errorMessage,
                    color = StpRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                // Botão de Atualizar Status se for erro de expiração
                if (errorMessage.contains("expir", ignoreCase = true) || errorMessage.contains("vencid", ignoreCase = true)) {
                    TextButton(
                        onClick = { viewModel.refreshSubscriptionStatus() },
                        enabled = !isLoading,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("JÁ RENOVEI (ATUALIZAR)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LoginButton(
            text = stringResource(R.string.login_now),
            isLoading = isLoading
        ) {
            viewModel.loginXtream(user, pass)
        }
    }
}

@Composable
fun M3uLoginForm(
    viewModel: PlayerViewModel,
    isLoading: Boolean,
    errorMessage: String?
) {
    var listName by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LoginTextField(
            value = listName,
            onValueChange = { listName = it },
            label = "Nome da Lista",
            icon = Icons.Default.Description,
            enabled = !isLoading
        )

        LoginTextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.list_url),
            icon = Icons.Default.Link,
            enabled = !isLoading
        )

        if (errorMessage != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = errorMessage,
                    color = StpRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                // Botão de Atualizar Status se for erro de expiração
                if (errorMessage.contains("expir", ignoreCase = true) || errorMessage.contains("vencid", ignoreCase = true)) {
                    TextButton(
                        onClick = { viewModel.refreshSubscriptionStatus() },
                        enabled = !isLoading,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("JÁ RENOVEI (ATUALIZAR)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LoginButton(
            text = stringResource(R.string.login_now),
            isLoading = isLoading
        ) {
            viewModel.loginM3u(listName, url)
        }
    }
}

@Composable
fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f, label = "scale")
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        label = { Text(label, fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                icon, 
                null, 
                tint = if (isFocused) MaterialTheme.colorScheme.primary else StpOnSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingIcon = trailingIcon,
        enabled = enabled,
        visualTransformation = visualTransformation,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = StpSurfaceHigh.copy(alpha = 0.8f),
            unfocusedContainerColor = Color.Black.copy(alpha = 0.2f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = StpOnSurfaceVariant
        )
    )
}

@Composable
fun LoginButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        ),
        label = "shimmer_x"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(scale * pulseScale)
            .onFocusChanged { isFocused = it.isFocused }
            .shadow(
                elevation = if (isFocused) 20.dp else 10.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = MaterialTheme.colorScheme.primary
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    if (isFocused) 
                        listOf(Color.White, Color.White)
                    else 
                        listOf(MaterialTheme.colorScheme.primary, Color(0xFFFFC107))
                )
            )
            .clickable(enabled = !isLoading) { onClick() }
            .drawWithContent {
                drawContent()
                if (!isFocused) {
                    clipRect {
                        val brush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(0.3f), Color.Transparent),
                            start = androidx.compose.ui.geometry.Offset(shimmerX, 0f),
                            end = androidx.compose.ui.geometry.Offset(shimmerX + 300f, 300f)
                        )
                        drawRect(brush = brush, blendMode = androidx.compose.ui.graphics.BlendMode.Overlay)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 3.dp)
        } else {
            Text(
                text = text.uppercase(),
                color = if (isFocused) Color.Black else Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
fun ContactSupportSection() {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.no_account_q),
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 14.sp
        )
        Surface(
            onClick = {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://wa.link/k6h325"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show()
                }
            },
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.05f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.click_here_to_activate),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

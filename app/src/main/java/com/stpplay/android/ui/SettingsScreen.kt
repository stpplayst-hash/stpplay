package com.stpplay.android.ui

import android.widget.Toast
import android.provider.Settings
import android.os.Build
import java.net.NetworkInterface
import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stpplay.android.R
import com.stpplay.android.ui.theme.*

enum class SettingsCategory(val titleRes: Int, val icon: ImageVector) {
    ACCOUNT(R.string.group_account_content, Icons.Default.Refresh),
    PARENTAL(R.string.parental_control, Icons.Default.Lock),
    PLAYBACK(R.string.playback_settings_title, Icons.AutoMirrored.Filled.PlaylistPlay),
    APPEARANCE(R.string.appearance_settings_title, Icons.Default.Palette),
    BEHAVIOR(R.string.behavior_settings_title, Icons.Default.History),
    MAINTENANCE(R.string.maintenance_settings_title, Icons.Default.Speed),
    LANGUAGE(R.string.language_label, Icons.Default.Language),
    SUPPORT(R.string.support_label, Icons.Default.SupportAgent)
}

@Composable
fun SettingsScreen(viewModel: PlayerViewModel, onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val channels by viewModel.channels.collectAsState()
    val lockedCategories by viewModel.lockedCategories.collectAsState()
    val updateInterval by viewModel.updateInterval.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val keywordFilters by viewModel.keywordFilters.collectAsState()
    val currentLanguage by viewModel.language.collectAsState()
    val isLargeText by viewModel.largeTextMode.collectAsState()
    
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp > 720
    val isTVLayout = isLandscape || isWideScreen
    val isCompactHeight = configuration.screenHeightDp < 500
    
    var selectedCategory by remember { mutableStateOf(SettingsCategory.ACCOUNT) }
    
    var showPinDialog by remember { mutableStateOf(false) }
    var pinAction by remember { mutableStateOf("VERIFY") } 
    var pinValue by remember { mutableStateOf("") }
    var newPinValue by remember { mutableStateOf("") }
    var pinStep by remember { mutableIntStateOf(1) }
    var pinError by remember { mutableStateOf(false) }
    
    var showCategoryLockDialog by remember { mutableStateOf(false) }
    var showKeywordsDialog by remember { mutableStateOf(false) }
    var showUpdateIntervalDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.2"
        } catch (e: Exception) { "1.2" }
    }

    Row(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(StpBackground, Color.Black)))) {
        if (isTVLayout) {
            PremiumNavigationRail(currentScreen = Screen.Settings, onNavigate = onNavigate)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isTVLayout) {
                Row(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
                    Column(modifier = Modifier.weight(0.3f).fillMaxHeight().background(Color.Black.copy(alpha = 0.3f)).padding(24.dp)) {
                        Text(text = stringResource(R.string.settings_title), color = StpOnSurface, fontWeight = FontWeight.Black, fontSize = if (isCompactHeight) 20.sp else 24.sp, letterSpacing = 1.sp)
                        Text(text = stringResource(R.string.settings_subtitle), color = StpOnSurfaceVariant, fontSize = if (isCompactHeight) 10.sp else 11.sp, modifier = Modifier.padding(bottom = if (isCompactHeight) 16.dp else 32.dp))
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 4.dp else 8.dp)) {
                            SettingsCategory.entries.forEach { category ->
                                SettingsCategoryItem(category = category, isSelected = selectedCategory == category, isCompact = isCompactHeight, onSelect = { selectedCategory = category })
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        SettingsCategoryItem(title = stringResource(R.string.logout), icon = Icons.AutoMirrored.Filled.Logout, isSelected = false, isCompact = isCompactHeight, color = StpRed, onSelect = { viewModel.logout() })
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Versão $appVersion", color = StpOnSurfaceVariant.copy(alpha = 0.5f), fontSize = if (isCompactHeight) 9.sp else 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    Box(modifier = Modifier.weight(0.7f).fillMaxHeight().padding(48.dp)) {
                        AnimatedContent(targetState = selectedCategory, transitionSpec = { (fadeIn(tween(400)) + slideInHorizontally(tween(400)) { it / 2 }) togetherWith fadeOut(tween(400)) }, label = "content") { category ->
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                when (category) {
                                    SettingsCategory.ACCOUNT -> AccountSettingsGroup(viewModel, isLoading, updateInterval) { showUpdateIntervalDialog = true }
                                    SettingsCategory.PARENTAL -> ParentalSettingsGroup(viewModel) { action -> pinAction = action; pinValue = ""; newPinValue = ""; pinStep = 1; showPinDialog = true }
                                    SettingsCategory.PLAYBACK -> PlaybackSettingsGroup(viewModel)
                                    SettingsCategory.APPEARANCE -> AppearanceSettingsGroup(viewModel)
                                    SettingsCategory.BEHAVIOR -> BehaviorSettingsGroup(viewModel)
                                    SettingsCategory.MAINTENANCE -> MaintenanceSettingsGroup(viewModel)
                                    SettingsCategory.LANGUAGE -> LanguageSettingsGroup(viewModel, currentLanguage) { showLanguageDialog = true }
                                    SettingsCategory.SUPPORT -> SupportSettingsGroup(appVersion)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = stringResource(R.string.settings_title), color = StpOnSurface, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = 1.sp)
                    Text(text = stringResource(R.string.settings_subtitle), color = StpOnSurfaceVariant, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(32.dp))
                    AccountSettingsGroup(viewModel, isLoading, updateInterval) { showUpdateIntervalDialog = true }
                    Spacer(modifier = Modifier.height(24.dp))
                    LanguageSettingsGroup(viewModel, currentLanguage) { showLanguageDialog = true }
                    Spacer(modifier = Modifier.height(24.dp))
                    SupportSettingsGroup(appVersion)
                    Spacer(modifier = Modifier.height(24.dp))
                    ParentalSettingsGroup(viewModel) { action -> pinAction = action; pinValue = ""; newPinValue = ""; pinStep = 1; showPinDialog = true }
                    Spacer(modifier = Modifier.height(24.dp))
                    PlaybackSettingsGroup(viewModel)
                    Spacer(modifier = Modifier.height(24.dp))
                    AppearanceSettingsGroup(viewModel)
                    Spacer(modifier = Modifier.height(24.dp))
                    BehaviorSettingsGroup(viewModel)
                    Spacer(modifier = Modifier.height(24.dp))
                    MaintenanceSettingsGroup(viewModel)
                    Spacer(modifier = Modifier.height(48.dp))
                    Surface(onClick = { viewModel.logout() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), color = StpRed.copy(alpha = 0.1f), border = BorderStroke(1.dp, StpRed.copy(alpha = 0.5f))) {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = StpRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp)); Text(stringResource(R.string.logout), color = StpRed, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Versão $appVersion", color = StpOnSurfaceVariant.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(120.dp))
                }
            }
        }
    }

    if (showKeywordsDialog) KeywordManagerDialog(keywords = keywordFilters, onAdd = { viewModel.addKeywordFilter(it) }, onRemove = { viewModel.removeKeywordFilter(it) }, onDismiss = { showKeywordsDialog = false })
    if (showPinDialog) AlertDialog(onDismissRequest = { showPinDialog = false; pinError = false }, title = { Text(if (pinAction == "CREATE") stringResource(R.string.create_pin) else if (pinAction == "CHANGE") stringResource(R.string.change_pin) else stringResource(R.string.confirm_pin), color = Color.White) }, text = { Column { val label = if (pinAction == "CHANGE" && pinStep == 2) stringResource(R.string.new_pin) else stringResource(R.string.pin_4_digits); val currentVal = if (pinAction == "CHANGE" && pinStep == 2) newPinValue else pinValue; OutlinedTextField(value = currentVal, onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) { if (pinAction == "CHANGE" && pinStep == 2) newPinValue = it else pinValue = it; if (pinError) pinError = false } }, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, isError = pinError, visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)); if (pinError) Text(text = stringResource(R.string.pin_incorrect_error), color = StpRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) } }, confirmButton = { Button(onClick = { when(pinAction) { "CREATE" -> if (pinValue.length == 4) { viewModel.setParentalPin(pinValue); showPinDialog = false }; "VERIFY" -> if (viewModel.verifyParentalPin(pinValue)) { pinError = false; showPinDialog = false; showCategoryLockDialog = true } else { pinError = true; Toast.makeText(context, context.getString(R.string.pin_incorrect_toast), Toast.LENGTH_SHORT).show(); pinValue = "" }; "MANAGE_KEYWORDS" -> if (viewModel.verifyParentalPin(pinValue)) { pinError = false; showPinDialog = false; showKeywordsDialog = true } else { pinError = true; Toast.makeText(context, context.getString(R.string.pin_incorrect_toast), Toast.LENGTH_SHORT).show(); pinValue = "" }; "CHANGE" -> if (pinStep == 1) { if (viewModel.verifyParentalPin(pinValue)) { pinError = false; pinStep = 2 } else { pinError = true; Toast.makeText(context, context.getString(R.string.current_pin_incorrect), Toast.LENGTH_SHORT).show(); pinValue = "" } } else { if (newPinValue.length == 4) { viewModel.setParentalPin(newPinValue); Toast.makeText(context, context.getString(R.string.pin_changed_success), Toast.LENGTH_SHORT).show(); showPinDialog = false } else { Toast.makeText(context, context.getString(R.string.pin_must_be_4_digits), Toast.LENGTH_SHORT).show() } } } }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(if (pinAction == "CHANGE" && pinStep == 1) stringResource(R.string.next) else stringResource(R.string.confirm), color = Color.Black) } }, containerColor = StpSurface)
    if (showCategoryLockDialog) AlertDialog(onDismissRequest = { showCategoryLockDialog = false }, title = { Text(stringResource(R.string.lock_categories), color = Color.White) }, text = { val allCategories = remember(channels) { channels.mapNotNull { it.group }.distinct().sorted() }; LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) { items(allCategories) { category -> val isLocked = lockedCategories.contains(category.trim()); Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleCategoryLock(category.trim(), !isLocked) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(category, color = Color.White, modifier = Modifier.weight(1f)); Switch(checked = isLocked, onCheckedChange = { viewModel.toggleCategoryLock(category.trim(), it) }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)) } } } }, confirmButton = { Button(onClick = { showCategoryLockDialog = false }) { Text(stringResource(R.string.ok)) } }, containerColor = StpSurface)
    if (showUpdateIntervalDialog) AlertDialog(onDismissRequest = { showUpdateIntervalDialog = false }, title = { Text(stringResource(R.string.auto_update), color = Color.White) }, text = { val options = listOf(0 to stringResource(R.string.update_never), 6 to stringResource(R.string.update_6h), 12 to stringResource(R.string.update_12h), 24 to stringResource(R.string.update_24h), 48 to stringResource(R.string.update_48h)); Column { options.forEach { (hours, label) -> Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setUpdateInterval(hours); showUpdateIntervalDialog = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = updateInterval == hours, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)); Spacer(modifier = Modifier.width(12.dp)); Text(label, color = Color.White) } } } }, confirmButton = { TextButton(onClick = { showUpdateIntervalDialog = false }) { Text(stringResource(R.string.cancel).uppercase(), color = MaterialTheme.colorScheme.primary) } }, containerColor = StpSurface)
    if (showLanguageDialog) {
        val options = listOf(
            "pt" to stringResource(R.string.portuguese),
            "en" to stringResource(R.string.english),
            "es" to stringResource(R.string.spanish)
        )
        SingleChoiceDialog(
            title = stringResource(R.string.select_language),
            options = options,
            currentSelected = currentLanguage,
            onSelect = { 
                viewModel.setLanguage(it)
                showLanguageDialog = false
            }
        )
    }
}

@Composable
fun SettingsCategoryItem(category: SettingsCategory? = null, title: String? = null, icon: ImageVector? = null, isSelected: Boolean, isCompact: Boolean = false, color: Color = MaterialTheme.colorScheme.primary, onSelect: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val displayTitle = if (category != null) stringResource(category.titleRes) else title ?: ""
    val displayIcon = category?.icon ?: icon ?: Icons.Default.Info
    Surface(onClick = onSelect, modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }, shape = RoundedCornerShape(if (isCompact) 8.dp else 12.dp), color = if (isSelected || isFocused) color.copy(alpha = 0.15f) else Color.Transparent, border = if (isFocused) BorderStroke(2.dp, color) else null) {
        Row(modifier = Modifier.padding(horizontal = if (isCompact) 12.dp else 16.dp, vertical = if (isCompact) 8.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(displayIcon, null, tint = if (isSelected || isFocused) color else StpOnSurfaceVariant, modifier = Modifier.size(if (isCompact) 18.dp else 20.dp))
            Spacer(modifier = Modifier.width(if (isCompact) 12.dp else 16.dp))
            Text(text = displayTitle, color = if (isSelected || isFocused) Color.White else StpOnSurfaceVariant, fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium, fontSize = if (isCompact) 13.sp else 14.sp)
        }
    }
}

@Composable
fun AccountSettingsGroup(viewModel: PlayerViewModel, isLoading: Boolean, updateInterval: Int, onIntervalClick: () -> Unit) {
    val context = LocalContext.current
    val deviceModel = remember { "${Build.MANUFACTURER} ${Build.MODEL}".uppercase() }
    val macAddress = remember { getMacAddress(context) }
    
    SettingsGroup(stringResource(R.string.group_account_content)) {
        SettingsItem(title = stringResource(R.string.update_list), icon = Icons.Default.Refresh, subtitle = if (isLoading) stringResource(R.string.synchronizing) else stringResource(R.string.reload_content), iconColor = MaterialTheme.colorScheme.primary, loading = isLoading) { viewModel.checkSavedCredentials(force = true); Toast.makeText(context, context.getString(R.string.synchronizing), Toast.LENGTH_SHORT).show() }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        val intervalText = when(updateInterval) { 0 -> stringResource(R.string.update_never); 6 -> stringResource(R.string.update_6h); 12 -> stringResource(R.string.update_12h); 24 -> stringResource(R.string.update_24h); 48 -> stringResource(R.string.update_48h); else -> "$updateInterval horas" }
        SettingsItem(stringResource(R.string.auto_update), Icons.Default.Update, intervalText, MaterialTheme.colorScheme.primary) { onIntervalClick() }
    }
    Spacer(modifier = Modifier.height(24.dp))
    SettingsGroup("Informações do Dispositivo") {
        SettingsItem(title = "Modelo do Aparelho", icon = Icons.Default.Devices, subtitle = deviceModel, onClick = { })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(title = "Endereço MAC", icon = Icons.Default.Dns, subtitle = macAddress, onClick = { })
    }
}

@Composable
fun ParentalSettingsGroup(viewModel: PlayerViewModel, onAction: (String) -> Unit) {
    val shouldHideLocked by viewModel.shouldHideLockedCategories.collectAsState()
    val keywordFilters by viewModel.keywordFilters.collectAsState()
    val hasPin by viewModel.hasParentalPin.collectAsState()
    SettingsGroup("Controle Parental Premium") {
        var isSwitchFocused by remember { mutableStateOf(false) }
        Row(modifier = Modifier.fillMaxWidth().onFocusChanged { isSwitchFocused = it.isFocused }.background(if (isSwitchFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent).clickable { viewModel.setHideLockedCategories(!shouldHideLocked) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(44.dp).background(if (isSwitchFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).border(if (isSwitchFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(if (shouldHideLocked) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                Spacer(modifier = Modifier.width(16.dp)); Column { Text("Ocultar Bloqueados", color = if (isSwitchFocused) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Esconde categorias trancadas das listas", color = if (isSwitchFocused) MaterialTheme.colorScheme.primary else StpOnSurfaceVariant, fontSize = 12.sp) }
            }
            Switch(checked = shouldHideLocked, onCheckedChange = { viewModel.setHideLockedCategories(it) }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(stringResource(R.string.parental_control), Icons.Default.Lock, if (hasPin) stringResource(R.string.manage_locks_pin) else stringResource(R.string.setup_pin_protection), MaterialTheme.colorScheme.primary) { if (hasPin) onAction("VERIFY") else onAction("CREATE") }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(title = "Filtro de Palavras-Chave", icon = Icons.Default.FilterAlt, subtitle = "${keywordFilters.size} termos monitorados", iconColor = MaterialTheme.colorScheme.primary) { if (hasPin) onAction("MANAGE_KEYWORDS") else onAction("CREATE") }
        if (hasPin) { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f)); SettingsItem(stringResource(R.string.change_pin), Icons.Default.Password, stringResource(R.string.change_security_code), MaterialTheme.colorScheme.primary) { onAction("CHANGE") } }
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlaybackSettingsGroup(viewModel: PlayerViewModel) {
    val defaultResizeMode by viewModel.defaultResizeMode.collectAsState()
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()
    val bufferSize by viewModel.bufferSize.collectAsState()
    val decodingMode by viewModel.decodingMode.collectAsState()
    var showResizeDialog by remember { mutableStateOf(false) }
    var showBufferDialog by remember { mutableStateOf(false) }
    var showDecodingDialog by remember { mutableStateOf(false) }
    SettingsGroup("Configurações de Reprodução") {
        val resizeModeLabel = when(defaultResizeMode) { androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"; androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Preencher"; else -> "Ajustar" }
        SettingsItem(title = "Formato de Tela Padrão", icon = Icons.Default.AspectRatio, subtitle = resizeModeLabel, onClick = { showResizeDialog = true })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(title = "Tamanho do Buffer", icon = Icons.Default.Timer, subtitle = if (bufferSize == 0) "Curto (Internet Rápida)" else "Longo (Internet Instável)", onClick = { showBufferDialog = true })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(title = "Mecanismo de Decodificação", icon = Icons.Default.Memory, subtitle = if (decodingMode == 0) "Hardware (Melhor Performance)" else "Software (Maior Compatibilidade)", onClick = { showDecodingDialog = true })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        var isAutoPlayFocused by remember { mutableStateOf(false) }
        Row(modifier = Modifier.fillMaxWidth().onFocusChanged { isAutoPlayFocused = it.isFocused }.background(if (isAutoPlayFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent).clickable { viewModel.setAutoPlayNext(!autoPlayNext) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(44.dp).background(if (isAutoPlayFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).border(if (isAutoPlayFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                Spacer(modifier = Modifier.width(16.dp)); Column { Text("Próximo Automaticamente", color = if (isAutoPlayFocused) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Inicia o próximo episódio sozinho", color = if (isAutoPlayFocused) MaterialTheme.colorScheme.primary else StpOnSurfaceVariant, fontSize = 12.sp) }
            }
            Switch(checked = autoPlayNext, onCheckedChange = { viewModel.setAutoPlayNext(it) }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
        }
    }
    if (showResizeDialog) SingleChoiceDialog("Formato Padrão", listOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT to "Ajustar (Original)", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom (Sem Bordas)", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL to "Preencher (Esticar)"), defaultResizeMode) { viewModel.setDefaultResizeMode(it); showResizeDialog = false }
    if (showBufferDialog) SingleChoiceDialog("Configurar Buffer", listOf(0 to "Curto (Internet Rápida)", 1 to "Longo (Internet Instável)"), bufferSize) { viewModel.setBufferSize(it); showBufferDialog = false }
    if (showDecodingDialog) SingleChoiceDialog("Mecanismo de Vídeo", listOf(0 to "Hardware (Recomendado)", 1 to "Software"), decodingMode) { viewModel.setDecodingMode(it); showDecodingDialog = false }
}

@Composable
fun AppearanceSettingsGroup(viewModel: PlayerViewModel) {
    val showClock by viewModel.showClock.collectAsState()
    val posterSize by viewModel.posterSize.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    var showPosterSizeDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    SettingsGroup("Personalização da Interface") {
        var isClockFocused by remember { mutableStateOf(false) }
        Row(modifier = Modifier.fillMaxWidth().onFocusChanged { isClockFocused = it.isFocused }.background(if (isClockFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent).clickable { viewModel.setShowClock(!showClock) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(44.dp).background(if (isClockFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).border(if (isClockFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                Spacer(modifier = Modifier.width(16.dp)); Column { Text("Exibir Relógio", color = if (isClockFocused) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Mostra a hora no topo das telas", color = if (isClockFocused) MaterialTheme.colorScheme.primary else StpOnSurfaceVariant, fontSize = 12.sp) }
            }
            Switch(checked = showClock, onCheckedChange = { viewModel.setShowClock(it) }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(title = "Tamanho dos Pôsteres", icon = Icons.Default.PhotoSizeSelectLarge, subtitle = when(posterSize) { 0.8f -> "Pequeno"; 1.2f -> "Grande"; else -> "Médio (Padrão)" }, onClick = { showPosterSizeDialog = true })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(title = stringResource(R.string.appearance_settings_title), icon = Icons.Default.Palette, subtitle = stringResource(R.string.settings_subtitle), iconColor = Color(themeColor), onClick = { showColorDialog = true })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        
        val isLargeText by viewModel.largeTextMode.collectAsState()
        var isLargeFocused by remember { mutableStateOf(false) }
        Row(modifier = Modifier.fillMaxWidth().onFocusChanged { isLargeFocused = it.isFocused }.background(if (isLargeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent).clickable { viewModel.setLargeTextMode(!isLargeText) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(44.dp).background(if (isLargeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).border(if (isLargeFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.TextFields, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                Spacer(modifier = Modifier.width(16.dp)); Column { Text(stringResource(R.string.large_text_mode), color = if (isLargeFocused) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(stringResource(R.string.large_text_mode_desc), color = if (isLargeFocused) MaterialTheme.colorScheme.primary else StpOnSurfaceVariant, fontSize = 12.sp) }
            }
            Switch(checked = isLargeText, onCheckedChange = { viewModel.setLargeTextMode(it) }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
        }
    }
    if (showPosterSizeDialog) SingleChoiceDialog(stringResource(R.string.appearance_settings_title), listOf(0.8f to "Pequeno", 1.0f to "Médio (Padrão)", 1.2f to "Grande"), posterSize) { viewModel.setPosterSize(it); showPosterSizeDialog = false }
    if (showColorDialog) SingleChoiceDialog("Cor do Tema", listOf(0xFFFFD60A.toLong() to "Ouro (Padrão)", 0xFF007AFF.toLong() to "Azul", 0xFFFF3B30.toLong() to "Vermelho", 0xFF34C759.toLong() to "Verde"), themeColor) { viewModel.setThemeColor(it); showColorDialog = false }
}

@Composable
fun BehaviorSettingsGroup(viewModel: PlayerViewModel) {
    val startOnBoot by viewModel.startOnBoot.collectAsState()
    val resumeLastChannel by viewModel.resumeLastChannel.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    var showSleepDialog by remember { mutableStateOf(false) }
    SettingsGroup("Comportamento do Aplicativo") {
        var isBootFocused by remember { mutableStateOf(false) }
        Row(modifier = Modifier.fillMaxWidth().onFocusChanged { isBootFocused = it.isFocused }.background(if (isBootFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent).clickable { viewModel.setStartOnBoot(!startOnBoot) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(44.dp).background(if (isBootFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).border(if (isBootFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                Spacer(modifier = Modifier.width(16.dp)); Column { Text("Iniciar com o Sistema", color = if (isBootFocused) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Abrir o app ao ligar o aparelho", color = if (isBootFocused) MaterialTheme.colorScheme.primary else StpOnSurfaceVariant, fontSize = 12.sp) }
            }
            Switch(checked = startOnBoot, onCheckedChange = { viewModel.setStartOnBoot(it) }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        var isResumeFocused by remember { mutableStateOf(false) }
        Row(modifier = Modifier.fillMaxWidth().onFocusChanged { isResumeFocused = it.isFocused }.background(if (isResumeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent).clickable { viewModel.setResumeLastChannel(!resumeLastChannel) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.size(44.dp).background(if (isResumeFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).border(if (isResumeFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
                Spacer(modifier = Modifier.width(16.dp)); Column { Text("Retomar Último Canal", color = if (isResumeFocused) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Tocar último conteúdo ao abrir", color = if (isResumeFocused) MaterialTheme.colorScheme.primary else StpOnSurfaceVariant, fontSize = 12.sp) }
            }
            Switch(checked = resumeLastChannel, onCheckedChange = { viewModel.setResumeLastChannel(it) }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(title = "Temporizador (Sleep)", icon = Icons.Default.Snooze, subtitle = if (sleepTimerMinutes > 0) "Desliga em $sleepTimerMinutes min" else "Desativado", onClick = { showSleepDialog = true })
    }
    if (showSleepDialog) SingleChoiceDialog("Temporizador", listOf(0 to "Desativado", 15 to "15 Minutos", 30 to "30 Minutos", 60 to "60 Minutos", 90 to "90 Minutos"), sleepTimerMinutes) { viewModel.startSleepTimer(it); showSleepDialog = false }
}

@Composable
fun MaintenanceSettingsGroup(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val downloadSpeed by viewModel.downloadSpeed.collectAsState()
    val customUA by viewModel.customUserAgent.collectAsState()
    var showUADialog by remember { mutableStateOf(false) }
    SettingsGroup("Performance e Rede") {
        val isLoading by viewModel.isLoading.collectAsState()
        SettingsItem(title = "Teste de Velocidade", icon = Icons.Default.Speed, subtitle = if (isLoading) "Testando velocidade..." else if (downloadSpeed > 0) String.format(java.util.Locale.US, "%.2f Mbps", downloadSpeed) else if (downloadSpeed < 0) "Erro no teste" else "Pronto para testar", loading = isLoading, onClick = { viewModel.runSpeedTest() })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(title = "User-Agent Personalizado", icon = Icons.Default.Http, subtitle = if (customUA.isNullOrBlank()) "Padrão do Sistema" else customUA!!, onClick = { showUADialog = true })
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(title = "Limpar Cache de Imagens", icon = Icons.Default.DeleteSweep, subtitle = "Libera espaço apagando posters", onClick = { viewModel.clearImageCache(); Toast.makeText(context, "Cache limpo!", Toast.LENGTH_SHORT).show() })
    }
    if (showUADialog) { var uaValue by remember { mutableStateOf(customUA ?: "") }; AlertDialog(onDismissRequest = { showUADialog = false }, title = { Text("User-Agent", color = Color.White) }, text = { OutlinedTextField(value = uaValue, onValueChange = { uaValue = it }, placeholder = { Text("Ex: VLC/3.0.0") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)) }, confirmButton = { Button(onClick = { viewModel.setCustomUserAgent(uaValue); showUADialog = false }) { Text("SALVAR") } }, containerColor = StpSurface) }
}

@Composable
fun ProfileScreen(viewModel: PlayerViewModel, onNavigate: (Screen) -> Unit) {
    val userInfo by viewModel.userInfo.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp > 720
    val isTVLayout = isLandscape || isWideScreen
    Row(modifier = Modifier.fillMaxSize().background(StpBackground)) {
        if (isTVLayout) PremiumNavigationRail(currentScreen = Screen.Profile, onNavigate = onNavigate)
        Column(modifier = Modifier.fillMaxSize().padding(top = if (isTVLayout) 24.dp else 20.dp).padding(horizontal = 20.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onNavigate(Screen.Home) }, modifier = Modifier.background(StpSurfaceHigh, CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Spacer(modifier = Modifier.width(16.dp)); Text(stringResource(R.string.my_profile), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, fontSize = if (isLandscape) 24.sp else 28.sp)
            }
            Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 32.dp))
            Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(StpSurface, Color.Black)), RoundedCornerShape(32.dp)).border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), RoundedCornerShape(32.dp)).padding(if (isLandscape) 32.dp else 24.dp)) {
                if (isLandscape) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.35f)) {
                            Box(modifier = Modifier.size(120.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.Black, modifier = Modifier.size(64.dp)) }
                            Spacer(modifier = Modifier.height(16.dp)); Text(userInfo?.username ?: "Usuário", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
                            Surface(color = StpSecondary.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, StpSecondary.copy(alpha = 0.3f))) { Text(if (userInfo?.isTrial == true) " CONTA TESTE " else " ASSINATURA PREMIUM ", color = StpSecondary, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                        }
                        Column(modifier = Modifier.weight(0.65f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ProfileInfoItem(Icons.Default.Info, "Status do Servidor", userInfo?.status ?: "Ativo")
                            ProfileInfoItem(Icons.Default.CalendarToday, "Início da Assinatura", userInfo?.createdAt ?: "N/A")
                            ProfileInfoItem(Icons.Default.Warning, "Data de Vencimento", userInfo?.expiryDate ?: "N/A")
                            val connectionsDisplay = if ((userInfo?.maxConnections ?: 1) <= 0 || (userInfo?.maxConnections ?: 1) > 100) "${userInfo?.activeConnections ?: 0} / Ilimitado" else "${userInfo?.activeConnections ?: 0} / ${userInfo?.maxConnections ?: 1}"
                            ProfileInfoItem(Icons.Default.Devices, "Telas Simultâneas", connectionsDisplay)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(88.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = Color.Black, modifier = Modifier.size(48.dp)) }
                        Spacer(modifier = Modifier.height(20.dp)); Text(userInfo?.username ?: "Usuário", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp)
                        Surface(color = StpSecondary.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, StpSecondary.copy(alpha = 0.3f))) { Text(if (userInfo?.isTrial == true) " CONTA TESTE " else " ASSINATURA PREMIUM ", color = StpSecondary, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                        Spacer(modifier = Modifier.height(32.dp)); HorizontalDivider(color = StpOutline.copy(alpha = 0.3f)); Spacer(modifier = Modifier.height(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileInfoItem(Icons.Default.Info, "Status", userInfo?.status ?: "Ativo")
                            ProfileInfoItem(Icons.Default.CalendarToday, "Início", userInfo?.createdAt ?: "N/A")
                            ProfileInfoItem(Icons.Default.Warning, "Vencimento", userInfo?.expiryDate ?: "N/A")
                            ProfileInfoItem(Icons.Default.Devices, "Conexões", "${userInfo?.activeConnections ?: 0} / ${userInfo?.maxConnections ?: 1}")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            SettingsGroup("GERENCIAR PERFIL") {
                SettingsItem(title = "Trocar de Perfil", icon = Icons.Default.SwitchAccount, subtitle = "Escolher outro perfil da família", onClick = { viewModel.logoutProfile() })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
                SettingsItem(title = "Sair da Conta", icon = Icons.AutoMirrored.Filled.Logout, subtitle = "Encerrar sessão deste servidor", iconColor = StpRed, onClick = { viewModel.logout() })
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
fun ProfileInfoItem(icon: ImageVector, label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(12.dp)); Text(label, color = StpOnSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(text = title, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp), letterSpacing = 1.5.sp)
        Surface(color = StpSurface, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, StpOutline.copy(alpha = 0.3f))) { Column { content() } }
    }
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, subtitle: String, iconColor: Color = MaterialTheme.colorScheme.primary, loading: Boolean = false, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }.background(if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent).clickable(enabled = !loading) { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(44.dp).background(if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else iconColor.copy(alpha = 0.1f), CircleShape).border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) { if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = iconColor, strokeWidth = 2.dp) else Icon(icon, null, tint = if (isFocused) MaterialTheme.colorScheme.primary else iconColor, modifier = Modifier.size(22.dp)) }
        Spacer(modifier = Modifier.width(16.dp)); Column(modifier = Modifier.weight(1f)) { Text(title, color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(subtitle, color = if (loading || isFocused) MaterialTheme.colorScheme.primary else StpOnSurfaceVariant, fontSize = 12.sp) }
        if (!loading) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = if (isFocused) MaterialTheme.colorScheme.primary else StpOutline, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SupportSettingsGroup(appVersion: String) {
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }
    
    SettingsGroup(stringResource(R.string.support_label).uppercase()) {
        SettingsItem(
            title = stringResource(R.string.contact_support),
            icon = Icons.Default.SupportAgent,
            subtitle = stringResource(R.string.contact_support_desc),
            onClick = {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.link/k6h325"))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, context.getString(R.string.could_not_open_link), Toast.LENGTH_SHORT).show()
                }
            }
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = StpOutline.copy(alpha = 0.5f))
        SettingsItem(
            title = stringResource(R.string.about_the_app),
            icon = Icons.Default.Info,
            subtitle = "Version $appVersion",
            onClick = { showAboutDialog = true }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    AnimatedLogo(size = 64.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(text = stringResource(R.string.app_name), color = Color.White, fontWeight = FontWeight.Black)
                    Text(text = "Versão $appVersion", color = StpOnSurfaceVariant, fontSize = 12.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.disclaimer_title),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.disclaimer_body),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("FECHAR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = StpSurface,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
fun LanguageSettingsGroup(viewModel: PlayerViewModel, currentLanguage: String, onClick: () -> Unit) {
    val languageName = when(currentLanguage) {
        "en" -> stringResource(R.string.english)
        "es" -> stringResource(R.string.spanish)
        else -> stringResource(R.string.portuguese)
    }
    
    SettingsGroup(stringResource(R.string.language_label).uppercase()) {
        SettingsItem(
            title = stringResource(R.string.select_language),
            icon = Icons.Default.Language,
            subtitle = languageName,
            onClick = onClick
        )
    }
}

@Composable
fun <T> SingleChoiceDialog(title: String, options: List<Pair<T, String>>, currentSelected: T, onSelect: (T) -> Unit) {
    AlertDialog(onDismissRequest = { }, title = { Text(title, color = Color.White) }, text = { Column { options.forEach { (value, label) -> Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = currentSelected == value, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)); Spacer(modifier = Modifier.width(12.dp)); Text(label, color = Color.White) } } } }, confirmButton = { }, containerColor = StpSurface)
}

@Composable
fun KeywordManagerDialog(keywords: Set<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit, onDismiss: () -> Unit) {
    var newKeyword by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Filtro Automático", color = Color.White) }, text = { Column { Text("Categorias que contenham estas palavras serão bloqueadas automaticamente ao carregar a lista.", color = StpOnSurfaceVariant, fontSize = 13.sp); Spacer(modifier = Modifier.height(16.dp)); OutlinedTextField(value = newKeyword, onValueChange = { newKeyword = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Nova palavra (ex: XXX)") }, trailingIcon = { IconButton(onClick = { if (newKeyword.isNotBlank()) { onAdd(newKeyword); newKeyword = "" } }) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) } }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)); Spacer(modifier = Modifier.height(16.dp)); LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) { items(keywords.toList()) { keyword -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(keyword, color = Color.White); IconButton(onClick = { onRemove(keyword) }) { Icon(Icons.Default.Delete, null, tint = StpRed, modifier = Modifier.size(20.dp)) } } } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("FECHAR", color = MaterialTheme.colorScheme.primary) } }, containerColor = StpSurface)
}

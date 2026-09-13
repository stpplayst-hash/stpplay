package com.stpplay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stpplay.android.R
import com.stpplay.android.database.ProfileEntity
import com.stpplay.android.ui.theme.StpBackground
import com.stpplay.android.ui.theme.StpSurface

@Composable
fun ProfileSelectionScreen(
    profiles: List<ProfileEntity>,
    hasParentalPin: Boolean,
    onVerifyPin: (String) -> Boolean,
    onSelect: (ProfileEntity) -> Unit,
    onCreate: (String, Boolean) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var isKids by remember { mutableStateOf(false) }

    var selectedProfileForPin by remember { mutableStateOf<ProfileEntity?>(null) }
    var isVerifyingForNewProfile by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StpBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.who_is_watching),
                color = Color.White,
                fontSize = if (profiles.isEmpty()) 20.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            if (profiles.isEmpty() && !showCreateDialog) {
                Button(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.create_first_profile), color = Color.Black, fontWeight = FontWeight.Black)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.widthIn(max = 800.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    items(profiles) { profile ->
                        ProfileItemView(
                            name = profile.name,
                            icon = if (profile.isKids) Icons.Default.ChildCare else Icons.Default.Person,
                            color = getColorForProfile(profile.id),
                            onClick = { 
                                if (!profile.isKids && hasParentalPin) {
                                    selectedProfileForPin = profile
                                } else {
                                    onSelect(profile) 
                                }
                            }
                        )
                    }

                    if (profiles.size < 5) {
                        item {
                            ProfileItemView(
                                name = stringResource(R.string.add_label),
                                icon = Icons.Default.Add,
                                color = Color.Gray.copy(alpha = 0.3f),
                                onClick = { 
                                    if (hasParentalPin) {
                                        isVerifyingForNewProfile = true
                                    } else {
                                        showCreateDialog = true 
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog de PIN para Perfil Adulto
    if (selectedProfileForPin != null) {
        ParentalPinDialog(
            onDismiss = { selectedProfileForPin = null },
            onVerify = { onVerifyPin(it) },
            onSuccess = {
                onSelect(selectedProfileForPin!!)
                selectedProfileForPin = null
            },
            title = stringResource(R.string.protected_profile),
            description = stringResource(R.string.enter_pin_to_access, selectedProfileForPin?.name ?: "")
        )
    }

    // Dialog de PIN para criar novo perfil
    if (isVerifyingForNewProfile) {
        ParentalPinDialog(
            onDismiss = { isVerifyingForNewProfile = false },
            onVerify = { onVerifyPin(it) },
            onSuccess = {
                isVerifyingForNewProfile = false
                showCreateDialog = true
            },
            title = stringResource(R.string.restricted_access),
            description = stringResource(R.string.confirm_pin)
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(R.string.new_profile), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = { newProfileName = it },
                        label = { Text(stringResource(R.string.profile_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isKids = !isKids }
                            .padding(vertical = 8.dp)
                    ) {
                        Checkbox(
                            checked = isKids, 
                            onCheckedChange = { isKids = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.kids_profile), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.kids_profile_desc), color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newProfileName.isNotBlank()) {
                            onCreate(newProfileName, isKids)
                            showCreateDialog = false
                            newProfileName = ""
                            isKids = false
                        }
                    },
                    enabled = newProfileName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.create_label), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("CANCELAR", color = Color.White)
                }
            },
            containerColor = StpSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun ProfileItemView(
    name: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp)
            .graphicsLayer(scaleX = if (isFocused) 1.1f else 1f, scaleY = if (isFocused) 1.1f else 1f)
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color)
                .border(if (isFocused) 4.dp else 0.dp, Color.White, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(64.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = name,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 18.sp,
            fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun getColorForProfile(id: String): Color {
    val colors = listOf(
        Color(0xFFE50914), // Red
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFFFFC107), // Amber
        Color(0xFF9C27B0), // Purple
        Color(0xFF00BCD4)  // Cyan
    )
    return colors[Math.abs(id.hashCode()) % colors.size]
}

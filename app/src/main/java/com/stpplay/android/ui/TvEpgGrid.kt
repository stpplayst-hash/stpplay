package com.stpplay.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stpplay.android.data.Channel
import com.stpplay.android.database.EpgProgramEntity
import com.stpplay.android.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

private const val HOUR_WIDTH = 600 // dp por hora
private const val CHANNEL_COLUMN_WIDTH = 250 // dp
private const val ROW_HEIGHT = 80 // dp

@Composable
fun TvEpgGrid(
    channels: List<Channel>,
    viewModel: PlayerViewModel,
    onChannelClick: (Channel) -> Unit
) {
    val now = remember { System.currentTimeMillis() }
    val startTime = remember(now) { 
        Calendar.getInstance().apply { 
            timeInMillis = now
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis 
    }
    val endTime = startTime + 6 * 3600000L // 6 horas à frente
    
    val allPrograms by viewModel.getEpgProgramsInRange(startTime, endTime).collectAsState(initial = emptyList())
    
    val horizontalScrollState = rememberScrollState()
    val verticalLazyListState = rememberLazyListState()
    
    Box(modifier = Modifier.fillMaxSize().background(StpBackground)) {
        Column {
            // 1. TIMELINE HEADER
            Row(modifier = Modifier.fillMaxWidth()) {
                // Espaço vazio acima dos canais
                Box(modifier = Modifier.width(CHANNEL_COLUMN_WIDTH.dp).height(40.dp))
                
                // Horários
                Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                    Row(modifier = Modifier.height(40.dp)) {
                        for (i in 0 until 6) {
                            val time = startTime + i * 3600000L
                            val label = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))
                            Box(
                                modifier = Modifier.width(HOUR_WIDTH.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            // 2. GRID PRINCIPAL (Canais + Programas)
            LazyColumn(
                state = verticalLazyListState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(channels) { channel ->
                    val channelPrograms = remember(allPrograms, channel) {
                        allPrograms.filter { 
                            it.streamId == channel.streamId || 
                            it.epgChannelId == channel.epgChannelId || 
                            (it.epgChannelId != null && it.epgChannelId == channel.streamId)
                        }.sortedBy { it.startTimestamp }
                    }
                    
                    EpgRow(
                        channel = channel,
                        programs = channelPrograms,
                        startTime = startTime,
                        horizontalScrollState = horizontalScrollState,
                        onChannelClick = onChannelClick
                    )
                }
            }
        }

        // 3. INDICADOR "AGORA" (Linha Vermelha)
        val elapsedMinutes = (System.currentTimeMillis() - startTime) / 60000
        val indicatorOffset = (elapsedMinutes * (HOUR_WIDTH / 60f)).dp
        
        Box(
            modifier = Modifier
                .padding(start = CHANNEL_COLUMN_WIDTH.dp)
                .offset(x = indicatorOffset - horizontalScrollState.value.dp)
                .fillMaxHeight()
                .width(2.dp)
                .background(StpRed)
        )
    }
}

@Composable
fun EpgRow(
    channel: Channel,
    programs: List<EpgProgramEntity>,
    startTime: Long,
    horizontalScrollState: ScrollState,
    onChannelClick: (Channel) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT.dp)) {
        // Coluna do Canal (Fixa)
        var isChannelFocused by remember { mutableStateOf(false) }
        Surface(
            onClick = { onChannelClick(channel) },
            modifier = Modifier
                .width(CHANNEL_COLUMN_WIDTH.dp)
                .fillMaxHeight()
                .onFocusChanged { isChannelFocused = it.isFocused },
            color = if (isChannelFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(channel.logo)
                        .crossfade(true)
                        .size(120, 120)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                    error = painterResource(id = com.stpplay.android.R.drawable.logo)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    channel.name, 
                    color = if (isChannelFocused) Color.Black else Color.White, 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Área de Programas (Scrollable)
        Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(horizontalScrollState)) {
            Row(modifier = Modifier.fillMaxHeight()) {
                if (programs.isEmpty()) {
                    // Placeholder se não houver EPG
                    Box(
                        modifier = Modifier
                            .width((HOUR_WIDTH * 6).dp)
                            .fillMaxHeight()
                            .border(0.5.dp, Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("Programação indisponível", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    programs.forEach { program ->
                        ProgramCell(program, startTime)
                    }
                }
            }
        }
    }
}

@Composable
fun ProgramCell(program: EpgProgramEntity, gridStartTime: Long) {
    val durationMinutes = (program.stopTimestamp - program.startTimestamp) / 60000
    val width = (durationMinutes * (HOUR_WIDTH / 60f)).dp
    
    // Calcular offset inicial se o programa começou antes do gridStartTime
    val startOffset = if (program.startTimestamp < gridStartTime) 0 else {
        ((program.startTimestamp - gridStartTime) / 60000 * (HOUR_WIDTH / 60f)).toInt()
    }

    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = { /* Mostrar detalhes do programa */ },
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .onFocusChanged { isFocused = it.isFocused },
        color = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                program.title, 
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White, 
                fontSize = 13.sp, 
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val timeLabel = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(program.startTimestamp))
            Text(
                timeLabel, 
                color = StpOnSurfaceVariant, 
                fontSize = 11.sp
            )
        }
    }
}

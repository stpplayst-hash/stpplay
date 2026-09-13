package com.stpplay.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stpplay.android.ui.theme.StpSurfaceHigh
import com.stpplay.android.ui.theme.StpSurfaceHighest
import java.util.Calendar
import java.util.Locale

@Composable
fun QualityBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun GlassySurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(0.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = Color.Black.copy(alpha = 0.7f),
        tonalElevation = 8.dp,
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        content()
    }
}

@Composable
fun DynamicLogoPlaceholder(name: String, modifier: Modifier = Modifier) {
    val initials = name.take(2).uppercase()
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val gradient = Brush.verticalGradient(
        colors = listOf(StpSurfaceHigh, Color.Black)
    )
    Box(
        modifier = modifier
            .background(gradient)
            .graphicsLayer(alpha = alpha)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TvClock() {
    var currentTime by remember { mutableStateOf(Calendar.getInstance().time) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance().time
            kotlinx.coroutines.delay(1000L)
        }
    }
    val sdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
    Text(
        text = sdf.format(currentTime),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
}

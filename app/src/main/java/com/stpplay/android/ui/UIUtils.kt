package com.stpplay.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stpplay.android.R
import com.stpplay.android.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun getGutter(): Dp {
    val configuration = LocalConfiguration.current
    val isTVLayout = configuration.screenWidthDp > 720 || configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    return if (isTVLayout) 0.dp else 20.dp
}

@Composable
fun GlassySurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
    blurRadius: Dp = 30.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .border(
                border = BorderStroke(0.5.dp, Brush.verticalGradient(listOf(Color.White.copy(0.15f), Color.Transparent))),
                shape = shape
            ),
        color = Color.Black.copy(alpha = 0.8f), // Um pouco mais opaco para TV
        shape = shape,
        content = content
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(StpSurfaceHigh.copy(alpha = alpha))
    )
}

@Composable
fun KenBurnsBackdrop(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ken_burns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = imageUrl,
            transitionSpec = { fadeIn(tween(1200)) togetherWith fadeOut(tween(1200)) },
            label = "backdrop_anim"
        ) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                contentScale = ContentScale.Crop,
                alpha = 0.6f
            )
        }
        
        // Premium Scrim (Refinado para máxima legibilidade e profundidade)
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.1f),
                0.4f to Color.Black.copy(alpha = 0.6f),
                0.7f to Color.Black.copy(alpha = 0.9f),
                1.0f to Color.Black
            )
        ))
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0.0f to Color.Black.copy(alpha = 0.85f), // Aumentado de 0.75f
                0.3f to Color.Black.copy(alpha = 0.4f),
                0.7f to Color.Transparent
            )
        ))
    }
}

@Composable
fun AnimatedLogo(size: androidx.compose.ui.unit.Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .shadow(elevation = 20.dp, shape = CircleShape)
            .clip(CircleShape)
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.logo),
            contentDescription = "Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun TvClock(modifier: Modifier = Modifier) {
    val viewModel: PlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val showClock by viewModel.showClock.collectAsState()
    
    if (!showClock) return

    var time by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            // Formato exato da imagem: seg., jul. 13 18:33
            val sdf = SimpleDateFormat("EEE, MMM dd HH:mm", Locale.getDefault())
            time = sdf.format(now)
            delay(1000)
        }
    }
    
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(
            text = time.lowercase(), 
            color = Color.White.copy(alpha = 0.9f), 
            fontSize = 18.sp, 
            fontWeight = FontWeight.Medium
        )
    }
}

package com.stpplay.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.05f),
        Color.White.copy(alpha = 0.15f),
        Color.White.copy(alpha = 0.05f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    background(brush)
}

@Composable
fun PosterSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(110.dp)
            .aspectRatio(2/3f)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .shimmerEffect()
    )
}

@Composable
fun CategoryRowSkeleton() {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp, bottom = 12.dp)
                .width(120.dp)
                .height(20.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                .shimmerEffect()
        )
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(6) { PosterSkeleton() }
        }
    }
}

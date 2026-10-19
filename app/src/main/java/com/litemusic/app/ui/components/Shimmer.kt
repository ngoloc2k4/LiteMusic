package com.litemusic.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Lightweight, high-performance Compose Shimmer modifier using standard LinearEasing.
 * Gives immediate visual feedback during initial loading, category switching, and refresh.
 */
@Composable
fun Modifier.shimmerEffect(durationMillis: Int = 1100): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerAnim"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)

    return this.background(
        brush = Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset(x = translateAnim - 350f, y = translateAnim - 350f),
            end = Offset(x = translateAnim, y = translateAnim)
        )
    )
}

@Composable
fun PillSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val pillWidths = listOf(56.dp, 72.dp, 68.dp, 84.dp, 70.dp, 76.dp)
        pillWidths.forEach { w ->
            Box(
                modifier = Modifier
                    .width(w)
                    .height(32.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .shimmerEffect()
            )
        }
    }
}

@Composable
fun QuickPicksSkeletonSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header Shimmer
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .shimmerEffect()
            )
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .shimmerEffect()
            )
        }

        // 4 stacked rows (YouTube Music style quick pick column)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerEffect()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(11.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerEffect()
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

@Composable
fun SquareCardsSkeletonSection(
    modifier: Modifier = Modifier,
    titleWidth: Int = 140
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(titleWidth.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Column(modifier = Modifier.width(136.dp)) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

@Composable
fun MusicVideosSkeletonSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(2) {
                Column(modifier = Modifier.width(220.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(124.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

/**
 * YouTube Music Style Full Home Skeleton Loading Layout
 */
@Composable
fun HomeSkeletonLoading(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        PillSkeletonRow()
        QuickPicksSkeletonSection()
        SquareCardsSkeletonSection(titleWidth = 120)
        MusicVideosSkeletonSection()
    }
}

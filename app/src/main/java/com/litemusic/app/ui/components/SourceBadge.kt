package com.litemusic.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litemusic.app.core.model.AudioSource

/**
 * Reusable audio source badge for song cards and lists.
 */
@Composable
fun SourceBadge(
    source: AudioSource,
    modifier: Modifier = Modifier
) {
    val (label, bg, fg) = when (source) {
        AudioSource.YOUTUBE_MUSIC -> Triple("YT Music", Color(0xFFFF0000).copy(alpha = 0.15f), Color(0xFFFF4E4E))
        AudioSource.YOUTUBE_VIDEO -> Triple("YouTube", Color(0xFFFF0000).copy(alpha = 0.15f), Color(0xFFFF4E4E))
        AudioSource.SOUNDCLOUD -> Triple("SoundCloud", Color(0xFFFF5500).copy(alpha = 0.15f), Color(0xFFFF7700))
        AudioSource.SPOTIFY -> Triple("Spotify", Color(0xFF1DB954).copy(alpha = 0.15f), Color(0xFF1ED760))
        AudioSource.ALL -> Triple("All", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bg,
        modifier = modifier
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

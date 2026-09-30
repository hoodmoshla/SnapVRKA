package com.mvrk.vrka

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ConfigurationSummary(
    mode: MediaMode,
    quality: VideoQuality,
    prefer60Fps: Boolean,
    audioFormat: AudioFormat,
    bitrate: Int,
    playlist: Boolean,
    playlistStart: String,
    playlistEnd: String,
    subtitles: Boolean,
    trimStart: String,
    trimEnd: String,
    sponsorBlock: Boolean,
) {
    val primary = when (mode) {
        MediaMode.VIDEO -> stringResource(
            R.string.config_video_line,
            stringResource(quality.labelRes),
            stringResource(if (prefer60Fps) R.string.config_on else R.string.config_off),
        )
        MediaMode.AUDIO -> when (audioFormat) {
            AudioFormat.MP3 -> stringResource(R.string.config_mp3_line, bitrate)
            AudioFormat.OPUS -> stringResource(R.string.config_opus_line)
            AudioFormat.WAV -> stringResource(R.string.config_wav_line)
        }
    }
    val playlistText = stringResource(R.string.config_playlist)
    val playlistRangeText = stringResource(R.string.config_playlist_range, playlistRangeLabel(playlistStart, playlistEnd))
    val subtitlesText = stringResource(R.string.config_subtitles)
    val trimText = stringResource(R.string.config_trim)
    val sponsorText = stringResource(R.string.config_sponsorblock)

    val extras = ArrayList<String>(5)
    if (playlist) {
        val range = playlistRangeLabel(playlistStart, playlistEnd)
        extras.add(if (range.isBlank()) playlistText else playlistRangeText)
    }
    if (subtitles) extras.add(subtitlesText)
    if (trimStart.isNotBlank() || trimEnd.isNotBlank()) extras.add(trimText)
    if (sponsorBlock) extras.add(sponsorText)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            stringResource(R.string.config_readout),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = VrkaMonoFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            ),
            color = VrkaTokens.TextTertiary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            primary,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = VrkaMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            color = VrkaTokens.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (extras.isNotEmpty()) {
            Text(
                extras.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = VrkaMonoFamily,
                    fontSize = 12.sp,
                ),
                color = VrkaTokens.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun playlistRangeLabel(start: String, end: String): String =
    listOf(start, end).filter(String::isNotBlank).joinToString("–")

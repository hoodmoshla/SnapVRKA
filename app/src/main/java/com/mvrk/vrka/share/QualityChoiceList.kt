package com.mvrk.vrka.share

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mvrk.vrka.R
import com.mvrk.vrka.VrkaMonoFamily
import com.mvrk.vrka.VrkaTokens

/**
 * Shared quality list used by **both** the Quick Download sheet and the Home screen.
 *
 * The rows are always built from a real yt-dlp probe result, so a resolution that the source does
 * not provide can never be offered.
 */
@Composable
internal fun QualityChoiceList(
    videoOptions: List<VideoQualityOption>,
    audioOptions: List<AudioQualityOption>,
    selectedVideo: VideoQualityOption?,
    selectedAudio: AudioQualityOption?,
    onSelectVideo: (VideoQualityOption) -> Unit,
    onSelectAudio: (AudioQualityOption) -> Unit,
    modifier: Modifier = Modifier,
    showVideoSection: Boolean = true,
    showAudioSection: Boolean = true,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (showVideoSection && videoOptions.isNotEmpty()) {
            SectionTitle(stringResource(R.string.quick_section_video))
            videoOptions.forEach { option ->
                QualityRow(
                    title = buildString {
                        append(option.label)
                        option.fps?.takeIf { it >= 50 }?.let { append(" · ${it.toInt()} FPS") }
                    },
                    subtitle = option.vcodec?.substringBefore('/'),
                    sizeText = SizeFormatter.format(context, option.sizeBytes, option.sizeIsApproximate),
                    selected = selectedVideo?.videoFormatId == option.videoFormatId &&
                        selectedVideo.height == option.height,
                    onClick = { onSelectVideo(option) },
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (showAudioSection && audioOptions.isNotEmpty()) {
            SectionTitle(stringResource(R.string.quick_section_audio))
            audioOptions.forEach { option ->
                QualityRow(
                    title = audioOptionTitle(option),
                    subtitle = if (option.isNativeCopy && option.codec == "opus") {
                        stringResource(R.string.quick_audio_opus_native)
                    } else {
                        null
                    },
                    sizeText = SizeFormatter.format(context, option.sizeBytes, option.sizeIsApproximate),
                    selected = selectedAudio?.codec == option.codec &&
                        selectedAudio.bitrateKbps == option.bitrateKbps,
                    onClick = { onSelectAudio(option) },
                )
            }
        }
    }
}

@Composable
internal fun audioOptionTitle(option: AudioQualityOption): String =
    if (option.codec == "opus") {
        stringResource(R.string.quick_audio_opus)
    } else {
        stringResource(R.string.quick_audio_mp3, option.bitrateKbps ?: 0)
    }

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = VrkaMonoFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
        ),
        color = VrkaTokens.TextTertiary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
internal fun QualityRow(
    title: String,
    subtitle: String?,
    sizeText: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) VrkaTokens.AccentContainer else VrkaTokens.SurfaceCard,
        border = BorderStroke(1.dp, if (selected) VrkaTokens.BorderActive else VrkaTokens.BorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = VrkaMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = VrkaTokens.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = VrkaTokens.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = VrkaMonoFamily),
                color = if (selected) VrkaTokens.AccentLight else VrkaTokens.TextSecondary,
                maxLines = 1,
            )
        }
    }
}

package com.mvrk.vrka.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mvrk.vrka.R
import com.mvrk.vrka.VrkaMonoFamily
import com.mvrk.vrka.VrkaTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Quick Download bottom sheet shown by [ShareActivity].
 *
 * Arabic-first, RTL, AMOLED friendly and intentionally compact so it never covers the whole screen.
 */
@Composable
fun QuickDownloadSheet(
    state: QuickDownloadState,
    onSelectVideo: (VideoQualityOption) -> Unit,
    onSelectAudio: (AudioQualityOption) -> Unit,
    onDownload: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = VrkaTokens.SurfaceElevated,
            border = BorderStroke(1.dp, VrkaTokens.BorderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(VrkaTokens.BorderSubtle),
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.quick_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = VrkaMonoFamily,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        ),
                        color = VrkaTokens.Accent,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close), color = VrkaTokens.TextSecondary)
                    }
                }

                when (state.phase) {
                    ProbePhase.FAILED -> ProbeErrorState(state, onRetry, onDismiss)
                    ProbePhase.READY -> ReadyState(state, onSelectVideo, onSelectAudio, onDownload)
                    else -> LoadingState(state)
                }
            }
        }
    }
}

@Composable
private fun LoadingState(state: QuickDownloadState) {
    val message = when (state.phase) {
        ProbePhase.ANALYSING -> stringResource(R.string.quick_analysing)
        ProbePhase.FETCHING_INFO -> stringResource(R.string.quick_fetching)
        ProbePhase.READING_FORMATS -> stringResource(R.string.quick_reading_formats)
        else -> stringResource(R.string.quick_analysing)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = VrkaTokens.Accent, strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = VrkaMonoFamily),
            color = VrkaTokens.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProbeErrorState(
    state: QuickDownloadState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.errorMessage ?: stringResource(R.string.quick_error_probe),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = VrkaMonoFamily),
            color = VrkaTokens.Error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.quick_retry))
            }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = VrkaTokens.Accent),
            ) {
                Text(stringResource(R.string.action_close), color = Color.White)
            }
        }
    }
}

@Composable
private fun ReadyState(
    state: QuickDownloadState,
    onSelectVideo: (VideoQualityOption) -> Unit,
    onSelectAudio: (AudioQualityOption) -> Unit,
    onDownload: () -> Unit,
) {
    val media = state.media

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Thumbnail(media?.thumbnailUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.media?.title.orEmpty().ifBlank { state.url },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = VrkaTokens.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            media?.uploader?.takeIf { it.isNotBlank() }?.let { uploader ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = uploader,
                    style = MaterialTheme.typography.labelSmall,
                    color = VrkaTokens.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val duration = formatDuration(media?.durationSeconds)
            if (duration != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.quick_duration, duration),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = VrkaMonoFamily),
                    color = VrkaTokens.TextSecondary,
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    if (state.videoOptions.isEmpty() && state.audioOptions.isEmpty()) {
        Text(
            text = stringResource(R.string.quick_no_formats),
            style = MaterialTheme.typography.bodyMedium,
            color = VrkaTokens.TextSecondary,
            modifier = Modifier.padding(vertical = 18.dp),
        )
        return
    }

    if (state.videoOptions.isNotEmpty()) {
        SectionTitle(stringResource(R.string.quick_section_video))
        state.videoOptions.forEach { option ->
            QualityRow(
                title = buildString {
                    append(option.label)
                    option.fps?.takeIf { it >= 50 }?.let { append(" · ${it.toInt()} FPS") }
                },
                subtitle = option.vcodec?.substringBefore('/'),
                sizeText = SizeFormatter.format(androidx.compose.ui.platform.LocalContext.current, option.sizeBytes, option.sizeIsApproximate),
                selected = state.selectedVideo?.videoFormatId == option.videoFormatId &&
                    state.selectedVideo?.height == option.height,
                onClick = { onSelectVideo(option) },
            )
        }
        Spacer(Modifier.height(10.dp))
    }

    if (state.audioOptions.isNotEmpty()) {
        SectionTitle(stringResource(R.string.quick_section_audio))
        state.audioOptions.forEach { option ->
            QualityRow(
                title = option.label,
                subtitle = if (option.isNativeCopy && option.codec == "opus") {
                    stringResource(R.string.quick_audio_opus_native)
                } else {
                    null
                },
                sizeText = SizeFormatter.format(androidx.compose.ui.platform.LocalContext.current, option.sizeBytes, option.sizeIsApproximate),
                selected = state.selectedAudio?.codec == option.codec &&
                    state.selectedAudio?.bitrateKbps == option.bitrateKbps,
                onClick = { onSelectAudio(option) },
            )
        }
    }

    Spacer(Modifier.height(18.dp))

    val canDownload = state.selectedVideo != null || state.selectedAudio != null
    Button(
        onClick = onDownload,
        enabled = canDownload && !state.enqueued,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = VrkaTokens.Accent,
            contentColor = Color.White,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (state.enqueued) stringResource(R.string.quick_enqueued) else stringResource(R.string.quick_download),
            fontWeight = FontWeight.Bold,
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionTitle(text: String) {
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
private fun QualityRow(
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

@Composable
private fun Thumbnail(url: String?) {
    val bitmap by produceState<Bitmap?>(initialValue = null, url) {
        value = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) { downloadBitmap(url) }
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = VrkaTokens.SurfaceInset,
        border = BorderStroke(1.dp, VrkaTokens.BorderSubtle),
        modifier = Modifier.size(width = 108.dp, height = 68.dp),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    tint = VrkaTokens.TextTertiary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun downloadBitmap(url: String): Bitmap? = runCatching {
    if (!url.startsWith("https://", ignoreCase = true)) return null
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 6000
        readTimeout = 6000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) SnapVRKA/1.0")
    }
    try {
        connection.inputStream.use { BitmapFactory.decodeStream(it) }
    } finally {
        connection.disconnect()
    }
}.getOrNull()

internal fun formatDuration(seconds: Double?): String? {
    if (seconds == null || seconds <= 0.0) return null
    val total = seconds.toLong()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val remainder = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%d:%02d".format(minutes, remainder)
    }
}

/** Wraps any Quick Download content in a forced RTL layout. */
@Composable
fun RtlContainer(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        content()
    }
}

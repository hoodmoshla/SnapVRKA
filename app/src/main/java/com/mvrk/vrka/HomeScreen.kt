package com.mvrk.vrka

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeScreen(
    settings: AppSettings,
    runtime: RuntimeStatus,
    modifier: Modifier = Modifier,
    onEnqueue: (DownloadRequest) -> Unit,
    onUpdateDownloadLocation: (uri: String, mode: SaveLocationMode, configured: Boolean) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(settings.defaultMode) }
    var quality by remember { mutableStateOf(settings.defaultQuality) }
    var prefer60Fps by remember { mutableStateOf(false) }
    var audioFormat by remember { mutableStateOf(settings.defaultAudioFormat) }
    var bitrate by remember { mutableStateOf(settings.defaultMp3Bitrate) }
    var advanced by remember { mutableStateOf(false) }
    var playlist by remember { mutableStateOf(false) }
    var playlistStart by remember { mutableStateOf("") }
    var playlistEnd by remember { mutableStateOf("") }
    var subtitles by remember { mutableStateOf(false) }
    var automaticCaptions by remember { mutableStateOf(true) }
    var embedSubtitles by remember { mutableStateOf(true) }
    var subtitleLanguages by remember { mutableStateOf("en.*") }
    var embedMetadata by remember { mutableStateOf(true) }
    var embedThumbnail by remember { mutableStateOf(true) }
    var sponsorBlock by remember { mutableStateOf(false) }
    var sponsorCategories by remember { mutableStateOf("sponsor,selfpromo,interaction") }
    var trimStart by remember { mutableStateOf("") }
    var trimEnd by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var customHeaders by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var validation by remember { mutableStateOf("") }
    var pendingRequestToEnqueue by remember { mutableStateOf<DownloadRequest?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var dialogSelectedUri by remember { mutableStateOf("") }
    var setAsDefaultChecked by remember { mutableStateOf(true) }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            dialogSelectedUri = uri.toString()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                ),
                color = VrkaTokens.Accent,
            )
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(VrkaTokens.SurfaceCard)
                .border(
                    1.dp,
                    if (validation.isNotBlank()) VrkaTokens.Error else VrkaTokens.BorderSubtle,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_download),
                    contentDescription = null,
                    tint = VrkaTokens.AccentLight,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(12.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = url,
                    onValueChange = {
                        url = it.trim()
                        validation = ""
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = VrkaTokens.TextPrimary,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(VrkaTokens.AccentLight),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (url.isEmpty()) {
                            Text(
                                stringResource(R.string.home_url_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = VrkaTokens.TextTertiary,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        innerTextField()
                    },
                )
                Spacer(Modifier.width(8.dp))
                if (url.isNotEmpty()) {
                    Surface(
                        onClick = {
                            url = ""
                            validation = ""
                        },
                        shape = CircleShape,
                        color = VrkaTokens.SurfaceElevated,
                        border = BorderStroke(1.dp, VrkaTokens.BorderSubtle),
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.action_clear),
                                tint = VrkaTokens.TextSecondary,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = {
                            val clipboard = context.getSystemService(
                                Context.CLIPBOARD_SERVICE,
                            ) as ClipboardManager
                            url = clipboard.primaryClip
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                ?.trim()
                                .orEmpty()
                            validation = ""
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = VrkaTokens.AccentContainer,
                        border = BorderStroke(1.dp, VrkaTokens.BorderActive),
                    ) {
                        Text(
                            stringResource(R.string.action_paste),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = VrkaTokens.AccentLight,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        if (validation.isNotBlank()) {
            Text(
                validation,
                style = MaterialTheme.typography.bodySmall,
                color = VrkaTokens.Error,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
        }

        Spacer(Modifier.height(18.dp))

        VrkaSectionContainer(
            shape = RoundedCornerShape(16.dp),
        ) {
            VrkaSegmentedControl(
                items = MediaMode.entries,
                selectedItem = mode,
                onItemSelected = { mode = it },
                label = { stringResource(it.labelRes) },
                isMonospace = true,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(14.dp))

            if (mode == MediaMode.VIDEO) {
                Text(
                    stringResource(R.string.home_video_resolution),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = VrkaMonoFamily,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = VrkaTokens.TextTertiary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                )
                ChoiceRow {
                    VideoQuality.entries.forEach { item ->
                        val chipLabel = stringResource(item.labelRes)
                        VrkaChip(
                            selected = quality == item,
                            onClick = { quality = item },
                            label = chipLabel,
                            isMonospace = true,
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.home_audio_format),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = VrkaMonoFamily,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = VrkaTokens.TextTertiary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                )
                ChoiceRow {
                    AudioFormat.entries.forEach { item ->
                        VrkaChip(
                            selected = audioFormat == item,
                            onClick = { audioFormat = item },
                            label = stringResource(item.labelRes).substringBefore(" ("),
                            isMonospace = true,
                        )
                    }
                }

                if (audioFormat == AudioFormat.MP3) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.home_mp3_bitrate),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = VrkaMonoFamily,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = VrkaTokens.TextTertiary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                    )
                    ChoiceRow {
                        listOf(320, 256, 224, 192, 160, 128).forEach { item ->
                            VrkaChip(
                                selected = bitrate == item,
                                onClick = { bitrate = item },
                                label = stringResource(R.string.home_kbps, item),
                                isMonospace = true,
                            )
                        }
                    }
                } else if (audioFormat == AudioFormat.OPUS) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.home_audio_quality),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = VrkaMonoFamily,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = VrkaTokens.TextTertiary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                    )
                    ChoiceRow {
                        VrkaChip(
                            selected = true,
                            onClick = {},
                            label = stringResource(R.string.home_prefer_native_opus),
                            isMonospace = true,
                        )
                    }
                } else if (audioFormat == AudioFormat.WAV) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.home_audio_quality),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = VrkaMonoFamily,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = VrkaTokens.TextTertiary,
                        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
                    )
                    ChoiceRow {
                        VrkaChip(
                            selected = true,
                            onClick = {},
                            label = stringResource(R.string.home_source_best_pcm),
                            isMonospace = true,
                        )
                    }
                }

                Text(
                    when (audioFormat) {
                        AudioFormat.MP3 -> stringResource(R.string.home_mp3_help)
                        AudioFormat.OPUS -> stringResource(R.string.home_opus_help)
                        AudioFormat.WAV -> stringResource(R.string.home_wav_help)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = VrkaMonoFamily),
                    color = VrkaTokens.TextTertiary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(16.dp))

        VrkaSectionContainer(
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { advanced = !advanced }
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = null,
                        tint = VrkaTokens.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (advanced) stringResource(R.string.action_hide_advanced_options) else stringResource(R.string.action_advanced_options),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = VrkaMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = VrkaTokens.TextPrimary,
                    )
                }
                Text(
                    if (advanced) "▲" else "▼",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = VrkaMonoFamily),
                    color = VrkaTokens.AccentLight,
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = advanced,
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = androidx.compose.animation.core.tween(200),
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(180),
                ),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = androidx.compose.animation.core.tween(160),
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(140),
                ),
            ) {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)) {
                    VrkaDivider()
                    Spacer(Modifier.height(8.dp))
                    if (mode == MediaMode.VIDEO) {
                        OptionToggle(stringResource(R.string.home_prefer_60fps), prefer60Fps) { prefer60Fps = it }
                    }
                    OptionToggle(stringResource(R.string.home_playlist_range), playlist) { playlist = it }
                    if (playlist) {
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CompactNumberField(
                                value = playlistStart,
                                label = stringResource(R.string.home_start),
                                modifier = Modifier.weight(1f),
                                onValueChange = { playlistStart = digitsOnly(it) },
                            )
                            CompactNumberField(
                                value = playlistEnd,
                                label = stringResource(R.string.home_end),
                                modifier = Modifier.weight(1f),
                                onValueChange = { playlistEnd = digitsOnly(it) },
                            )
                        }
                    }
                    OptionToggle(stringResource(R.string.home_download_subtitles), subtitles) { subtitles = it }
                    if (subtitles) {
                        OptionToggle(stringResource(R.string.home_auto_captions), automaticCaptions) {
                            automaticCaptions = it
                        }
                        if (mode == MediaMode.VIDEO) {
                            OptionToggle(stringResource(R.string.home_embed_subtitles), embedSubtitles) {
                                embedSubtitles = it
                            }
                        }
                        OutlinedTextField(
                            value = subtitleLanguages,
                            onValueChange = { subtitleLanguages = it.take(80) },
                            label = { Text(stringResource(R.string.home_subtitle_language)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                    OptionToggle(stringResource(R.string.home_embed_metadata), embedMetadata) {
                        embedMetadata = it
                    }
                    if (mode == MediaMode.AUDIO && audioFormat != AudioFormat.WAV) {
                        OptionToggle(stringResource(R.string.home_embed_thumbnail), embedThumbnail) {
                            embedThumbnail = it
                        }
                    }
                    OptionToggle(stringResource(R.string.home_sponsorblock), sponsorBlock) {
                        sponsorBlock = it
                    }
                    if (sponsorBlock) {
                        OutlinedTextField(
                            value = sponsorCategories,
                            onValueChange = { sponsorCategories = it.take(120) },
                            label = { Text(stringResource(R.string.home_sponsor_categories)) },
                            supportingText = {
                                Text(stringResource(R.string.home_sponsor_categories_hint))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.home_trim_optional),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VrkaTokens.TextSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = trimStart,
                            onValueChange = { trimStart = it.take(16) },
                            label = { Text(stringResource(R.string.home_start)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = trimEnd,
                            onValueChange = { trimEnd = it.take(16) },
                            label = { Text(stringResource(R.string.home_end)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        stringResource(R.string.home_network_headers),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = VrkaTokens.TextSecondary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    OutlinedTextField(
                        value = referer,
                        onValueChange = { referer = it.take(500) },
                        label = { Text(stringResource(R.string.home_referer)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = origin,
                        onValueChange = { origin = it.take(500) },
                        label = { Text(stringResource(R.string.home_origin)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.home_custom_headers, customHeaders.size),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VrkaTokens.TextSecondary,
                        )
                        VrkaTextButton(
                            text = stringResource(R.string.home_add_header),
                            onClick = {
                                customHeaders = customHeaders + ("" to "")
                            },
                        )
                    }

                    customHeaders.forEachIndexed { index, (hName, hVal) ->
                        val nameValidation = HeaderValidation.validateHeaderName(hName)
                        val valValidation = HeaderValidation.validateHeaderValue(hVal)
                        val isSensitive = HeaderValidation.isSensitiveHeader(hName)

                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = hName,
                                    onValueChange = { newName ->
                                        customHeaders = customHeaders.toMutableList().also {
                                            it[index] = newName to hVal
                                        }
                                    },
                                    label = { Text(stringResource(R.string.home_header_name)) },
                                    singleLine = true,
                                    isError = hName.isNotBlank() && nameValidation is HeaderValidationResult.Invalid,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = hVal,
                                    onValueChange = { newVal ->
                                        customHeaders = customHeaders.toMutableList().also {
                                            it[index] = hName to newVal
                                        }
                                    },
                                    label = { Text(stringResource(if (isSensitive) R.string.home_header_value_redacted else R.string.home_header_value)) },
                                    singleLine = true,
                                    isError = hVal.isNotBlank() && valValidation is HeaderValidationResult.Invalid,
                                    modifier = Modifier.weight(1.2f),
                                )
                                VrkaTextButton(
                                    text = "✕",
                                    onClick = {
                                        customHeaders = customHeaders.toMutableList().also { it.removeAt(index) }
                                    },
                                    color = VrkaTokens.Destructive,
                                )
                            }
                            if (hName.isNotBlank() && nameValidation is HeaderValidationResult.Invalid) {
                                Text(
                                    nameValidation.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VrkaTokens.Error,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                )
                            } else if (hVal.isNotBlank() && valValidation is HeaderValidationResult.Invalid) {
                                Text(
                                    valValidation.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VrkaTokens.Error,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                )
                            } else if (isSensitive) {
                                Text(
                                    stringResource(R.string.home_sensitive_header),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = VrkaTokens.AccentLight,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        ConfigurationSummary(
            mode = mode,
            quality = quality,
            prefer60Fps = prefer60Fps,
            audioFormat = audioFormat,
            bitrate = bitrate,
            playlist = playlist,
            playlistStart = playlistStart,
            playlistEnd = playlistEnd,
            subtitles = subtitles,
            trimStart = trimStart,
            trimEnd = trimEnd,
            sponsorBlock = sponsorBlock,
        )

        Spacer(Modifier.height(20.dp))

        VrkaPrimaryButton(
            text = stringResource(R.string.action_add_queue),
            iconRes = R.drawable.ic_download,
            enabled = url.isNotBlank(),
            onClick = {
                val issue = validateRequest(
                    context = context,
                    url = url,
                    playlist = playlist,
                    playlistStart = playlistStart,
                    playlistEnd = playlistEnd,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                )
                if (issue != null) {
                    validation = issue
                    return@VrkaPrimaryButton
                }

                var headerError: String? = null
                for ((k, v) in customHeaders) {
                    if (k.isNotBlank()) {
                        when (val r = HeaderValidation.validateHeaderName(k)) {
                            is HeaderValidationResult.Invalid -> {
                                headerError = context.getString(R.string.error_invalid_header, k, r.reason)
                                break
                            }
                            else -> {}
                        }
                        when (val r = HeaderValidation.validateHeaderValue(v)) {
                            is HeaderValidationResult.Invalid -> {
                                headerError = context.getString(R.string.error_invalid_header_value, k, r.reason)
                                break
                            }
                            else -> {}
                        }
                    }
                }
                if (headerError != null) {
                    validation = headerError
                    return@VrkaPrimaryButton
                }

                val customHeadersMap = HeaderValidation.parseHeaderPairs(customHeaders)
                val req = DownloadRequest(
                    url = url,
                    mode = mode,
                    quality = quality,
                    prefer60Fps = prefer60Fps,
                    audioFormat = audioFormat,
                    mp3Bitrate = bitrate,
                    isPlaylist = playlist,
                    playlistStart = playlistStart.toIntOrNull(),
                    playlistEnd = playlistEnd.toIntOrNull(),
                    downloadSubtitles = subtitles,
                    automaticCaptions = automaticCaptions,
                    embedSubtitles = embedSubtitles,
                    subtitleLanguages = subtitleLanguages,
                    embedMetadata = embedMetadata,
                    embedThumbnail = embedThumbnail,
                    sponsorBlock = sponsorBlock,
                    sponsorCategories = sponsorCategories,
                    trimStart = trimStart,
                    trimEnd = trimEnd,
                    referer = referer.trim(),
                    origin = origin.trim(),
                    customHeaders = customHeadersMap,
                )
                val isLocationValid = OutputPublisher.isTreePermissionValid(context, settings.outputTreeUri)
                val needsLocationPrompt = !settings.isDownloadLocationConfigured ||
                    settings.saveLocationMode == SaveLocationMode.ASK_EVERY_TIME ||
                    !isLocationValid

                if (needsLocationPrompt) {
                    pendingRequestToEnqueue = req
                    dialogSelectedUri = if (isLocationValid) settings.outputTreeUri else ""
                    setAsDefaultChecked = !settings.isDownloadLocationConfigured || !isLocationValid
                    showLocationDialog = true
                } else {
                    onEnqueue(req.copy(destinationTreeUri = settings.outputTreeUri))
                    url = ""
                    validation = ""
                }
            },
        )

        if (runtime.message.isNotBlank()) {
            Text(
                localizedRuntimeMessage(runtime),
                style = MaterialTheme.typography.bodySmall,
                color = VrkaTokens.TextSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (showLocationDialog && pendingRequestToEnqueue != null) {
            AlertDialog(
                onDismissRequest = {
                    showLocationDialog = false
                    pendingRequestToEnqueue = null
                },
                title = {
                    Text(
                        stringResource(R.string.home_download_location),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = VrkaTokens.TextPrimary,
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.home_choose_location_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = VrkaTokens.TextSecondary,
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = VrkaTokens.SurfaceCard,
                            border = BorderStroke(1.dp, VrkaTokens.BorderSubtle),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column {
                                    Text(
                                        stringResource(R.string.home_destination),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = VrkaTokens.TextTertiary,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        OutputPublisher.formatDisplayPath(dialogSelectedUri),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = VrkaMonoFamily),
                                        fontWeight = FontWeight.SemiBold,
                                        color = VrkaTokens.TextPrimary,
                                    )
                                }
                                VrkaOutlinedButton(
                                    text = stringResource(R.string.action_choose_location),
                                    onClick = { folderPicker.launch(null) },
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 36.dp,
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { setAsDefaultChecked = !setAsDefaultChecked }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = setAsDefaultChecked,
                                onCheckedChange = { setAsDefaultChecked = it },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.home_set_default),
                                style = MaterialTheme.typography.bodySmall,
                                color = VrkaTokens.TextPrimary,
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val req = pendingRequestToEnqueue ?: return@Button
                            val mode = if (setAsDefaultChecked) SaveLocationMode.REMEMBER_LOCATION else SaveLocationMode.ASK_EVERY_TIME
                            val destUri = dialogSelectedUri
                            if (setAsDefaultChecked) {
                                onUpdateDownloadLocation(destUri, mode, true)
                            } else {
                                onUpdateDownloadLocation(settings.outputTreeUri, mode, true)
                            }
                            onEnqueue(req.copy(destinationTreeUri = destUri))
                            url = ""
                            validation = ""
                            showLocationDialog = false
                            pendingRequestToEnqueue = null
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = VrkaTokens.Accent,
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.action_download),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                dismissButton = {
                    VrkaTextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = {
                            showLocationDialog = false
                            pendingRequestToEnqueue = null
                        },
                    )
                },
                containerColor = VrkaTokens.SurfaceElevated,
                shape = RoundedCornerShape(18.dp),
            )
        }

        Spacer(Modifier.navigationBarsPadding().height(110.dp))
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 18.dp, bottom = 7.dp),
    )
}

@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
private fun OptionToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = androidx.compose.material3.CheckboxDefaults.colors(
                checkedColor = VrkaTokens.Accent,
                checkmarkColor = Color.White,
            ),
        )
    }
}

@Composable
private fun CompactNumberField(
    value: String,
    label: String,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

private fun digitsOnly(value: String): String = value.filter(Char::isDigit).take(5)

private fun validateRequest(
    context: Context,
    url: String,
    playlist: Boolean,
    playlistStart: String,
    playlistEnd: String,
    trimStart: String,
    trimEnd: String,
): String? {
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
        return context.getString(R.string.error_enter_url)
    }
    if (playlist) {
        val start = playlistStart.toIntOrNull()
        val end = playlistEnd.toIntOrNull()
        if (playlistStart.isNotBlank() && (start == null || start < 1)) {
            return context.getString(R.string.error_playlist_start)
        }
        if (playlistEnd.isNotBlank() && (end == null || end < 1)) {
            return context.getString(R.string.error_playlist_end)
        }
        if (start != null && end != null && end < start) {
            return context.getString(R.string.error_playlist_order)
        }
    }
    val parsedStart = parseTimestamp(trimStart)
    val parsedEnd = parseTimestamp(trimEnd)
    if (trimStart.isNotBlank() && parsedStart == null) {
        return context.getString(R.string.error_trim_start)
    }
    if (trimEnd.isNotBlank() && parsedEnd == null) {
        return context.getString(R.string.error_trim_end)
    }
    if (parsedStart != null && parsedEnd != null && parsedEnd <= parsedStart) {
        return context.getString(R.string.error_trim_order)
    }
    return null
}

private fun parseTimestamp(value: String): Double? {
    if (value.isBlank()) return null
    val parts = value.trim().split(':')
    if (parts.size !in 1..3) return null
    val numbers = parts.map { it.toDoubleOrNull() ?: return null }
    if (numbers.any { it < 0 || !it.isFinite() }) return null
    if (parts.size > 1 && numbers.last() >= 60) return null
    if (parts.size == 3 && numbers[1] >= 60) return null
    return numbers.fold(0.0) { total, part -> total * 60 + part }
}


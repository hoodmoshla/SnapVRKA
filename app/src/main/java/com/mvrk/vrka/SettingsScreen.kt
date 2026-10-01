package com.mvrk.vrka

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
internal fun SettingsScreen(
    settings: AppSettings,
    runtime: RuntimeStatus,
    repository: SettingsRepository,
    onUpdateRuntime: (UpdatePreference) -> Unit,
    diagnostics: List<DiagnosticEntry> = emptyList(),
    onClearDiagnostics: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val upToDateMessage = stringResource(R.string.settings_check_uptodate_version, BuildConfig.VERSION_NAME)
    val sessionClearedMessage = stringResource(R.string.settings_session_cleared)
    val sessionClearFailedMessage = stringResource(R.string.settings_session_clear_failed)
    val diagnosticsCopiedMessage = stringResource(R.string.settings_diagnostics_copied)
    val updateManager = remember { ComponentUpdateManager.getInstance(context) }
    val componentMap by updateManager.components.collectAsStateWithLifecycle()
    val batchState by updateManager.batchState.collectAsStateWithLifecycle()
    val appUpdateManager = remember { com.mvrk.vrka.update.AppUpdateManager.getInstance(context, repository) }
    val appUpdateState by appUpdateManager.checkState.collectAsStateWithLifecycle()
    var manualCheckRequested by remember { mutableStateOf(false) }

    LaunchedEffect(appUpdateState) {
        if (manualCheckRequested) {
            when (val s = appUpdateState) {
                is com.mvrk.vrka.update.AppUpdateCheckState.UpToDate -> {
                    Toast.makeText(
                        context,
                        upToDateMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                    manualCheckRequested = false
                }
                is com.mvrk.vrka.update.AppUpdateCheckState.Error -> {
                    Toast.makeText(context, s.message, Toast.LENGTH_LONG).show()
                    manualCheckRequested = false
                }
                is com.mvrk.vrka.update.AppUpdateCheckState.UpdateAvailable -> {
                    manualCheckRequested = false
                }
                else -> Unit
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            scope.launch { repository.setOutputTree(uri.toString()) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = VrkaTokens.TextPrimary,
        )

        SettingsHeading(stringResource(R.string.settings_save_folder))
        VrkaSectionContainer {
            VrkaSettingRow(
                title = stringResource(R.string.home_destination),
                subtitle = OutputPublisher.formatDisplayPath(settings.outputTreeUri),
                isSubtitleMono = true,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VrkaOutlinedButton(
                        text = stringResource(R.string.action_change),
                        onClick = { folderPicker.launch(null) },
                        height = 34.dp,
                    )
                    if (settings.outputTreeUri.isNotBlank()) {
                        VrkaTextButton(
                            text = stringResource(R.string.action_reset),
                            onClick = { scope.launch { repository.setOutputTree("") } },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            VrkaDivider()
            Spacer(Modifier.height(10.dp))

            VrkaSegmentedControl(
                items = SaveLocationMode.entries,
                selectedItem = settings.saveLocationMode,
                onItemSelected = { mode -> scope.launch { repository.setSaveLocationMode(mode) } },
                label = { stringResource(it.labelRes) },
                isMonospace = true,
            )

            Text(
                text = if (settings.saveLocationMode == SaveLocationMode.REMEMBER_LOCATION) {
                    stringResource(R.string.settings_save_directly)
                } else {
                    stringResource(R.string.settings_ask_each_time)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = VrkaMonoFamily),
                color = VrkaTokens.TextTertiary,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }

        SettingsHeading(stringResource(R.string.settings_appearance))
        VrkaSectionContainer {
            Text(
                stringResource(R.string.settings_theme_mode),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = VrkaMonoFamily),
                fontWeight = FontWeight.SemiBold,
                color = VrkaTokens.TextPrimary,
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
            )
            VrkaSegmentedControl(
                items = ThemeMode.entries,
                selectedItem = settings.themeMode,
                onItemSelected = { mode -> scope.launch { repository.setThemeMode(mode) } },
                label = { stringResource(it.labelRes) },
                isMonospace = true,
            )

            Text(
                text = stringResource(
                    when (settings.themeMode) {
                        ThemeMode.AUTO -> R.string.settings_theme_auto_hint
                        ThemeMode.LIGHT -> R.string.settings_theme_light_hint
                        ThemeMode.DARK -> R.string.settings_theme_dark_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = VrkaMonoFamily),
                color = VrkaTokens.TextTertiary,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(10.dp))
            VrkaDivider()

            VrkaSettingRow(
                title = stringResource(R.string.settings_amoled_black),
                subtitle = stringResource(R.string.settings_amoled_subtitle),
            ) {
                Switch(
                    checked = settings.amoled,
                    enabled = resolveDarkMode(settings.themeMode, isSystemInDarkTheme()),
                    onCheckedChange = { scope.launch { repository.setAmoled(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = VrkaTokens.Accent,
                    ),
                )
            }

            Spacer(Modifier.height(10.dp))
            VrkaDivider()

            Text(
                stringResource(R.string.settings_font),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = VrkaMonoFamily),
                fontWeight = FontWeight.SemiBold,
                color = VrkaTokens.TextPrimary,
                modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
            )
            VrkaSegmentedControl(
                items = FontPreference.entries,
                selectedItem = settings.fontPreference,
                onItemSelected = { font -> scope.launch { repository.setFontPreference(font) } },
                label = { stringResource(it.labelRes) },
                isMonospace = true,
            )
        }

        SettingsHeading(stringResource(R.string.settings_components_updates))
        Text(
            stringResource(R.string.settings_components_body),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = VrkaMonoFamily),
            color = VrkaTokens.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )

        val isAnyCheckingOrUpdating = componentMap.values.any { it.isChecking || it.isUpdating }

        VrkaSectionContainer {
            componentMap.values.forEachIndexed { index, comp ->
                if (index > 0) VrkaDivider()
                val cleanInstalled = ComponentUpdateManager.cleanVersionString(comp.installedVersion)
                val displayVer = when {
                    cleanInstalled.equals("Unknown", ignoreCase = true) || cleanInstalled.isBlank() -> stringResource(R.string.settings_unknown)
                    cleanInstalled.startsWith("v") -> cleanInstalled
                    else -> "v$cleanInstalled"
                }
                VrkaSettingRow(
                    title = comp.name,
                    subtitle = stringResource(R.string.settings_installed, displayVer),
                    isSubtitleMono = true,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when {
                            comp.isUpdating -> {
                                val updateLabel = when (comp.updateState) {
                                    ComponentUpdateState.DOWNLOADING -> stringResource(R.string.settings_status_downloading)
                                    ComponentUpdateState.VERIFYING -> stringResource(R.string.settings_status_verifying)
                                    ComponentUpdateState.INSTALLING -> stringResource(R.string.settings_status_installing)
                                    else -> stringResource(R.string.settings_status_updating)
                                }
                                VrkaStatusBadge(updateLabel, VrkaTokens.Warning, isMonospace = true)
                            }
                            comp.isChecking -> {
                                VrkaStatusBadge(stringResource(R.string.settings_status_checking), VrkaTokens.AccentLight, isMonospace = true)
                            }
                            comp.updateState == ComponentUpdateState.UPDATE_SUCCESS -> {
                                VrkaStatusBadge(stringResource(R.string.settings_status_updated), VrkaTokens.Success, isMonospace = true)
                            }
                            comp.updateState == ComponentUpdateState.UPDATE_FAILED -> {
                                VrkaStatusBadge(stringResource(R.string.settings_status_failed), VrkaTokens.Error, isMonospace = true)
                                VrkaOutlinedButton(
                                    text = stringResource(R.string.action_retry),
                                    onClick = { updateManager.applyUpdate(comp.id, settings.updatePreference) },
                                    height = 32.dp,
                                )
                            }
                            comp.checkState == ComponentCheckState.UPDATE_AVAILABLE -> {
                                VrkaStatusBadge("v${comp.latestVersion}", VrkaTokens.AccentLight, isMonospace = true)
                                VrkaOutlinedButton(
                                    text = stringResource(R.string.action_update),
                                    onClick = { updateManager.applyUpdate(comp.id, settings.updatePreference) },
                                    height = 32.dp,
                                )
                            }
                            comp.checkState == ComponentCheckState.CHECK_FAILED -> {
                                VrkaStatusBadge(stringResource(R.string.settings_status_error), VrkaTokens.Error, isMonospace = true)
                                VrkaOutlinedButton(
                                    text = stringResource(R.string.action_retry),
                                    onClick = { updateManager.checkUpdate(comp.id, settings.updatePreference) },
                                    height = 32.dp,
                                )
                            }
                            comp.checkState == ComponentCheckState.UP_TO_DATE -> {
                                VrkaStatusBadge(stringResource(R.string.settings_status_ready), VrkaTokens.Success, isMonospace = true)
                            }
                            else -> {
                                VrkaStatusBadge(stringResource(R.string.settings_status_ready), VrkaTokens.Success, isMonospace = true)
                            }
                        }
                    }
                }
                if (comp.message.isNotBlank()) {
                    Text(
                        comp.message,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = VrkaMonoFamily),
                        color = if (comp.error != null) VrkaTokens.Error else VrkaTokens.TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            VrkaDivider()

            val isBusy = batchState in setOf(BatchOperationState.CHECKING, BatchOperationState.UPDATING) ||
                componentMap.values.any { it.isChecking || it.isUpdating }

            val checkButtonText = when {
                batchState == BatchOperationState.UPDATING || componentMap.values.any { it.isUpdating } -> stringResource(R.string.settings_updating_components)
                batchState == BatchOperationState.CHECKING || componentMap.values.any { it.isChecking } -> stringResource(R.string.settings_checking_updates)
                else -> stringResource(R.string.settings_check_all_updates)
            }

            VrkaOutlinedButton(
                text = checkButtonText,
                onClick = { updateManager.checkAllUpdates(settings.updatePreference) },
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                height = 40.dp,
            )

            Spacer(Modifier.height(10.dp))
            VrkaDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "yt-dlp",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = VrkaMonoFamily),
                    fontWeight = FontWeight.SemiBold,
                    color = VrkaTokens.TextPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UpdatePreference.entries.forEach { item ->
                        VrkaChip(
                            selected = settings.updatePreference == item,
                            onClick = {
                                scope.launch {
                                    repository.setUpdatePreference(item)
                                    updateManager.onChannelChanged(item)
                                }
                            },
                            label = stringResource(item.labelRes),
                            isMonospace = true,
                        )
                    }
                }
            }
        }

        SettingsHeading(stringResource(R.string.settings_browser_subsystems))
        VrkaSectionContainer {
            VrkaSettingRow(
                title = stringResource(R.string.settings_browser_engine),
                subtitle = stringResource(R.string.settings_browser_engine_subtitle),
            ) {
                VrkaStatusBadge(stringResource(R.string.settings_status_bundled), VrkaTokens.AccentLight, isMonospace = true)
            }

            VrkaDivider()

            VrkaSettingRow(
                title = stringResource(R.string.settings_ad_blocking),
                subtitle = "uBlock Origin",
            ) {
                VrkaStatusBadge(stringResource(R.string.settings_status_active), VrkaTokens.Success, isMonospace = true)
            }

            VrkaDivider()

            VrkaSettingRow(
                title = stringResource(R.string.settings_media_detection),
                subtitle = stringResource(R.string.settings_media_detection_subtitle),
            ) {
                VrkaStatusBadge(stringResource(R.string.settings_status_active), VrkaTokens.Success, isMonospace = true)
            }

            VrkaDivider()

            var showClearDialog by remember { mutableStateOf(false) }
            var isClearingSession by remember { mutableStateOf(false) }
            var clearSessionMessage by remember { mutableStateOf<String?>(null) }

            VrkaSettingRow(
                title = stringResource(R.string.settings_browser_session),
                subtitle = clearSessionMessage ?: stringResource(R.string.settings_browser_session_subtitle),
            ) {
                VrkaOutlinedButton(
                    text = if (isClearingSession) stringResource(R.string.settings_clearing) else stringResource(R.string.settings_clear_session),
                    onClick = { showClearDialog = true },
                    enabled = !isClearingSession,
                    height = 32.dp,
                )
            }

            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = {
                        Text(
                            stringResource(R.string.settings_clear_session_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = VrkaTokens.TextPrimary,
                        )
                    },
                    text = {
                        Text(
                            stringResource(R.string.settings_clear_session_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = VrkaTokens.TextSecondary,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showClearDialog = false
                                isClearingSession = true
                                scope.launch {
                                    val success = GeckoRuntimeManager.getInstance(context).clearBrowserSession()
                                    isClearingSession = false
                                    clearSessionMessage = if (success) sessionClearedMessage else sessionClearFailedMessage
                                }
                            }
                        ) {
                            Text(stringResource(R.string.action_clear), color = VrkaTokens.Accent)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearDialog = false }) {
                            Text(stringResource(R.string.action_cancel), color = VrkaTokens.TextSecondary)
                        }
                    },
                    containerColor = VrkaTokens.SurfaceCard,
                )
            }
        }

        SettingsHeading(stringResource(R.string.settings_concurrency))
        VrkaSectionContainer {
            Text(
                stringResource(R.string.settings_concurrency_body),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = VrkaMonoFamily,
                    lineHeight = 18.sp,
                ),
                color = VrkaTokens.TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        SettingsHeading(stringResource(R.string.settings_diagnostics))
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        var expandedDiagnosticId by remember { mutableStateOf<String?>(null) }

        VrkaSectionContainer {
            if (diagnostics.isEmpty()) {
                Text(
                    stringResource(R.string.settings_no_diagnostics),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = VrkaMonoFamily,
                        lineHeight = 18.sp,
                    ),
                    color = VrkaTokens.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (diagnostics.size == 1) stringResource(R.string.settings_diagnostics_count_one) else stringResource(R.string.settings_diagnostics_count, diagnostics.size),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = VrkaMonoFamily),
                        color = VrkaTokens.TextSecondary,
                    )
                    VrkaOutlinedButton(
                        text = stringResource(R.string.action_clear_all),
                        onClick = onClearDiagnostics,
                        height = 30.dp,
                    )
                }

                diagnostics.forEachIndexed { index, entry ->
                    if (index > 0) VrkaDivider()
                    val isExpanded = expandedDiagnosticId == entry.id
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val timeStr = java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss",
                                java.util.Locale.US,
                            ).format(java.util.Date(entry.timestamp))
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = VrkaMonoFamily),
                                color = VrkaTokens.TextTertiary,
                            )
                            VrkaStatusBadge(entry.failureCategory, VrkaTokens.Error, isMonospace = true)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = entry.title.ifBlank { entry.url },
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = VrkaMonoFamily),
                            fontWeight = FontWeight.SemiBold,
                            color = VrkaTokens.TextPrimary,
                            maxLines = 2,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.settings_stage, entry.stage),
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = VrkaMonoFamily),
                                color = VrkaTokens.AccentLight,
                            )
                            if (entry.quality.isNotBlank()) {
                                Text(
                                    text = "• " + stringResource(R.string.settings_quality, entry.quality),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = VrkaMonoFamily),
                                    color = VrkaTokens.TextSecondary,
                                )
                            }
                        }

                        if (entry.summary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.summary,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = VrkaMonoFamily),
                                color = VrkaTokens.Warning,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VrkaOutlinedButton(
                                text = stringResource(if (isExpanded) R.string.action_hide_details else R.string.action_view_details),
                                onClick = {
                                    expandedDiagnosticId = if (isExpanded) null else entry.id
                                },
                                height = 30.dp,
                            )

                            VrkaOutlinedButton(
                                text = stringResource(R.string.action_copy_details),
                                onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(entry.toFormattedString()))
                                    Toast.makeText(
                                        context,
                                        diagnosticsCopiedMessage,
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                },
                                height = 30.dp,
                            )
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF141218),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_url, entry.url),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = VrkaMonoFamily,
                                            fontSize = 11.sp,
                                        ),
                                        color = VrkaTokens.TextSecondary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.settings_method, entry.acquisitionMethod),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = VrkaMonoFamily,
                                            fontSize = 11.sp,
                                        ),
                                        color = VrkaTokens.TextSecondary,
                                    )
                                    if (entry.detail.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.settings_log_tail),
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = VrkaMonoFamily),
                                            color = VrkaTokens.TextTertiary,
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = entry.detail,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = VrkaMonoFamily,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                            ),
                                            color = VrkaTokens.TextPrimary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsHeading(stringResource(R.string.settings_about))
        val uriHandler = LocalUriHandler.current
        VrkaSectionContainer(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.app_name) + " v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = VrkaMonoFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = VrkaTokens.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_by),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = VrkaMonoFamily,
                        ),
                        color = VrkaTokens.TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = VrkaMonoFamily,
                        ),
                        color = VrkaTokens.TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = VrkaMonoFamily,
                        ),
                        color = VrkaTokens.TextTertiary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.about_features),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = VrkaMonoFamily,
                            lineHeight = 18.sp,
                        ),
                        color = VrkaTokens.TextSecondary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.about_license),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = VrkaMonoFamily,
                            lineHeight = 18.sp,
                        ),
                        color = VrkaTokens.TextTertiary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "GitHub",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = VrkaMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        ),
                        color = VrkaTokens.Accent,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/hoodmoshla/SnapVRKA")
                        },
                    )
                }

                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.vrka_logo_512),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.size(72.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            VrkaDivider()
            Spacer(modifier = Modifier.height(4.dp))

            VrkaSettingRow(
                title = stringResource(R.string.settings_updates),
                subtitle = when (val state = appUpdateState) {
                    is com.mvrk.vrka.update.AppUpdateCheckState.Checking -> stringResource(R.string.settings_checking_updates)
                    is com.mvrk.vrka.update.AppUpdateCheckState.UpdateAvailable -> stringResource(R.string.settings_check_available, state.release.version.toString())
                    is com.mvrk.vrka.update.AppUpdateCheckState.UpToDate -> stringResource(R.string.settings_check_uptodate)
                    is com.mvrk.vrka.update.AppUpdateCheckState.Error -> state.message
                    else -> stringResource(R.string.settings_check_prompt)
                },
                isSubtitleMono = true,
            ) {
                VrkaOutlinedButton(
                    text = if (appUpdateState is com.mvrk.vrka.update.AppUpdateCheckState.Checking) stringResource(R.string.settings_checking) else stringResource(R.string.action_check_updates),
                    onClick = {
                        manualCheckRequested = true
                        appUpdateManager.checkForUpdate(isManual = true)
                    },
                    enabled = appUpdateState !is com.mvrk.vrka.update.AppUpdateCheckState.Checking,
                    height = 32.dp,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            VrkaDivider()

            VrkaSettingRow(
                title = stringResource(R.string.settings_auto_update_check),
                subtitle = stringResource(R.string.settings_auto_update_check_subtitle),
            ) {
                Switch(
                    checked = settings.autoUpdateCheck,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setAutoUpdateCheck(enabled) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = VrkaTokens.Accent,
                    ),
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(110.dp))
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = VrkaMonoFamily,
            letterSpacing = 1.2.sp,
        ),
        fontWeight = FontWeight.Bold,
        color = VrkaTokens.TextTertiary,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp, start = 4.dp),
    )
}

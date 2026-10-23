package com.mvrk.vrka

internal enum class ComponentStatusTone {
    SUCCESS,
    WARNING,
    ERROR,
    ACCENT,
    NEUTRAL,
}

internal data class ComponentStatusPresentation(
    val label: String,
    val tone: ComponentStatusTone,
    val installedText: String = "",
    val secondaryText: String? = null,
) {
    companion object {
        fun from(status: ComponentStatus): ComponentStatusPresentation {
            val state = when {
                status.isUpdating -> ComponentStatusPresentation(
                    label = SnapVrkaArabic.text(when (status.updateState) {
                        ComponentUpdateState.DOWNLOADING -> "Downloading"
                        ComponentUpdateState.VERIFYING -> "Verifying"
                        ComponentUpdateState.INSTALLING -> "Installing"
                        else -> "Updating"
                    }),
                    tone = ComponentStatusTone.WARNING,
                )
                status.isChecking -> ComponentStatusPresentation(SnapVrkaArabic.text("Checking"), ComponentStatusTone.ACCENT)
                status.updateState == ComponentUpdateState.UPDATE_SUCCESS ->
                    ComponentStatusPresentation(SnapVrkaArabic.text("Updated"), ComponentStatusTone.SUCCESS)
                status.updateState == ComponentUpdateState.UPDATE_FAILED ->
                    ComponentStatusPresentation(SnapVrkaArabic.text("Failed"), ComponentStatusTone.ERROR)
                status.checkState == ComponentCheckState.UPDATE_AVAILABLE ->
                    ComponentStatusPresentation(
                    label = status.latestVersion?.let { versionLabel(it) } ?: SnapVrkaArabic.text("Update available"),
                    tone = ComponentStatusTone.ACCENT,
                    )
                status.checkState == ComponentCheckState.CHECK_FAILED ->
                    ComponentStatusPresentation(SnapVrkaArabic.text("Error"), ComponentStatusTone.ERROR)
                status.checkState == ComponentCheckState.UP_TO_DATE ->
                    ComponentStatusPresentation(SnapVrkaArabic.text("Ready"), ComponentStatusTone.SUCCESS)
                else -> ComponentStatusPresentation(SnapVrkaArabic.text("Not checked"), ComponentStatusTone.NEUTRAL)
            }
            return state.copy(
                installedText = SnapVrkaArabic.text("Installed: ${versionLabel(status.installedVersion)}"),
                secondaryText = status.message.takeIf(String::isNotBlank)?.let(SnapVrkaArabic::text)
                    ?: defaultSecondaryText(status)?.let(SnapVrkaArabic::text),
            )
        }

        fun versionLabel(installedVersion: String): String {
            val clean = ComponentUpdateManager.cleanVersionString(installedVersion)
            return when {
                clean.isBlank() || clean.equals("Unknown", ignoreCase = true) -> "Unknown"
                clean.startsWith("v", ignoreCase = true) -> clean
                else -> "v$clean"
            }.let(SnapVrkaArabic::text)
        }

        private fun defaultSecondaryText(status: ComponentStatus): String? = when {
            status.checkState == ComponentCheckState.UP_TO_DATE ->
                status.latestVersion?.let { SnapVrkaArabic.text("Up to date (${versionLabel(it)})") }
            status.checkState == ComponentCheckState.UPDATE_AVAILABLE ->
                status.latestVersion?.let { SnapVrkaArabic.text("Update available: ${versionLabel(it)}") }
            status.updateState == ComponentUpdateState.UPDATE_SUCCESS ->
                SnapVrkaArabic.text("Updated successfully to ${versionLabel(status.installedVersion)}")
            status.checkState == ComponentCheckState.CHECK_FAILED || status.updateState == ComponentUpdateState.UPDATE_FAILED ->
                status.error?.let { SnapVrkaArabic.text("Check failed: $it") }
            else -> null
        }
    }
}

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.graphics.shapes.RoundedPolygon

@Immutable
data class SettingsProfileState(
    val accountName: String,
    val accountEmail: String,
    val accountHandle: String,
    val accountImageUrl: String?,
    val accountBannerUrl: String?,
)

@Immutable
sealed interface SettingsProfileSlot {
    data object Loading : SettingsProfileSlot

    data object Hidden : SettingsProfileSlot

    data class Profile(
        val state: SettingsProfileState,
    ) : SettingsProfileSlot
}

@Immutable
data class SettingsContent(
    val highlights: List<SettingsHighlight>,
    val groups: List<SettingsGroup>,
)

@Immutable
data class SettingsGroup(
    val key: String,
    val title: String,
    val items: List<SettingsItem>,
)

enum class SettingsTone {
    Primary,
    Secondary,
    Tertiary,
    Neutral,
}

@Immutable
data class SettingsHighlight(
    val item: SettingsItem,
    val tone: SettingsTone,
    val glyph: RoundedPolygon,
)

@Immutable
data class SettingsItem(
    val key: String,
    val icon: Painter,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val showUpdateIndicator: Boolean = false,
    val accentColor: Color = Color.Unspecified,
    val onClick: () -> Unit,
)

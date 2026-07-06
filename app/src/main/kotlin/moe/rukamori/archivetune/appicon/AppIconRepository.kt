/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.appicon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.DrawableRes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.R
import javax.inject.Inject
import javax.inject.Singleton

data class AppIcon(
    val id: String,
    val name: String?,
    val author: String?,
    val githubAuthorUrl: String?,
    @DrawableRes val previewDrawableResId: Int,
    val aliasClassName: String,
    val isDefault: Boolean,
)

data class AppIconCatalog(
    val icons: List<AppIcon>,
    val selectedIconId: String,
)

@Serializable
private data class GeneratedAppIcon(
    val id: String,
    val name: String,
    val author: String,
    val githubAuthorUrl: String = "",
    val source: String,
    val drawableResourceName: String,
    val aliasClassName: String,
)

@Singleton
class AppIconRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val packageManager: PackageManager = context.packageManager
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun loadCatalog(): AppIconCatalog =
            withContext(Dispatchers.IO) {
                val icons = loadIcons()
                val selectedIcon = findSelectedIcon(icons)
                if (!isSelectionApplied(icons, selectedIcon)) {
                    applySelection(icons, selectedIcon)
                }
                AppIconCatalog(
                    icons = icons,
                    selectedIconId = selectedIcon.id,
                )
            }

        suspend fun selectIcon(iconId: String): AppIconCatalog =
            withContext(Dispatchers.IO + NonCancellable) {
                val icons = loadIcons()
                val selectedIcon =
                    icons.firstOrNull { icon -> icon.id == iconId }
                        ?: throw IllegalArgumentException("Unknown app icon ID.")
                if (!isSelectionApplied(icons, selectedIcon)) {
                    applySelection(icons, selectedIcon)
                }
                AppIconCatalog(
                    icons = icons,
                    selectedIconId = selectedIcon.id,
                )
            }

        private fun loadIcons(): List<AppIcon> {
            val generatedIcons =
                context.assets
                    .open(CatalogAssetPath)
                    .bufferedReader()
                    .use { reader -> json.decodeFromString<List<GeneratedAppIcon>>(reader.readText()) }
                    .map { generated ->
                        val drawableResId =
                            context.resources.getIdentifier(
                                generated.drawableResourceName,
                                "drawable",
                                context.packageName,
                            )
                        check(drawableResId != 0) {
                            "Missing generated drawable ${generated.drawableResourceName} for ${generated.source}."
                        }
                        AppIcon(
                            id = generated.id,
                            name = generated.name,
                            author = generated.author,
                            githubAuthorUrl = generated.githubAuthorUrl.takeIf(String::isNotBlank),
                            previewDrawableResId = drawableResId,
                            aliasClassName = generated.aliasClassName,
                            isDefault = false,
                        )
                    }

            return buildList(generatedIcons.size + 1) {
                add(
                    AppIcon(
                        id = DefaultIconId,
                        name = null,
                        author = null,
                        githubAuthorUrl = null,
                        previewDrawableResId = R.drawable.app_icon_small,
                        aliasClassName = "${context.packageName}.launcher.DefaultIconAlias",
                        isDefault = true,
                    ),
                )
                addAll(generatedIcons)
            }
        }

        private fun findSelectedIcon(icons: List<AppIcon>): AppIcon =
            icons.firstOrNull { icon ->
                packageManager.getComponentEnabledSetting(icon.componentName()) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
                ?: icons.first { icon -> icon.id == DefaultIconId }

        private fun isSelectionApplied(
            icons: List<AppIcon>,
            selectedIcon: AppIcon,
        ): Boolean =
            icons.count(::isEffectivelyEnabled) == 1 &&
                isEffectivelyEnabled(selectedIcon)

        private fun isEffectivelyEnabled(icon: AppIcon): Boolean =
            when (packageManager.getComponentEnabledSetting(icon.componentName())) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon.isDefault
                else -> false
            }

        private fun applySelection(
            icons: List<AppIcon>,
            selectedIcon: AppIcon,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.setComponentEnabledSettings(
                    icons.map { icon ->
                        PackageManager.ComponentEnabledSetting(
                            icon.componentName(),
                            if (icon.id == selectedIcon.id) {
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            } else {
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            },
                            PackageManager.DONT_KILL_APP,
                        )
                    },
                )
            } else {
                val previousStates =
                    icons.associateWith { icon ->
                        packageManager.getComponentEnabledSetting(icon.componentName())
                    }
                try {
                    packageManager.setComponentEnabledSetting(
                        selectedIcon.componentName(),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                    icons
                        .asSequence()
                        .filterNot { icon -> icon.id == selectedIcon.id }
                        .forEach { icon ->
                            packageManager.setComponentEnabledSetting(
                                icon.componentName(),
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP,
                            )
                        }
                } catch (error: RuntimeException) {
                    previousStates.forEach { (icon, state) ->
                        runCatching {
                            packageManager.setComponentEnabledSetting(
                                icon.componentName(),
                                state,
                                PackageManager.DONT_KILL_APP,
                            )
                        }
                    }
                    throw error
                }
            }

            check(isSelectionApplied(icons, selectedIcon)) {
                "Unable to apply launcher icon ${selectedIcon.id} exclusively."
            }
        }

        private fun AppIcon.componentName(): ComponentName = ComponentName(context.packageName, aliasClassName)

        private companion object {
            const val CatalogAssetPath = "icon_pack/catalog.json"
            const val DefaultIconId = "default"
        }
    }

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

private data class LauncherAlias(
    val id: String,
    val componentName: ComponentName,
    val isDefault: Boolean,
)

@Singleton
class AppIconRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val packageManager: PackageManager = context.packageManager
        private val json = Json { ignoreUnknownKeys = true }
        private val stateLock = Mutex()

        suspend fun loadCatalog(): AppIconCatalog =
            withContext(Dispatchers.IO) {
                stateLock.withLock {
                    val generatedIcons = readGeneratedIcons()
                    val selectedAlias = reconcileLauncherEntry(launcherAliases(generatedIcons))
                    AppIconCatalog(
                        icons = appIcons(generatedIcons),
                        selectedIconId = selectedAlias.id,
                    )
                }
            }

        suspend fun restoreLauncherEntry() {
            withContext(Dispatchers.IO) {
                stateLock.withLock {
                    reconcileLauncherEntry(launcherAliases(readGeneratedIcons()))
                }
            }
        }

        suspend fun selectIcon(iconId: String): AppIconCatalog =
            withContext(Dispatchers.IO + NonCancellable) {
                stateLock.withLock {
                    val generatedIcons = readGeneratedIcons()
                    val aliases = launcherAliases(generatedIcons)
                    val selectedAlias =
                        aliases.firstOrNull { alias -> alias.id == iconId }
                            ?: throw IllegalArgumentException("Unknown app icon ID.")
                    if (!isSelectionApplied(aliases, selectedAlias)) {
                        applySelection(aliases, selectedAlias)
                    }
                    AppIconCatalog(
                        icons = appIcons(generatedIcons),
                        selectedIconId = selectedAlias.id,
                    )
                }
            }

        private fun readGeneratedIcons(): List<GeneratedAppIcon> =
            context.assets
                .open(CatalogAssetPath)
                .bufferedReader()
                .use { reader -> json.decodeFromString<List<GeneratedAppIcon>>(reader.readText()) }

        private fun launcherAliases(generatedIcons: List<GeneratedAppIcon>): List<LauncherAlias> =
            buildList(generatedIcons.size + 1) {
                add(
                    LauncherAlias(
                        id = DefaultIconId,
                        componentName =
                            ComponentName(
                                context.packageName,
                                "${context.packageName}.launcher.DefaultIconAlias",
                            ),
                        isDefault = true,
                    ),
                )
                generatedIcons.mapTo(this) { generated ->
                    LauncherAlias(
                        id = generated.id,
                        componentName = ComponentName(context.packageName, generated.aliasClassName),
                        isDefault = false,
                    )
                }
            }

        private fun appIcons(generatedIcons: List<GeneratedAppIcon>): List<AppIcon> =
            buildList(generatedIcons.size + 1) {
                add(
                    AppIcon(
                        id = DefaultIconId,
                        name = null,
                        author = null,
                        githubAuthorUrl = null,
                        previewDrawableResId = R.drawable.app_icon_small,
                        isDefault = true,
                    ),
                )
                generatedIcons.mapTo(this) { generated -> communityAppIcon(generated) }
            }

        private fun communityAppIcon(generated: GeneratedAppIcon): AppIcon {
            val drawableResId =
                context.resources.getIdentifier(
                    generated.drawableResourceName,
                    "drawable",
                    context.packageName,
                )
            check(drawableResId != 0) {
                "Missing generated drawable ${generated.drawableResourceName} for ${generated.source}."
            }
            return AppIcon(
                id = generated.id,
                name = generated.name,
                author = generated.author,
                githubAuthorUrl = generated.githubAuthorUrl.takeIf(String::isNotBlank),
                previewDrawableResId = drawableResId,
                isDefault = false,
            )
        }

        private fun reconcileLauncherEntry(aliases: List<LauncherAlias>): LauncherAlias {
            val selectedAlias = findSelectedAlias(aliases)
            if (!isSelectionApplied(aliases, selectedAlias)) {
                applySelection(aliases, selectedAlias)
            }
            return selectedAlias
        }

        private fun findSelectedAlias(aliases: List<LauncherAlias>): LauncherAlias =
            aliases.firstOrNull { alias ->
                packageManager.getComponentEnabledSetting(alias.componentName) ==
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }
                ?: aliases.first(LauncherAlias::isDefault)

        private fun isSelectionApplied(
            aliases: List<LauncherAlias>,
            selectedAlias: LauncherAlias,
        ): Boolean =
            aliases.count(::isEffectivelyEnabled) == 1 &&
                isEffectivelyEnabled(selectedAlias)

        private fun isEffectivelyEnabled(alias: LauncherAlias): Boolean =
            when (packageManager.getComponentEnabledSetting(alias.componentName)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> alias.isDefault
                else -> false
            }

        private fun applySelection(
            aliases: List<LauncherAlias>,
            selectedAlias: LauncherAlias,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.setComponentEnabledSettings(
                    aliases.map { alias ->
                        PackageManager.ComponentEnabledSetting(
                            alias.componentName,
                            if (alias.id == selectedAlias.id) {
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
                    aliases.associateWith { alias ->
                        packageManager.getComponentEnabledSetting(alias.componentName)
                    }
                try {
                    packageManager.setComponentEnabledSetting(
                        selectedAlias.componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                    aliases
                        .asSequence()
                        .filterNot { alias -> alias.id == selectedAlias.id }
                        .forEach { alias ->
                            packageManager.setComponentEnabledSetting(
                                alias.componentName,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP,
                            )
                        }
                } catch (error: RuntimeException) {
                    previousStates.forEach { (alias, state) ->
                        runCatching {
                            packageManager.setComponentEnabledSetting(
                                alias.componentName,
                                state,
                                PackageManager.DONT_KILL_APP,
                            )
                        }
                    }
                    throw error
                }
            }

            check(isSelectionApplied(aliases, selectedAlias)) {
                "Unable to apply launcher icon ${selectedAlias.id} exclusively."
            }
        }

        private companion object {
            const val CatalogAssetPath = "icon_pack/catalog.json"
            const val DefaultIconId = "default"
        }
    }

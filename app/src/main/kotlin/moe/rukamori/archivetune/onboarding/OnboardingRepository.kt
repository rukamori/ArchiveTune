/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import com.google.common.collect.ImmutableList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.LaunchCountKey
import moe.rukamori.archivetune.constants.OnboardingCompletedKey
import moe.rukamori.archivetune.utils.dataStore
import javax.inject.Inject

class OnboardingRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun observeShouldShowOnboarding(): Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[OnboardingCompletedKey] != true &&
                    (preferences[LaunchCountKey] ?: 0) <= 0
            }

        fun currentPermissions(): ImmutableList<OnboardingPermissionData> =
            ImmutableList.copyOf(
                buildList {
                    add(runtimePermissionData(OnboardingPermissionId.NOTIFICATIONS, notificationPermission()))
                    add(runtimePermissionData(OnboardingPermissionId.LOCAL_AUDIO, localAudioPermission()))
                    add(runtimePermissionData(OnboardingPermissionId.MICROPHONE, Manifest.permission.RECORD_AUDIO))
                    add(runtimePermissionData(OnboardingPermissionId.BLUETOOTH_CONNECT, bluetoothConnectPermission()))
                    add(installGrantedData(OnboardingPermissionId.NETWORK))
                    add(installGrantedData(OnboardingPermissionId.PLAYBACK_SERVICE))
                    add(installGrantedData(OnboardingPermissionId.AUDIO_SETTINGS))

                    if (BuildConfig.DISTRIBUTION == DISTRIBUTION_GMS) {
                        add(appInstallationData())
                        add(runtimePermissionData(OnboardingPermissionId.BLUETOOTH_SCAN, bluetoothScanPermission()))
                    }
                },
            )

        suspend fun markCompleted() {
            context.dataStore.edit { preferences ->
                preferences[OnboardingCompletedKey] = true
            }
        }

        private fun notificationPermission(): String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else {
                null
            }

        private fun localAudioPermission(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        private fun bluetoothConnectPermission(): String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_CONNECT
            } else {
                null
            }

        private fun bluetoothScanPermission(): String? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Manifest.permission.BLUETOOTH_SCAN
            } else {
                null
            }

        private fun runtimePermissionData(
            id: OnboardingPermissionId,
            permission: String?,
        ): OnboardingPermissionData =
            if (permission == null) {
                installGrantedData(id)
            } else if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                OnboardingPermissionData(
                    id = id,
                    status = OnboardingPermissionStatus.ALLOWED,
                    action = null,
                )
            } else {
                OnboardingPermissionData(
                    id = id,
                    status = OnboardingPermissionStatus.NEEDS_ACTION,
                    action = OnboardingPermissionAction.RequestRuntimePermission(permission),
                )
            }

        private fun appInstallationData(): OnboardingPermissionData {
            val isAllowed =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                    context.packageManager.canRequestPackageInstalls()

            return if (isAllowed) {
                OnboardingPermissionData(
                    id = OnboardingPermissionId.APP_INSTALLATION,
                    status = OnboardingPermissionStatus.ALLOWED,
                    action = null,
                )
            } else {
                OnboardingPermissionData(
                    id = OnboardingPermissionId.APP_INSTALLATION,
                    status = OnboardingPermissionStatus.NEEDS_ACTION,
                    action = OnboardingPermissionAction.OpenInstallPackagesSettings,
                )
            }
        }

        private fun installGrantedData(id: OnboardingPermissionId): OnboardingPermissionData =
            OnboardingPermissionData(
                id = id,
                status = OnboardingPermissionStatus.ALLOWED_BY_INSTALL,
                action = null,
            )

        private companion object {
            const val DISTRIBUTION_GMS = "gms"
        }
    }

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ads

import android.app.Application
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import moe.rukamori.archivetune.ads.data.StartIoSupportAdRepository

object SupportAdsInitializer {
    fun initialize(application: Application) {
        EntryPointAccessors
            .fromApplication(application, SupportAdsEntryPoint::class.java)
            .supportAdRepository()
            .initialize(application)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SupportAdsEntryPoint {
    fun supportAdRepository(): StartIoSupportAdRepository
}

package moe.rukamori.archivetune.ads

import android.app.Application
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import moe.rukamori.archivetune.ads.data.GoogleSupportAdRepository

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
    fun supportAdRepository(): GoogleSupportAdRepository
}

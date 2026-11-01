/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import moe.rukamori.archivetune.models.NewsItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsRepository
    @Inject
    constructor() {
        suspend fun fetchNews(): List<NewsItem> = emptyList()

        suspend fun fetchNewsContent(id: String): String = ""

        fun getCachedItem(id: String): NewsItem? = null
    }

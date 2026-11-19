/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.recap

import com.google.common.collect.ImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveRecapUseCase @Inject constructor(private val repository: RecapRepository) {
    internal operator fun invoke(year: Int, month: Int? = null): Flow<RecapResult> =
        repository.changes().mapLatest {
            val now = LocalDateTime.now()
            val start = LocalDate.of(year, month ?: 1, 1).atStartOfDay()
            val periodEnd = if (month == null) start.plusYears(1) else start.plusMonths(1)
            val snapshot = repository.load(start, minOf(periodEnd, now))
            val firstYear = snapshot.firstTimestamp?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC).year
            } ?: now.year
            RecapResult(
                data = snapshot.toData(year, month, now.toLocalDate()),
                years = ImmutableList.copyOf((now.year downTo minOf(firstYear, now.year)).toList()),
            )
        }.flowOn(Dispatchers.Default)

    private fun RecapSnapshot.toData(year: Int, month: Int?, today: LocalDate): RecapData {
        val monthly = LongArray(12)
        val weekdays = LongArray(7)
        val hours = LongArray(24)
        val days = sortedSetOf<LocalDate>()
        dayHours.forEach { row ->
            val date = LocalDate.parse(row.day)
            monthly[date.monthValue - 1] += row.milliseconds
            weekdays[date.dayOfWeek.value - 1] += row.milliseconds
            hours[row.hour] += row.milliseconds
            days.add(date)
        }
        var longest = 0
        var streak = 0
        var previous: LocalDate? = null
        days.forEach { day ->
            streak = if (previous?.plusDays(1) == day) streak + 1 else 1
            longest = maxOf(longest, streak)
            previous = day
        }
        val chapters = buildList {
            add(RecapChapter.INTRO)
            add(RecapChapter.TIME)
            if (songs.isNotEmpty()) {
                add(RecapChapter.TRACK)
                add(RecapChapter.SONGS)
            }
            if (artists.isNotEmpty()) add(RecapChapter.ARTISTS)
            if (albums.isNotEmpty()) add(RecapChapter.ALBUMS)
            add(RecapChapter.MONTHS)
            add(RecapChapter.HABITS)
            add(RecapChapter.SUMMARY)
            add(RecapChapter.END)
        }
        val monthArtists = monthlyArtists.distinctBy { it.month }.associateBy { it.month }
        return RecapData(
            year, month, today, totals.milliseconds, totals.plays, totals.tracks,
            songs.entries(RecapEntryKind.SONG), artists.entries(RecapEntryKind.ARTIST), albums.entries(RecapEntryKind.ALBUM),
            ImmutableList.copyOf((1..12).map {
                RecapMonth(it, monthly[it - 1], year < today.year || year == today.year && it <= today.monthValue,
                    monthArtists[it]?.let { artist ->
                        RecapEntry(artist.id, artist.title, "", artist.artwork, artist.milliseconds,
                            artist.plays, RecapEntryKind.ARTIST)
                    })
            }),
            RecapHabits(days.size, longest, monthly.peak()?.plus(1), weekdays.peak()?.plus(1), hours.peak()),
            ImmutableList.copyOf(chapters),
        )
    }

    private fun LongArray.peak(): Int? = indices.maxByOrNull { this[it] }?.takeIf { this[it] > 0L }

    private fun List<RecapRankedRow>.entries(kind: RecapEntryKind): ImmutableList<RecapEntry> =
        ImmutableList.copyOf(map { RecapEntry(it.id, it.title, it.subtitle, it.artwork, it.milliseconds, it.plays, kind) })
}

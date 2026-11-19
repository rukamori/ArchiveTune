/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.recap

import android.content.Context
import android.text.format.DateFormat
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.R
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

internal class RecapScene(val base: IntArray, val glow: IntArray, val light: Boolean)

internal object RecapAppearance {
    const val Foreground = 0xFFFFFFFF.toInt()
    const val Secondary = 0xB3FFFFFF.toInt()
    const val Surface = 0x33FFFFFF
    const val Ink = 0xFF1C1C1E.toInt()

    private val Pastel = RecapScene(intArrayOf(0xFFFBF3D5.toInt(), 0xFFF3EDC4.toInt(), 0xFFDDEFC9.toInt()),
        intArrayOf(0xFFF8DC7C.toInt(), 0xFFB5E6C2.toInt(), 0xFFFFF4DC.toInt()), light = true)
    private val Lagoon = RecapScene(intArrayOf(0xFF5CB9DD.toInt(), 0xFF4A92A8.toInt(), 0xFF27474F.toInt()),
        intArrayOf(0xFF7FD6EE.toInt(), 0xFF3E7F92.toInt(), 0xFF8FD3C5.toInt()), light = false)
    private val Cobalt = RecapScene(intArrayOf(0xFF2A5BD0.toInt(), 0xFF3F78E0.toInt(), 0xFF1D2C47.toInt()),
        intArrayOf(0xFF4F8BF0.toInt(), 0xFF3AA5A0.toInt(), 0xFF8E5A3C.toInt()), light = false)
    private val Ember = RecapScene(intArrayOf(0xFFF0A23A.toInt(), 0xFFE0703A.toInt(), 0xFF4A2230.toInt()),
        intArrayOf(0xFFFFC85A.toInt(), 0xFFE2565A.toInt(), 0xFF6CCB9A.toInt()), light = false)
    private val Grove = RecapScene(intArrayOf(0xFF4FB98A.toInt(), 0xFF2F8F86.toInt(), 0xFF17363A.toInt()),
        intArrayOf(0xFF9BE3A5.toInt(), 0xFF2FA7C9.toInt(), 0xFFF2C14E.toInt()), light = false)

    private val atmospheres = listOf(
        intArrayOf(0xFFF7C948.toInt(), 0xFF7BDCB5.toInt(), 0xFF4FB3E8.toInt(), 0xFFF29E4C.toInt()),
        intArrayOf(0xFFF59A3C.toInt(), 0xFFF6D04D.toInt(), 0xFF6FDDB0.toInt(), 0xFFF06A5D.toInt()),
        intArrayOf(0xFF5B8CF0.toInt(), 0xFF7FD6EE.toInt(), 0xFFB79CF2.toInt(), 0xFF6FDDB0.toInt()),
    )

    fun scene(chapter: RecapChapter): RecapScene = when (chapter) {
        RecapChapter.INTRO -> Pastel
        RecapChapter.TIME, RecapChapter.HABITS -> Lagoon
        RecapChapter.ARTISTS -> Ember
        RecapChapter.ALBUMS -> Grove
        else -> Cobalt
    }

    fun atmosphere(year: Int): IntArray = atmospheres[Math.floorMod(year, atmospheres.size)]
}

internal data class RecapFact(val label: String, val value: String)

internal class RecapText(private val context: Context) {
    private val locale = context.resources.configuration.locales[0]
    private val numbers = NumberFormat.getIntegerInstance(locale)

    fun number(value: Long): String = numbers.format(value)
    fun minutes(milliseconds: Long): String = context.getString(R.string.recap_minutes_value, number(milliseconds / 60_000))
    fun plays(value: Long): String = context.resources.getQuantityString(
        R.plurals.recap_plays_value, value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), number(value),
    )
    fun days(value: Int): String = context.resources.getQuantityString(R.plurals.recap_days_value, value, number(value.toLong()))
    fun month(month: Int, short: Boolean = false): String = Month.of(month).getDisplayName(
        if (short) TextStyle.SHORT else TextStyle.FULL, locale,
    )
    fun period(year: Int, month: Int?): String = month?.let {
        LocalDate.of(year, it, 1).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale))
    } ?: year.toString()

    fun period(data: RecapData): String {
        val period = period(data.year, data.month)
        return if (data.month == null && data.year == data.throughDate.year) {
            context.getString(R.string.recap_period_so_far, period)
        } else period
    }

    fun chapterTitle(chapter: RecapChapter): String = context.getString(when (chapter) {
        RecapChapter.INTRO -> R.string.recap_story_intro
        RecapChapter.TIME -> R.string.year_in_music_minutes_label
        RecapChapter.TRACK -> R.string.year_in_music_top_track
        RecapChapter.SONGS -> R.string.top_songs
        RecapChapter.ARTISTS -> R.string.top_artists
        RecapChapter.ALBUMS -> R.string.albums
        RecapChapter.MONTHS -> R.string.recap_months
        RecapChapter.HABITS -> R.string.recap_habits
        RecapChapter.SUMMARY -> R.string.recap_summary
        RecapChapter.END -> R.string.recap_story_end
    })

    fun facts(data: RecapData): ImmutableList<RecapFact> = ImmutableList.copyOf(buildList {
        add(RecapFact(context.getString(R.string.recap_active_days), number(data.habits.activeDays.toLong())))
        add(RecapFact(context.getString(R.string.recap_longest_streak),
            context.resources.getQuantityString(R.plurals.recap_days_value, data.habits.longestStreak,
                number(data.habits.longestStreak.toLong()))))
        if (data.month == null) data.habits.busiestMonth?.let {
            add(RecapFact(context.getString(R.string.recap_busiest_month), month(it)))
        }
        data.habits.weekday?.let {
            add(RecapFact(context.getString(R.string.recap_weekday), DayOfWeek.of(it).getDisplayName(TextStyle.FULL, locale)))
        }
        data.habits.hour?.let {
            val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")
            add(RecapFact(context.getString(R.string.recap_hour), LocalTime.of(it, 0).format(DateTimeFormatter.ofPattern(pattern, locale))))
        }
    })
}
